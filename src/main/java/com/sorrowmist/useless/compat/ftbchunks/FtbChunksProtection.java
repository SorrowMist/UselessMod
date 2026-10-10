package com.sorrowmist.useless.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * {@link BlockEditGuard} 的 FTB Chunks 实现。
 *
 * <p><b>只有本类（以及它引用的 FTB Chunks 类）会在安装了该模组时被加载。</b></p>
 *
 * <p>复用 {@code ClaimedChunkManager#shouldPreventInteraction} 而不是自行组合区块查询与
 * 队伍权限判断：前者同时涵盖全局保护开关、管理员 bypass、荒野策略、假玩家策略与
 * 保护策略覆写，自行拼装的口径会随 FTB Chunks 版本演进产生偏差。</p>
 */
public final class FtbChunksProtection implements BlockEditGuard {

    @Override
    public boolean isEditAllowed(ServerPlayer player, BlockPos pos) {
        try {
            FTBChunksAPI.API api = FTBChunksAPI.api();
            if (!api.isManagerLoaded()) {
                return true;
            }
            ClaimedChunkManager manager = api.getManager();
            if (manager == null) {
                return true;
            }
            return !manager.shouldPreventInteraction(
                    player, InteractionHand.MAIN_HAND, pos, Protection.EDIT_BLOCK, null);
        } catch (RuntimeException | LinkageError notReady) {
            // FTB Chunks 的 API 在初始化前会抛 NPE，管理器在服务端启动阶段也尚未建立。
            // 保护判定不可用时不介入方块编辑。
            return true;
        }
    }
}