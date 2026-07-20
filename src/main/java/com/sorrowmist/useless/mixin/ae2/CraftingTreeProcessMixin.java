package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeProcess;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternPlanning;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public abstract class CraftingTreeProcessMixin {
    @Shadow(remap = false)
    @Final
    IPatternDetails details;

    @ModifyArgs(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/CraftingTreeNode;<init>(Lappeng/api/networking/crafting/ICraftingService;Lappeng/crafting/CraftingCalculation;Lappeng/api/stacks/AEKey;JLappeng/crafting/CraftingTreeProcess;I)V"),
            remap = false)
    private void uselessMod$preferDeclaredDynamicInput(Args args) {
        int slot = args.get(5);
        AEKey preferred = DynamicPatternPlanning.preferDeclaredCraftableInput(
                details,
                slot,
                args.get(2),
                (ICraftingService) args.get(0));
        args.set(2, preferred);
    }
}
