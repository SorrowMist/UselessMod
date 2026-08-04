package com.sorrowmist.useless.content.recipe.adapters.enderio;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Ender IO alloy-smelter recipes to the alloy furnace. */
public final class AlloySmeltingRecipeAdapter implements IRecipeAdapter<AlloySmeltingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<AlloySmeltingRecipe> getRecipeClass() {
        return AlloySmeltingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EIOBlocks.ALLOY_SMELTER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AlloySmeltingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        AlloySmeltingRecipe source = holder.value();
        List<CountedIngredient> inputs = EnderIOAdapterUtils.counted(source.inputs());
        ItemStack output = source.output();
        if (inputs == null || inputs.isEmpty() || output == null || output.isEmpty()
                || output.getCount() <= 0 || source.energy() < 0) {
            LOGGER.warn("Skipping invalid Ender IO alloy-smelting recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(output.copy()), List.of(),
                source.energy(), AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(getMoldItem()), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<AlloySmeltingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<AlloySmeltingRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<AlloySmeltingRecipe> holder : manager.getAllRecipesFor(
                EIORecipes.ALLOY_SMELTING.type().get())) {
            AlloySmeltingRecipe source = holder.value();
            List<CountedIngredient> inputs = EnderIOAdapterUtils.counted(source.inputs());
            ItemStack output = source.output();
            if (inputs != null && output != null && !output.isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs, EnderIOAdapterUtils.requirements(inputs))) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
