package com.sorrowmist.useless.content.recipe.adapters.ironsspellbooks;

import io.redspace.ironsspellbooks.fluids.PotionFluid;
import io.redspace.ironsspellbooks.recipe_types.alchemist_cauldron.BrewAlchemistCauldronRecipe;
import io.redspace.ironsspellbooks.recipe_types.alchemist_cauldron.EmptyAlchemistCauldronRecipe;
import io.redspace.ironsspellbooks.recipe_types.alchemist_cauldron.FillAlchemistCauldronRecipe;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.RecipeRegistry;
import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Iron's Spells 'n Spellbooks alchemist-cauldron brews into item-only recipes. */
public final class AlchemistCauldronRecipeAdapter implements IRecipeAdapter<BrewAlchemistCauldronRecipe> {
    private static final int MILLIBUCKETS_PER_POTION = 250;
    private static final int PROCESS_TIME = 100;

    @Override
    public String sourceId() {
        return RecipeSourceIds.IRONS_SPELLBOOKS;
    }

    @Override
    public Class<BrewAlchemistCauldronRecipe> getRecipeClass() {
        return BrewAlchemistCauldronRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BrewAlchemistCauldronRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || level == null) {
            return List.of();
        }

        BrewAlchemistCauldronRecipe source = holder.value();
        if (source.fluidIn() == null || source.fluidIn().isEmpty()
                || source.fluidIn().getAmount() <= 0
                || source.reagent() == null || source.reagent().isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<CountedIngredient> fluidInputs = itemInputsForFluid(source.fluidIn(), recipeManager);
        if (fluidInputs.isEmpty()) {
            return List.of();
        }

        List<List<ItemStack>> outputVariants = outputVariants(source.results(), source.byproduct(), recipeManager);
        if (outputVariants.isEmpty() || outputVariants.getFirst().isEmpty()) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>();
        int variant = 0;
        for (CountedIngredient fluidInput : fluidInputs) {
            for (List<ItemStack> output : outputVariants) {
                List<CountedIngredient> inputs = List.of(
                        fluidInput,
                        new CountedIngredient(source.reagent(), 1L));
                converted.add(new AdvancedAlloyFurnaceRecipe(
                        variantId(holder.id(), variant++),
                        inputs,
                        List.of(),
                        List.of(),
                        copyStacks(output),
                        List.of(),
                        List.of(),
                        AdapterUtils.DEFAULT_ENERGY,
                        PROCESS_TIME,
                        Ingredient.EMPTY,
                        0,
                        List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                        AlloyFurnaceMode.NORMAL));
            }
        }
        return List.copyOf(converted);
    }

    @Override
    public List<RecipeHolder<BrewAlchemistCauldronRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<BrewAlchemistCauldronRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<BrewAlchemistCauldronRecipe> holder : recipeManager.getAllRecipesFor(
                RecipeRegistry.ALCHEMIST_CAULDRON_BREW_TYPE.get())) {
            for (AdvancedAlloyFurnaceRecipe converted : convertAll(holder, level)) {
                if (AdapterUtils.matchesRequired(mergedInputs, requirements(converted.inputs()))) {
                    matches.add(holder);
                    break;
                }
            }
        }
        return matches;
    }

    private static Map<Ingredient, Long> requirements(List<CountedIngredient> inputs) {
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            if (input != null && input.ingredient() != null && input.count() > 0) {
                AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count());
            }
        }
        return requirements;
    }

    private static List<CountedIngredient> itemInputsForFluid(
            FluidStack fluid, RecipeManager recipeManager) {
        long units;
        List<CountedIngredient> result = new ArrayList<>();

        for (RecipeHolder<FillAlchemistCauldronRecipe> holder : recipeManager.getAllRecipesFor(
                RecipeRegistry.ALCHEMIST_CAULDRON_FILL_TYPE.get())) {
            FillAlchemistCauldronRecipe recipe = holder.value();
            FluidStack filled = recipe.result();
            units = matchingUnits(fluid, filled);
            if (units > 0 && recipe.input() != null && !recipe.input().isEmpty()) {
                addDistinctIngredient(result, recipe.input(), units);
            }
        }

        if (!result.isEmpty()) {
            return List.copyOf(result);
        }

        // A custom datapack may define only the emptying direction. Use it as a fallback,
        // without mixing it with the normal filling-direction choices above.
        for (RecipeHolder<EmptyAlchemistCauldronRecipe> holder : recipeManager.getAllRecipesFor(
                RecipeRegistry.ALCHEMIST_CAULDRON_EMPTY_TYPE.get())) {
            EmptyAlchemistCauldronRecipe recipe = holder.value();
            units = matchingUnits(fluid, recipe.fluid());
            ItemStack resultStack = recipe.result();
            if (units > 0 && resultStack != null && !resultStack.isEmpty()) {
                long count = safeMultiply(units, resultStack.getCount());
                if (count > 0) {
                    addDistinctIngredient(result, exact(resultStack), count);
                }
            }
        }

        if (!result.isEmpty()) {
            return List.copyOf(result);
        }

        ItemStack potion = potionItem(fluid);
        if (!potion.isEmpty()) {
            units = fluid.getAmount() / MILLIBUCKETS_PER_POTION;
            return units > 0 && fluid.getAmount() % MILLIBUCKETS_PER_POTION == 0
                    ? List.of(new CountedIngredient(exact(potion), units))
                    : List.of();
        }
        return List.of();
    }

    private static List<List<ItemStack>> outputVariants(
            List<FluidStack> fluids, java.util.Optional<ItemStack> byproduct,
            RecipeManager recipeManager) {
        List<List<ItemStack>> variants = new ArrayList<>();
        variants.add(new ArrayList<>());

        if (fluids != null) {
            for (FluidStack fluid : fluids) {
                List<ItemStack> choices = itemOutputsForFluid(fluid, recipeManager);
                if (choices.isEmpty()) {
                    return List.of();
                }
                List<List<ItemStack>> next = new ArrayList<>();
                for (List<ItemStack> prefix : variants) {
                    for (ItemStack choice : choices) {
                        List<ItemStack> combined = new ArrayList<>(prefix.size() + 1);
                        combined.addAll(copyStacks(prefix));
                        combined.add(choice.copy());
                        next.add(combined);
                    }
                }
                variants = next;
            }
        }

        if (byproduct != null && byproduct.isPresent()
                && byproduct.get() != null && !byproduct.get().isEmpty()) {
            for (List<ItemStack> variant : variants) {
                variant.add(byproduct.get().copy());
            }
        }
        return variants.stream().map(List::copyOf).toList();
    }

    private static List<ItemStack> itemOutputsForFluid(FluidStack fluid, RecipeManager recipeManager) {
        if (fluid == null || fluid.isEmpty() || fluid.getAmount() <= 0) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();
        for (RecipeHolder<EmptyAlchemistCauldronRecipe> holder : recipeManager.getAllRecipesFor(
                RecipeRegistry.ALCHEMIST_CAULDRON_EMPTY_TYPE.get())) {
            EmptyAlchemistCauldronRecipe recipe = holder.value();
            long units = matchingUnits(fluid, recipe.fluid());
            ItemStack stack = recipe.result();
            long count = stack.isEmpty() ? 0 : safeMultiply(units, stack.getCount());
            if (count > 0 && count <= Integer.MAX_VALUE) {
                addDistinctStack(result, stack.copyWithCount((int) count));
            }
        }

        if (!result.isEmpty()) {
            return List.copyOf(result);
        }

        // Fall back to filling recipes for custom fluids that have no item-emptying recipe.
        for (RecipeHolder<FillAlchemistCauldronRecipe> holder : recipeManager.getAllRecipesFor(
                RecipeRegistry.ALCHEMIST_CAULDRON_FILL_TYPE.get())) {
            FillAlchemistCauldronRecipe recipe = holder.value();
            long units = matchingUnits(fluid, recipe.result());
            if (units <= 0 || recipe.input() == null || recipe.input().isEmpty()) {
                continue;
            }
            int count = toIntCount(units);
            if (count <= 0) {
                continue;
            }
            for (ItemStack stack : recipe.input().getItems()) {
                if (!stack.isEmpty()) {
                    addDistinctStack(result, stack.copyWithCount(count));
                }
            }
        }

        if (!result.isEmpty()) {
            return List.copyOf(result);
        }

        ItemStack potion = potionItem(fluid);
        if (potion.isEmpty() || fluid.getAmount() % MILLIBUCKETS_PER_POTION != 0) {
            return List.of();
        }
        long units = fluid.getAmount() / MILLIBUCKETS_PER_POTION;
        return units <= Integer.MAX_VALUE
                ? List.of(potion.copyWithCount((int) units))
                : List.of();
    }

    private static long matchingUnits(FluidStack requested, FluidStack conversion) {
        if (requested == null || requested.isEmpty() || conversion == null || conversion.isEmpty()
                || conversion.getAmount() <= 0
                || !FluidStack.isSameFluidSameComponents(requested, conversion)
                || requested.getAmount() % conversion.getAmount() != 0) {
            return 0;
        }
        return requested.getAmount() / conversion.getAmount();
    }

    @Nullable
    private static ItemStack potionItem(FluidStack fluid) {
        try {
            return PotionFluid.from(fluid);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
    }

    private static void addDistinctIngredient(
            List<CountedIngredient> target, Ingredient ingredient, long count) {
        for (CountedIngredient existing : target) {
            if (existing.count() == count
                    && AdapterUtils.areIngredientsEqual(existing.ingredient(), ingredient)) {
                return;
            }
        }
        target.add(new CountedIngredient(ingredient, count));
    }

    private static void addDistinctStack(List<ItemStack> target, ItemStack candidate) {
        for (ItemStack existing : target) {
            if (existing.getCount() == candidate.getCount()
                    && ItemStack.isSameItemSameComponents(existing, candidate)) {
                return;
            }
        }
        target.add(candidate);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static long safeMultiply(long left, long right) {
        if (left <= 0 || right <= 0 || left > Long.MAX_VALUE / right) {
            return 0;
        }
        return left * right;
    }

    private static int toIntCount(long count) {
        return count > 0 && count <= Integer.MAX_VALUE ? (int) count : 0;
    }

    private static ResourceLocation variantId(ResourceLocation original, int variant) {
        if (variant == 0) {
            return AdapterUtils.convertedId(original);
        }
        return ResourceLocation.fromNamespaceAndPath(
                original.getNamespace(), original.getPath() + "_items_" + variant + "_converted");
    }
}
