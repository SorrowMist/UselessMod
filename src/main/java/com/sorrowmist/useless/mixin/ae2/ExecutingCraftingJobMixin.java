package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob", remap = false)
public abstract class ExecutingCraftingJobMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;",
                    remap = false))
    private IPatternDetails uselessMod$resolveRestoredPattern(
            AEItemKey definition, Level level) {
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(definition, level);
        return decoded == null ? null : AdvancedAlloyFurnacePatternResolver.resolve(decoded, level);
    }
}
