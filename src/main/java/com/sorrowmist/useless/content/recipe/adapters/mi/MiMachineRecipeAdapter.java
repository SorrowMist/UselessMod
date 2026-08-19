package com.sorrowmist.useless.content.recipe.adapters.mi;

import aztech.modern_industrialization.machines.init.MIMachineRecipeTypes;
import aztech.modern_industrialization.machines.recipe.MachineRecipe;
import aztech.modern_industrialization.machines.recipe.MachineRecipeType;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts one Modern Industrialization machine's recipes into alloy-furnace recipes.
 *
 * <p>MI uses a single {@link MachineRecipe} class across every machine, so this adapter is
 * instantiated once per machine and verifies {@link MachineRecipeType} in both conversion and
 * matching.</p>
 *
 * <p>MI recipes use per-component probabilities. Probabilistic inputs (e.g. a quarry drill that is
 * consumed only part of the time) are made deterministic, and every output is scaled by
 * {@code amount * probability / inputProbability} so the input:output ratio stays the same. EU cost
 * maps to FE. The oil drilling rig additionally receives 1 mB of water as a placeholder input so
 * its recipes can always be encoded as AE2 patterns.</p>
 */
public final class MiMachineRecipeAdapter implements IRecipeAdapter<MachineRecipe> {

    private final MachineRecipeType recipeType;
    private final ItemStack moldItem;
    private final String sourceId;

    public MiMachineRecipeAdapter(MachineRecipeType recipeType, String moldBlockPath) {
        this(recipeType, ResourceLocation.fromNamespaceAndPath(MiAdapterUtils.MOD_ID, moldBlockPath),
                RecipeSourceIds.MODERN_INDUSTRIALIZATION);
    }

    public MiMachineRecipeAdapter(MachineRecipeType recipeType, String moldBlockPath, String sourceId) {
        this(recipeType, ResourceLocation.fromNamespaceAndPath(MiAdapterUtils.MOD_ID, moldBlockPath), sourceId);
    }

    public MiMachineRecipeAdapter(MachineRecipeType recipeType, ResourceLocation moldId, String sourceId) {
        this.recipeType = recipeType;
        this.moldItem = MiAdapterUtils.item(moldId);
        this.sourceId = sourceId;
        if (this.recipeType == null) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[useless_mod] Recipe adapter for {} has a null recipe type; its recipes will be ignored", moldId);
        }
        if (this.moldItem.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[useless_mod] Recipe adapter mold is missing/empty: {}", moldId);
        }
    }

    @Override
    public String sourceId() {
        return sourceId;
    }

    @Override
    public Class<MachineRecipe> getRecipeClass() {
        return MachineRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return moldItem;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MachineRecipe> holder, Level level) {
        if (this.recipeType == null) {
            return List.of();
        }
        MachineRecipe source = holder == null ? null : holder.value();
        if (source == null || !isOwnRecipe(source)) {
            return List.of();
        }

        double scale = inputScale(source);

        List<ItemStack> outputs = new ArrayList<>();
        for (MachineRecipe.ItemOutput output : source.itemOutputs) {
            long amount = scaledAmount(output.amount(), output.probability(), scale);
            ItemStack stack = output.getStack();
            if (stack != null && !stack.isEmpty() && amount > 0) {
                outputs.add(stack.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }

        List<FluidStack> outputFluids = new ArrayList<>();
        for (MachineRecipe.FluidOutput output : source.fluidOutputs) {
            long amount = scaledAmount(output.amount(), output.probability(), scale);
            if (amount > 0) {
                outputFluids.add(new FluidStack(output.fluid(), (int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }

        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            return List.of();
        }

        List<CountedIngredient> inputs = new ArrayList<>();
        Map<Ingredient, Long> mergedInputs = new LinkedHashMap<>();
        for (MachineRecipe.ItemInput input : source.itemInputs) {
            if (input.ingredient() != null && !input.ingredient().isEmpty() && input.amount() > 0) {
                AdapterUtils.mergeIngredient(mergedInputs, input.ingredient(), input.amount());
            }
        }
        for (Map.Entry<Ingredient, Long> entry : mergedInputs.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        for (MachineRecipe.FluidInput input : source.fluidInputs) {
            if (input.fluid() != null && input.amount() > 0) {
                inputFluids.add(new SizedFluidIngredient(input.fluid(), (int) Math.min(Integer.MAX_VALUE, input.amount())));
            }
        }
        if (isOilDrillingRig()) {
            addOilDrillingWater(inputFluids);
        }

        int processTime = Math.max(1, source.duration);
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, source.getTotalEu());

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                inputFluids,
                outputs,
                outputFluids,
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<MachineRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (this.recipeType == null || level == null || !matchesMold(mold)) {
            return List.of();
        }
        boolean hasItems = mergedInputs != null && !mergedInputs.isEmpty();
        boolean hasFluids = mergedFluids != null && !mergedFluids.isEmpty();
        if (!hasItems && !hasFluids) {
            return List.of();
        }

        List<RecipeHolder<MachineRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<MachineRecipe> holder : manager.getAllRecipesFor(this.recipeType)) {
            MachineRecipe source = holder.value();
            if (source == null || !isOwnRecipe(source)) {
                continue;
            }

            Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
            for (MachineRecipe.ItemInput input : source.itemInputs) {
                if (input.ingredient() != null && !input.ingredient().isEmpty() && input.amount() > 0) {
                    AdapterUtils.mergeIngredient(requiredItems, input.ingredient(), input.amount());
                }
            }
            if (!requiredItems.isEmpty() && !AdapterUtils.matchesRequired(mergedInputs, requiredItems)) {
                continue;
            }

            List<SizedFluidIngredient> requiredFluids = new ArrayList<>();
            for (MachineRecipe.FluidInput input : source.fluidInputs) {
                if (input.fluid() != null && input.amount() > 0) {
                    requiredFluids.add(new SizedFluidIngredient(input.fluid(), (int) Math.min(Integer.MAX_VALUE, input.amount())));
                }
            }
            if (isOilDrillingRig()) {
                addOilDrillingWater(requiredFluids);
            }
            if (AdapterUtils.matchesFluidIngredients(mergedFluids, requiredFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private boolean isOwnRecipe(MachineRecipe source) {
        Object type = source.getType();
        if (type == this.recipeType) {
            return true;
        }
        if (type instanceof MachineRecipeType machineType) {
            return machineType.getId() != null && machineType.getId().equals(this.recipeType.getId());
        }
        return false;
    }

    private boolean isOilDrillingRig() {
        return this.recipeType == MIMachineRecipeTypes.OIL_DRILLING_RIG;
    }

    private static void addOilDrillingWater(List<SizedFluidIngredient> target) {
        SizedFluidIngredient water = AdapterUtils.toSizedFluidIngredient(new FluidStack(Fluids.WATER, 1));
        if (water != null) {
            target.add(water);
        }
    }

    /** Returns 1 / (minimum probability among probabilistic inputs), or 1 when none exist. */
    private static double inputScale(MachineRecipe source) {
        double minProbability = 1.0;
        for (MachineRecipe.ItemInput input : source.itemInputs) {
            if (input.probability() > 0 && input.probability() < 1) {
                minProbability = Math.min(minProbability, input.probability());
            }
        }
        for (MachineRecipe.FluidInput input : source.fluidInputs) {
            if (input.probability() > 0 && input.probability() < 1) {
                minProbability = Math.min(minProbability, input.probability());
            }
        }
        return minProbability <= 0 ? 1.0 : 1.0 / minProbability;
    }

    /** Deterministic amount for a component: {@code amount * probability * inputScale}, min 1. */
    private static long scaledAmount(long amount, float probability, double inputScale) {
        if (amount <= 0) {
            return 0;
        }
        double scaled = amount * probability * inputScale;
        return Math.max(1, Math.round(scaled));
    }
}
