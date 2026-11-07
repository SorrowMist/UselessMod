package com.sorrowmist.useless.content.recipe.adapters.delight.brewinandchewin;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.*;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import umpaz.brewinandchewin.common.crafting.KegPouringRecipe;
import umpaz.brewinandchewin.common.registry.BnCItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Native pouring, including container-free cheese. Dynamic potion pouring is not a fixed recipe. */
public final class KegPouringRecipeAdapter implements IRecipeAdapter<KegPouringRecipe> {
    @Override public String sourceId() { return RecipeSourceIds.BREWIN_AND_CHEWIN; }
    @Override public Class<KegPouringRecipe> getRecipeClass() { return KegPouringRecipe.class; }
    @Override public ItemStack getMoldItem() { return new ItemStack(BnCItems.KEG.get()); }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<KegPouringRecipe> holder, Level level) {
        if (holder == null || holder.value().hasSpecialFluid()) return List.of();
        KegPouringRecipe source = holder.value();
        FluidStack fluid = source.getFluid(source.getOutput());
        ItemStack container = source.getContainer();
        ItemStack output = source.getOutput();
        if (fluid == null || fluid.isEmpty() || output == null || output.isEmpty()) return List.of();
        List<CountedIngredient> inputs = container == null || container.isEmpty() ? List.of()
                : List.of(new CountedIngredient(DelightRecipeAdapterUtils.exactStack(container), container.getCount()));
        return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                inputs, List.of(LongSizedFluidIngredient.from(AdapterUtils.toSizedFluidIngredient(fluid))),
                List.of(), List.of(output.copy()), List.of(), List.of(),
                AdapterUtils.DEFAULT_ENERGY, AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<KegPouringRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<KegPouringRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<KegPouringRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), KegPouringRecipe.class)) {
            for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
                if (DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                        && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
