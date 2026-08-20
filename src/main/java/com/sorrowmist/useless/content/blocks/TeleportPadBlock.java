package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.world.teleport.AbstractDimensionTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
        handleUse(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state,
                                                        @NotNull Level level,
                                                        @NotNull BlockPos pos,
                                                        @NotNull Player player,
                                                        @NotNull BlockHitResult hitResult) {
        handleUse(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void handleUse(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            boolean editConfiguration = serverPlayer.isShiftKeyDown();
            Objects.requireNonNull(level.getServer()).execute(() -> {
                AbstractDimensionTeleporter teleporter = teleporterSupplier.get();
                if (editConfiguration) {
                    com.sorrowmist.useless.content.menus.DimensionConfigMenu.openForEdit(
                            serverPlayer, teleporter, pos);
                } else {
                    teleporter.handleTeleport(serverPlayer, pos);
                }
            });
        }
    }
}
