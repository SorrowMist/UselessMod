package com.sorrowmist.useless.content.recipe.adapters.delight.expandeddelight;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import ianm1647.expandeddelight.common.crafting.JuicerRecipe;
import ianm1647.expandeddelight.common.registry.EDItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Expanded Delight juicer recipes, including their output container input. */
public final class JuicerRecipeAdapter implements IRecipeAdapter<JuicerRecipe> {
    private static final int BASE_JUICE_TIME = 200;

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXPANDED_DELIGHT;
    }

    @Override
    public Class<JuicerRecipe> getRecipeClass() {
        return JuicerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return EDItems.JUICER.get().getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<JuicerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        JuicerRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(sourceIngredients(source));
        if (inputs.isEmpty()) {
            return List.of();
        }

        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return List.of();
        }

        int processTime = Math.max(1, source.getJuiceTime());
        long energy = Math.max(1L,
                (long) processTime * AdapterUtils.DEFAULT_ENERGY / BASE_JUICE_TIME);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                molds(source),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<JuicerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || (mergedFluids != null && !mergedFluids.isEmpty())
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<JuicerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<JuicerRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), JuicerRecipe.class)) {
            JuicerRecipe source = holder.value();
            List<CountedIngredient> requirements =
                    AdapterUtils.mergeIngredients(sourceIngredients(source));
            if (!requirements.isEmpty()
                    && DelightRecipeAdapterUtils.matchesItems(
                    requirements, mergedInputs, List.of())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static List<Ingredient> sourceIngredients(JuicerRecipe recipe) {
        List<Ingredient> ingredients = new ArrayList<>(recipe.getIngredients());
        ItemStack container = recipe.getOutputContainer();
        if (!DelightRecipeAdapterUtils.isBakingTray(container)
                && container != null && !container.isEmpty() && container.getCount() > 0) {
            for (int i = 0; i < container.getCount(); i++) {
                ingredients.add(Ingredient.of(container.copyWithCount(1)));
            }
        }
        return ingredients;
    }

    private static List<Ingredient> molds(JuicerRecipe recipe) {
        List<Ingredient> molds = new ArrayList<>();
        Ingredient juicer = AdapterUtils.toMoldIngredient(EDItems.JUICER.get().getDefaultInstance());
        if (!juicer.isEmpty()) {
            molds.add(juicer);
        }
        molds.addAll(DelightRecipeAdapterUtils.bakingTrayMolds(
                recipe == null ? ItemStack.EMPTY : recipe.getOutputContainer()));
        return List.copyOf(molds);
    }
}
