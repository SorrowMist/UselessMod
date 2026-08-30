package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.meltingpot.MeltingPotRecipe;
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

/** Converts Extra Delight melting-pot recipes, which produce fluids. */
public final class MeltingPotRecipeAdapter implements IRecipeAdapter<MeltingPotRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "melting_pot");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<MeltingPotRecipe> getRecipeClass() {
        return MeltingPotRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MeltingPotRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        MeltingPotRecipe source = holder.value();
        Ingredient input = source.input;
        FluidStack output = source.result;
        if (input == null || input.isEmpty() || output == null || output.isEmpty()) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(source.cooktime);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)), List.of(), List.of(), List.of(),
                List.of(output.copy()), List.of(), ExtraDelightRecipeAdapterUtils.energy(time), time,
                Ingredient.EMPTY, 0, List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<MeltingPotRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedFluids != null && !mergedFluids.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<MeltingPotRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<MeltingPotRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), MeltingPotRecipe.class)) {
            Ingredient input = holder.value().input;
            if (input != null && !input.isEmpty()
                    && DelightRecipeAdapterUtils.matchesItems(
                    List.of(new CountedIngredient(input, 1L)), mergedInputs, List.of())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
