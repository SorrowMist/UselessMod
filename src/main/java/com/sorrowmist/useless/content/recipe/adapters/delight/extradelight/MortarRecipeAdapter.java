package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.mortar.recipes.MortarRecipe;
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

public final class MortarRecipeAdapter implements IRecipeAdapter<MortarRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "mortar_stone");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<MortarRecipe> getRecipeClass() {
        return MortarRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MortarRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        MortarRecipe source = holder.value();
        Ingredient input = source.getIngredients().isEmpty() ? Ingredient.EMPTY
                : source.getIngredients().getFirst();
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        FluidStack outputFluid = source.getFluid();
        if (input.isEmpty()
                || (output == null || output.isEmpty()) && (outputFluid == null || outputFluid.isEmpty())) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(source.getGrinds());
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)), List.of(), List.of(),
                output == null || output.isEmpty() ? List.of() : List.of(output.copy()),
                outputFluid == null || outputFluid.isEmpty() ? List.of() : List.of(outputFluid.copy()), List.of(),
                ExtraDelightRecipeAdapterUtils.energy(time), time, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<MortarRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<MortarRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<MortarRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), MortarRecipe.class)) {
            MortarRecipe source = holder.value();
            Ingredient input = source.getIngredients().isEmpty() ? Ingredient.EMPTY
                    : source.getIngredients().getFirst();
            if (!input.isEmpty()
                    && DelightRecipeAdapterUtils.matchesItems(
                    List.of(new CountedIngredient(input, 1L)), mergedInputs, List.of())
                    && (mergedFluids == null || mergedFluids.isEmpty())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
