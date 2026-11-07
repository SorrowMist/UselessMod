package com.sorrowmist.useless.content.recipe.adapters.delight.brewinandchewin;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.*;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import umpaz.brewinandchewin.common.crafting.KegFermentingRecipe;
import umpaz.brewinandchewin.common.registry.BnCItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Native B&C 3.2.1: FluidStack input, Item/Fluid result and a shared int amount. */
public final class KegFermentingRecipeAdapter implements IRecipeAdapter<KegFermentingRecipe> {
    @Override public String sourceId() { return RecipeSourceIds.BREWIN_AND_CHEWIN; }
    @Override public Class<KegFermentingRecipe> getRecipeClass() { return KegFermentingRecipe.class; }
    @Override public ItemStack getMoldItem() { return new ItemStack(BnCItems.KEG.get()); }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<KegFermentingRecipe> holder, Level level) {
        if (holder == null || holder.value().getAmount() <= 0) return List.of();
        KegFermentingRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.getIngredients());
        FluidStack fluid = source.getFluidIngredient();
        List<LongSizedFluidIngredient> fluids = fluid == null || fluid.isEmpty() ? List.of()
                : List.of(LongSizedFluidIngredient.from(AdapterUtils.toSizedFluidIngredient(fluid)));
        if (inputs.isEmpty() && fluids.isEmpty()) return List.of();

        // Do not synthesize a bottling recipe: fermentation really produces a tank fluid.
        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = new ArrayList<>();
        if (source.getResultFluid() != null && source.getResultFluid() != Fluids.EMPTY) {
            outputFluids.add(new FluidStack(source.getResultFluid(), source.getAmount()));
        } else if (source.getResultItem() != null) {
            ItemStack output = new ItemStack(source.getResultItem(), source.getAmount());
            if (!output.isEmpty()) outputs.add(output);
        }
        if (outputs.isEmpty() && outputFluids.isEmpty()) return List.of();
        int time = Math.max(1, source.getFermentTime());
        return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                inputs, fluids, List.of(), outputs, outputFluids, List.of(),
                Math.max(1L, (long) time * AdapterUtils.DEFAULT_ENERGY / 200L), time,
                Ingredient.EMPTY, 0, List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<KegFermentingRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<KegFermentingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<KegFermentingRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), KegFermentingRecipe.class)) {
            for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
                if (DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                        && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
