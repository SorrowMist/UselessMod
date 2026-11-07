package com.sorrowmist.useless.content.recipe.adapters.delight.barbequesdelight;

import com.mao.barbequesdelight.content.recipe.SimpleGrillingRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.*;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Barbeque's Delight 1.0.6 exposes these inputs/results as public fields. */
public final class GrillingRecipeAdapter implements IRecipeAdapter<SimpleGrillingRecipe> {
    private static final ResourceLocation GRILL_ID = new ResourceLocation("barbequesdelight", "grill");
    @Override public String sourceId() { return RecipeSourceIds.BARBEQUES_DELIGHT; }
    @Override public Class<SimpleGrillingRecipe> getRecipeClass() { return SimpleGrillingRecipe.class; }
    @Override public ItemStack getMoldItem() {
        Item grill = DelightRecipeAdapterUtils.registeredItem(GRILL_ID);
        return grill == null ? ItemStack.EMPTY : grill.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SimpleGrillingRecipe> holder, Level level) {
        if (holder == null) return List.of();
        SimpleGrillingRecipe source = holder.value();
        if (AdapterUtils.isIngredientEmpty(source.ingredient) || source.output == null
                || source.output.isEmpty()) return List.of();
        int time = Math.max(1, source.getBarbecuingTime());
        return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.ingredient, 1L)), List.of(), List.of(),
                List.of(source.output.copy()), List.of(), List.of(),
                Math.max(1L, (long) time * AdapterUtils.DEFAULT_ENERGY / 200L), time,
                Ingredient.EMPTY, 0, List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<SimpleGrillingRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<SimpleGrillingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<SimpleGrillingRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), SimpleGrillingRecipe.class)) {
            for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
                if (DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                        && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
