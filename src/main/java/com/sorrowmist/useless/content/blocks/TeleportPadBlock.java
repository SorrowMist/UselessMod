package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.world.teleport.AbstractDimensionTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

public class TeleportPadBlock extends Block {
    private final Supplier<AbstractDimensionTeleporter> teleporterSupplier;

    public TeleportPadBlock(Supplier<AbstractDimensionTeleporter> teleporterSupplier, BlockBehaviour.Properties properties) {
        super(properties);
        this.teleporterSupplier = teleporterSupplier;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                         @NotNull BlockState state,
                                                         @NotNull Level level,
                                                         @NotNull BlockPos pos,
                                                         @NotNull Player player,
                                                         @NotNull InteractionHand hand,
                                                         @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            Objects.requireNonNull(level.getServer()).execute(() -> {
                teleporterSupplier.get().handleTeleport(serverPlayer, pos);
            });
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
