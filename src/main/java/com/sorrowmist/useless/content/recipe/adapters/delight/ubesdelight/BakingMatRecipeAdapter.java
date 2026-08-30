package com.sorrowmist.useless.content.recipe.adapters.delight.ubesdelight;

import com.chefmooon.ubesdelight.common.crafting.neoforge.BakingMatRecipeImpl;
import com.chefmooon.ubesdelight.common.crafting.ingredient.ChanceResult;
import com.chefmooon.ubesdelight.common.tag.CommonTags;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Ube's Delight baking-mat recipes into deterministic alloy-furnace batches. */
public final class BakingMatRecipeAdapter implements IRecipeAdapter<BakingMatRecipeImpl> {
    private static final ResourceLocation BAKING_MAT_ID =
            ResourceLocation.fromNamespaceAndPath("ubesdelight", "baking_mat_bamboo");

    @Override
    public String sourceId() {
        return RecipeSourceIds.UBES_DELIGHT;
    }

    @Override
    public Class<BakingMatRecipeImpl> getRecipeClass() {
        return BakingMatRecipeImpl.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = DelightRecipeAdapterUtils.registeredItem(BAKING_MAT_ID);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BakingMatRecipeImpl> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        BakingMatRecipeImpl source = holder.value();
        if (!isSupported(source)) {
            return List.of();
        }

        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.getIngredients());
        if (inputs.isEmpty()) {
            return List.of();
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        for (ChanceResult result : source.getRollableResults()) {
            if (result == null || result.stack() == null || result.stack().isEmpty()
                    || result.stack().getCount() <= 0 || !Float.isFinite(result.chance())) {
                continue;
            }
            int count = result.stack().getCount();
            weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    result.stack().copy(), count, count, result.chance()));
        }

        var scaled = ExpectedOutputScaler.scale(weightedOutputs);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
            return List.of();
        }

        int operations = scaled.get().operations();
        List<CountedIngredient> scaledInputs = scaleInputs(inputs, operations);
        var energy = ExpectedOutputScaler.multiplyToInt(AdapterUtils.DEFAULT_ENERGY, operations);
        var processTime = ExpectedOutputScaler.multiplyToInt(
                AdapterUtils.DEFAULT_PROCESS_TIME, operations);
        if (scaledInputs.isEmpty() || energy.isEmpty() || processTime.isEmpty()) {
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                scaledInputs,
                List.of(),
                List.of(),
                scaled.get().outputs(),
                List.of(),
                List.of(),
                energy.getAsInt(),
                processTime.getAsInt(),
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<BakingMatRecipeImpl>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<BakingMatRecipeImpl>> matches = new ArrayList<>();
        for (RecipeHolder<BakingMatRecipeImpl> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), BakingMatRecipeImpl.class)) {
            BakingMatRecipeImpl source = holder.value();
            if (!isSupported(source)) {
                continue;
            }
            Map<Ingredient, Long> requirements = requirements(source.getIngredients());
            if (!requirements.isEmpty() && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static List<CountedIngredient> scaleInputs(
            List<CountedIngredient> inputs, int operations) {
        List<CountedIngredient> result = new ArrayList<>(inputs.size());
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty()
                    || input.count() <= 0) {
                continue;
            }
            final long count;
            try {
                count = Math.multiplyExact(input.count(), (long) operations);
            } catch (ArithmeticException exception) {
                return List.of();
            }
            if (count <= 0) {
                return List.of();
            }
            result.add(new CountedIngredient(input.ingredient(), count));
        }
        return List.copyOf(result);
    }

    private static Map<Ingredient, Long> requirements(List<Ingredient> ingredients) {
        Map<Ingredient, Long> result = new LinkedHashMap<>();
        if (ingredients == null) {
            return result;
        }
        for (Ingredient ingredient : ingredients) {
            if (!AdapterUtils.isIngredientEmpty(ingredient)) {
                AdapterUtils.mergeIngredient(result, ingredient, 1L);
            }
        }
        return result;
    }

    private static boolean isSupported(BakingMatRecipeImpl recipe) {
        return recipe != null
                && recipe.getProcessStages() != null
                && recipe.getProcessStages().isEmpty()
                && isRollingPinTool(recipe.getTool());
    }

    private static boolean isRollingPinTool(@Nullable Ingredient tool) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }

        try {
            for (Ingredient.Value value : tool.getValues()) {
                if (value instanceof Ingredient.TagValue tagValue
                        && tagValue.tag().equals(CommonTags.C_TOOLS_ROLLING_PIN)) {
                    return true;
                }
            }
            for (ItemStack rollingPin : Ingredient.of(CommonTags.C_TOOLS_ROLLING_PIN).getItems()) {
                if (!rollingPin.isEmpty() && tool.test(rollingPin)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }
}
