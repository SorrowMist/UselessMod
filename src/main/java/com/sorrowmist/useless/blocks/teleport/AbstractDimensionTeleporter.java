package com.sorrowmist.useless.blocks.teleport;

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

@SuppressWarnings("all")
public abstract class AbstractDimensionTeleporter {

    /* ================= 子类提供 ================= */

    protected abstract ResourceKey<Level> getDimensionKey();

    protected abstract Supplier<Block> getTeleportBlock();

    /* ================= 入口 ================= */

    void handleTeleport(ServerPlayer player, BlockPos sourcePos) {
        ServerLevel from = (ServerLevel) player.level();

        ResourceKey<Level> targetKey =
                from.dimension().equals(getDimensionKey())
                        ? Level.OVERWORLD
                        : getDimensionKey();

        ServerLevel target = player.server.getLevel(targetKey);
        if (target == null) return;

        BlockPos targetBlock = findOrCreateTeleportBlock(target, sourcePos);
        if (targetBlock == null) return;

        // 轻量级区块票证
        target.getChunkSource().addRegionTicket(
                TicketType.PORTAL,
                new ChunkPos(targetBlock),
                1,
                targetBlock
        );

        player.teleportTo(
                target,
                targetBlock.getX() + 0.5,
                targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5,
                player.getYRot(),
                player.getXRot()
        );
    }

    /* ================= 查找 / 创建 ================= */

    private BlockPos findOrCreateTeleportBlock(ServerLevel level, BlockPos sourcePos) {
        int searchRadius = 16;

        int[] priorityYLevels = {
                sourcePos.getY(),
                sourcePos.getY() - 1,
                sourcePos.getY() + 1,
                sourcePos.getY() - 2,
                sourcePos.getY() + 2,
                sourcePos.getY() - 3,
                sourcePos.getY() + 3
        };

        // 1. 优先层搜索
        for (int y : priorityYLevels) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }

            BlockPos found = searchAtYLevel(level, sourcePos, y, searchRadius);
            if (found != null) {
                return found;
            }
        }

        // 2. 向下扩展
        for (int y = sourcePos.getY() - 4;
             y >= level.getMinBuildHeight() + 1;
             y--) {

            BlockPos found = searchAtYLevel(level, sourcePos, y, searchRadius);
            if (found != null) {
                return found;
            }
        }

        // 3. 向上兜底
        for (int y = sourcePos.getY() + 4;
             y < level.getMaxBuildHeight() - 10;
             y++) {

            BlockPos found = searchAtYLevel(level, sourcePos, y, searchRadius);
            if (found != null) {
                return found;
            }
        }

        // 4. 创建新的
        return createTeleportBlockFast(level, sourcePos);
    }

    private BlockPos searchAtYLevel(ServerLevel level,
                                    BlockPos center,
                                    int y,
                                    int radius) {

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {

                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;

                    pos.set(center.getX() + dx, y, center.getZ() + dz);

                    if (isValidTeleportBlock(level, pos)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidTeleportBlock(ServerLevel level, BlockPos pos) {

        // 不强制加载区块
        if (!level.isLoaded(pos)) {
            ChunkPos chunkPos = new ChunkPos(pos);
            if (!level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                return false;
            }
        }

        return level.getBlockState(pos).getBlock() == getTeleportBlock().get();
    }

    /* ================= 创建逻辑（无垫石、无抬高） ================= */

    private BlockPos createTeleportBlockFast(ServerLevel level, BlockPos sourcePos) {

        int[] safeHeights = {64, 80, 96, 112, 128};

        for (int y : safeHeights) {
            BlockPos testPos = new BlockPos(sourcePos.getX(), y, sourcePos.getZ());
            if (canPlaceTeleportBlockFast(level, testPos)) {
                level.setBlock(testPos, teleportState(), 3);
                return testPos;
            }
        }

        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING,
                sourcePos
        );

        // 找到真正的固体地面
        BlockPos solidGround = surface;
        while (solidGround.getY() > level.getMinBuildHeight() && !level.getBlockState(solidGround).isSolid()) {
            solidGround = solidGround.below();
        }

        // 固体方块上方 1 格放置传送方块
        BlockPos placePos = solidGround.above();
        level.setBlock(placePos, teleportState(), 3);
        return placePos;
    }

    private boolean canPlaceTeleportBlockFast(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }

        return level.getBlockState(pos).canBeReplaced()
                && level.getBlockState(pos.below()).isSolid();
    }

    private BlockState teleportState() {
        return getTeleportBlock().get().defaultBlockState();
    }
}