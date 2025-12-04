package com.sorrowmist.useless.mixin;

import de.castcrafter.travelanchors.block.BlockTravelAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockTravelAnchor.class)
public class BlockTravelAnchorMixin {

    @Inject(
            method = "onRemove",
            at = @At("HEAD")
    )
    private void onRemoveInjection(BlockState state, Level level, BlockPos pos,
                                   BlockState newState, boolean moved, CallbackInfo ci) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            handleTravelAnchorBreak(level, pos, state);
        }
    }

    private void handleTravelAnchorBreak(Level level, BlockPos pos, BlockState state) {
        // TODO: 你的逻辑
    }
}
