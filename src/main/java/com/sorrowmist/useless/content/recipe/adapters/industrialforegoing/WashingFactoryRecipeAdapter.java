package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.config.machine.resourceproduction.WashingFactoryConfig;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleResourceProduction;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
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

/** Converts Industrial Foregoing's tag-driven ore washing recipes. */
public final class WashingFactoryRecipeAdapter
        implements IRecipeAdapter<IndustrialForegoingSyntheticRecipe> {
    private static final int FLUID_AMOUNT = 100;

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
        return new ItemStack(ModuleResourceProduction.WASHING_FACTORY.getBlock());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ResourceLocation rawTag : IndustrialForegoingRecipeAdapterUtils.validRawMaterialTags()) {
            FluidStack meat = new FluidStack(ModuleCore.MEAT.getSourceFluid().get(), FLUID_AMOUNT);
            FluidStack output = IndustrialForegoingRecipeAdapterUtils.rawOreMeat(rawTag, FLUID_AMOUNT);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    RecipeSourceIds.INDUSTRIAL_FOREGOING,
                    "washing_factory_" + IndustrialForegoingRecipeAdapterUtils.tagPath(rawTag));
            int processTime = IndustrialForegoingRecipeAdapterUtils.positive(
                    WashingFactoryConfig.maxProgress);
            recipes.add(new AdvancedAlloyFurnaceRecipe(
                    id,
                    List.of(new CountedIngredient(
                            Ingredient.of(IndustrialForegoingRecipeAdapterUtils.itemTag(rawTag)), 1L)),
                    List.of(LongSizedFluidIngredient.from(meat)), List.of(), List.of(),
                    List.of(output), List.of(), IndustrialForegoingRecipeAdapterUtils.energyPerTick(
                            WashingFactoryConfig.powerPerTick, processTime), processTime,
                    Ingredient.EMPTY, 0,
                    List.of(Ingredient.of(new ItemStack(ModuleResourceProduction.WASHING_FACTORY.getBlock()))),
                    AlloyFurnaceMode.NORMAL));
        }
        return IndustrialForegoingRecipeAdapterUtils.holders(recipes);
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
