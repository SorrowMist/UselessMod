package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.helpers.IPatternTerminalLogicHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 构造并发送万象样板移交结果对应的玩家提示。
 *
 * <p>提示由坐标、维度与距离三段组成：坐标片段与维度片段各自携带 {@code SUGGEST_COMMAND}
 * 点击事件，玩家点击后对应的传送命令被填入聊天框，由玩家自行确认执行。命令采用
 * {@code SUGGEST_COMMAND} 而非 {@code RUN_COMMAND}，使玩家在传送前仍可核对目标与落点。</p>
 *
 * <p>该形态与 ExtendedAE 的样板供应器定位提示一致：坐标片段提供当前维度内的直接传送，
 * 维度片段以 {@code execute in} 包裹同一落点，使目标位于其它维度时仍可抵达。</p>
 *
 * <p>维度取自接收样板的总成，而非编码终端。二者可能不在同一维度：跨维度 AE 网络下
 * 终端位于玩家所在维度，总成却在另一维度，沿用终端维度会给出无法抵达的错误落点。</p>
 */
public final class PatternUploadNotice {

    private static final String MESSAGE_KEY = "gui.useless_mod.omniversal_pattern.uploaded";
    private static final String DUPLICATE_KEY = "gui.useless_mod.omniversal_pattern.duplicate";
    private static final String TOOLTIP_KEY = "gui.useless_mod.omniversal_pattern.uploaded.tooltip";

    private PatternUploadNotice() {
    }

    /**
     * 反查发起本次编码的玩家。
     *
     * <p>编码终端的逻辑对象不持有玩家引用，因此按「当前打开的容器菜单正以同一个逻辑实例
     * 为编码目标」反查玩家。比较的是逻辑实例而非宿主对象：有线终端部件与无线终端宿主的
     * 对象身份不同，但二者暴露的是同一个 {@code PatternEncodingLogic}。编码由玩家在编码
     * 终端界面内触发，触发时其菜单必然仍指向该逻辑，因此这一反查是可靠的。</p>
     *
     * <p>客户端预览与无玩家在线的场景返回 {@code null}。调用方除发送提示外，也据此决定
     * 能否退还空白样板，因此该方法对外可见。</p>
     *
     * @param logic 本次编码所经过的逻辑实例，即 mixin 所在对象
     * @param host  编码终端的逻辑宿主，用于取得所在服务端
     */
    public static @Nullable ServerPlayer resolvePlayer(Object logic, IPatternTerminalLogicHost host) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof PatternEncodingTermMenu menu
                    && menu.getTarget() instanceof IPatternTerminalLogicHost targetHost
                    && targetHost.getLogic() == logic) {
                return player;
            }
        }
        return null;
    }

    /**
     * 告知玩家样板已移交，并给出可点击的传送目标。
     *
     * @param player 发起本次编码的玩家，可能为空（无玩家在线）
     * @param target 接收样板的总成位置及其所在维度
     */
    public static void notifyUploaded(@Nullable ServerPlayer player, GlobalPos target) {
        if (player != null) {
            player.displayClientMessage(uploaded(player, target), false);
        }
    }

    /**
     * 告知玩家网络中已存在等价样板，本次未写入。
     *
     * @param player 发起本次编码的玩家，可能为空（无玩家在线）
     */
    public static void notifyDuplicate(@Nullable ServerPlayer player) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(DUPLICATE_KEY), false);
        }
    }

    /**
     * @param player 接收提示的玩家，用于计算与目标的距离
     * @param target 接收样板的总成位置及其所在维度
     * @return 含可点击坐标片段与维度片段的提示文本
     */
    public static Component uploaded(ServerPlayer player, GlobalPos target) {
        var pos = target.pos();
        double landingX = pos.getX() + 0.5;
        int landingY = pos.getY() + 1;
        double landingZ = pos.getZ() + 0.5;

        Component coordinates = Component.literal("[" + pos.toShortString() + "]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                teleportCommand(landingX, landingY, landingZ)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(TOOLTIP_KEY))));

        String dimensionId = target.dimension().location().toString();
        Component dimensionComponent = Component.literal(dimensionId)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %d %.1f",
                                        dimensionId, landingX, landingY, landingZ)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(TOOLTIP_KEY))));

        int distance = (int) Math.sqrt(player.blockPosition().distSqr(pos));
        return Component.translatable(MESSAGE_KEY, coordinates, dimensionComponent, distance);
    }

    /**
     * 落点取方块正上方一格：样板总成嵌在多方块结构内部，直接传送到其自身坐标会卡进结构。
     */
    private static String teleportCommand(double x, int y, double z) {
        return String.format(Locale.ROOT, "/tp @s %.1f %d %.1f", x, y, z);
    }
}