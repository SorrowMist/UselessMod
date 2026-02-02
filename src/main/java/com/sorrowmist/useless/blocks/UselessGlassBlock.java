package com.sorrowmist.useless.blocks;


import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 无用玻璃方块抽象基类
 * 特性：
 * 1. 防爆（高爆炸抗性）
 * 2. Shift+右键快速破坏并直接进入背包
 * 3. 透光（类似原版玻璃）
 */
public abstract class UselessGlassBlock extends Block {

    protected UselessGlassBlock(Properties properties) {
        super(properties.instrument(NoteBlockInstrument.HAT)
                        .sound(SoundType.GLASS)
                        .noOcclusion() // 不完全遮挡视线
                        // 防止生物在玻璃上刷出
                        .isValidSpawn((state, getter, pos, entityType) -> false)
                        // 红石不导电
                        .isRedstoneConductor((state, getter, pos) -> false)
                        // 玩家不会窒息
                        .isSuffocating((state, getter, pos) -> false)
                        // 不阻挡视线
                        .isViewBlocking((state, getter, pos) -> false));
    }

    /**
     * 跳过相邻同类方块的渲染面（连接纹理效果）
     */
    @Override
    protected boolean skipRendering(@NotNull BlockState state,
                                    BlockState adjacentBlockState,
                                    @NotNull Direction direction) {
        return adjacentBlockState.getBlock() instanceof UselessGlassBlock;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack heldItem,
                                                       @NotNull BlockState state,
                                                       @NotNull Level level,
                                                       @NotNull BlockPos pos,
                                                       Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                if (heldItem.getItem() instanceof EndlessBeafItem){
                    MiningUtils.quickBreakBlock(level, pos, state, player, heldItem);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    public int getLightBlock(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 0; // 0 表示完全透光（不衰减光线）
    }

    @Override
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F; // 保证方块下方的阴影不会过黑
    }
}
