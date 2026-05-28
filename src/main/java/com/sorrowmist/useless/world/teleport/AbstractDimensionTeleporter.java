package com.sorrowmist.useless.world.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract Dimension Teleporter
 * <p>
 * Code reference from AllTheModium (ATM) mod - TeleportPad class
 * GitHub: https://github.com/AllTheMods/AllTheModium/blob/1.21.x/src/main/java/com/thevortex/allthemodium/blocks/TeleportPad.java
 * <p>
 * 参考内容包括:
 * - POI系统查找传送垫
 * - 螺旋搜索算法 (BlockPos.spiralAround)
 * - 安全检查 (isSafeSpot)
 * - 天花板维度Y轴搜索 (findSafeY)
 */
@SuppressWarnings("all")
public abstract class AbstractDimensionTeleporter {

    /* ================= 子类提供 ================= */

    protected abstract ResourceKey<Level> getDimensionKey();

    protected abstract Supplier<Block> getTeleportBlock();

    protected abstract net.minecraft.core.Holder<PoiType> getPOI();

    /* ================= 入口 ================= */

    public void handleTeleport(ServerPlayer player, BlockPos sourcePos) {
        ServerLevel from = (ServerLevel) player.level();

        ResourceKey<Level> targetKey =
                from.dimension().equals(getDimensionKey())
                        ? Level.OVERWORLD
                        : getDimensionKey();

        ServerLevel target = player.server.getLevel(targetKey);
        if (target == null) return;

        BlockPos targetBlock = findSafeExit(target, sourcePos);
        if (targetBlock == null) return;

        // 轻量级区块票证
        target.getChunkSource().addRegionTicket(
                TicketType.PORTAL,
                new ChunkPos(targetBlock),
                1,
                targetBlock
        );

        teleport(player, target, targetBlock);
    }

    /// 查找安全的出口位置，参考ATM的findSafeExit
    private BlockPos findSafeExit(ServerLevel level, BlockPos entryPos) {
        // 1. 使用POI系统查找现有的传送方块
        Optional<BlockPos> existing = findClosestTeleportBlock(
                level, entryPos, 32, level.getWorldBorder()
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. 使用螺旋搜索查找安全位置
        for (BlockPos.MutableBlockPos candidate : BlockPos.spiralAround(entryPos, 32, Direction.EAST, Direction.SOUTH)) {
            if (!level.getWorldBorder().isWithinBounds(candidate)) continue;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ());
            if (level.dimensionType().hasCeiling()) {
                y = findSafeY(level, candidate.getX(), y, candidate.getZ(), candidate);
            }

            BlockPos spot = new BlockPos(candidate.getX(), y, candidate.getZ());
            if (isSafeSpot(level, spot)) {
                // 创建新的传送方块
                BlockState teleportState = getTeleportBlock().get().defaultBlockState();
                level.setBlockAndUpdate(spot, teleportState);
                return spot;
            }
        }

        // 3. 回退到原始位置
        BlockState teleportState = getTeleportBlock().get().defaultBlockState();
        level.setBlockAndUpdate(entryPos, teleportState);
        return entryPos;
    }

    // 在天花板维度（如下界）中查找安全的Y坐标
    private int findSafeY(ServerLevel level, int x, int y, int z, BlockPos.MutableBlockPos pos) {
        int minY = level.getMinBuildHeight();
        pos.set(x, y - 1, z);
        pos.move(Direction.DOWN);

        while (pos.getY() > minY) {
            if (isSafeSpot(level, pos.immutable())) {
                return pos.getY();
            }
            pos.move(Direction.DOWN);
        }

        return level.getChunkSource().getGenerator().getSpawnHeight(level.getChunk(pos).getHeightAccessorForGeneration());
    }

    // 安全检查：确保1x2空间 + 固体地面
    private boolean isSafeSpot(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        FluidState hereFluid = level.getFluidState(pos);
        BlockState below = level.getBlockState(pos.below());
        FluidState belowFluid = level.getFluidState(pos.below());
        BlockState above = level.getBlockState(pos.above());
        FluidState aboveFluid = level.getFluidState(pos.above());

        return ((here.isAir() || here.is(BlockTags.REPLACEABLE)) && hereFluid.isEmpty()) &&
                ((above.isAir() || above.is(BlockTags.REPLACEABLE)) && aboveFluid.isEmpty()) &&
                (!(below.isAir() || below.is(BlockTags.REPLACEABLE) || below.is(Blocks.BEDROCK)) && belowFluid.isEmpty());
    }

    // 使用POI系统查找最近的传送方块
    public Optional<BlockPos> findClosestTeleportBlock(
            ServerLevel level,
            BlockPos origin,
            int radius,
            WorldBorder border
    ) {
        PoiManager poiManager = level.getPoiManager();
        poiManager.ensureLoadedAndValid(level, origin, radius);

        return poiManager.getInSquare(
                        record -> record.is(getPOI()),
                        origin,
                        radius,
                        PoiManager.Occupancy.ANY
                )
                .map(PoiRecord::getPos)
                .filter(border::isWithinBounds)
                .filter(pos -> level.getBlockState(pos).is(getTeleportBlock().get()))
                .min(Comparator.<BlockPos>
                                comparingDouble(pos -> pos.distSqr(origin))
                        .thenComparingInt(Vec3i::getY)
                );
    }

    /// 执行传送
    private void teleport(ServerPlayer player, ServerLevel level, BlockPos targetPos) {
        player.teleportTo(
                level,
                targetPos.getX() + 0.5D,
                targetPos.getY() + 1.25D,
                targetPos.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }
}