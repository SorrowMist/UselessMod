package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternPlanning;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public abstract class CraftingTreeProcessMixin {
    @Shadow(remap = false)
    @Final
    IPatternDetails details;

    // Forge 47 / Mixin 0.8.5 cannot reliably load ModifyArgs synthetic classes
    // from ModLauncher's module classloader on the AE Crafting Calculator thread.
    // Wrap allocation instead: preserve every argument and change only the key.
    @WrapOperation(
            method = "<init>",
            at = @At(value = "NEW", target = "appeng/crafting/CraftingTreeNode"),
            remap = false)
    private CraftingTreeNode uselessMod$preferDeclaredDynamicInput(
            ICraftingService service, CraftingCalculation calculation, AEKey key,
            long amount, CraftingTreeProcess parent, int slot,
            Operation<CraftingTreeNode> original) {
        AEKey preferred = DynamicPatternPlanning.preferDeclaredCraftableInput(
                details, slot, key, service);
        return original.call(service, calculation, preferred, amount, parent, slot);
    }
}
