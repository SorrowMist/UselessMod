package com.sorrowmist.useless.blocks.teleport;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TeleportBlock {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UselessMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 维度ID
    private static final ResourceLocation USELESSDIM_ID = ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "uselessdim");
    private static final ResourceKey<Level> USELESSDIM_KEY = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, USELESSDIM_ID);


    public static final RegistryObject<Block> TELEPORT_BLOCK = BLOCKS.register("teleport_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0f,65536.0f)
                    .requiresCorrectToolForDrops()) {
                @Override
                public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        // 异步处理传送，避免阻塞主线程
                        level.getServer().execute(() -> {
                            handleTeleport(serverPlayer, pos);
                        });
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide());
                }
            });

    public static final RegistryObject<Item> TELEPORT_BLOCK_ITEM = ITEMS.register("teleport_block",
            () -> new BlockItem(TELEPORT_BLOCK.get(), new Item.Properties()));

    private static void handleTeleport(ServerPlayer player, BlockPos sourcePos) {
        Level currentLevel = player.level();
        ResourceKey<Level> currentDimension = currentLevel.dimension();

        // 判断当前维度
        boolean isInUselessDim = currentDimension.location().equals(USELESSDIM_ID);
        ResourceKey<Level> targetDimension = isInUselessDim ? Level.OVERWORLD : USELESSDIM_KEY;

        ServerLevel targetLevel = player.server.getLevel(targetDimension);
        if (targetLevel == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("目标维度未加载"), false);
            return;
        }

        processTeleport(player, sourcePos, targetLevel);
    }

    private static void processTeleport(ServerPlayer player, BlockPos sourcePos, ServerLevel targetLevel) {
        // 预加载目标区块（但不强制保持加载）
        ChunkPos targetChunkPos = new ChunkPos(sourcePos);

        // 只预加载区块，不强制保持
        targetLevel.getChunk(sourcePos.getX() >> 4, sourcePos.getZ() >> 4);

        // 查找或创建目标传送方块
        BlockPos targetPos = findOrCreateTeleportBlock(targetLevel, sourcePos);
        if (targetPos == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("传送失败，请在其他位置尝试"), false);
            return;
        }

        // 使用轻量级的区块票证（只在传送瞬间保持加载）
        targetLevel.getChunkSource().addRegionTicket(
                TicketType.PORTAL,
                new ChunkPos(targetPos),
                1, // 减少加载范围
                targetPos
        );

        // 立即执行传送
        player.teleportTo(targetLevel,
                targetPos.getX() + 0.5,
                targetPos.getY() + 1.0,
                targetPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot());

        player.displayClientMessage(net.minecraft.network.chat.Component.literal("传送成功！"), false);

        // 不再需要强制加载清理，让Minecraft自然管理区块
    }

    private static BlockPos findOrCreateTeleportBlock(ServerLevel level, BlockPos sourcePos) {
        int searchRadius = 16;
        BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos();

        UselessMod.LOGGER.info("开始在位置 {} 附近搜索传送方块", sourcePos);

        // 优先搜索同一Y层和相邻层
        int[] priorityYLevels = {
                sourcePos.getY(),      // 同一层 (最高优先级)
                sourcePos.getY() - 1,  // 下一层 (高优先级)
                sourcePos.getY() + 1,  // 上一层 (高优先级)
                sourcePos.getY() - 2,  // 下两层
                sourcePos.getY() + 2,  // 上两层
                sourcePos.getY() - 3,  // 下三层
                sourcePos.getY() + 3   // 上三层
        };

        // 先搜索优先级高的层次
        for (int y : priorityYLevels) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }

            BlockPos found = searchAtYLevel(level, sourcePos, y, searchRadius);
            if (found != null) {
                UselessMod.LOGGER.info("在Y层 {} 找到传送方块: {}", y, found);
                return found;
            }
        }

        // 如果优先级层次没找到，向下扩展搜索（地下可能有很多传送方块）
        UselessMod.LOGGER.info("在主要层次未找到，向下扩展搜索");
        for (int y = sourcePos.getY() - 4; y >= level.getMinBuildHeight() + 1; y--) {
            BlockPos found = searchAtYLevel(level, sourcePos, y, searchRadius);
            if (found != null) {
                UselessMod.LOGGER.info("在下方Y层 {} 找到传送方块: {}", y, found);
                return found;
            }

            // 每搜索5层输出一次进度
            if ((sourcePos.getY() - y) % 5 == 0) {
                UselessMod.LOGGER.debug("已向下搜索到Y层 {}", y);
            }
        }

        // 最后向上搜索（作为备选）
        UselessMod.LOGGER.info("向下搜索未找到，向上扩展搜索");
        for (int y = sourcePos.getY() + 4; y < level.getMaxBuildHeight() - 10; y++) {
            BlockPos found = searchAtYLevel(level, sourcePos, y, searchRadius);
            if (found != null) {
                UselessMod.LOGGER.info("在上方Y层 {} 找到传送方块: {}", y, found);
                return found;
            }
        }

        UselessMod.LOGGER.info("未找到现有传送方块，创建新的");
        return createTeleportBlockFast(level, sourcePos);
    }

    private static BlockPos searchAtYLevel(ServerLevel level, BlockPos center, int y, int radius) {
        BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos();

        // 从内向外搜索
        for (int currentRadius = 0; currentRadius <= radius; currentRadius++) {
            // 搜索当前半径的边界
            for (int x = -currentRadius; x <= currentRadius; x++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    // 只搜索边界，避免重复搜索内部
                    if (Math.abs(x) != currentRadius && Math.abs(z) != currentRadius) {
                        continue;
                    }

                    searchPos.set(center.getX() + x, y, center.getZ() + z);

                    if (isValidTeleportBlock(level, searchPos)) {
                        return searchPos.immutable();
                    }
                }
            }
        }

        return null;
    }

    private static boolean isValidTeleportBlock(ServerLevel level, BlockPos pos) {
        // 确保区块已加载
        if (!level.isLoaded(pos)) {
            // 如果区块未加载，尝试快速加载
            ChunkPos chunkPos = new ChunkPos(pos);
            if (!level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                return false; // 不强制加载未加载的区块
            }
        }

        boolean isValid = level.getBlockState(pos).getBlock() == TELEPORT_BLOCK.get();
        if (isValid) {
            UselessMod.LOGGER.debug("验证传送方块在位置 {}: {}", pos, isValid);
        }
        return isValid;
    }

    private static BlockPos createTeleportBlockFast(ServerLevel level, BlockPos sourcePos) {
        // 尝试几个已知的安全高度
        int[] safeHeights = {64, 80, 96, 112, 128};

        for (int height : safeHeights) {
            BlockPos testPos = new BlockPos(sourcePos.getX(), height, sourcePos.getZ());

            if (canPlaceTeleportBlockFast(level, testPos)) {
                level.setBlock(testPos, TELEPORT_BLOCK.get().defaultBlockState(), 3);
                return testPos;
            }
        }

        // 如果预设高度都不行，使用原版的世界生成高度
        BlockPos worldSurfacePos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, sourcePos);
        BlockPos placementPos = worldSurfacePos.above(2); // 在地表上方2格

        if (canPlaceTeleportBlockFast(level, placementPos)) {
            level.setBlock(placementPos, TELEPORT_BLOCK.get().defaultBlockState(), 3);
            return placementPos;
        }

        // 最后尝试：直接在地表创建
        level.setBlock(placementPos, TELEPORT_BLOCK.get().defaultBlockState(), 3);
        // 确保玩家不会掉下去
        level.setBlock(placementPos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);

        return placementPos;
    }

    private static boolean canPlaceTeleportBlockFast(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }

        // 快速检查：位置是否可替换且下方有支撑
        return level.getBlockState(pos).canBeReplaced() &&
                level.getBlockState(pos.below()).isSolid();
    }
}