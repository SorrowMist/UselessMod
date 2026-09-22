package com.sorrowmist.useless.compat.occultism;

import com.klikli_dev.modonomicon.api.ModonomiconAPI;
import com.klikli_dev.modonomicon.multiblock.matcher.AnyMatcher;
import com.klikli_dev.modonomicon.multiblock.matcher.DisplayOnlyMatcher;
import com.sorrowmist.useless.network.RitualSatchelPlacePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 匠心仪式挎包的客户端侧：识别玩家魔典预览里有没有可摆放的五芒星。
 *
 * <p>预览信息只存在于客户端，所以这里只负责「认出玩家点的是哪座仪式的哪一格」
 * 并把预览三元组（结构 id / 锚点 / 朝向）发给服务端；真正的取料与摆放全在服务端完成。</p>
 *
 * <p>该类只在 occultism 已加载且处于客户端环境时才会被加载。</p>
 */
public final class RitualSatchelClientCompat {

    private RitualSatchelClientCompat() {
    }

    /**
     * 玩家右键某个方块时，判断它是不是当前魔典预览五芒星上的一格。
     *
     * @return true 表示已经接管这次右键（并已把摆放请求发给服务端）
     */
    public static boolean trySendPlacement(Level level, Player player, BlockPos pos) {
        var preview = ModonomiconAPI.get().getCurrentPreviewMultiblock();
        // 世界里没有锚定好的预览就没得摆
        if (preview == null || !preview.isAnchored()) return false;

        var simulation = preview.multiblock().simulate(level, preview.anchor(), preview.facing(), false, false);
        var target = simulation.getSecond().stream()
                .filter(p -> p.getWorldPosition().equals(pos))
                .findFirst();
        if (target.isEmpty()) return false;

        // 「任意方块」与「仅显示」两类占位不需要真的摆东西，交给原版右键链路
        var matcher = target.get().getStateMatcher();
        if (matcher.getType().equals(AnyMatcher.TYPE) || matcher.getType().equals(DisplayOnlyMatcher.TYPE)) {
            return false;
        }

        PacketDistributor.sendToServer(new RitualSatchelPlacePacket(
                preview.multiblock().getId(), preview.anchor(), preview.facing()));
        return true;
    }
}
