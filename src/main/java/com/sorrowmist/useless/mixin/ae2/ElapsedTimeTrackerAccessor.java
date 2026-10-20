package com.sorrowmist.useless.mixin.ae2;

import appeng.api.stacks.AEKeyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "appeng.crafting.execution.ElapsedTimeTracker", remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void uselessMod$decrementItems(long amount, AEKeyType keyType);
}
