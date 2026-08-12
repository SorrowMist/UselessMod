package com.sorrowmist.useless.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Turns the pattern a pattern encoding terminal just produced into an Omniversal Pattern, but only
 * when the player transferred an Omniversal Alloy Furnace recipe from JEI into that terminal.
 *
 * <p>The recipe identity — which carries the mold — is recorded by
 * {@code OmniversalPatternJeiTransferHandler} the moment "+" is clicked and stored on this logic
 * instance. Encoding is not intercepted at click time: the player still goes through AE2's normal
 * flow, so a blank pattern is consumed from the terminal and the result appears in the bottom-right
 * slot. That also means one "+" click can encode several identical patterns, and AE2's
 * network-aware ingredient substitution still applies.
 *
 * <p>Hooking {@code onChangeInventory} rather than {@code PatternEncodingTermMenu#encode} covers
 * every way a pattern can land in the encoded slot: AE2's own {@code encode()}, DataEnergistics'
 * {@code encode()} mixin, and ExtendedAE's {@code UniversalPatternEncodingTermMenu}, which writes
 * the slot directly. Calling {@code setItemDirect} from inside the callback does not recurse —
 * {@code AppEngInternalInventory.notifyingChanges} is already true while the callback runs.
 */
@Mixin(targets = "appeng.parts.encoding.PatternEncodingLogic", remap = false)
public class PatternEncodingLogicMixin implements PendingOmniversalPatternHolder {
    @Shadow(remap = false)
    @Final
    private IPatternTerminalLogicHost host;

    @Shadow(remap = false)
    @Final
    private AppEngInternalInventory encodedPatternInv;

    /**
     * Not persisted: a pending pick only makes sense within the session that made it, and a stale
     * identity surviving a world reload would silently re-tag an unrelated pattern.
     */
    @Unique
    @Nullable
    private AlloyFurnaceRecipeIdentity uselessMod$pendingOmniversalRecipe;

    @Unique
    @Nullable
    private String uselessMod$pendingOmniversalSourceId;

    @Override
    @Nullable
    public AlloyFurnaceRecipeIdentity uselessMod$getPendingOmniversalRecipe() {
        return uselessMod$pendingOmniversalRecipe;
    }

    @Override
    public void uselessMod$setPendingOmniversalRecipe(@Nullable AlloyFurnaceRecipeIdentity identity) {
        this.uselessMod$pendingOmniversalRecipe = identity;
    }

    @Override
    @Nullable
    public String uselessMod$getPendingOmniversalSourceId() {
        return uselessMod$pendingOmniversalSourceId;
    }

    @Override
    public void uselessMod$setPendingOmniversalSourceId(@Nullable String sourceId) {
        this.uselessMod$pendingOmniversalSourceId = sourceId == null
                ? RecipeSourceIds.UNKNOWN : RecipeSourceIds.normalize(sourceId);
    }

    @Override
    public void uselessMod$tryConvertPendingOmniversalPattern() {
        uselessMod$tryConvertToOmniversal();
    }

    @Inject(method = "onChangeInventory(Lappeng/util/inv/AppEngInternalInventory;I)V", at = @At("HEAD"), remap = false)
    private void uselessMod$tryConvertToOmniversal(AppEngInternalInventory inv, int slot, CallbackInfo ci) {
        if (inv != encodedPatternInv || slot != 0) return;

        uselessMod$tryConvertToOmniversal();
    }

    @Unique
    private void uselessMod$tryConvertToOmniversal() {
        AlloyFurnaceRecipeIdentity pending = uselessMod$pendingOmniversalRecipe;

        ItemStack pattern = encodedPatternInv.getStackInSlot(0);
        if (pattern.isEmpty()) return;

        Level level = host.getLevel();
        if (level == null || level.isClientSide()) return;

        IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, level);
        if (!(details instanceof AEProcessingPattern) || details instanceof OmniversalPatternDetails) return;

        Optional<AlloyFurnaceRecipeCatalog.Entry> entry = Optional.empty();
        if (pending != null) {
            String sourceId = uselessMod$pendingOmniversalSourceId;
            // The client and server can materialize a tag-backed Ingredient with different
            // representative items. Resolve the selected recipe by id and the encoded pattern
            // contents when the fingerprint differs, otherwise a shared chemical-conversion /
            // oxidizing recipe is indistinguishable from the plain AE2 pattern at this point.
            entry = AlloyFurnaceRecipeCatalog.resolvePattern(level, sourceId, pending, details)
                    .filter(candidate -> AlloyFurnaceRecipeCatalog.matchesRecipe(
                            level, sourceId, candidate.recipe(), details));
        }
        if (entry.isEmpty()) {
            entry = AlloyFurnaceRecipeCatalog.findUniqueMoldPatternCandidate(level, details);
        }
        if (entry.isEmpty()) return;

        ItemStack omniversal = OmniversalPatternEncoding.encode(pattern, details, entry.get(), level);
        if (!omniversal.isEmpty()) {
            encodedPatternInv.setItemDirect(0, omniversal);
        }
    }
}
