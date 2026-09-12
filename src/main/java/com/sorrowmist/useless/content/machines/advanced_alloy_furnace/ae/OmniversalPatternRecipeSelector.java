package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.EncodedCraftingPattern;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Selects the recipe represented by a processing pattern and a mold hub inventory. */
public final class OmniversalPatternRecipeSelector {
    private OmniversalPatternRecipeSelector() {
    }

    /**
     * Selects the most specific available recipe first, then the recipe with the highest output
     * ratio. The recipe id is the final stable tie-breaker.
     */
    public static Optional<AlloyFurnaceRecipeCatalog.Entry> select(
            Level level, IPatternDetails pattern, List<ItemStack> availableMolds) {
        return select(level, pattern, MoldMatcher.prepare(MoldMatcher.sparseSlots(availableMolds)));
    }

    public static Optional<AlloyFurnaceRecipeCatalog.Entry> select(
            Level level, IPatternDetails pattern, Map<Integer, ItemStack> availableMolds) {
        return select(level, pattern, MoldMatcher.prepare(availableMolds));
    }

    public static Optional<AlloyFurnaceRecipeCatalog.Entry> select(
            Level level, IPatternDetails pattern, MoldMatcher.PreparedMolds availableMolds) {
        if (level == null || pattern == null) return Optional.empty();

        List<AlloyFurnaceRecipeCatalog.Entry> candidates =
                AlloyFurnaceRecipeCatalog.findPatternCandidates(level, pattern);
        // Prefer a complete processing pattern. Only when no complete recipe matches do we accept
        // a pattern whose secondary outputs were removed by the player.
        if (candidates.isEmpty()) {
            candidates = AlloyFurnaceRecipeCatalog.findPatternCandidates(level, pattern, true);
        }
        if (candidates.isEmpty()) return Optional.empty();

        return selectCandidate(candidates, availableMolds);
    }

    /**
     * Selects the existing workbench conversion represented by an AE crafting pattern.
     *
     * <p>Crafting patterns retain the source workbench recipe id, while the alloy-furnace
     * catalogue stores the corresponding converted recipe with the standard {@code _converted}
     * suffix. Restricting this lookup to that exact id and the Minecraft source prevents a crafting
     * pattern from being converted merely because another adapter happens to have the same input
     * and output shape.</p>
     */
    public static Optional<AlloyFurnaceRecipeCatalog.Entry> selectCrafting(
            Level level, AECraftingPattern pattern, MoldMatcher.PreparedMolds availableMolds) {
        if (level == null || pattern == null) return Optional.empty();

        var encoded = pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null) return Optional.empty();

        RecipeHolder<?> sourceHolder = level.getRecipeManager().byKey(encoded.recipeId()).orElse(null);
        if (!(sourceHolder != null && sourceHolder.value() instanceof CraftingRecipe sourceRecipe)
                || !matchesSourcePattern(sourceRecipe, encoded, level)) {
            return Optional.empty();
        }

        ResourceLocation convertedId;
        try {
            convertedId = AdapterUtils.convertedId(encoded.recipeId());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        List<AlloyFurnaceRecipeCatalog.Entry> candidates = AlloyFurnaceRecipeCatalog
                .entries(level, RecipeSourceIds.MINECRAFT).stream()
                .filter(entry -> entry != null
                        && convertedId.equals(entry.identity().recipeId()))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();

        return selectCandidate(candidates, availableMolds);
    }

    /** Validates the actual encoded 3x3 grid before trusting its recipe id. */
    private static boolean matchesSourcePattern(
            CraftingRecipe recipe, EncodedCraftingPattern encoded, Level level) {
        List<ItemStack> encodedInputs = encoded.inputs();
        if (encodedInputs == null || encodedInputs.size() > 9) return false;

        List<ItemStack> grid = new java.util.ArrayList<>(9);
        for (ItemStack input : encodedInputs) {
            if (input == null) return false;
            grid.add(input.copy());
        }
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);

        try {
            CraftingInput input = CraftingInput.of(3, 3, grid);
            if (!recipe.matches(input, level)) return false;
            ItemStack assembled = recipe.assemble(input, level.registryAccess());
            return assembled != null && !assembled.isEmpty()
                    && ItemStack.isSameItemSameComponents(assembled, encoded.result());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Optional<AlloyFurnaceRecipeCatalog.Entry> selectCandidate(
            List<AlloyFurnaceRecipeCatalog.Entry> candidates,
            MoldMatcher.PreparedMolds availableMolds) {
        if (candidates == null || candidates.isEmpty()) return Optional.empty();

        // Prefer the most specific and most productive recipe. For example, a SAG Mill + grinding
        // ball recipe must be checked before the plain SAG Mill recipe, otherwise the latter would
        // consume the same input and output shape and silently lose the grinding-ball bonus.
        MoldMatcher.PreparedMolds molds = availableMolds == null
                ? MoldMatcher.prepare(Map.of()) : availableMolds;
        // The mold map is already sparse, so each candidate is tested once instead of testing the
        // same candidate once for every occupied hub slot. Only candidates that can actually use
        // the current hub are scored, avoiding a full sort of unusable recipes.
        CandidateScore best = null;
        for (AlloyFurnaceRecipeCatalog.Entry candidate : candidates) {
            AdvancedAlloyFurnaceRecipe recipe = candidate.recipe();
            if (recipe == null || !molds.matches(recipe.molds())) {
                continue;
            }
            CandidateScore score = new CandidateScore(candidate);
            if (best == null || compareCandidates(score, best) < 0) best = score;
        }

        return best == null ? Optional.empty() : Optional.of(best.entry());
    }

    private static int compareCandidates(CandidateScore left, CandidateScore right) {
        int moldComparison = Integer.compare(right.moldCount(), left.moldCount());
        if (moldComparison != 0) return moldComparison;

        int outputRatioComparison = compareOutputRatio(
                right.inputAmount(), right.outputAmount(),
                left.inputAmount(), left.outputAmount());
        if (outputRatioComparison != 0) return outputRatioComparison;

        return left.entry().identity().recipeId().toString()
                .compareTo(right.entry().identity().recipeId().toString());
    }

    /** Compares total declared output amount per total declared input amount without overflow. */
    private static int compareOutputRatio(
            BigInteger leftInputs, BigInteger leftOutputs,
            BigInteger rightInputs, BigInteger rightOutputs) {
        if (leftInputs.signum() <= 0 || rightInputs.signum() <= 0) {
            return Integer.compare(leftInputs.signum(), rightInputs.signum());
        }
        return leftOutputs.multiply(rightInputs).compareTo(rightOutputs.multiply(leftInputs));
    }

    private static BigInteger totalInputAmount(AdvancedAlloyFurnaceRecipe recipe) {
        BigInteger result = BigInteger.ZERO;
        for (var input : recipe.inputs()) {
            if (input != null && input.count() > 0L) {
                result = result.add(BigInteger.valueOf(input.count()));
            }
        }
        for (var input : recipe.inputFluids()) {
            if (input != null && input.amount() > 0L) {
                result = result.add(BigInteger.valueOf(input.amount()));
            }
        }
        for (var input : recipe.keyInputs()) {
            if (input != null && input.amount() > 0L) {
                result = result.add(BigInteger.valueOf(input.amount()));
            }
        }
        return result;
    }

    private static BigInteger totalOutputAmount(AdvancedAlloyFurnaceRecipe recipe) {
        BigInteger result = BigInteger.ZERO;
        for (ItemStack output : recipe.outputs()) {
            if (output != null && !output.isEmpty() && output.getCount() > 0) {
                result = result.add(BigInteger.valueOf(output.getCount()));
            }
        }
        for (var output : recipe.outputFluids()) {
            if (output != null && !output.isEmpty() && output.getAmount() > 0L) {
                result = result.add(BigInteger.valueOf(output.getAmount()));
            }
        }
        for (var output : recipe.keyOutputs()) {
            if (output != null && output.what() != null && output.amount() > 0L) {
                result = result.add(BigInteger.valueOf(output.amount()));
            }
        }
        return result;
    }

    private record CandidateScore(
            AlloyFurnaceRecipeCatalog.Entry entry,
            int moldCount,
            BigInteger inputAmount,
            BigInteger outputAmount) {
        private CandidateScore(AlloyFurnaceRecipeCatalog.Entry entry) {
            this(entry,
                    moldCount(entry.recipe()),
                    totalInputAmount(entry.recipe()),
                    totalOutputAmount(entry.recipe()));
        }

        private static int moldCount(AdvancedAlloyFurnaceRecipe recipe) {
            return recipe == null ? 0 : recipe.molds().size();
        }

        private static BigInteger totalInputAmount(AdvancedAlloyFurnaceRecipe recipe) {
            if (recipe == null) return BigInteger.ZERO;
            return OmniversalPatternRecipeSelector.totalInputAmount(recipe);
        }

        private static BigInteger totalOutputAmount(AdvancedAlloyFurnaceRecipe recipe) {
            if (recipe == null) return BigInteger.ZERO;
            return OmniversalPatternRecipeSelector.totalOutputAmount(recipe);
        }
    }
}
