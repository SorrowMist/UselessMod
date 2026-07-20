package com.sorrowmist.useless.mixin.ae2;

import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob", remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("waitingFor")
    ListCraftingInventory uselessMod$getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker uselessMod$getTimeTracker();

    @Accessor("finalOutput")
    GenericStack uselessMod$getFinalOutput();

    @Accessor("remainingAmount")
    long uselessMod$getRemainingAmount();

    @Accessor("remainingAmount")
    void uselessMod$setRemainingAmount(long amount);

    @Accessor("link")
    CraftingLink uselessMod$getLink();
}
