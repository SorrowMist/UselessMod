package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.LinkFilterCondition;
import com.sorrowmist.useless.content.stafflink.LinkFilterPattern;
import com.sorrowmist.useless.content.stafflink.LinkFilterSlot;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.ResourceFamily;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端提交一条线路配置。
 *
 * <p>服务端是唯一的校验点：只接受「当前打开的网络 + 已绑定的锚点 + 当前环境支持的资源类型」，
 * 数值再由 {@link StaffLinkRoute} 的构造器夹取一次。通过后回发整网快照，客户端以服务端为准。</p>
 */
public record StaffLinkConfigurePacket(GlobalPos anchor, int route, StaffLinkRoute config)
        implements CustomPacketPayload {

    public static final Type<StaffLinkConfigurePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkConfigurePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        StaffLinkRoute.writeAnchor(buffer, packet.anchor());
                        buffer.writeVarInt(packet.route());
                        StaffLinkRoute.STREAM_CODEC.encode(buffer, packet.config());
                    },
                    buffer -> {
                        GlobalPos anchor = StaffLinkRoute.readAnchor(buffer);
                        int route = buffer.readVarInt();
                        StaffLinkRoute config = StaffLinkRoute.STREAM_CODEC.decode(buffer);
                        return new StaffLinkConfigurePacket(anchor, route, config);
                    });

    public static void handle(StaffLinkConfigurePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof StaffLinkMenu menu)) {
                return;
            }
            // 越权校验：界面所属的网络必须挂在他（或他队伍）名下。
            if (!StaffLinkManager.canAccess(player.server, player, menu.getNetworkId())) {
                return;
            }
            StaffLinkNetwork network = StaffLinkManager.networkById(player.server, menu.getNetworkId());
            if (network == null || !network.isBound(packet.anchor())) {
                return;
            }
            if (packet.route() < 0 || packet.route() >= StaffLinkNetwork.ROUTE_COUNT) {
                return;
            }
            if (!packet.config().medium().isSupported()) {
                return;
            }

            // 以包里的 anchor / route 为准重写一次，防止客户端送来不匹配的字段。
            StaffLinkRoute sanitized = new StaffLinkRoute(
                    packet.anchor(),
                    packet.route(),
                    packet.config().enabled(),
                    packet.config().flow(),
                    packet.config().medium(),
                    sanitizeAmount(packet.config()),
                    sanitizeInterval(packet.config()),
                    packet.config().side(),
                    packet.config().weight(),
                    sanitizeFilter(packet.config().filter(), packet.config().medium()));

            network.putRoute(sanitized);
            StaffLinkSavedData.get(player.server).markDirty();
            // 改完立刻重跑：新开的配对不该等上一次排定的退避。
            StaffLinkEngine.wake(network.id());
            PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(
                    player.server, StaffLinkManager.ownerIdOf(player), network));
        });
    }

    /**
     * 应力线路上「数量」承载的是目标转速，要夹进合理区间。
     *
     * <p>这里只做一个<b>绝对上界</b>的粗夹：真正的上限是动力学配置里的最高转速，那是可选集成
     * 侧才知道的事，由桥在运行时再夹一次。别的介质保持原值——它们的「数量」本来就没有上限。</p>
     *
     * <p><b>气压是例外</b>：它的「数量」承载的是目标气压（毫巴），<b>真空是负值</b>，
     * 所以必须夹到 {@code [-1000, 20000]}（-1 ~ 20 bar）而不是「至少 1」。</p>
     */
    private static long sanitizeAmount(StaffLinkRoute config) {
        if (config.medium().family() == ResourceFamily.PRESSURE) {
            return Math.max(StaffLinkRoute.PRESSURE_MIN_MBAR,
                    Math.min(StaffLinkRoute.PRESSURE_MAX_MBAR, config.amount()));
        }
        if (config.medium().family() != ResourceFamily.STRESS) {
            return config.amount();
        }
        return Math.max(StaffLinkRoute.MIN_AMOUNT,
                Math.min(StaffLinkRoute.STRESS_RPM_HARD_LIMIT, config.amount()));
    }

    /**
     * 应力线路上「周期」承载的是旋转方向，只允许「顺时针 / 逆时针」两个取值。
     *
     * <p>别的取值一律收敛成顺时针，而不是拒绝整条配置：玩家可能只是刚从别的介质切过来，
     * 那两个数字还没改，为此把配置打回去反而更让人困惑。</p>
     */
    private static int sanitizeInterval(StaffLinkRoute config) {
        if (config.medium().family() != ResourceFamily.STRESS) {
            return config.interval();
        }
        return config.interval() == StaffLinkRoute.STRESS_COUNTER_CLOCKWISE
                ? StaffLinkRoute.STRESS_COUNTER_CLOCKWISE
                : StaffLinkRoute.STRESS_CLOCKWISE;
    }

    /**
     * 逐格清洗过滤器。
     *
     * <p>一格现在有五样东西：标记、模式文本、包含/排除方向、两条控制条件。{@link StaffLinkRoute}
     * 的构造器只会把「格数」补齐到 {@code FILTER_LIMIT}，不校验格<b>内容</b>；
     * 一个被改过的客户端可以塞进来非法模式、越界的条件或跨族的控制材料。所以这里逐格重造一遍：</p>
     *
     * <ul>
     *   <li>模式文本必须能被 {@link LinkFilterPattern#parse} 接受，否则整格清空
     *       （宁可「什么都不搬」，也不要留一个语义不明的字符串在存档里）。</li>
     *   <li>{@code exclude} 原样布尔。</li>
     *   <li>两条条件：方向未知 → OFF；阈值夹非负；控制材料与线路族不符 / 该族不吃条件 → 清空。</li>
     * </ul>
     *
     * <p><b>不能跳过这一步直接把 {@code packet.config().filter()} 传下去</b>：
     * 那样等于把服务端当成客户端的镜子，任何越界值都会直接落进存档。</p>
     */
    private static java.util.List<LinkFilterSlot> sanitizeFilter(
            java.util.List<LinkFilterSlot> received, LinkMedium medium) {
        java.util.List<LinkFilterSlot> cleaned =
                new java.util.ArrayList<>(StaffLinkRoute.FILTER_LIMIT);
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            LinkFilterSlot slot = received != null && index < received.size()
                    ? received.get(index) : null;
            if (slot == null || slot.isEmpty()) {
                cleaned.add(LinkFilterSlot.EMPTY);
                continue;
            }
            LinkFilterSlot base;
            if (slot.isPattern()) {
                LinkFilterPattern pattern = LinkFilterPattern.parse(slot.pattern());
                if (pattern == null) {
                    cleaned.add(LinkFilterSlot.EMPTY);
                    continue;
                }
                base = LinkFilterSlot.ofPattern(pattern);
            } else {
                // 物品 / 流体标记：构造器已经归一化（count=1、类型互斥）。
                base = slot;
            }
            cleaned.add(base
                    .withExclude(slot.exclude())
                    .withConditions(sanitizeCondition(slot.outCond(), medium),
                            sanitizeCondition(slot.inCond(), medium)));
        }
        return cleaned;
    }

    /** 清洗一条控制条件：方向未知 → OFF；阈值非负；控制材料与线路族不符 → 清空。 */
    private static LinkFilterCondition sanitizeCondition(LinkFilterCondition cond, LinkMedium medium) {
        if (cond == null || cond.isOff()) {
            return LinkFilterCondition.OFF;
        }
        ResourceFamily family = medium.family();
        // 条件只对物品 / 流体有意义：化学品没有存量查询，能量 / 魔源 / 应力 / 气压压根不吃过滤器。
        if (family != ResourceFamily.ITEM && family != ResourceFamily.FLUID) {
            return LinkFilterCondition.OFF;
        }
        ItemStack item = family == ResourceFamily.ITEM ? cond.item() : ItemStack.EMPTY;
        FluidStack fluid = family == ResourceFamily.FLUID ? cond.fluid() : FluidStack.EMPTY;
        return new LinkFilterCondition(cond.op(), item, fluid, Math.max(0L, cond.value()));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
