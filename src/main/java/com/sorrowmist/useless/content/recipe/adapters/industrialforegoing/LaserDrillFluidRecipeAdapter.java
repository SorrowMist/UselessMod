package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.config.machine.resourceproduction.FluidLaserBaseConfig;
import com.buuz135.industrial.config.machine.resourceproduction.LaserDrillConfig;
import com.buuz135.industrial.module.ModuleResourceProduction;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generates the fixed fluid-laser outputs that use special alloy-furnace molds. */
public final class LaserDrillFluidRecipeAdapter
        implements IRecipeAdapter<IndustrialForegoingSyntheticRecipe> {
    private static final int WATER_AMOUNT = 1000;
    private static final int OUTPUT_AMOUNT = 10;

    private static final List<Target> TARGETS = List.of(
            new Target(
                    ResourceLocation.fromNamespaceAndPath("industrialforegoing", "ether_gas"),
                    Items.NETHER_STAR),
            new Target(
                    ResourceLocation.fromNamespaceAndPath("ifeu", "liquid_sculk_matter"),
                    Items.WARDEN_SPAWN_EGG),
            new Target(
                    ResourceLocation.fromNamespaceAndPath("ifeu", "liquid_dragon_breath"),
                    Items.DRAGON_EGG));

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
        return new ItemStack(ModuleResourceProduction.FLUID_LASER_BASE.getBlock());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>(TARGETS.size());
        for (Target target : TARGETS) {
            BuiltInRegistries.FLUID.getOptional(target.fluidId()).ifPresent(fluid ->
                    recipes.add(createRecipe(target, fluid)));
        }
        return IndustrialForegoingRecipeAdapterUtils.holders(recipes);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(Target target, Fluid fluid) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.INDUSTRIAL_FOREGOING,
                "laser_drill_fluid_" + target.fluidId().getNamespace()
                        + "_" + target.fluidId().getPath());
        int processTime = IndustrialForegoingRecipeAdapterUtils.positive(
                FluidLaserBaseConfig.maxProgress);
        long energy = IndustrialForegoingRecipeAdapterUtils.energyPerTick(
                LaserDrillConfig.powerPerOperation, processTime);

        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(),
                List.of(LongSizedFluidIngredient.from(new FluidStack(Fluids.WATER, WATER_AMOUNT))),
                List.of(),
                List.of(),
                List.of(new FluidStack(fluid, OUTPUT_AMOUNT)),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(
                        Ingredient.of(new ItemStack(ModuleResourceProduction.FLUID_LASER_BASE.getBlock())),
                        Ingredient.of(target.mold())),
                AlloyFurnaceMode.NORMAL);
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

    private record Target(ResourceLocation fluidId, Item mold) {
    }
}
