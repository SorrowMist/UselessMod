package com.sorrowmist.useless.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 方块破坏工具类，包含普通破坏单个方块的逻辑
 */
public class BlockBreakUtils {

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