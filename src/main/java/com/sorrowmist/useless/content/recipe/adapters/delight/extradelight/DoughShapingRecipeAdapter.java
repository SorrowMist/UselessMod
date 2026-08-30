package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.doughshaping.recipes.DoughShapingRecipe;
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

public final class DoughShapingRecipeAdapter implements IRecipeAdapter<DoughShapingRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "dough_shaping");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<DoughShapingRecipe> getRecipeClass() {
        return DoughShapingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<DoughShapingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        DoughShapingRecipe source = holder.value();
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (source.getIngredients().isEmpty() || output == null || output.isEmpty()) {
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.getIngredients().getFirst(), 1L)),
                List.of(), List.of(), List.of(output.copy()), List.of(), List.of(),
                AdapterUtils.DEFAULT_ENERGY, AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<DoughShapingRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedFluids != null && !mergedFluids.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<DoughShapingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DoughShapingRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), DoughShapingRecipe.class)) {
            if (!holder.value().getIngredients().isEmpty()
                    && DelightRecipeAdapterUtils.matchesItems(
                    List.of(new CountedIngredient(holder.value().getIngredients().getFirst(), 1L)),
                    mergedInputs, List.of())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
