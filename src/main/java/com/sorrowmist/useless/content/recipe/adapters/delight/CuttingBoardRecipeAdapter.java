package com.sorrowmist.useless.content.recipe.adapters.delight;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.common.tag.CommonTags;
import vectorwing.farmersdelight.common.tag.ModTags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts knife-based Cutting Board recipes into alloy-furnace recipes. */
public final class CuttingBoardRecipeAdapter implements IRecipeAdapter<CuttingBoardRecipe> {
    private static final Ingredient KNIFE_TOOL = Ingredient.of(ModTags.Items.KNIVES);
    private static final ResourceLocation FORGE_KNIFE_TAG =
            ResourceLocation.fromNamespaceAndPath("forge", "tools/knives");

    @Override
    public Class<CuttingBoardRecipe> getRecipeClass() {
        return CuttingBoardRecipe.class;
    }

    /** The board is the reusable workstation mold for converted cutting recipes. */
    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModItems.CUTTING_BOARD.get());
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(ModItems.CUTTING_BOARD.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CuttingBoardRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        CuttingBoardRecipe source = holder.value();
        if (!isKnifeRecipe(source)) {
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
            ItemStack stack = result.stack();
            weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    stack.copy(), stack.getCount(), stack.getCount(), result.chance()));
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
    public List<RecipeHolder<CuttingBoardRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CuttingBoardRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CuttingBoardRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.CUTTING.get())) {
            CuttingBoardRecipe source = holder.value();
            if (source == null || !isKnifeRecipe(source)) {
                continue;
            }

            Map<Ingredient, Long> requirements = ingredientRequirements(source);
            if (!requirements.isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static List<CountedIngredient> scaleInputs(
            List<CountedIngredient> inputs, int operations) {
        List<CountedIngredient> scaled = new ArrayList<>(inputs.size());
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
            scaled.add(new CountedIngredient(input.ingredient(), count));
        }
        return List.copyOf(scaled);
    }

    private static Map<Ingredient, Long> ingredientRequirements(CuttingBoardRecipe recipe) {
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (recipe == null) {
            return requirements;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!AdapterUtils.isIngredientEmpty(ingredient)) {
                AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
            }
        }
        return requirements;
    }

    /**
     * Cutting Board also supports tool-action ingredients. Only recipes whose tool accepts at
     * least one knife are safe to represent with the alloy furnace's cutting-board mold.
     */
    private static boolean isKnifeRecipe(CuttingBoardRecipe recipe) {
        if (recipe == null || recipe.getTool() == null || recipe.getTool().isEmpty()) {
            return false;
        }

        Ingredient tool = recipe.getTool();
        try {
            for (Ingredient.Value value : tool.getValues()) {
                if (value instanceof Ingredient.TagValue tagValue
                        && isKnifeTag(tagValue.tag().location())) {
                    return true;
                }
            }

            for (ItemStack knife : KNIFE_TOOL.getItems()) {
                if (!knife.isEmpty() && tool.test(knife)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static boolean isKnifeTag(ResourceLocation tag) {
        return tag.equals(ModTags.Items.KNIVES.location())
                || tag.equals(CommonTags.Items.TOOLS_KNIFE.location())
                || tag.equals(FORGE_KNIFE_TAG);
    }
}
