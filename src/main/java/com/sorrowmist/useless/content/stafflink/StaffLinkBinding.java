package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.network.StaffLinkSyncPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 潜行右键容器 → 绑定 / 解绑。
 *
 * <p>绑定只登记「这个坐标属于这张网络」并给一条默认线路；具体怎么搬由玩家在界面里改。</p>
 */
public final class StaffLinkBinding {
    /** 新锚点的默认单次搬运量。 */
    private static final int DEFAULT_AMOUNT = 16;
    /** 新锚点的默认搬运周期（tick）。取小值，绑完就能明显看到东西在动。 */
    private static final int DEFAULT_INTERVAL = 5;

    private StaffLinkBinding() {
    }

    public static void toggle(ServerLevel level, ServerPlayer player, ItemStack staff, BlockPos pos) {
        StaffLinkNetwork network = StaffLinkManager.activeNetwork(level.getServer(), staff, true);
        if (network == null) {
            return;
        }
        GlobalPos anchor = GlobalPos.of(level.dimension(), pos.immutable());
        StaffLinkSavedData data = StaffLinkSavedData.get(level.getServer());

        if (network.isBound(anchor)) {
            network.detach(anchor);
            data.markDirty();
            display(player, "gui.useless_mod.wireless_logistics.unbound", anchor, ChatFormatting.GREEN);
            chime(level, pos, false);
        } else {
            LinkMedium medium = StaffLinkTargets.defaultMediumFor(level, pos);
            if (medium == null) {
                notify(player, "gui.useless_mod.wireless_logistics.not_bindable", ChatFormatting.YELLOW);
                return;
            }
            // 网络里还没有释放端时，第一个绑定的容器就当释放端——绑两个容器即可跑起来。
            LinkFlow flow = network.hasReleaseRoute() ? LinkFlow.ABSORB : LinkFlow.RELEASE;
            // 新锚点默认关闭：要搬什么由玩家显式打开，免得一绑上就开始动别人的库存。
            network.putRoute(new StaffLinkRoute(anchor, 0, false, flow, medium,
                    DEFAULT_AMOUNT, DEFAULT_INTERVAL, null, LinkTrigger.ALWAYS, 0, List.of()));
            data.markDirty();
            display(player, "gui.useless_mod.wireless_logistics.bound", anchor, ChatFormatting.GREEN);
            chime(level, pos, true);
        }

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
