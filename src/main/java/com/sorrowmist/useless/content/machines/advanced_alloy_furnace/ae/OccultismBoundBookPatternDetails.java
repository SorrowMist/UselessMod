package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AECraftingPattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;

/** AE crafting pattern whose Occultism spirit name is generated when it is assembled. */
public final class OccultismBoundBookPatternDetails extends AECraftingPattern
        implements DynamicComponentPattern {
    static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            "occultism", "crafting/bound_book_of_binding");
    private static final Set<ResourceLocation> BOUND_BOOK_IDS = Set.of(
            occultismItem("book_of_binding_bound_foliot"),
            occultismItem("book_of_binding_bound_djinni"),
            occultismItem("book_of_binding_bound_afrit"),
            occultismItem("book_of_binding_bound_marid"));

    private final String identity;

    private OccultismBoundBookPatternDetails(AEItemKey definition, Level level) {
        super(definition, level);
        this.identity = "useless_mod:occultism_bound_book|definition_sha256="
                + DynamicComponentPatternDetails.definitionFingerprint(
                        definition, level.registryAccess());
    }

    public static IPatternDetails wrap(IPatternDetails details, Level level) {
        if (!(details instanceof AECraftingPattern crafting)
                || details instanceof OccultismBoundBookPatternDetails
                || level == null
                || !isSupported(crafting)) {
            return details;
        }
        return new OccultismBoundBookPatternDetails(crafting.getDefinition(), level);
    }

    static boolean isSupported(AECraftingPattern pattern) {
        var encoded = pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null || !RECIPE_ID.equals(encoded.recipeId())
                || pattern.getOutputs().size() != 1
                || !(pattern.getOutputs().getFirst().what() instanceof AEItemKey output)) {
            return false;
        }
        return isSupportedRecipeAndOutput(encoded.recipeId(), output.getId());
    }

    static boolean isSupportedRecipeAndOutput(ResourceLocation recipeId, ResourceLocation outputId) {
        return RECIPE_ID.equals(recipeId) && BOUND_BOOK_IDS.contains(outputId);
    }

    private static ResourceLocation occultismItem(String path) {
        return ResourceLocation.fromNamespaceAndPath("occultism", path);
    }

    @Override
    public String dynamicPatternIdentity() {
        return identity;
    }

    @Override
    public boolean isItemIdInput(int slot) {
        return false;
    }

    @Override
    public boolean isItemIdOutput(int slot) {
        return slot == 0;
    }

    @Override
    public boolean usesDynamicOutputs() {
        return true;
    }
}
