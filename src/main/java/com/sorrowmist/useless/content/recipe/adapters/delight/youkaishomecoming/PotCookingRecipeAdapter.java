package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightSyntheticRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Lite Youkai's Homecoming unordered cooking recipes. */
public final class PotCookingRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final String SOURCE_CLASS =
            "dev.xkmc.youkaishomecoming.content.pot.cooking.core.UnorderedCookingRecipe";

    @Override
    public String sourceId() {
        return RecipeSourceIds.YOUKAI_HOMECOMING;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    /** The native cooking-pot recipe selects its station from the output remainder. */
    @Override
    public ItemStack getMoldItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DelightSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> generated = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() == null || !SOURCE_CLASS.equals(holder.value().getClass().getName())) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = convertSource(holder.id(), holder.value());
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(), new DelightSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(generated);
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DelightSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe == null || recipe.molds().isEmpty()
                    || !AdapterUtils.matchesMold(recipe.mold(), mold)) {
                continue;
            }
            if (DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSource(ResourceLocation sourceId,
                                                              Object source) {
        @SuppressWarnings("unchecked")
        List<Ingredient> ingredients = DelightRecipeAdapterUtils.fieldValue(
                source, "input", List.class);
        ItemStack result = DelightRecipeAdapterUtils.fieldValue(
                source, "result", ItemStack.class);
        if (result == null || result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        ItemStack mold = result.getCraftingRemainingItem();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(ingredients);
        if (inputs.isEmpty() || mold.isEmpty()) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                inputs,
                List.of(),
                List.of(),
                List.of(result.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                Math.max(1, DelightRecipeAdapterUtils.intField(
                        source, "time", AdapterUtils.DEFAULT_PROCESS_TIME)),
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(mold)),
                AlloyFurnaceMode.NORMAL
        );
    }
}
