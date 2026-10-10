package com.sorrowmist.useless.compat.ftbchunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 方块编辑保护判定的桥接口。
 *
 * <p>与 {@code SharedOwnerBridge} 同一套手法：本接口不含任何 FTB Chunks 的类型，
 * 实现类由 {@link FtbChunksProtectionLoader} 反射加载。FTB Chunks 在
 * {@code build.gradle} 中仅为 {@code implementation} 依赖，未写入
 * {@code neoforge.mods.toml}，运行时可能缺席，因此该隔离是必需的。</p>
 */
public interface BlockEditGuard {

    /**
     * 判定该玩家是否被允许在指定位置编辑方块。
     *
     * <p>判定以单格为单位：一次建造或破坏可能跨越多个区块，各区块的归属队伍与
     * 权限设置并不相同，不能只对起点做一次判定。</p>
     *
     * @return {@code true} 表示允许编辑；保护判定不可用时同样返回 {@code true}。
     */
    boolean isEditAllowed(ServerPlayer player, BlockPos pos);
}