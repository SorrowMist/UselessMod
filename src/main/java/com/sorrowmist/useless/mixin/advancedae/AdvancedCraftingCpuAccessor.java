package com.sorrowmist.useless.mixin.advancedae;

import appeng.api.stacks.GenericStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU", remap = false)
public interface AdvancedCraftingCpuAccessor {
    @Invoker("markDirty")
    void uselessMod$markDirty();

    @Invoker("updateOutput")
    void uselessMod$updateOutput(GenericStack stack);
}
