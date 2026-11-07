package com.sorrowmist.useless.compat.occultism;

import com.klikli_dev.modonomicon.client.render.MultiblockPreviewRenderer;
import com.sorrowmist.useless.network.RitualSatchelPlacePacket;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;

/**
 * 匠心仪式挎包的客户端侧：识别魔典预览中是否存在可摆放的五芒星。
 *
 * <p>Forge 1.20.1 的 Modonomicon 1.79 通过 {@link MultiblockPreviewRenderer} 的静态状态
 * 暴露当前预览，而不是 1.21 的 {@code PreviewMultiblock} 对象。这里只读取客户端预览的
 * 结构 id / 锚点 / 朝向，并把请求发送到服务端；取料与摆放均在服务端完成。</p>
 *
 * <p>本类仅在 occultism 已加载且处于客户端环境时才会被加载。</p>
 */
public final class RitualSatchelClientCompat {

    private RitualSatchelClientCompat() {
    }

    /**
     * 玩家右键方块时，判断该方块是否为当前魔典预览五芒星的一格。
     *
     * @return true 表示已接管该次右键，并已将摆放请求发送至服务端
     */
    public static boolean trySendPlacement(Level level, Player player, BlockPos pos) {
        if (!level.isClientSide || player == null || pos == null
                || !MultiblockPreviewRenderer.hasMultiblock || !MultiblockPreviewRenderer.isAnchored()) return false;
        var toolEntry = UselessItemUtils.findTargetToolInHands(player);
        if (toolEntry.isEmpty() || !RitualSatchelCompat.canUseTool(level, player, toolEntry.get().getKey())) return false;
        var multiblock = MultiblockPreviewRenderer.getMultiblock();
        if (multiblock == null || !RitualSatchelCompat.boundedSize(multiblock)
                || !RitualSatchelCompat.isRitualMultiblock(level, multiblock.getId())) return false;

        BlockPos anchor = MultiblockPreviewRenderer.getStartPos();
        var facing = MultiblockPreviewRenderer.getFacingRotation();
        if (anchor == null || facing == null) return false;
        // 1.79.3 的渲染器用 view offset，而不是 validate 的 offset。
        var simulation = multiblock.simulate(level, anchor, facing, true, false);
        boolean clickedRitual = simulation.getSecond().stream().anyMatch(target ->
                RitualSatchelCompat.isPlaceable(target.getStateMatcher())
                        && (target.getWorldPosition().equals(pos) || target.getWorldPosition().equals(pos.above())));
        // 允许点空气预览下的支撑块；面向/射线、权限及实际目标都由服务端重新核查。
        if (!clickedRitual) return false;

        PacketDistributor.sendToServer(new RitualSatchelPlacePacket(
                multiblock.getId(), anchor, facing));
        return true;
    }
}
