package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.config.machine.resourceproduction.FermentationStationConfig;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleResourceProduction;
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
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Industrial Foregoing's tag-driven ore fermentation modes. */
public final class FermentationStationRecipeAdapter
        implements IRecipeAdapter<IndustrialForegoingSyntheticRecipe> {
    private static final int INPUT_AMOUNT = 100;

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
        return new ItemStack(ModuleResourceProduction.FERMENTATION_STATION.getBlock());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ResourceLocation rawTag : IndustrialForegoingRecipeAdapterUtils.validRawMaterialTags()) {
            for (Mode mode : Mode.values()) {
                recipes.add(createRecipe(rawTag, mode));
            }
        }
        return IndustrialForegoingRecipeAdapterUtils.holders(recipes);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IndustrialForegoingSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IndustrialForegoingSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (IndustrialForegoingRecipeAdapterUtils.matches(recipe, mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(ResourceLocation rawTag, Mode mode) {
        String tagPath = IndustrialForegoingRecipeAdapterUtils.tagPath(rawTag);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.INDUSTRIAL_FOREGOING,
                "fermentation_station_" + tagPath + "_x" + mode.multiplier);
        FluidStack input = IndustrialForegoingRecipeAdapterUtils.rawOreMeat(rawTag, INPUT_AMOUNT);
        FluidStack output = IndustrialForegoingRecipeAdapterUtils.fermentedOreMeat(
                rawTag, INPUT_AMOUNT * mode.multiplier);
        List<LongSizedFluidIngredient> fluids = new ArrayList<>();
        fluids.add(LongSizedFluidIngredient.from(input));
        if (mode.catalyst != null) fluids.add(LongSizedFluidIngredient.from(mode.catalyst));

        int processTime = IndustrialForegoingRecipeAdapterUtils.positive(mode.processTime());
        return new AdvancedAlloyFurnaceRecipe(
                id, List.of(), fluids, List.of(), List.of(), List.of(output), List.of(),
                IndustrialForegoingRecipeAdapterUtils.energyPerTick(
                        FermentationStationConfig.powerPerTick, processTime),
                processTime, Ingredient.EMPTY, 0,
                List.of(Ingredient.of(new ItemStack(ModuleResourceProduction.FERMENTATION_STATION.getBlock()))),
                AlloyFurnaceMode.NORMAL);
    }

    private enum Mode {
        X2(2, FermentationStationConfig.ticksFor2XProduction, null),
        X3(3, FermentationStationConfig.ticksFor3XProduction, null),
        X4(4, FermentationStationConfig.ticksFor4XProduction,
                new FluidStack(ModuleCore.PINK_SLIME.getSourceFluid().get(), 2)),
        X5(5, FermentationStationConfig.ticksFor5XProduction,
                new FluidStack(ModuleCore.ETHER.getSourceFluid().get(), 1));

        private final int multiplier;
        private final int processTime;
        private final FluidStack catalyst;

        Mode(int multiplier, int processTime, @Nullable FluidStack catalyst) {
            this.multiplier = multiplier;
            this.processTime = processTime;
            this.catalyst = catalyst;
        }

        private int processTime() {
            return processTime;
        }
    }
}
