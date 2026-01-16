package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 方块破坏工具类，包含普通破坏单个方块的逻辑
 */
public class BlockBreakUtils {

    /**
     * 普通破坏单个方块
     * @param event 方块破坏事件
     * @param level 世界
     * @param pos 方块位置
     * @param state 方块状态
     * @param player 玩家
     * @param stack 工具
     * @return 是否成功破坏
     */
    public static boolean normalBreakBlock(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        // 修复：创造模式下不处理自动收集
        if (player.isCreative()) {
            return false;
        }

        // 检查方块是否含水（更通用的方法，适用于原版和模组方块）
        // 获取方块的流体状态
        net.minecraft.world.level.material.FluidState fluidState = level.getFluidState(pos);
        // 检查流体是否是水，并且是水源方块（level == 8表示水源）
        if (fluidState.getType() == net.minecraft.world.level.material.Fluids.WATER && fluidState.isSource()) {
            // 含水方块，直接返回false，让原版破坏逻辑处理
            return false;
        }

        // 尝试获取方块的掉落物
        List<ItemStack> drops = getBlockDrops(state, level, pos, player, stack);

        // 检查是否成功获取到掉落物
        if (drops == null || drops.isEmpty() || hasInvalidDrops(drops)) {
            // 如果没有获取到有效的掉落物，回退到原版破坏逻辑
            handleFallbackBlockBreak(level, pos, state, player, stack);
            return false;
        }

        // 对于能正常获取掉落物的方块，使用自动收集逻辑
        return handleNormalBlockBreak(event, level, pos, state, player, stack, drops);
    }

    /**
     * 获取方块掉落物
     */
    public static List<ItemStack> getBlockDrops(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Collections.emptyList();
        }

        try {
            // 创建LootParams来获取正确的掉落物
            LootParams.Builder lootParamsBuilder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos));

            List<ItemStack> drops = state.getDrops(lootParamsBuilder);

            // 过滤掉空气和空堆叠
            return drops.stream()
                    .filter(drop -> !drop.isEmpty() && drop.getItem() != Items.AIR)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // 忽略错误但不崩溃
            return Collections.emptyList();
        }
    }

    /**
     * 检查掉落物列表是否有效
     */
    public static boolean hasInvalidDrops(List<ItemStack> drops) {
        // 检查所有掉落物是否都是空气或无效物品
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                return false; // 至少有一个有效掉落物
            }
        }
        return true; // 所有掉落物都无效
    }

    /**
     * 处理普通方块的自动收集
     */
    public static boolean handleNormalBlockBreak(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack, List<ItemStack> drops) {
        // 处理掉落物：优先存入AE网络，然后是玩家背包，最后是掉落
        EndlessBeafItem.handleDrops(drops, player, stack);

        // 生成剩余的掉落物（如果有）
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                // 掉落在玩家脚下
                ItemEntity itemEntity = new ItemEntity(level,
                        player.getX(), player.getY(), player.getZ(),
                        drop.copy());
                level.addFreshEntity(itemEntity);
            }
        }

        // 无论是否收集成功，都取消原版事件并手动破坏方块，避免重复掉落
        event.setCanceled(true);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        // 播放破坏音效
        playBreakSoundWithCooldown(level, pos, state, player);
        return true;
    }

    /**
     * 回退到原版破坏逻辑的处理
     */
    public static void handleFallbackBlockBreak(Level level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        // 记录破坏前已有的物品实体
        List<ItemEntity> existingItems = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos).inflate(3.0));
        Set<UUID> existingItemIds = new HashSet<>();
        for (ItemEntity item : existingItems) {
            existingItemIds.add(item.getUUID());
        }

        // 让方块正常破坏（不取消事件）
        // 使用延迟任务来收集新生成的掉落物
        level.getServer().execute(() -> {
            // 等待一小段时间让掉落物生成
            try {
                Thread.sleep(10); // 10ms通常足够
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 获取新生成的物品实体
            List<ItemEntity> newItems = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(pos).inflate(3.0));

            for (ItemEntity itemEntity : newItems) {
                // 只处理新生成的物品
                if (!existingItemIds.contains(itemEntity.getUUID())) {
                    ItemStack itemStack = itemEntity.getItem().copy();

                    if (!itemStack.isEmpty()) {
                        // 尝试将物品添加到玩家背包
                        if (addItemToPlayerInventory(player, itemStack)) {
                            // 如果成功添加到背包，移除原物品实体
                            itemEntity.discard();
                        }
                        // 如果背包满了，物品会保留在原地
                    }
                }
            }
        });

        // 播放破坏音效
        playBreakSoundWithCooldown(level, pos, state, player);
    }

    /**
     * 将物品添加到玩家背包
     */
    public static boolean addItemToPlayerInventory(Player player, ItemStack stack) {
        if (player.getInventory().add(stack)) {
            // 成功添加到背包
            return true;
        } else {
            // 背包已满
            return false;
        }
    }

    /**
     * 带冷却的音效播放方法
     */
    public static void playBreakSoundWithCooldown(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) return;

        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastSoundTime.get(playerId);

        // 检查冷却时间
        if (lastTime == null || currentTime - lastTime >= SOUND_COOLDOWN) {
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            lastSoundTime.put(playerId, currentTime);
        }
    }

    // 音效冷却系统
    private static final Map<UUID, Long> lastSoundTime = new HashMap<>();
    private static final long SOUND_COOLDOWN = 50; // 50毫秒冷却时间
}