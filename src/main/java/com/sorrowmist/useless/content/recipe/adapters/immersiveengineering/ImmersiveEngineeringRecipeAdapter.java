package com.sorrowmist.useless.content.recipe.adapters.immersiveengineering;

import blusunrize.immersiveengineering.api.crafting.AlloyRecipe;
import blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.BlueprintCraftingRecipe;
import blusunrize.immersiveengineering.api.crafting.BottlingMachineRecipe;
import blusunrize.immersiveengineering.api.crafting.ClocheRecipe;
import blusunrize.immersiveengineering.api.crafting.CokeOvenRecipe;
import blusunrize.immersiveengineering.api.crafting.CrusherRecipe;
import blusunrize.immersiveengineering.api.crafting.FermenterRecipe;
import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import blusunrize.immersiveengineering.api.crafting.IESerializableRecipe;
import blusunrize.immersiveengineering.api.crafting.MetalPressRecipe;
import blusunrize.immersiveengineering.api.crafting.MixerRecipe;
import blusunrize.immersiveengineering.api.crafting.MultiblockRecipe;
import blusunrize.immersiveengineering.api.crafting.RefineryRecipe;
import blusunrize.immersiveengineering.api.crafting.SawmillRecipe;
import blusunrize.immersiveengineering.api.crafting.SqueezerRecipe;
import blusunrize.immersiveengineering.api.crafting.StackWithChance;
import blusunrize.immersiveengineering.api.crafting.TagOutput;
import blusunrize.immersiveengineering.api.crafting.TagOutputList;
import blusunrize.immersiveengineering.common.config.IEServerConfig;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
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
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts Immersive Engineering machine recipes to alloy-furnace recipes. */
public final class ImmersiveEngineeringRecipeAdapter
        implements IRecipeAdapter<IESerializableRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "immersiveengineering";
    private static final Set<String> MACHINE_MOLD_PATHS = Set.of(
            "alloy_smelter",
            "arc_furnace",
            "blast_furnace",
            "workbench",
            "bottling_machine",
            "cloche",
            "coke_oven",
            "crusher",
            "fermenter",
            "metal_press",
            "mixer",
            "refinery",
            "sawmill",
            "squeezer");

    @Override
    public String sourceId() {
        return RecipeSourceIds.IMMERSIVE_ENGINEERING;
    }

    @Override
    public Class<IESerializableRecipe> getRecipeClass() {
        return IESerializableRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(mold.getItem());
        return id != null && MOD_ID.equals(id.getNamespace()) && MACHINE_MOLD_PATHS.contains(id.getPath());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IESerializableRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || !isSupported(holder.value())) {
            return List.of();
        }

        try {
            AdvancedAlloyFurnaceRecipe converted = convert(holder, holder.value());
            return converted == null ? List.of() : List.of(converted);
        } catch (RuntimeException exception) {
            LOGGER.warn("Skipping Immersive Engineering recipe conversion: {}", holder.id(), exception);
            return List.of();
        }
    }

    @Override
    public List<RecipeHolder<IESerializableRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        Map<Ingredient, Long> safeInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> safeFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<IESerializableRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> rawHolder : level.getRecipeManager().getRecipes()) {
            if (!(rawHolder.value() instanceof IESerializableRecipe source) || !isSupported(source)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            RecipeHolder<IESerializableRecipe> holder =
                    (RecipeHolder<IESerializableRecipe>) (RecipeHolder<?>) rawHolder;
            for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
                if (recipe.molds().size() != 1
                        || !AdapterUtils.matchesMold(recipe.mold(), mold)
                        || !matchesInputs(recipe, safeInputs, safeFluids)) {
                    continue;
                }
                matches.add(holder);
                break;
            }
        }
        return matches;
    }

    private static boolean isSupported(IESerializableRecipe recipe) {
        return recipe instanceof AlloyRecipe
                || recipe instanceof ArcFurnaceRecipe
                || recipe instanceof BlastFurnaceRecipe
                || recipe instanceof BlueprintCraftingRecipe
                || recipe instanceof BottlingMachineRecipe
                || recipe instanceof ClocheRecipe
                || recipe instanceof CokeOvenRecipe
                || recipe instanceof CrusherRecipe
                || recipe instanceof FermenterRecipe
                || recipe instanceof MetalPressRecipe
                || recipe instanceof MixerRecipe
                || recipe instanceof RefineryRecipe
                || recipe instanceof SawmillRecipe
                || recipe instanceof SqueezerRecipe;
    }

    private static AdvancedAlloyFurnaceRecipe convert(
            RecipeHolder<IESerializableRecipe> holder, IESerializableRecipe source) {
        if (source instanceof AlloyRecipe recipe) return convertAlloy(holder.id(), recipe);
        if (source instanceof ArcFurnaceRecipe recipe) return convertArcFurnace(holder.id(), recipe);
        if (source instanceof BlastFurnaceRecipe recipe) return convertBlastFurnace(holder.id(), recipe);
        if (source instanceof BlueprintCraftingRecipe recipe) return convertBlueprint(holder.id(), recipe);
        if (source instanceof BottlingMachineRecipe recipe) return convertBottling(holder.id(), recipe);
        if (source instanceof ClocheRecipe recipe) return convertCloche(holder.id(), recipe);
        if (source instanceof CokeOvenRecipe recipe) return convertCokeOven(holder.id(), recipe);
        if (source instanceof CrusherRecipe recipe) return convertCrusher(holder.id(), recipe);
        if (source instanceof FermenterRecipe recipe) return convertFermenter(holder.id(), recipe);
        if (source instanceof MetalPressRecipe recipe) return convertMetalPress(holder.id(), recipe);
        if (source instanceof MixerRecipe recipe) return convertMixer(holder.id(), recipe);
        if (source instanceof RefineryRecipe recipe) return convertRefinery(holder.id(), recipe);
        if (source instanceof SawmillRecipe recipe) return convertSawmill(holder.id(), recipe);
        if (source instanceof SqueezerRecipe recipe) return convertSqueezer(holder.id(), recipe);
        return null;
    }

    private static AdvancedAlloyFurnaceRecipe convertAlloy(
            ResourceLocation id, AlloyRecipe source) {
        return build(id,
                countedInputs(source.input0, source.input1),
                List.of(),
                tagOutput(source.output),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                source.time,
                molds("alloy_smelter"));
    }

    private static AdvancedAlloyFurnaceRecipe convertArcFurnace(
            ResourceLocation id, ArcFurnaceRecipe source) {
        List<CountedIngredient> inputs = new ArrayList<>();
        inputs.addAll(countedInputs(source.input));
        for (IngredientWithSize additive : source.additives) {
            inputs.addAll(countedInputs(additive));
        }
        return build(id,
                inputs,
                List.of(),
                append(tagOutputs(source.output), tagOutput(source.slag)),
                weightedOutputs(source.secondaryOutputs),
                List.of(),
                processEnergy(source),
                processTime(source),
                molds("arc_furnace"));
    }

    private static AdvancedAlloyFurnaceRecipe convertBlastFurnace(
            ResourceLocation id, BlastFurnaceRecipe source) {
        return build(id,
                countedInputs(source.input),
                List.of(),
                append(tagOutput(source.output), tagOutput(source.slag)),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                source.time,
                molds("blast_furnace"));
    }

    private static AdvancedAlloyFurnaceRecipe convertBlueprint(
            ResourceLocation id, BlueprintCraftingRecipe source) {
        Ingredient blueprint = blueprintMold(source.blueprintCategory);
        if (blueprint.isEmpty()) return null;
        return build(id,
                countedInputs(source.inputs),
                List.of(),
                tagOutput(source.output),
                List.of(),
                List.of(),
                processEnergy(source),
                processTime(source),
                molds("workbench", blueprint));
    }

    private static AdvancedAlloyFurnaceRecipe convertBottling(
            ResourceLocation id, BottlingMachineRecipe source) {
        return build(id,
                countedInputs(source.inputs),
                fluidInputs(source.fluidInput),
                tagOutputs(source.output),
                List.of(),
                List.of(),
                processEnergy(source),
                processTime(source),
                molds("bottling_machine"));
    }

    private static AdvancedAlloyFurnaceRecipe convertCloche(
            ResourceLocation id, ClocheRecipe source) {
        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        int fluidAmount = IEServerConfig.getOrDefault(IEServerConfig.MACHINES.cloche_fluid);
        if (source.requiredFluid != null && !source.requiredFluid.isEmpty()) {
            if (fluidAmount <= 0) return null;
            inputFluids.add(new SizedFluidIngredient(source.requiredFluid, fluidAmount));
        }

        long energyPerTick = IEServerConfig.getOrDefault(IEServerConfig.MACHINES.cloche_consumption);
        return build(id,
                List.of(),
                inputFluids,
                List.of(),
                weightedOutputs(source.outputs),
                List.of(),
                Math.multiplyExact(energyPerTick, (long) source.time),
                source.time,
                molds("cloche", source.seed, source.soil),
                true);
    }

    private static AdvancedAlloyFurnaceRecipe convertCokeOven(
            ResourceLocation id, CokeOvenRecipe source) {
        List<FluidStack> outputFluids = new ArrayList<>();
        if (source.creosoteOutput > 0) {
            Fluid creosote = BuiltInRegistries.FLUID.getOptional(id("creosote")).orElse(null);
            if (creosote == null) return null;
            outputFluids.add(new FluidStack(creosote, source.creosoteOutput));
        }
        return build(id,
                countedInputs(source.input),
                List.of(),
                tagOutput(source.output),
                List.of(),
                outputFluids,
                AdapterUtils.DEFAULT_ENERGY,
                source.time,
                molds("coke_oven"));
    }

    private static AdvancedAlloyFurnaceRecipe convertCrusher(
            ResourceLocation id, CrusherRecipe source) {
        return build(id,
                countedInputs(new IngredientWithSize(source.input)),
                List.of(),
                tagOutput(source.output),
                weightedOutputs(source.secondaryOutputs),
                List.of(),
                processEnergy(source),
                processTime(source),
                molds("crusher"));
    }

    private static AdvancedAlloyFurnaceRecipe convertFermenter(
            ResourceLocation id, FermenterRecipe source) {
        return build(id,
                countedInputs(source.input),
                List.of(),
                tagOutput(source.itemOutput),
                List.of(),
                fluidOutput(source.fluidOutput),
                processEnergy(source),
                processTime(source),
                molds("fermenter"));
    }

    private static AdvancedAlloyFurnaceRecipe convertMetalPress(
            ResourceLocation id, MetalPressRecipe source) {
        if (source.mold == null) return null;
        return build(id,
                countedInputs(source.input),
                List.of(),
                tagOutput(source.output),
                List.of(),
                List.of(),
                processEnergy(source),
                processTime(source),
                molds("metal_press", Ingredient.of(source.mold)));
    }

    private static AdvancedAlloyFurnaceRecipe convertMixer(
            ResourceLocation id, MixerRecipe source) {
        return build(id,
                countedInputs(source.itemInputs),
                fluidInputs(source.fluidInput),
                List.of(),
                List.of(),
                fluidOutput(source.fluidOutput),
                processEnergy(source),
                processTime(source),
                molds("mixer"));
    }

    private static AdvancedAlloyFurnaceRecipe convertRefinery(
            ResourceLocation id, RefineryRecipe source) {
        return build(id,
                List.of(),
                fluidInputs(source.input0, source.input1),
                List.of(),
                List.of(),
                fluidOutput(source.output),
                processEnergy(source),
                processTime(source),
                molds("refinery", source.catalyst));
    }

    private static AdvancedAlloyFurnaceRecipe convertSawmill(
            ResourceLocation id, SawmillRecipe source) {
        return build(id,
                countedInputs(new IngredientWithSize(source.input)),
                List.of(),
                itemStacks(source.getActualItemOutputs()),
                List.of(),
                List.of(),
                processEnergy(source),
                processTime(source),
                molds("sawmill"));
    }

    private static AdvancedAlloyFurnaceRecipe convertSqueezer(
            ResourceLocation id, SqueezerRecipe source) {
        return build(id,
                countedInputs(source.input),
                List.of(),
                tagOutput(source.itemOutput),
                List.of(),
                fluidOutput(source.fluidOutput),
                processEnergy(source),
                processTime(source),
                molds("squeezer"));
    }

    private static AdvancedAlloyFurnaceRecipe build(
            ResourceLocation id,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> deterministicOutputs,
            List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs,
            List<FluidStack> outputFluids,
            long energy,
            int processTime,
            List<Ingredient> molds) {
        return build(id, inputs, inputFluids, deterministicOutputs, weightedOutputs,
                outputFluids, energy, processTime, molds, false);
    }

    private static AdvancedAlloyFurnaceRecipe build(
            ResourceLocation id,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> deterministicOutputs,
            List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs,
            List<FluidStack> outputFluids,
            long energy,
            int processTime,
            List<Ingredient> molds,
            boolean allowEmptyInputs) {
        if (!allowEmptyInputs && inputs.isEmpty() && inputFluids.isEmpty()) return null;
        if (energy < 0 || processTime <= 0 || molds.isEmpty()) return null;

        var scaledChance = ExpectedOutputScaler.scale(weightedOutputs);
        if (scaledChance.isEmpty()) return null;
        int operations = scaledChance.get().operations();

        List<CountedIngredient> scaledInputs = scaleInputs(inputs, operations);
        List<SizedFluidIngredient> scaledInputFluids = scaleFluidInputs(inputFluids, operations);
        List<ItemStack> outputs = scaleOutputs(deterministicOutputs, operations);
        if (outputs == null) return null;
        for (ItemStack output : scaledChance.get().outputs()) {
            if (!addOutput(outputs, output)) return null;
        }
        List<FluidStack> scaledOutputFluids = scaleFluidOutputs(outputFluids, operations);
        if (scaledInputs == null || scaledInputFluids == null || scaledOutputFluids == null
                || (outputs.isEmpty() && scaledOutputFluids.isEmpty())) {
            return null;
        }

        try {
            long scaledEnergy = Math.multiplyExact(energy, (long) operations);
            long scaledTime = Math.multiplyExact((long) processTime, operations);
            if (scaledTime <= 0 || scaledTime > Integer.MAX_VALUE) return null;
            return new AdvancedAlloyFurnaceRecipe(
                    AdapterUtils.convertedId(id),
                    scaledInputs,
                    scaledInputFluids,
                    List.of(),
                    outputs,
                    scaledOutputFluids,
                    List.of(),
                    scaledEnergy,
                    (int) scaledTime,
                    Ingredient.EMPTY,
                    0,
                    molds,
                    AlloyFurnaceMode.NORMAL);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static boolean matchesInputs(
            AdvancedAlloyFurnaceRecipe recipe,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, required)
                && FluidIngredientAllocator.matchesLong(recipe.inputFluids(), mergedFluids, 1L);
    }

    private static long processEnergy(MultiblockRecipe recipe) {
        try {
            return recipe.getTotalProcessEnergy();
        } catch (RuntimeException exception) {
            return recipe.getBaseEnergy();
        }
    }

    private static int processTime(MultiblockRecipe recipe) {
        try {
            return recipe.getTotalProcessTime();
        } catch (RuntimeException exception) {
            return recipe.getBaseTime();
        }
    }

    private static List<CountedIngredient> countedInputs(IngredientWithSize... inputs) {
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (inputs != null) {
            for (IngredientWithSize input : inputs) {
                if (input == null || input.getBaseIngredient() == null
                        || input.getBaseIngredient().isEmpty() || input.getCount() <= 0) {
                    continue;
                }
                AdapterUtils.mergeIngredient(
                        requirements, input.getBaseIngredient(), input.getCount());
            }
        }
        return requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<CountedIngredient> countedInputs(List<IngredientWithSize> inputs) {
        return countedInputs(inputs == null ? null : inputs.toArray(IngredientWithSize[]::new));
    }

    private static List<SizedFluidIngredient> fluidInputs(
            @Nullable SizedFluidIngredient... inputs) {
        List<SizedFluidIngredient> result = new ArrayList<>();
        if (inputs != null) {
            for (SizedFluidIngredient input : inputs) {
                if (input != null && input.ingredient() != null
                        && !input.ingredient().isEmpty() && input.amount() > 0) {
                    result.add(input);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<CountedIngredient> scaleInputs(
            List<CountedIngredient> inputs, int operations) {
        Map<Ingredient, Long> result = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            try {
                long count = Math.multiplyExact(input.count(), (long) operations);
                if (count <= 0) return null;
                AdapterUtils.mergeIngredient(result, input.ingredient(), count);
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return result.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<SizedFluidIngredient> scaleFluidInputs(
            List<SizedFluidIngredient> inputs, int operations) {
        List<SizedFluidIngredient> result = new ArrayList<>();
        for (SizedFluidIngredient input : inputs) {
            try {
                long amount = Math.multiplyExact((long) input.amount(), operations);
                if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
                result.add(new SizedFluidIngredient(input.ingredient(), (int) amount));
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return List.copyOf(result);
    }

    private static List<FluidStack> scaleFluidOutputs(
            List<FluidStack> outputs, int operations) {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack output : outputs) {
            if (output == null || output.isEmpty() || output.getAmount() <= 0) continue;
            try {
                long amount = Math.multiplyExact((long) output.getAmount(), operations);
                if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
                if (!addFluidOutput(result, output.copyWithAmount((int) amount))) return null;
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> scaleOutputs(List<ItemStack> outputs, int operations) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack output : outputs) {
            if (output == null || output.isEmpty() || output.getCount() <= 0) continue;
            try {
                long count = Math.multiplyExact((long) output.getCount(), operations);
                if (count <= 0 || count > Integer.MAX_VALUE) return null;
                if (!addOutput(result, output.copyWithCount((int) count))) return null;
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return result;
    }

    private static boolean addOutput(List<ItemStack> outputs, ItemStack output) {
        if (output == null || output.isEmpty() || output.getCount() <= 0) return true;
        for (ItemStack existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, output)) continue;
            long count = (long) existing.getCount() + output.getCount();
            if (count > Integer.MAX_VALUE) return false;
            existing.setCount((int) count);
            return true;
        }
        outputs.add(output.copy());
        return true;
    }

    private static boolean addFluidOutput(List<FluidStack> outputs, FluidStack output) {
        for (FluidStack existing : outputs) {
            if (!FluidStack.isSameFluidSameComponents(existing, output)) continue;
            if (output.getAmount() > Integer.MAX_VALUE - existing.getAmount()) return false;
            existing.grow(output.getAmount());
            return true;
        }
        outputs.add(output.copy());
        return true;
    }

    private static List<ItemStack> tagOutputs(TagOutputList outputs) {
        if (outputs == null) return List.of();
        return itemStacks(outputs.get());
    }

    private static List<ItemStack> tagOutput(TagOutput output) {
        return output == null ? List.of() : itemStacks(List.of(output.get()));
    }

    private static List<ItemStack> itemStacks(Iterable<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    private static List<FluidStack> fluidOutput(@Nullable FluidStack output) {
        return output == null || output.isEmpty() || output.getAmount() <= 0
                ? List.of()
                : List.of(output.copy());
    }

    private static List<SizedFluidIngredient> fluidInputs(
            @Nullable SizedFluidIngredient first, @Nullable SizedFluidIngredient second) {
        return fluidInputs(new SizedFluidIngredient[]{first, second});
    }

    private static List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs(
            List<StackWithChance> outputs) {
        List<ExpectedOutputScaler.WeightedItemOutput> result = new ArrayList<>();
        if (outputs != null) {
            for (StackWithChance output : outputs) {
                if (output == null || output.stack() == null) continue;
                ItemStack stack = output.stack().get();
                if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                    result.add(new ExpectedOutputScaler.WeightedItemOutput(
                            stack.copy(), stack.getCount(), stack.getCount(), output.chance()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<Ingredient> molds(String machine, Ingredient... extras) {
        Ingredient machineMold = machineMold(machine);
        if (machineMold.isEmpty()) return List.of();

        List<Ingredient> result = new ArrayList<>();
        result.add(machineMold);
        if (extras != null) {
            for (Ingredient extra : extras) {
                if (extra != null && !extra.isEmpty()) result.add(extra);
            }
        }
        return List.copyOf(result);
    }

    private static Ingredient machineMold(String path) {
        Item item = BuiltInRegistries.ITEM.getOptional(id(path)).orElse(null);
        return item == null || item == Items.AIR ? Ingredient.EMPTY : Ingredient.of(item);
    }

    private static Ingredient blueprintMold(@Nullable String category) {
        if (category == null || category.isBlank()) return Ingredient.EMPTY;
        ItemStack blueprint;
        try {
            blueprint = BlueprintCraftingRecipe.getTypedBlueprint(category);
        } catch (RuntimeException exception) {
            return Ingredient.EMPTY;
        }
        return blueprint == null || blueprint.isEmpty()
                ? Ingredient.EMPTY
                : DataComponentIngredient.of(true, blueprint);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static List<ItemStack> append(List<ItemStack> first, List<ItemStack> second) {
        List<ItemStack> result = new ArrayList<>(first);
        for (ItemStack stack : second) {
            addOutput(result, stack);
        }
        return result;
    }
}
