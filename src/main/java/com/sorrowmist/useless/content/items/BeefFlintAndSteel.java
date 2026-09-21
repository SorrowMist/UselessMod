package com.sorrowmist.useless.content.items;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 造化杖的「打火石」行为。
 *
 * <p>与 {@link net.minecraft.world.item.FlintAndSteelItem} 保持一致的两条路径：
 * <ul>
 *   <li>右键可点亮的方块（营火、蜡烛、蜡烛蛋糕）→ 直接点亮；</li>
 *   <li>否则在点击面的相邻位置尝试放火，位置不合适则不生效。</li>
 * </ul>
 *
 * <p>造化杖本身不可损坏，因此不扣耐久，其余判定与音效完全照抄原版。
 */
public final class BeefFlintAndSteel {

    private BeefFlintAndSteel() {
    }

    /**
     * 尝试执行一次打火石点火。
     *
     * @return {@link InteractionResult#PASS} 表示当前位置打火石没有可做的事，
     *         交由后续行为链继续处理。
     */
    public static InteractionResult tryUse(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
                    level.getRandom().nextFloat() * 0.4F + 0.8F);
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, Boolean.TRUE), 11);
                awardStat(level, player, ctx.getItemInHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos firePos = pos.relative(ctx.getClickedFace());
        if (!BaseFireBlock.canBePlacedAt(level, firePos, ctx.getHorizontalDirection())) {
            return InteractionResult.PASS;
        }

        level.playSound(player, firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
                level.getRandom().nextFloat() * 0.4F + 0.8F);
        if (!level.isClientSide) {
            level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
            awardStat(level, player, ctx.getItemInHand());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void awardStat(Level level, Player player, ItemStack tool) {
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, BlockPos.ZERO, tool);
        }
    }
}
