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
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
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

        List<FluidStack> inputFluidChoices = converted.inputFluidChoices();
        if (inputFluidChoices.isEmpty()) {
            return List.of(createRecipe(AdapterUtils.convertedId(holder.id()), converted, List.of()));
        }

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>(inputFluidChoices.size());
        for (int index = 0; index < inputFluidChoices.size(); index++) {
            ResourceLocation id = index == 0
                    ? AdapterUtils.convertedId(holder.id())
                    : convertedFluidId(holder.id(), index);
            recipes.add(createRecipe(id, converted, List.of(inputFluidChoices.get(index).copy())));
        }
        return recipes;
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
                    && matchesFluidRequirement(mergedFluids, converted.inputFluid())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, Converted converted, List<FluidStack> inputFluids) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                converted.itemInputs(),
                inputFluids,
                copyItems(converted.itemOutputs()),
                copyFluids(converted.fluidOutputs()),
                converted.energy(),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
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
        if (inputFluid == null || inputFluid.amount() <= 0 || inputFluid.ingredient() == null) {
            return null;
        }
        List<FluidStack> fluidChoices = fluidChoices(inputFluid);
        if (fluidChoices == null) {
            return null;
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

        if ((requirements.isEmpty() && fluidChoices.isEmpty())
                || (itemOutputs.isEmpty() && fluidOutputs.isEmpty())) {
            return null;
        }

        List<CountedIngredient> itemInputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(itemInputs, requirements, inputFluid, fluidChoices, itemOutputs, fluidOutputs,
                source.energy());
    }

    @Nullable
    private static List<FluidStack> fluidChoices(SizedFluidIngredient input) {
        if (input.ingredient().isEmpty()) {
            return List.of();
        }

        Map<FluidStack, FluidStack> uniqueChoices = new LinkedHashMap<>();
        for (FluidStack stack : input.getFluids()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            // Fluid tags such as c:water include both the source and flowing variants. Tanks
            // store the source fluid, so normalize first to avoid emitting duplicate recipes.
            Fluid fluid = stack.getFluid();
            if (fluid instanceof FlowingFluid flowingFluid) {
                fluid = flowingFluid.getSource();
            }
            FluidStack choice = new FluidStack(fluid, input.amount());
            choice.applyComponents(stack.getComponentsPatch());
            boolean duplicate = uniqueChoices.keySet().stream()
                    .anyMatch(existing -> FluidStack.isSameFluidSameComponents(existing, choice));
            if (!duplicate) {
                uniqueChoices.put(choice, choice);
            }
        }
        return uniqueChoices.isEmpty() ? null : List.copyOf(uniqueChoices.values());
    }

    private static boolean matchesFluidRequirement(
            Map<FluidStack, Long> mergedFluids, SizedFluidIngredient required) {
        if (required.ingredient().isEmpty()) {
            return true;
        }
        for (Map.Entry<FluidStack, Long> entry : mergedFluids.entrySet()) {
            if (entry.getValue() >= required.amount() && required.test(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation convertedFluidId(ResourceLocation originalId, int fluidIndex) {
        return ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(), originalId.getPath() + "_fluid_" + fluidIndex + "_converted");
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
            SizedFluidIngredient inputFluid,
            List<FluidStack> inputFluidChoices,
            List<ItemStack> itemOutputs,
            List<FluidStack> fluidOutputs,
            long energy) {
    }
}
