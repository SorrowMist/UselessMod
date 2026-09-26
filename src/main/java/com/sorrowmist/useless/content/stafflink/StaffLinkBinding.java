package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.network.StaffLinkSyncPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 潜行右键容器 → 绑定 / 解绑。
 *
 * <p>绑定只登记「这个坐标属于这张网络」并给一条默认线路；具体怎么搬由玩家在界面里改。</p>
 *
 * <p>按住 Ctrl 时走批量：把和点击位置<b>连在一起的同种方块</b>一次性全绑上，
 * 不用一格一格点。判断「同种」用的是方块类型（{@code BlockState#getBlock()}），
 * 所以一排同型号的机器算一批，中间夹了一台别的机器就断开。</p>
 */
public final class StaffLinkBinding {
    /** 新锚点的默认单次搬运量。 */
    private static final int DEFAULT_AMOUNT = 16;
    /** 新锚点的默认搬运周期（tick）。取小值，绑完就能明显看到东西在动。 */
    private static final int DEFAULT_INTERVAL = 5;
    /**
     * 一次批量操作最多处理多少台机器。
     *
     * <p>玩家有可能圈到一大片（比如整面墙的储罐），不设上限的话一次点击要跑几百次
     * 能力探测，会卡住服务端 tick。512 台已经远超正常用量。</p>
     */
    private static final int BATCH_LIMIT = 512;

    private StaffLinkBinding() {
    }

    public static void toggle(ServerLevel level, ServerPlayer player, ItemStack staff, BlockPos pos) {
        StaffLinkNetwork network = StaffLinkManager.activeNetwork(level.getServer(), staff, true);
        if (network == null) {
            return;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(level.getServer());
        GlobalPos anchor = GlobalPos.of(level.dimension(), pos.immutable());

        if (network.isBound(anchor)) {
            network.detach(anchor);
            data.markDirty();
            display(player, "gui.useless_mod.wireless_logistics.unbound", anchor, ChatFormatting.GREEN);
            chime(level, pos, false);
        } else {
            if (!bindOne(level, player, network, pos)) {
                return;
            }
            data.markDirty();
            display(player, "gui.useless_mod.wireless_logistics.bound", anchor, ChatFormatting.GREEN);
            chime(level, pos, true);
        }

        finish(level, player, network, data);
    }

    /**
     * 批量绑定 / 解绑：把与 {@code origin} 连成一片的同种方块一起处理。
     *
     * <p>方向由起点决定——起点已经绑过了就当「整批解绑」，否则整批绑定。这样玩家
     * 不用先想清楚「我现在是在加还是在减」，看一眼起点状态就知道结果。</p>
     */
    public static void toggleBatch(ServerLevel level, ServerPlayer player, ItemStack staff, BlockPos origin) {
        StaffLinkNetwork network = StaffLinkManager.activeNetwork(level.getServer(), staff, true);
        if (network == null) {
            return;
        }
        if (!StaffLinkTargets.isBindable(level, origin)) {
            notify(player, "gui.useless_mod.wireless_logistics.not_bindable", ChatFormatting.YELLOW);
            return;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(level.getServer());
        GlobalPos originAnchor = GlobalPos.of(level.dimension(), origin.immutable());
        boolean unbinding = network.isBound(originAnchor);

        List<BlockPos> group = collectGroup(level, origin);
        int changed = 0;
        for (BlockPos pos : group) {
            if (unbinding) {
                GlobalPos anchor = GlobalPos.of(level.dimension(), pos.immutable());
                if (network.isBound(anchor)) {
                    network.detach(anchor);
                    changed++;
                }
            } else {
                if (bindOne(level, player, network, pos)) {
                    changed++;
                }
            }
        }

        if (changed == 0) {
            // 起点可绑定、但整批里没有一台产生变化（例如批量绑定但全都早就绑过了）。
            notify(player, unbinding
                    ? "gui.useless_mod.wireless_logistics.batch_none_unbound"
                    : "gui.useless_mod.wireless_logistics.batch_none_bound", ChatFormatting.YELLOW);
            return;
        }

        data.markDirty();
        player.displayClientMessage(Component.translatable(unbinding
                        ? "gui.useless_mod.wireless_logistics.batch_unbound"
                        : "gui.useless_mod.wireless_logistics.batch_bound", changed)
                .withStyle(ChatFormatting.GREEN), true);
        chime(level, origin, !unbinding);

        finish(level, player, network, data);
    }

    /**
     * 绑定单台机器，成功返回 true。
     *
     * <p>不在这里发包 / 存盘：批量时几十台一起改，中途同步只会让界面闪。
     * 调用方改完统一走 {@link #finish}。</p>
     */
    private static boolean bindOne(ServerLevel level, ServerPlayer player, StaffLinkNetwork network, BlockPos pos) {
        if (!StaffLinkTargets.isBindable(level, pos)) {
            return false;
        }
        GlobalPos anchor = GlobalPos.of(level.dimension(), pos.immutable());
        if (network.isBound(anchor)) {
            return false;
        }
        LinkMedium medium = StaffLinkTargets.defaultMediumFor(level, pos);
        if (medium == null) {
            return false;
        }
        // 网络里还没有释放端时，第一个绑定的容器就当释放端——绑两个容器即可跑起来。
        LinkFlow flow = network.hasReleaseRoute() ? LinkFlow.ABSORB : LinkFlow.RELEASE;
        // 新锚点默认关闭：要搬什么由玩家显式打开，免得一绑上就开始动别人的库存。
        network.putRoute(new StaffLinkRoute(anchor, 0, false, flow, medium,
                DEFAULT_AMOUNT, DEFAULT_INTERVAL, null, LinkTrigger.ALWAYS, 0, List.of()));
        return true;
    }

    /**
     * 从 {@code origin} 出发广度优先收集连成一片的同种方块。
     *
     * <p>只走 6 个正交方向、只接受方块类型相同的位置，因此斜角相邻不算连通。
     * 结果按坐标排序，保证同一批机器每次绑定顺序一致（谁当释放端是可预期的）。</p>
     */
    private static List<BlockPos> collectGroup(ServerLevel level, BlockPos origin) {
        BlockState originState = level.getBlockState(origin);
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        BlockPos start = origin.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && found.size() < BATCH_LIMIT) {
            BlockPos current = queue.poll();
            // 起点本身已经校验过了；其余位置只比较方块类型，可绑定与否留给 bindOne 判定，
            // 这样「中间有个同种方块但没能力」不会把整条链切断。
            if (level.getBlockState(current).getBlock() != originState.getBlock()) {
                continue;
            }
            found.add(current);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!level.isLoaded(next) || !visited.add(next)) {
                    continue;
                }
                queue.add(next);
            }
        }
        found.sort((a, b) -> {
            int byY = Integer.compare(a.getY(), b.getY());
            if (byY != 0) return byY;
            int byX = Integer.compare(a.getX(), b.getX());
            if (byX != 0) return byX;
            return Integer.compare(a.getZ(), b.getZ());
        });
        return found;
    }

    /** 收尾：同步界面、唤醒引擎。所有绑定路径都必须走这里。 */
    private static void finish(ServerLevel level, ServerPlayer player, StaffLinkNetwork network,
                               StaffLinkSavedData data) {
        // activeNetwork 可能刚给杖写上了新网络 ID，显式同步一次。
        player.containerMenu.broadcastChanges();
        // 界面开着时同步刷新，避免显示的还是旧锚点列表。
        PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
        // 立刻让引擎重跑一遍：新加进来的配对不该等上一次排定的退避。
        StaffLinkEngine.wake(network.id());
    }

    private static void display(ServerPlayer player, String key, GlobalPos anchor, ChatFormatting style) {
        player.displayClientMessage(
                Component.translatable(key, describe(anchor)).withStyle(style), true);
    }

    private static void notify(ServerPlayer player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    /** 「维度:坐标」的可读描述。 */
    public static String describe(GlobalPos anchor) {
        return anchor.dimension().location() + " " + anchor.pos().toShortString();
    }

    private static void chime(ServerLevel level, BlockPos pos, boolean bound) {
        level.playSound(null, pos,
                bound ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 0.7F, bound ? 1.4F : 0.8F);
    }
}