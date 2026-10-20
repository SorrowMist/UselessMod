package com.sorrowmist.useless.compat.forge1201.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/** Native 1.20.1 lookups: preserve sides and never load an unloaded chunk for automation. */
public final class CapabilityAccess {
    private CapabilityAccess() {}
    @Nullable public static <T> T get(Level level, Capability<T> capability, BlockPos pos,
                                      @Nullable Direction side) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        return fromEntity(level.getBlockEntity(pos), capability, side);
    }
    @Nullable public static <T> T get(Level level, Capability<T> capability, BlockPos pos,
                                      @Nullable BlockState state, @Nullable BlockEntity entity,
                                      @Nullable Direction side) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        // A cached entity from a previous block must not receive resources.
        BlockEntity current = level.getBlockEntity(pos);
        return fromEntity(current, capability, side);
    }
    @Nullable private static <T> T fromEntity(@Nullable BlockEntity entity, Capability<T> capability,
                                             @Nullable Direction side) {
        return entity == null || entity.isRemoved() ? null : entity.getCapability(capability, side).orElse(null);
    }
    @Nullable public static <T> T get(ItemStack stack, Capability<T> capability) {
        return stack == null || stack.isEmpty() ? null : stack.getCapability(capability).orElse(null);
    }
}
