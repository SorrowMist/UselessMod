package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.evaporator.recipes.EvaporatorRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts evaporator recipes using their deterministic display item output. */
public final class EvaporatorRecipeAdapter implements IRecipeAdapter<EvaporatorRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "evaporator");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<EvaporatorRecipe> getRecipeClass() {
        return EvaporatorRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<EvaporatorRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        EvaporatorRecipe source = holder.value();
        ItemStack output = source.getResultItem();
        if (!hasFluid(source.getFluid())
                || output == null || output.isEmpty()) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(source.getCookTime());
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), List.of(),
                List.of(com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient.from(
                        source.getFluid())), List.of(), List.of(output.copy()), List.of(), List.of(),
                ExtraDelightRecipeAdapterUtils.energy(time), time, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<EvaporatorRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<EvaporatorRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<EvaporatorRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), EvaporatorRecipe.class)) {
            EvaporatorRecipe source = holder.value();
            if (hasFluid(source.getFluid())
                    && source.getResultItem() != null && !source.getResultItem().isEmpty()
                    && DelightRecipeAdapterUtils.matchesFluids(
                    List.of(com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient.from(
                            source.getFluid())), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static boolean hasFluid(@Nullable SizedFluidIngredient fluid) {
        return fluid != null && fluid.amount() > 0 && fluid.ingredient() != null
                && !fluid.ingredient().isEmpty();
    }
}
