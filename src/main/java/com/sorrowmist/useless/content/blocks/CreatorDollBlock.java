package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.content.blockentities.CreatorDollBlockEntity;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** 空手交互触发的单次弹跳行为参考 ExtendedAE Plus 提交 3832a827e859807a2fff3857c8719a097181804c。 */
public final class CreatorDollBlock extends Block implements EntityBlock {
    public static final BooleanProperty BOUNCING = BooleanProperty.create("bouncing");
    public static final IntegerProperty BOUNCE_ID = IntegerProperty.create("bounce_id", 0, 15);

    public CreatorDollBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(BOUNCING, false)
                .setValue(BOUNCE_ID, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOUNCING, BOUNCE_ID);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreatorDollBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CREATOR_DOLL.get()) return null;
        return level.isClientSide
                ? (world, pos, current, entity) -> CreatorDollBlockEntity.clientTick(
                        world, current, (CreatorDollBlockEntity) entity)
                : (world, pos, current, entity) -> CreatorDollBlockEntity.serverTick(
                        world, pos, current, (CreatorDollBlockEntity) entity);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof CreatorDollBlockEntity doll)) return InteractionResult.PASS;
        if (!level.isClientSide) doll.startBounce();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
