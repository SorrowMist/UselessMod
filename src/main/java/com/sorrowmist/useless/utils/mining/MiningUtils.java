package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;
import java.util.stream.Collectors;

class MiningUtils {
    // 音效冷却系统
    private static final Map<UUID, Long> lastSoundTime = new HashMap<>();
    private static final long SOUND_COOLDOWN = 50; // 50毫秒冷却时间

    /**
     * 获取方块掉落物
     */
    static List<ItemStack> getBlockDrops(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Collections.emptyList();
        }

        BlockEntity be = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, be, player, tool);

        return drops.stream()
                    .filter(drop -> !drop.isEmpty() && drop.getItem() != Items.AIR)
                    .collect(Collectors.toList());
    }

    /**
     * 检查是否完全没有有效掉落物
     */
    static boolean hasNoValidDrops(List<ItemStack> drops) {
        return drops.isEmpty() || drops.stream().allMatch(stack -> stack.isEmpty() || stack.is(Items.AIR));
    }

    /**
     * 回退：使用原版掉落，然后立即收集物品实体（更稳定，避免Thread.sleep）
     */
    static void handleFallbackBlockBreak(Level level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (level.isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // 记录破坏前的物品实体UUID
        AABB area = new AABB(pos).inflate(3.0);
        Set<UUID> existingIds = level.getEntitiesOfClass(ItemEntity.class, area)
                                     .stream()
                                     .map(ItemEntity::getUUID)
                                     .collect(Collectors.toSet());

        // 原版破坏（会生成掉落物实体）
        level.destroyBlock(pos, true, player);

        // 立即收集新出现的物品实体
        level.getEntitiesOfClass(ItemEntity.class, area).stream()
             .filter(entity -> !existingIds.contains(entity.getUUID()))
             .forEach(entity -> {
                 ItemStack stack = entity.getItem().copy();
                 if (!stack.isEmpty() && player.getInventory().add(stack)) {
                     entity.discard(); // 成功进背包 → 删除实体
                 }
                 // 背包满 → 留在地上
             });

        playBreakSoundWithCooldown(level, pos, state, player);
    }

    /**
     * 正常处理：自动收集到背包 + 经验 + 水源保留
     */
    static void handleNormalBlockBreak(BlockEvent.BreakEvent event, Level level, BlockPos pos,
                                       BlockState state, Player player, ItemStack stack, List<ItemStack> drops) {
        // 1. 仅服务端
        if (level.isClientSide) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        BlockEntity be = level.getBlockEntity(pos);

        // 3. 直接给玩家（原版方式：自动处理满了掉落）
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                player.getInventory().placeItemBackInInventory(drop);
            }
        }

        // 4. 计算并弹出经验（原版API，无需事件方法）
        if (stack.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
            int exp = state.getBlock().getExpDrop(state, level, pos, be, player, stack);
            if (exp > 0) {
                state.getBlock().popExperience(serverLevel, pos, exp);
            }
        }

        // 5. 破坏方块（原版：false=不掉落物品；自动处理水源、粒子、音效）
        level.destroyBlock(pos, false, player);

        // 6. 取消事件
        event.setCanceled(true);
    }

    /**
     * 播放破坏音效（带防刷音效）
     */
    private static void playBreakSoundWithCooldown(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) return;

        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastSoundTime.get(playerId);

        if (lastTime == null || currentTime - lastTime >= SOUND_COOLDOWN) {
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            lastSoundTime.put(playerId, currentTime);
        }
    }
}
