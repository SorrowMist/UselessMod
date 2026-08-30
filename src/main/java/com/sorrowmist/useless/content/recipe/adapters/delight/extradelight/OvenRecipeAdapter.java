package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.oven.recipes.OvenRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
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

public final class OvenRecipeAdapter implements IRecipeAdapter<OvenRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "oven");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<OvenRecipe> getRecipeClass() {
        return OvenRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<OvenRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        OvenRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(
                ExtraDelightRecipeAdapterUtils.withContainer(source.getIngredients(), source.getOutputContainer()));
        List<Ingredient> molds = new ArrayList<>();
        Ingredient oven = AdapterUtils.toMoldIngredient(getMoldItem());
        if (!oven.isEmpty()) {
            molds.add(oven);
        }
        molds.addAll(DelightRecipeAdapterUtils.bakingTrayMolds(source.getOutputContainer()));
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (inputs.isEmpty() || output == null || output.isEmpty()) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(source.getCookTime());
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(), List.of(output.copy()),
                List.of(), List.of(), ExtraDelightRecipeAdapterUtils.energy(time), time,
                Ingredient.EMPTY, 0, molds,
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<OvenRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedFluids != null && !mergedFluids.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<OvenRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<OvenRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), OvenRecipe.class)) {
            List<CountedIngredient> requirements = AdapterUtils.mergeIngredients(
                    ExtraDelightRecipeAdapterUtils.withContainer(holder.value().getIngredients(),
                            holder.value().getOutputContainer()));
            if (!requirements.isEmpty() && DelightRecipeAdapterUtils.matchesItems(
                    requirements, mergedInputs, List.of())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
