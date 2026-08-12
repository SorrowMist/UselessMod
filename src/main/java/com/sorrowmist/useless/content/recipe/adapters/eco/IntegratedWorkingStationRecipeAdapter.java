package com.sorrowmist.useless.content.recipe.adapters.eco;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts ECO Integrated Working Station recipes to alloy-furnace recipes. */
public final class IntegratedWorkingStationRecipeAdapter
        implements IRecipeAdapter<IntegratedWorkingStationRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation INTEGRATED_WORKING_STATION =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "integrated_working_station");

    @Override
    public Class<IntegratedWorkingStationRecipe> getRecipeClass() {
        return IntegratedWorkingStationRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(BuiltInRegistries.ITEM.get(INTEGRATED_WORKING_STATION));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IntegratedWorkingStationRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid ECO Integrated Working Station recipe: {}", holder.id());
            return List.of();
        }

        return List.of(createRecipe(AdapterUtils.convertedId(holder.id()), converted));
    }

    @Override
    public List<RecipeHolder<IntegratedWorkingStationRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedFluids == null || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<IntegratedWorkingStationRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IntegratedWorkingStationRecipe> holder : recipeManager.getAllRecipesFor(
                NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null
                    && AdapterUtils.matchesRequired(mergedInputs, converted.itemRequirements())
                    && matchesFluidRequirement(mergedFluids, converted.inputFluids())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                converted.itemInputs(),
                converted.inputFluids(),
                List.of(),
                copyItems(converted.itemOutputs()),
                copyFluids(converted.fluidOutputs()),
                List.of(),
                converted.energy(),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Nullable
    private static Converted convertData(@Nullable IntegratedWorkingStationRecipe source) {
        if (source == null || source.energy() < 0) {
            return null;
        }

        List<SizedIngredient> sourceInputs = source.inputItems();
        if (sourceInputs == null || sourceInputs.size() > 9) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (SizedIngredient input : sourceInputs) {
            if (input == null || input.count() < 0 || input.ingredient() == null) {
                return null;
            }
            if (input.count() == 0) {
                continue;
            }
            if (input.ingredient().isEmpty()) {
                return null;
            }
            AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count());
        }

        SizedFluidIngredient inputFluid = source.inputFluid();
        if (inputFluid == null || inputFluid.ingredient() == null) {
            return null;
        }
        List<SizedFluidIngredient> inputFluids = List.of();
        if (!inputFluid.ingredient().isEmpty()) {
            if (inputFluid.amount() <= 0) {
                return null;
            }
            inputFluids = List.of(inputFluid);
        }

        ItemStack itemOutput = source.itemOutput();
        FluidStack fluidOutput = source.fluidOutput();
        List<ItemStack> itemOutputs = new ArrayList<>();
        if (itemOutput != null && !itemOutput.isEmpty() && itemOutput.getCount() > 0) {
            itemOutputs.add(itemOutput.copy());
        }
        List<FluidStack> fluidOutputs = new ArrayList<>();
        if (fluidOutput != null && !fluidOutput.isEmpty() && fluidOutput.getAmount() > 0) {
            fluidOutputs.add(fluidOutput.copy());
        }

        if ((requirements.isEmpty() && inputFluids.isEmpty())
                || (itemOutputs.isEmpty() && fluidOutputs.isEmpty())) {
            return null;
        }

        List<CountedIngredient> itemInputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(itemInputs, requirements, inputFluids, itemOutputs, fluidOutputs,
                source.energy());
    }

    private static boolean matchesFluidRequirement(
            Map<FluidStack, Long> mergedFluids, List<SizedFluidIngredient> required) {
        if (required.isEmpty()) {
            return true;
        }
        return com.sorrowmist.useless.content.recipe.FluidIngredientAllocator.matches(
                required, mergedFluids, 1L);
    }

    private static List<ItemStack> copyItems(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static List<FluidStack> copyFluids(List<FluidStack> stacks) {
        return stacks.stream().map(FluidStack::copy).toList();
    }

    private record Converted(
            List<CountedIngredient> itemInputs,
            Map<Ingredient, Long> itemRequirements,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> itemOutputs,
            List<FluidStack> fluidOutputs,
            long energy) {
    }
}
