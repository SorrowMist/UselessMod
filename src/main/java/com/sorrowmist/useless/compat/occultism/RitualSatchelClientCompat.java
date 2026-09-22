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
 * 匠心仪式挎包的客户端侧：识别魔典预览中是否存在可摆放的五芒星。
 *
 * <p>预览信息仅存在于客户端，此处只负责识别玩家点击的仪式与格位，并将预览三元组
 * （结构 id / 锚点 / 朝向）发送至服务端；取料与摆放均在服务端完成。</p>
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
        var preview = ModonomiconAPI.get().getCurrentPreviewMultiblock();
        // 预览未锚定时不参与摆放
        if (preview == null || !preview.isAnchored()) return false;

        var simulation = preview.multiblock().simulate(level, preview.anchor(), preview.facing(), false, false);
        var target = simulation.getSecond().stream()
                .filter(p -> p.getWorldPosition().equals(pos))
                .findFirst();
        if (target.isEmpty()) return false;

        // 「任意方块」与「仅显示」两类占位无需实际放置，交由原版右键链路处理
        var matcher = target.get().getStateMatcher();
        if (matcher.getType().equals(AnyMatcher.TYPE) || matcher.getType().equals(DisplayOnlyMatcher.TYPE)) {
            return false;
        }

        PacketDistributor.sendToServer(new RitualSatchelPlacePacket(
                preview.multiblock().getId(), preview.anchor(), preview.facing()));
        return true;
    }
}
