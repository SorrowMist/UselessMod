package com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte;

import com.jdte.common.recipes.BioFactoryInput;
import com.jdte.common.recipes.BioFactoryOutput;
import com.jdte.common.recipes.BioFactoryRecipe;
import com.jdte.setup.JDTEBlocks;
import com.jdte.setup.JDTEConfig;
import com.jdte.setup.JDTEFluids;
import com.jdte.setup.JDTERecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts JDTE Bio Factory recipes into alloy-furnace recipes. */
public final class BioFactoryRecipeAdapter implements IRecipeAdapter<BioFactoryRecipe> {
    private static final ResourceLocation TIME_FLUID_ID =
            ResourceLocation.fromNamespaceAndPath("justdirethings", "time_fluid_source");
    private static final int WATER_PER_CYCLE = 1000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<BioFactoryRecipe> getRecipeClass() {
        return BioFactoryRecipe.class;
    }

    /** The reusable specimen is the recipe-specific mold; no item is consumed for it. */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(
            RecipeHolder<BioFactoryRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        BioFactoryRecipe source = holder.value();
        Ingredient specimen = source.specimen();
        if (specimen == null || specimen.isEmpty()) return null;

        InputParts inputParts = splitInputs(source.inputs());
        if (inputParts == null) return null;

        Optional<ExpectedOutputScaler.ScaledOutputs> scaledOutputs = scaleOutputs(source.outputs());
        if (scaledOutputs.isEmpty()) return null;

        int operations = scaledOutputs.get().operations();
        OptionalInt energy = ExpectedOutputScaler.multiplyToInt(
                Math.max(0, source.energy()), operations);
        OptionalInt processTime = ExpectedOutputScaler.multiplyToInt(
                Math.max(1, source.processTicks()), operations);
        if (energy.isEmpty() || processTime.isEmpty()) return null;

        FluidStack processFluid = resolveFluid(source.processFluid(), source.processFluidAmount());
        if (source.processFluid() != null && source.processFluid().isPresent()
                && source.processFluidAmount() > 0 && processFluid == null) {
            return null;
        }
        FluidStack outputFluid = resolveFluid(source.outputFluid(), source.outputFluidAmount());
        if (source.outputFluid() != null && source.outputFluid().isPresent()
                && source.outputFluidAmount() > 0 && outputFluid == null) {
            return null;
        }
        if (scaledOutputs.get().outputs().isEmpty() && outputFluid == null) return null;

        int timeFluidAmount = Math.max(0, JDTEConfig.COMMON.bioFactoryTimeFluidPerCycle.get());
        int lifeFluidAmount = Math.max(0, JDTEConfig.COMMON.bioFactoryLifeFluidPerCycle.get());
        FluidStack timeFluid = resolveFluid(Optional.of(TIME_FLUID_ID), timeFluidAmount);
        FluidStack lifeFluid = lifeFluidAmount <= 0
                ? null : new FluidStack(JDTEFluids.LIFE_FLUID_SOURCE.get(), lifeFluidAmount);
        if (timeFluidAmount > 0 && timeFluid == null) return null;

        List<LongSizedFluidIngredient> inputFluids = inputFluids(
                processFluid, timeFluid, lifeFluid,
                new FluidStack(Fluids.WATER, WATER_PER_CYCLE), operations);
        if (inputFluids == null) return null;

        OptionalInt scaledOutputAmount = outputFluid == null
                ? OptionalInt.empty() : scaleAmount(outputFluid.getAmount(), operations);
        if (outputFluid != null && scaledOutputAmount.isEmpty()) return null;
        List<FluidStack> outputFluids = outputFluid == null ? List.of() : List.of(
                outputFluid.copyWithAmount(scaledOutputAmount.getAsInt()));

        List<CountedIngredient> scaledInputs = scaleInputs(inputParts.consumed(), operations);
        if (scaledInputs == null) return null;

        List<Ingredient> molds = new ArrayList<>();
        molds.add(AdapterUtils.toMoldIngredient(new ItemStack(JDTEBlocks.BIO_FACTORY.get())));
        molds.add(specimen);
        molds.addAll(inputParts.reusable());

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                scaledInputs,
                inputFluids,
                List.of(),
                scaledOutputs.get().outputs(),
                outputFluids,
                List.of(),
                energy.getAsInt(),
                processTime.getAsInt(),
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<RecipeHolder<BioFactoryRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        Map<Ingredient, Long> availableInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<BioFactoryRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BioFactoryRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(JDTERecipes.BIO_FACTORY_RECIPE_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = convert(holder, level);
            if (converted == null
                    // This lookup has one legacy mold argument. Use the dynamic specimen to
                    // select the source recipe; the complete machine + specimen requirement is
                    // enforced by the multiblock mold hub for the converted recipe.
                    || !AdapterUtils.matchesMold(holder.value().specimen(), mold)
                    || !AdapterUtils.matchesRequired(availableInputs, requiredInputs(converted))
                    || !FluidIngredientAllocator.matchesLong(
                    converted.inputFluids(), availableFluids, 1L)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static InputParts splitInputs(@Nullable List<BioFactoryInput> sourceInputs) {
        if (sourceInputs == null || sourceInputs.isEmpty()) {
            return new InputParts(List.of(), List.of());
        }

        List<CountedIngredient> consumed = new ArrayList<>();
        List<Ingredient> reusable = new ArrayList<>();
        for (BioFactoryInput input : sourceInputs) {
            if (input == null) return null;
            if (input.ingredient() == null || input.ingredient().isEmpty() || input.count() < 0) {
                return null;
            }
            if (input.count() == 0) {
                reusable.add(input.ingredient());
            } else {
                consumed.add(new CountedIngredient(input.ingredient(), input.count()));
            }
        }
        return new InputParts(List.copyOf(consumed), List.copyOf(reusable));
    }

    @Nullable
    private static List<LongSizedFluidIngredient> inputFluids(
            @Nullable FluidStack processFluid, @Nullable FluidStack timeFluid,
            @Nullable FluidStack lifeFluid, FluidStack water, int operations) {
        List<FluidRequirement> requirements = new ArrayList<>();
        if (!addFluidRequirement(requirements, processFluid, operations)
                || !addFluidRequirement(requirements, timeFluid, operations)
                || !addFluidRequirement(requirements, lifeFluid, operations)
                || !addFluidRequirement(requirements, water, operations)) {
            return null;
        }

        List<LongSizedFluidIngredient> result = new ArrayList<>(requirements.size());
        for (FluidRequirement requirement : requirements) {
            result.add(new LongSizedFluidIngredient(
                    FluidIngredient.single(requirement.stack()), requirement.amount()));
        }
        return List.copyOf(result);
    }

    private static boolean addFluidRequirement(
            List<FluidRequirement> requirements, @Nullable FluidStack fluid, int operations) {
        if (fluid == null || fluid.isEmpty() || fluid.getAmount() <= 0) return true;

        long amount = (long) fluid.getAmount() * operations;
        if (amount <= 0) return false;
        for (int index = 0; index < requirements.size(); index++) {
            FluidRequirement existing = requirements.get(index);
            if (!FluidStack.isSameFluidSameComponents(existing.stack(), fluid)) continue;
            try {
                requirements.set(index, new FluidRequirement(
                        existing.stack(), Math.addExact(existing.amount(), amount)));
            } catch (ArithmeticException exception) {
                return false;
            }
            return true;
        }

        requirements.add(new FluidRequirement(fluid.copyWithAmount(1), amount));
        return true;
    }

    @Nullable
    private static List<CountedIngredient> scaleInputs(
            List<CountedIngredient> inputs, int operations) {
        if (operations == 1 || inputs.isEmpty()) return inputs;

        List<CountedIngredient> result = new ArrayList<>(inputs.size());
        for (CountedIngredient input : inputs) {
            long count;
            try {
                count = Math.multiplyExact(input.count(), (long) operations);
            } catch (ArithmeticException exception) {
                return null;
            }
            result.add(new CountedIngredient(input.ingredient(), count));
        }
        return List.copyOf(result);
    }

    private static Map<Ingredient, Long> requiredInputs(AdvancedAlloyFurnaceRecipe recipe) {
        java.util.LinkedHashMap<Ingredient, Long> required = new java.util.LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input != null && input.ingredient() != null && input.count() > 0) {
                AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
            }
        }
        return required;
    }

    private static Optional<ExpectedOutputScaler.ScaledOutputs> scaleOutputs(
            @Nullable List<BioFactoryOutput> sourceOutputs) {
        if (sourceOutputs == null || sourceOutputs.isEmpty()) {
            return Optional.of(new ExpectedOutputScaler.ScaledOutputs(1, List.of()));
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        for (BioFactoryOutput output : sourceOutputs) {
            if (output == null || output.stack() == null || output.stack().isEmpty()
                    || output.stack().getCount() <= 0) {
                continue;
            }
            weighted.add(new ExpectedOutputScaler.WeightedItemOutput(
                    output.stack().copy(), output.stack().getCount(), output.stack().getCount(),
                    output.chance()));
        }
        return ExpectedOutputScaler.scale(weighted);
    }

    @Nullable
    private static FluidStack resolveFluid(
            @Nullable Optional<ResourceLocation> id, int amount) {
        if (id == null || id.isEmpty() || amount <= 0) return null;
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id.get()).orElse(null);
        if (fluid == null || fluid == Fluids.EMPTY) return null;
        return new FluidStack(fluid, amount);
    }

    private static OptionalInt scaleAmount(int amount, int operations) {
        long scaled = (long) amount * operations;
        return scaled <= 0 || scaled > Integer.MAX_VALUE
                ? OptionalInt.empty() : OptionalInt.of((int) scaled);
    }

    private record InputParts(List<CountedIngredient> consumed, List<Ingredient> reusable) {
    }

    private record FluidRequirement(FluidStack stack, long amount) {
    }
}
