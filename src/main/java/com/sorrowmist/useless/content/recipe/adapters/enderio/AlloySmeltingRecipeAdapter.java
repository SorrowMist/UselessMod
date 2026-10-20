package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.sorrowmist.useless.utils.RateLimitedLogger;
import com.enderio.machines.common.recipe.AlloySmeltingRecipe;
import com.enderio.machines.common.init.MachineBlocks;
import com.enderio.machines.common.init.MachineRecipes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
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
        return new ItemStack(MachineBlocks.ALLOY_SMELTER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AlloySmeltingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        AlloySmeltingRecipe source = holder.value();
        List<CountedIngredient> inputs = EnderIOAdapterUtils.counted(source.getInputs());
        List<ItemStack> outputs = EnderIOAdapterUtils.itemOutputs(source.getResultStacks(
                level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess()));
        if (inputs == null || inputs.isEmpty() || outputs == null || outputs.isEmpty() || source.getBaseEnergyCost() < 0) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Ender IO alloy-smelting recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), outputs, List.of(),
                source.getBaseEnergyCost(), AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
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
        for (RecipeHolder<AlloySmeltingRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(manager,
                MachineRecipes.ALLOY_SMELTING.type().get())) {
            AlloySmeltingRecipe source = holder.value();
            List<CountedIngredient> inputs = EnderIOAdapterUtils.counted(source.getInputs());
            List<ItemStack> outputs = EnderIOAdapterUtils.itemOutputs(source.getResultStacks(
                level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess()));
            if (inputs != null && outputs != null && !outputs.isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs, EnderIOAdapterUtils.requirements(inputs))) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
