package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import net.minecraft.world.item.ItemStack;
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
