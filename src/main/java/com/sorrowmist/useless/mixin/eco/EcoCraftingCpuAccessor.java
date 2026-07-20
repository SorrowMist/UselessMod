package com.sorrowmist.useless.mixin.eco;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU", remap = false)
public interface EcoCraftingCpuAccessor {
    @Invoker("markDirty")
    void uselessMod$markDirty();
}
