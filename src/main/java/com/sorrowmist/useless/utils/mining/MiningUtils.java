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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MiningUtils {
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
     * 回退：使用原版掉落，然后立即收集物品实体
     */
    static void handleFallbackBlockBreak(Level level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (level.isClientSide()) return;

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
    }

    /**
     * 正常处理
     */
    static void handleNormalBlockBreak(
            BlockEvent.BreakEvent event,
            Level level,
            BlockPos pos,
            BlockState state,
            Player player,
            ItemStack tool,
            List<ItemStack> drops
    ) {
        if (level.isClientSide()) return;

        // ======== 掉落物 → 尝试进背包 ========
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }
            }
        }

        // 4. 计算并弹出经验（原版API，无需事件方法）
        if (tool.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
            int exp = state.getBlock().getExpDrop(state, level, pos, level.getBlockEntity(pos), player, tool);
            if (exp > 0) {
                state.getBlock().popExperience((ServerLevel) level, pos, exp);
            }
        }

        // 静默移除方块（不触发额外动画/粒子）
        level.removeBlock(pos, false);

        // 阻止原版重复掉落
        event.setCanceled(true);
    }

    /**
     * 快速破坏指定方块（Shift+右键物品使用时调用）
     * 功能：掉落物直接进背包、背包满掉脚下、正确保留 waterlogged 水源、弹出经验、粒子音效
     *
     * @param world  世界
     * @param pos    方块位置
     * @param state  方块状态
     * @param player 玩家（必须非空）
     * @param tool   手中物品（用于计算掉落、附魔、耐久等）
     */
    public static void quickBreakBlock(Level world, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (world.isClientSide()) {
            world.playSound(player, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) world;
        BlockEntity blockEntity = world.getBlockEntity(pos);

        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, tool);

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false); // 背包满 → 丢出
                }
            }
        }

        world.destroyBlock(pos, false, player);
    }
}
