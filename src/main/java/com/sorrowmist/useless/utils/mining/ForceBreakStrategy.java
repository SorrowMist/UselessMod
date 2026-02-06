package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.SophisticatedCompat;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collections;
import java.util.List;

/**
 * R键单方块破坏策略
 * 特殊掉落逻辑：
 * 1. 精准采集模式：强制获取带NBT的方块
 * 2. 非精准采集模式：正常获取掉落物，无掉落物时强制掉落方块本身
 */
public class ForceBreakStrategy implements MiningStrategy {

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();

        // 检查是否为精准采集模式
        boolean isSilkTouch = hand.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE)
                == EnchantMode.SILK_TOUCH;

        List<ItemStack> drops;

        if (isSilkTouch) {
            // 精准采集模式：强制获取带NBT的方块
            drops = MiningUtils.getSilkTouchDrops(state, level, pos);
        } else {
            // 非精准采集模式：正常获取掉落物
            BlockEntity be = level.getBlockEntity(pos);
            drops = Block.getDrops(state, level, pos, be, player, hand);

            // 如果没有有效掉落物，强制掉落方块本身
            if (MiningUtils.hasNoValidDrops(drops)) {
                drops = Collections.singletonList(new ItemStack(block.asItem()));
            }
        }

        // 处理掉落物
        MiningUtils.handleDrops(player, drops, hand);

        // 计算并弹出经验（时运模式）
        BlockEntity be = level.getBlockEntity(pos);
        if (!isSilkTouch && hand.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
            int exp = block.getExpDrop(state, level, pos, be, player, hand);
            if (exp > 0) {
                block.popExperience(level, pos, exp);
            }
        }

        // 对于 SophisticatedStorage 方块，需要先设置 packed 状态
        if (ModList.get().isLoaded("sophisticatedstorage") && be != null) {
            SophisticatedCompat.handlePreRemoval(be);
        }

        // 破坏方块（使用安全移除方法，防止容器内容物额外掉落）
        MiningUtils.removeBlockSafely(level, pos);

        // 取消原版事件
        event.setCanceled(true);
    }
}
