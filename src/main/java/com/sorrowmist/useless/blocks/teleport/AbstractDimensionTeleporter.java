package com.sorrowmist.useless.blocks.teleport;

import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Supplier;

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 C-H716
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

@SuppressWarnings("all")
public abstract class AbstractDimensionTeleporter {

    /* ================= 子类提供 ================= */

    /**
     * 平台维度
     */
    protected abstract ResourceKey<Level> getDimensionKey();

    /**
     * 传送方块
     */
    protected abstract Supplier<Block> getTeleportBlock();

    /* ================= 入口 ================= */

    void handleTeleport(ServerPlayer player, BlockPos sourceBlockPos) {
        ServerLevel from = (ServerLevel) player.level();

        ResourceKey<Level> targetKey =
                from.dimension().equals(getDimensionKey())
                        ? Level.OVERWORLD
                        : getDimensionKey();

        ServerLevel target = player.server.getLevel(targetKey);
        if (target == null) return;

        BlockPos targetTeleportBlock = findOrCreateTeleportBlock(
                target,
                sourceBlockPos
        );

        if (targetTeleportBlock == null) return;

        BlockPos standPos = targetTeleportBlock.above();

        // 保证区块加载
        target.getChunkSource().addRegionTicket(
                TicketType.PORTAL,
                new ChunkPos(targetTeleportBlock),
                1,
                targetTeleportBlock
        );

        player.teleportTo(
                target,
                standPos.getX() + 0.5,
                standPos.getY(),
                standPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot()
        );
    }

    private BlockPos findOrCreateTeleportBlock(ServerLevel level,
                                               BlockPos sourceBlockPos) {

        // 搜索已有
        BlockPos found = searchExistingTeleport(level, sourceBlockPos);
        if (found != null) {
            return found;
        }

        // 2创建新的
        return createTeleportBlock(level, sourceBlockPos);
    }

    /**
     * 搜索已有传送方块（返回方块位置）
     */
    private BlockPos searchExistingTeleport(ServerLevel level,
                                            BlockPos sourceBlockPos) {

        boolean isPlatformDim = level.dimension().equals(getDimensionKey());

        int baseY = isPlatformDim
                ? getPlatformTopY()
                : getSurfaceY(level, sourceBlockPos);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {

                if (isPlatformDim) {
                    pos.set(
                            sourceBlockPos.getX() + dx,
                            baseY,
                            sourceBlockPos.getZ() + dz
                    );

                    if (isTeleportBlock(level, pos)) {
                        return adjustTeleportBlock(level, pos);
                    }

                } else {
                    for (int dy = -4; dy <= 4; dy++) {
                        pos.set(
                                sourceBlockPos.getX() + dx,
                                baseY + dy,
                                sourceBlockPos.getZ() + dz
                        );

                        if (isTeleportBlock(level, pos)) {
                            return adjustTeleportBlock(level, pos);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 创建传送方块（返回最终方块位置）
     */
    private BlockPos createTeleportBlock(ServerLevel level,
                                         BlockPos sourceBlockPos) {

        boolean isPlatformDim = level.dimension().equals(getDimensionKey());

        int baseY = isPlatformDim
                ? getPlatformTopY()
                : getSurfaceY(level, sourceBlockPos);

        BlockPos base = new BlockPos(
                sourceBlockPos.getX(),
                baseY,
                sourceBlockPos.getZ()
        );

        // 确定最终站人位置
        BlockPos standPos = findNearestStandable(level, base, 8);
        if (standPos == null) return null;

        BlockPos blockPos = standPos.below();

        // 若不是传送方块放置
        if (!isTeleportBlock(level, blockPos)) {
            level.setBlock(blockPos, teleportState(), 3);
        }

        return blockPos;
    }


    /**
     * 已存在的传送方块，修正其“站人高度”
     */
    private BlockPos adjustTeleportBlock(ServerLevel level,
                                         BlockPos teleportBlockPos) {

        BlockPos standBase = teleportBlockPos.above();

        BlockPos standPos = findNearestStandable(level, standBase, 8);
        if (standPos == null) return teleportBlockPos;

        BlockPos correctBlockPos = standPos.below();

        // 方块位置不对 → 移动
        if (!correctBlockPos.equals(teleportBlockPos)) {
            level.setBlock(teleportBlockPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(correctBlockPos, teleportState(), 3);
        }

        return correctBlockPos;
    }


    /**
     * 站立判定
     */
    private boolean isStandable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir();
    }

    /**
     * 在同一 XZ 上向上 / 向下寻找最近可站立点
     */
    private BlockPos findNearestStandable(ServerLevel level,
                                          BlockPos start,
                                          int maxDistance) {

        if (isStandable(level, start)) {
            return start;
        }

        for (int i = 1; i <= maxDistance; i++) {

            BlockPos up = start.above(i);
            if (isStandable(level, up)) {
                return up;
            }

            BlockPos down = start.below(i);
            if (down.getY() > level.getMinBuildHeight()
                    && isStandable(level, down)) {
                return down;
            }
        }

        return null;
    }

    private int getPlatformTopY() {
        return ConfigManager.getPlatformStartY()
                + ConfigManager.getPlatformLayers();
    }

    private int getSurfaceY(ServerLevel level, BlockPos pos) {
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING,
                pos
        );
        return Math.max(surface.getY(), level.getSeaLevel());
    }

    private boolean isTeleportBlock(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).getBlock() == getTeleportBlock().get();
    }

    private BlockState teleportState() {
        return getTeleportBlock().get().defaultBlockState();
    }
}