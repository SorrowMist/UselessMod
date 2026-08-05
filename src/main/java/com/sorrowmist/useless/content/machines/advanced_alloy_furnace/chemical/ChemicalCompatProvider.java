package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Optional chemical integration owned by a compatibility module. */
public interface ChemicalCompatProvider {
    ChemicalCompatProvider NONE = new ChemicalCompatProvider() {
        @Override
        public FurnaceChemicalStorage createStorage(long capacity, Runnable onChanged) {
            return FurnaceChemicalStorage.DISABLED;
        }

        @Override
        public @Nullable ChemicalHandlerView getAdjacentHandler(Level level, BlockPos pos, BlockState state,
                                                                BlockEntity entity, @Nullable Direction side) {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    };

    FurnaceChemicalStorage createStorage(long capacity, Runnable onChanged);

    @Nullable
    ChemicalHandlerView getAdjacentHandler(Level level, BlockPos pos, BlockState state,
                                           BlockEntity entity, @Nullable Direction side);

    default boolean isAvailable() {
        return true;
    }
}
