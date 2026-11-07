package com.sorrowmist.useless.content.recipe.adapters.delight.barbequesdelight;

import com.mao.barbequesdelight.content.recipe.SimpleSkeweringRecipe;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The native skewering recipe consumes a skewer (tool), ingredients and optional sides. */
public final class SkeweringRecipeAdapter implements IRecipeAdapter<SimpleSkeweringRecipe> {
    private static final ResourceLocation BASIN_ID = new ResourceLocation("barbequesdelight", "basin");
    @Override public String sourceId() { return RecipeSourceIds.BARBEQUES_DELIGHT; }
    @Override public Class<SimpleSkeweringRecipe> getRecipeClass() { return SimpleSkeweringRecipe.class; }
    @Override public ItemStack getMoldItem() {
        Item basin = DelightRecipeAdapterUtils.registeredItem(BASIN_ID);
        return basin == null ? ItemStack.EMPTY : basin.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SimpleSkeweringRecipe> holder, Level level) {
        if (holder == null) return List.of();
        SimpleSkeweringRecipe source = holder.value();
        if (AdapterUtils.isIngredientEmpty(source.tool) || AdapterUtils.isIngredientEmpty(source.ingredient)
                || source.ingredientCount <= 0 || source.sideCount < 0
                || source.sideCount > 0 && AdapterUtils.isIngredientEmpty(source.side)
                || source.output == null || source.output.isEmpty()) return List.of();
        Map<Ingredient, Long> counts = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(counts, source.tool, 1L);
        AdapterUtils.mergeIngredient(counts, source.ingredient, (long) source.ingredientCount);
        if (source.sideCount > 0) AdapterUtils.mergeIngredient(counts, source.side, (long) source.sideCount);
        List<CountedIngredient> inputs = counts.entrySet().stream()
                .map(e -> new CountedIngredient(e.getKey(), e.getValue())).toList();
        return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                inputs, List.of(), List.of(), List.of(source.output.copy()), List.of(), List.of(),
                AdapterUtils.DEFAULT_ENERGY, AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<SimpleSkeweringRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<SimpleSkeweringRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<SimpleSkeweringRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), SimpleSkeweringRecipe.class)) {
            for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
                if (DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                        && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
