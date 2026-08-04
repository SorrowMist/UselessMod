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
        List<FluidStack> fluids = EnderIOAdapterUtils.fluidChoices(source.input());
        if (fluids == null || fluids.isEmpty() || !valid(source)) {
            LOGGER.warn("Skipping invalid Ender IO Vat recipe: {}", holder.id());
            return List.of();
        }

        List<MultiplierGroup> groups = multiplierGroups(source);
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        int variantIndex = 0;
        for (int fluidIndex = 0; fluidIndex < fluids.size(); fluidIndex++) {
            if (groups.isEmpty()) {
                result.add(createRecipe(holder.id(), source,
                        new CountedIngredient(Ingredient.of(source.firstReagent()), 1),
                        new CountedIngredient(Ingredient.of(source.secondReagent()), 1),
                        fluids.get(fluidIndex), source.output().copy(),
                        variantId(holder.id(), fluidIndex, variantIndex++)));
                continue;
            }

            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                MultiplierGroup group = groups.get(groupIndex);
                result.add(createRecipe(holder.id(), source,
                        new CountedIngredient(group.firstIngredient(source.firstReagent()), 1),
                        new CountedIngredient(group.secondIngredient(source.secondReagent()), 1),
                        fluids.get(fluidIndex), source.output().copyWithAmount((int) group.outputAmount()),
                        variantId(holder.id(), fluidIndex, variantIndex++)));
            }
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

    /**
     * Expands the two reagent tags into static recipes. Items with the same product of data-map
     * modifiers share one recipe, while different products get different outputs/pages. The
     * alloy furnace then selects the matching static recipe from the concrete reagent stacks.
     */
    private static List<MultiplierGroup> multiplierGroups(FermentingRecipe source) {
        List<ItemStack> firstItems = tagItems(source.firstReagent());
        List<ItemStack> secondItems = tagItems(source.secondReagent());
        if (firstItems.isEmpty() || secondItems.isEmpty()) {
            return List.of();
        }

        Map<Double, MultiplierGroup> groups = new LinkedHashMap<>();
        Set<String> seenPairs = new LinkedHashSet<>();
        for (ItemStack first : firstItems) {
            for (ItemStack second : secondItems) {
                addPair(source, first, second, groups, seenPairs);

                // FermentingRecipe accepts either reagent order. Include the reverse assignment
                // when the two tags overlap, because the two data-map lookups may differ.
                if (first.is(source.secondReagent()) && second.is(source.firstReagent())) {
                    addPair(source, second, first, groups, seenPairs);
                }
            }
        }
        return List.copyOf(groups.values());
    }

    private static void addPair(
            FermentingRecipe source, ItemStack first, ItemStack second,
            Map<Double, MultiplierGroup> groups, Set<String> seenPairs) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()) {
            return;
        }
        String pair = stackFingerprint(first) + "|" + stackFingerprint(second);
        if (!seenPairs.add(pair)) {
            return;
        }

        double multiplier = FermentingRecipe.getModifier(first, source.firstReagent())
                * FermentingRecipe.getModifier(second, source.secondReagent());
        long outputAmount = scaledAmount(source.output().getAmount(), multiplier);
        if (!Double.isFinite(multiplier) || outputAmount <= 0 || outputAmount > Integer.MAX_VALUE) {
            return;
        }

        MultiplierGroup group = groups.computeIfAbsent(multiplier,
                ignored -> new MultiplierGroup(outputAmount));
        group.add(first, second);
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
        return new AdvancedAlloyFurnaceRecipe(
                recipeId, List.of(first, second), List.of(inputFluid.copy()), List.of(),
                List.of(), List.of(output.copy()), List.of(), 0L,
                Math.max(1, source.ticks()), Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(new ItemStack(EIOBlocks.VAT.get())), AlloyFurnaceMode.NORMAL);
    }

    private static boolean valid(FermentingRecipe source) {
        return source != null && source.input() != null && source.input().amount() > 0
                && source.firstReagent() != null && source.secondReagent() != null
                && source.output() != null && !source.output().isEmpty()
                && source.output().getAmount() > 0 && source.ticks() > 0;
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

    private static final class MultiplierGroup {
        private final long outputAmount;
        private final List<ItemStack> firstItems = new ArrayList<>();
        private final List<ItemStack> secondItems = new ArrayList<>();

        private MultiplierGroup(long outputAmount) {
            this.outputAmount = outputAmount;
        }

        private void add(ItemStack first, ItemStack second) {
            addDistinct(firstItems, first);
            addDistinct(secondItems, second);
        }

        private long outputAmount() {
            return outputAmount;
        }

        private Ingredient firstIngredient(TagKey<Item> fallback) {
            return ingredient(firstItems, fallback);
        }

        private Ingredient secondIngredient(TagKey<Item> fallback) {
            return ingredient(secondItems, fallback);
        }

        private static Ingredient ingredient(List<ItemStack> stacks, TagKey<Item> fallback) {
            if (stacks.isEmpty()) {
                return Ingredient.of(fallback);
            }
            return Ingredient.of(stacks.stream().map(stack -> stack.copyWithCount(1)));
        }

        private static void addDistinct(List<ItemStack> target, ItemStack candidate) {
            if (target.stream().noneMatch(existing -> EnderIOAdapterUtils.sameItemStack(existing, candidate))) {
                target.add(candidate.copyWithCount(1));
            }
        }
    }
}
