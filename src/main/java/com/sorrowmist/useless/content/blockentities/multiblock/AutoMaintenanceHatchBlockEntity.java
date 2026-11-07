package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The controller holds maintenance at full every tick while this alternative hatch is installed. */
public final class AutoMaintenanceHatchBlockEntity extends OwnedFurnacePartBlockEntity {
    public AutoMaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_MAINTENANCE_HATCH.get(), pos, state);
    }

    @Override protected boolean isClaimedBy(MultiblockAlloyFurnaceCoreBlockEntity core) {
        return core.claimsMaintenanceHatch(worldPosition);
    }
}
