package com.sorrowmist.useless.mixin.ae2;

import appeng.api.stacks.AEKey;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public interface CraftingCpuLogicAccessor {
    @Accessor("job")
    @Nullable
    ExecutingCraftingJob uselessMod$getJob();

    @Invoker("finishJob")
    void uselessMod$finishJob(boolean success);

    @Invoker("postChange")
    void uselessMod$postChange(AEKey key);
}
