package com.sorrowmist.useless.mixin.advancedae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public abstract class ExecutingCraftingJobMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lnet/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob$CraftingDifferenceListener;Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;)V",
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
