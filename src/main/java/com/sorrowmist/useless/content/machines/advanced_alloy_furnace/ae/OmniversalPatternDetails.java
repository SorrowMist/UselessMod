package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class OmniversalPatternDetails extends DynamicComponentPatternDetails {
    private final OmniversalPatternData data;
    private final AdvancedAlloyFurnaceRecipe recipe;

    private OmniversalPatternDetails(Decoded decoded) {
        super(decoded.source, OmniversalPatternEncoding.resolveItemIdInputSlots(
                        decoded.entry.recipe(), decoded.source, decoded.data.itemIdInputSlots()),
                decoded.data.itemIdOutputSlots(),
                decoded.level.registryAccess());
        this.data = decoded.data;
        this.recipe = decoded.entry.recipe();
    }

    public static OmniversalPatternDetails decode(AEItemKey definition, Level level) {
        if (definition == null || level == null) return null;
        OmniversalPatternData data = definition.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null || data.version() > OmniversalPatternData.CURRENT_VERSION) {
            throw new IllegalArgumentException("Missing or unsupported omniversal pattern data");
        }
        AEProcessingPattern source = new AEProcessingPattern(definition);
        Optional<AlloyFurnaceRecipeCatalog.Entry> resolved =
                data.version() < OmniversalPatternData.SEMANTIC_FINGERPRINT_VERSION
                        ? AlloyFurnaceRecipeCatalog.resolveLegacyPattern(level, data.identity(), source)
                        : AlloyFurnaceRecipeCatalog.resolve(level, data.identity());
        AlloyFurnaceRecipeCatalog.Entry entry = resolved
                .orElseThrow(() -> new IllegalArgumentException(
                        "The bound alloy-furnace recipe is missing or has changed: " + data.recipeId()));
        return new OmniversalPatternDetails(new Decoded(
                source, data, entry, level));
    }

    public OmniversalPatternData data() {
        return data;
    }

    public AdvancedAlloyFurnaceRecipe recipe() {
        return recipe;
    }

    @Override
    public String dynamicPatternIdentity() {
        return "useless_mod:omniversal|recipe=" + data.recipeId()
                + "|fingerprint=" + data.recipeFingerprint()
                + "|dynamic=" + super.dynamicPatternIdentity();
    }

    private record Decoded(AEProcessingPattern source, OmniversalPatternData data,
                           AlloyFurnaceRecipeCatalog.Entry entry, Level level) {
    }
}
