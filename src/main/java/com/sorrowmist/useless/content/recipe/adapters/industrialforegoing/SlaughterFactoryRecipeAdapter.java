package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.config.machine.agriculturehusbandry.SlaughterFactoryConfig;
import com.buuz135.industrial.module.ModuleAgricultureHusbandry;
import com.buuz135.industrial.module.ModuleCore;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts the dynamic Industrial Foregoing slaughter operation to a fixed fluid recipe. */
public final class SlaughterFactoryRecipeAdapter
        implements IRecipeAdapter<IndustrialForegoingSyntheticRecipe> {
    private static final int WATER_AMOUNT = 1000;
    private static final int MEAT_AMOUNT = 100;
    private static final int PINK_SLIME_AMOUNT = 1000;
    private static final int ENERGY_MULTIPLIER = 100;

    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<IndustrialForegoingSyntheticRecipe> getRecipeClass() {
        return IndustrialForegoingSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModuleAgricultureHusbandry.SLAUGHTER_FACTORY.getBlock());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.INDUSTRIAL_FOREGOING, "mob_slaughter_factory");
        int processTime = IndustrialForegoingRecipeAdapterUtils.positive(
                SlaughterFactoryConfig.maxProgress);
        long energy = Math.max(1L, IndustrialForegoingRecipeAdapterUtils.multiply(
                IndustrialForegoingRecipeAdapterUtils.positive(
                        SlaughterFactoryConfig.powerPerOperation),
                ENERGY_MULTIPLIER));
        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(),
                List.of(new LongSizedFluidIngredient(
                        FluidIngredient.single(Fluids.WATER), WATER_AMOUNT)),
                List.of(),
                List.of(),
                List.of(
                        new FluidStack(ModuleCore.MEAT.getSourceFluid().get(), MEAT_AMOUNT),
                        new FluidStack(ModuleCore.PINK_SLIME.getSourceFluid().get(), PINK_SLIME_AMOUNT)),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(new ItemStack(
                        ModuleAgricultureHusbandry.SLAUGHTER_FACTORY.getBlock()))),
                AlloyFurnaceMode.NORMAL);
        return IndustrialForegoingRecipeAdapterUtils.holders(List.of(recipe));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IndustrialForegoingSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) return List.of();
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IndustrialForegoingSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            if (IndustrialForegoingRecipeAdapterUtils.matches(
                    holder.value().convertedRecipe(), mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
