package com.sorrowmist.useless.blocks.teleport;

import com.sorrowmist.useless.UselessMod;
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

    private BlockPos findOrCreateTeleportBlock(ServerLevel level, BlockPos sourcePos) {
        int searchRadius = 16;
        boolean isTeleportingToDimensionA = level.dimension().location().getNamespace().equals(UselessMod.MODID);

        if (isTeleportingToDimensionA) {
            // 传送到本mod维度：优先搜索平台层高度
            return findOrCreateTeleportBlockToModDimension(level, sourcePos, searchRadius);
        } else {
            // 传送到其他维度：优先搜索地形高度区域
            return findOrCreateTeleportBlockToOtherDimension(level, sourcePos, searchRadius);
        }
    }

    /**
     * 传送到本mod维度：优先搜索平台层高度的传送方块
     */
    private BlockPos findOrCreateTeleportBlockToModDimension(ServerLevel level, BlockPos sourcePos, int searchRadius) {
        int platformStartY = ConfigManager.getPlatformStartY();
        int platformLayers = ConfigManager.getPlatformLayers();
        int platformHeight = platformStartY + platformLayers;

        // 1. 优先搜索平台层高度
        BlockPos found = searchAtYLevel(level, sourcePos, platformHeight, searchRadius);
        if (found != null) {
            return found;
        }

        // 2. 搜索平台层上下各2层
        for (int offset = 1; offset <= 2; offset++) {
            // 向上搜索
            int yUp = platformHeight + offset;
            if (yUp < level.getMaxBuildHeight()) {
                found = searchAtYLevel(level, sourcePos, yUp, searchRadius);
                if (found != null) {
                    return found;
                }
            }

            // 向下搜索
            int yDown = platformHeight - offset;
            if (yDown > level.getMinBuildHeight()) {
                found = searchAtYLevel(level, sourcePos, yDown, searchRadius);
                if (found != null) {
                    return found;
                }
            }
        }

        // 3. 创建新的传送方块
        return createTeleportBlockToModDimension(level, sourcePos);
    }

    /**
     * 传送到其他维度：优先搜索地形高度区域的传送方块
     */
    private BlockPos findOrCreateTeleportBlockToOtherDimension(ServerLevel level, BlockPos sourcePos,
                                                               int searchRadius) {
        // 获取实际固体地面高度
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sourcePos);
        int originY = Math.max(62, surface.getY()) + 1;
        BlockPos origin = new BlockPos(sourcePos.getX(), originY, sourcePos.getZ());

        // 从原点出发，向上向下搜索
        BlockPos found = searchVertical(level, origin, 32, 32); // 向上搜索32层，向下搜索32层
        if (found != null) {
            return found;
        }

        // 进行5*5*5范围搜索
        found = search5x5x5Area(level, origin);
        if (found != null) {
            return found;
        }

        // 原点创建
        return createTeleportBlockToOtherDimension(level, sourcePos);
    }

    /**
     * 垂直搜索：从原点向上向下搜索指定层数
     */
    private BlockPos searchVertical(ServerLevel level, BlockPos origin, int upRange, int downRange) {
        // 向上搜索
        for (int offset = 1; offset <= upRange; offset++) {
            int y = origin.getY() + offset;
            if (y < level.getMaxBuildHeight()) {
                BlockPos testPos = new BlockPos(origin.getX(), y, origin.getZ());
                if (isValidTeleportBlock(level, testPos)) {
                    return testPos;
                }
            }
        }

        // 向下搜索
        for (int offset = 1; offset <= downRange; offset++) {
            int y = origin.getY() - offset;
            if (y > level.getMinBuildHeight()) {
                BlockPos testPos = new BlockPos(origin.getX(), y, origin.getZ());
                if (isValidTeleportBlock(level, testPos)) {
                    return testPos;
                }
            }
        }

        return null;
    }

    /**
     * 5*5*5范围搜索
     */
    private BlockPos search5x5x5Area(ServerLevel level, BlockPos origin) {
        int searchRadius = 2; // 5*5*5范围

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // 跳过原点

                    BlockPos testPos = origin.offset(dx, dy, dz);
                    if (testPos.getY() > level.getMinBuildHeight() && testPos.getY() < level.getMaxBuildHeight()) {
                        if (isValidTeleportBlock(level, testPos)) {
                            return testPos;
                        }
                    }
                }
            }
        }

        return null;
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

    /**
     * 传送到本mod维度：基于平台层数高度创建，确保下方是平台方块
     */
    private BlockPos createTeleportBlockToModDimension(ServerLevel level, BlockPos sourcePos) {
        int platformStartY = ConfigManager.getPlatformStartY();
        int platformLayers = ConfigManager.getPlatformLayers();
        int platformHeight = platformStartY + platformLayers;

        // 在平台层数高度搜索合适的位置，最多16x16范围
        BlockPos placePos = findPlatformPosition(level, sourcePos, platformHeight, 16);
        if (placePos != null) {
            // 确保区块已加载
            if (!level.isLoaded(placePos)) {
                level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(placePos), 3, placePos);
            }

            // 使用正确的放置参数确保方块被保存
            level.setBlockAndUpdate(placePos, teleportState());
            return placePos;
        }

        // 如果找不到合适位置，检查平台层数高度中心位置是否为空气或可替换
        BlockPos centerPos = new BlockPos(sourcePos.getX(), platformHeight, sourcePos.getZ());

        // 如果中心位置是空气或可替换，直接放置
        if (level.getBlockState(centerPos).canBeReplaced()) {
            if (!level.isLoaded(centerPos)) {
                level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(centerPos), 3, centerPos);
            }
            level.setBlockAndUpdate(centerPos, teleportState());
            return centerPos;
        } else {
            // 如果中心位置不可替换，向上寻找最近的空气位置
            for (int y = platformHeight + 1; y < level.getMaxBuildHeight(); y++) {
                BlockPos testPos = new BlockPos(sourcePos.getX(), y, sourcePos.getZ());
                if (level.getBlockState(testPos).canBeReplaced()) {
                    // 确保区块已加载
                    if (!level.isLoaded(testPos)) {
                        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(testPos), 3, testPos);
                    }
                    level.setBlockAndUpdate(testPos, teleportState());
                    return testPos;
                }
            }

            // 找不到合适位置，在中心位置强行放置
            if (!level.isLoaded(centerPos)) {
                level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(centerPos), 3, centerPos);
            }
            level.setBlockAndUpdate(centerPos, teleportState());
            return centerPos;
        }
    }

    /**
     * 传送到其他维度：基于地形高度+1创建，如果创建位置不为空气，则向上尝试
     */
    private BlockPos createTeleportBlockToOtherDimension(ServerLevel level, BlockPos sourcePos) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sourcePos);

        // 确保至少在Y=62以上生成
        int surfaceHeight = Math.max(62, surface.getY());

        // 从固体地面高度+1开始向上尝试放置
        for (int y = surfaceHeight + 1; y < level.getMaxBuildHeight(); y++) {
            BlockPos testPos = new BlockPos(sourcePos.getX(), y, sourcePos.getZ());
            if (level.getBlockState(testPos).canBeReplaced() && level.getBlockState(testPos.below()).isSolid()) {
                // 确保区块已加载
                if (!level.isLoaded(testPos)) {
                    level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(testPos), 3, testPos);
                }
                // 使用正确的放置参数确保方块被保存
                level.setBlockAndUpdate(testPos, teleportState());
                return testPos;
            }
        }

        // 如果找不到合适位置，直接在固体地面高度+1位置创建
        BlockPos fallbackPos = new BlockPos(sourcePos.getX(), surfaceHeight + 1, sourcePos.getZ());

        // 确保区块已加载
        if (!level.isLoaded(fallbackPos)) {
            level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(fallbackPos), 3, fallbackPos);
        }

        // 使用正确的放置参数确保方块被保存
        level.setBlockAndUpdate(fallbackPos, teleportState());
        return fallbackPos;
    }

    /**
     * 搜索平台位置，最多16x16范围
     */
    private BlockPos findPlatformPosition(ServerLevel level, BlockPos center, int targetY, int maxSearchRadius) {
        // 先检查中心位置
        BlockPos centerPos = new BlockPos(center.getX(), targetY, center.getZ());
        if (level.getBlockState(centerPos.below()).isSolid() && ConfigManager.isPlatformBlock(
                level.getBlockState(centerPos.below()).getBlock()) && level.getBlockState(centerPos).canBeReplaced()) {
            return centerPos;
        }

        // 向外搜索，最多16x16范围
        for (int radius = 1; radius <= maxSearchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // 只检查环形区域，减少检查次数
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        BlockPos testPos = new BlockPos(center.getX() + dx, targetY, center.getZ() + dz);
                        // 检查下方是否为平台方块且当前位置可替换
                        if (level.getBlockState(testPos.below()).isSolid() && ConfigManager.isPlatformBlock(
                                level.getBlockState(testPos.below()).getBlock()) && level.getBlockState(testPos)
                                                                                         .canBeReplaced()) {
                            return testPos;
                        }
                    }
                }
            }
        }

        return null;
    }

    private BlockState teleportState() {
        return getTeleportBlock().get().defaultBlockState();
    }
}