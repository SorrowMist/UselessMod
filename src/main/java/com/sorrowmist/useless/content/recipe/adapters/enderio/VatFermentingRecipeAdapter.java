package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts Ender IO Vat recipes into static multiplier-specific alloy-furnace recipes. */
public final class VatFermentingRecipeAdapter implements IRecipeAdapter<FermentingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<FermentingRecipe> getRecipeClass() {
        return FermentingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EIOBlocks.VAT.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<FermentingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        FermentingRecipe source = holder.value();
        if (!valid(source)) {
            LOGGER.warn("Skipping invalid Ender IO Vat recipe: {}", holder.id());
            return List.of();
        }

        List<PairResult> pairs = candidatePairs(source);
        if (pairs.isEmpty()) {
            LOGGER.debug("Skipping Ender IO Vat recipe without a complete reagent candidate set: {}", holder.id());
            return List.of();
        }
        if (allPairsHaveSameResult(pairs)) {
            long outputAmount = pairs.getFirst().outputAmount();
            return List.of(createRecipe(holder.id(), source,
                    new CountedIngredient(Ingredient.of(source.firstReagent()), 1),
                    new CountedIngredient(Ingredient.of(source.secondReagent()), 1),
                    source.input(), source.output().copyWithAmount((int) outputAmount),
                    AdapterUtils.convertedId(holder.id())));
        }

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        int variantIndex = 0;
        for (PairResult pair : pairs) {
            result.add(createRecipe(holder.id(), source,
                    new CountedIngredient(exact(pair.first()), 1),
                    new CountedIngredient(exact(pair.second()), 1),
                    source.input(), source.output().copyWithAmount((int) pair.outputAmount()),
                    variantId(holder.id(), 0, variantIndex++)));
        }
        return result;
    }

    @Override
    public List<RecipeHolder<FermentingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedFluids == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<FermentingRecipe>> result = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<FermentingRecipe> holder : manager.getAllRecipesFor(
                EIORecipes.VAT_FERMENTING.type().get())) {
            FermentingRecipe source = holder.value();
            if (!valid(source) || !EnderIOAdapterUtils.matchesFluid(mergedFluids, source.input())) {
                continue;
            }
            Map<Ingredient, Long> requirements = new LinkedHashMap<>();
            AdapterUtils.mergeIngredient(requirements, Ingredient.of(source.firstReagent()), 1L);
            AdapterUtils.mergeIngredient(requirements, Ingredient.of(source.secondReagent()), 1L);
            if (AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                result.add(holder);
            }
        }
        return result;
    }

    private static long scaledAmount(int baseAmount, double multiplier) {
        if (baseAmount <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) {
            return 0;
        }
        double scaled = baseAmount * multiplier;
        if (!Double.isFinite(scaled) || scaled <= 0 || scaled > Integer.MAX_VALUE) {
            return 0;
        }
        return (long) Math.floor(scaled);
    }

    private static List<ItemStack> tagItems(@Nullable TagKey<Item> tag) {
        if (tag == null) {
            return List.of();
        }
        return EnderIOAdapterUtils.distinctStacks(
                Arrays.asList(Ingredient.of(tag).getItems()));
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            net.minecraft.resources.ResourceLocation id, FermentingRecipe source,
            CountedIngredient first, CountedIngredient second, FluidStack inputFluid,
            FluidStack output, net.minecraft.resources.ResourceLocation recipeId) {
        return createRecipe(id, source, first, second,
                SizedFluidIngredient.of(inputFluid.copy()), output, recipeId);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            net.minecraft.resources.ResourceLocation id, FermentingRecipe source,
            CountedIngredient first, CountedIngredient second, SizedFluidIngredient inputFluid,
            FluidStack output, net.minecraft.resources.ResourceLocation recipeId) {
        return new AdvancedAlloyFurnaceRecipe(
                recipeId, List.of(first, second), List.of(inputFluid), List.of(),
                List.of(), List.of(output.copy()), List.of(), 0L,
                Math.max(1, source.ticks()), Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(new ItemStack(EIOBlocks.VAT.get()))), AlloyFurnaceMode.NORMAL);
    }

    private static boolean valid(FermentingRecipe source) {
        return source != null && source.input() != null && source.input().amount() > 0
                && source.firstReagent() != null && source.secondReagent() != null
                && source.output() != null && !source.output().isEmpty()
                && source.output().getAmount() > 0 && source.ticks() > 0
                && source.input().ingredient() != null && !source.input().ingredient().isEmpty();
    }

    /**
     * Enumerates the complete reagent product. An empty result means that the tags are
     * unresolved or that at least one possible pair cannot be represented safely.
     */
    private static List<PairResult> candidatePairs(FermentingRecipe source) {
        List<ItemStack> firstItems = tagItems(source.firstReagent());
        List<ItemStack> secondItems = tagItems(source.secondReagent());
        if (firstItems.isEmpty() || secondItems.isEmpty()) return List.of();

        List<PairResult> pairs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ItemStack first : firstItems) {
            for (ItemStack second : secondItems) {
                if (!addPairResult(source, first, second, pairs, seen)) return List.of();
                if (first.is(source.secondReagent()) && second.is(source.firstReagent())) {
                    if (!addPairResult(source, second, first, pairs, seen)) return List.of();
                }
            }
        }
        return List.copyOf(pairs);
    }

    private static boolean addPairResult(FermentingRecipe source, ItemStack first, ItemStack second,
                                         List<PairResult> pairs, Set<String> seen) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()) return false;
        String key = stackFingerprint(first) + "|" + stackFingerprint(second);
        if (!seen.add(key)) return true;
        double multiplier = FermentingRecipe.getModifier(first, source.firstReagent())
                * FermentingRecipe.getModifier(second, source.secondReagent());
        long amount = scaledAmount(source.output().getAmount(), multiplier);
        if (!Double.isFinite(multiplier) || amount <= 0 || amount > Integer.MAX_VALUE) return false;
        pairs.add(new PairResult(first.copyWithCount(1), second.copyWithCount(1), multiplier, amount));
        return true;
    }

    private static boolean allPairsHaveSameResult(List<PairResult> pairs) {
        if (pairs.isEmpty()) return false;
        PairResult first = pairs.getFirst();
        return pairs.stream().allMatch(pair -> Double.compare(pair.multiplier(), first.multiplier()) == 0
                && pair.outputAmount() == first.outputAmount());
    }

    private static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
    }

    private static net.minecraft.resources.ResourceLocation variantId(
            net.minecraft.resources.ResourceLocation original, int fluidIndex, int variantIndex) {
        if (fluidIndex == 0 && variantIndex == 0) {
            return AdapterUtils.convertedId(original);
        }
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                original.getNamespace(), original.getPath() + "_fluid_" + fluidIndex
                        + "_multiplier_" + variantIndex + "_converted");
    }

    private static String stackFingerprint(ItemStack stack) {
        return stack.getItemHolder().unwrapKey().map(key -> key.location().toString()).orElse("unknown")
                + "|" + stack.getComponents();
    }

    private record PairResult(ItemStack first, ItemStack second, double multiplier, long outputAmount) {
    }
}
