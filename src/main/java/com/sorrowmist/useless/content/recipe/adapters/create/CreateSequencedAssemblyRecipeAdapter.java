package com.sorrowmist.useless.content.recipe.adapters.create;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts a complete Create sequenced assembly into one deterministic recipe. */
public final class CreateSequencedAssemblyRecipeAdapter
        implements IRecipeAdapter<SequencedAssemblyRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<SequencedAssemblyRecipe> getRecipeClass() {
        return SequencedAssemblyRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return CreateRecipeAdapterUtils.isCreateMachineMold(mold);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SequencedAssemblyRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AdvancedAlloyFurnaceRecipe converted = convert(holder.id(), holder.value());
        return converted == null ? List.of() : List.of(converted);
    }

    static AdvancedAlloyFurnaceRecipe convert(
            ResourceLocation originalId, SequencedAssemblyRecipe source) {
        if (originalId == null || source == null) return null;
        int loops = source.getLoops();
        if (loops <= 0 || source.getSequence() == null || source.getSequence().isEmpty()) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Create sequenced assembly recipe: {}", originalId);
            return null;
        }

        Ingredient initial = source.getIngredient();
        if (initial == null || initial.isEmpty()) {
            RateLimitedLogger.warn(LOGGER, "Skipping Create sequenced assembly recipe {} without an initial input", originalId);
            return null;
        }

        Map<Ingredient, Long> itemRequirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(itemRequirements, initial, 1L);
        List<SizedFluidIngredient> fluidRequirements = new ArrayList<>();
        List<Ingredient> molds = new ArrayList<>();
        long totalTime = 0L;
        long totalEnergy = 0L;
        int validSteps = 0;

        for (SequencedRecipe<?> wrapper : source.getSequence()) {
            if (wrapper == null || wrapper.getRecipe() == null) {
                RateLimitedLogger.warn(LOGGER, "Skipping invalid step in Create sequenced assembly recipe {}", originalId);
                return null;
            }
            try {
                ProcessingRecipe<?> step = wrapper.getRecipe();
                IAssemblyRecipe assembly = wrapper.getAsAssemblyRecipe();
                if (!assembly.supportsAssembly()) {
                    RateLimitedLogger.warn(LOGGER, "Skipping Create assembly step that does not support assembly in {}", originalId);
                    return null;
                }
                List<Ingredient> stepIngredients = new ArrayList<>();
                assembly.addAssemblyIngredients(stepIngredients);
                List<com.simibubi.create.foundation.fluid.FluidIngredient> nativeStepFluids = new ArrayList<>();
                assembly.addAssemblyFluidIngredients(nativeStepFluids);
                List<SizedFluidIngredient> stepFluids = new ArrayList<>();
                for (var fluid : nativeStepFluids) {
                    var convertedFluid = CreateRecipeAdapterUtils.nativeFluid(fluid);
                    if (convertedFluid == null) return null;
                    stepFluids.add(convertedFluid);
                }
                Set<ItemLike> machines = new LinkedHashSet<>();
                assembly.addRequiredMachines(machines);
                if (machines.isEmpty()) {
                    RateLimitedLogger.warn(LOGGER, "Skipping Create assembly step without a required machine in {}", originalId);
                    return null;
                }

                List<Ingredient> convertedStepIngredients = new ArrayList<>();
                for (Ingredient ingredient : stepIngredients) {
                    if (ingredient == null || ingredient.isEmpty()) {
                        RateLimitedLogger.warn(LOGGER, "Skipping empty material declaration in Create assembly step of {}", originalId);
                        return null;
                    }
                    convertedStepIngredients.add(ingredient);
                }

                if (step instanceof ItemApplicationRecipe application) {
                    Ingredient tool = application.getRequiredHeldItem();
                    if (tool == null || tool.isEmpty()) {
                        RateLimitedLogger.warn(LOGGER, "Skipping empty deployer tool declaration in {}", originalId);
                    } else if (application.shouldKeepHeldItem()) {
                        removeOneIngredient(convertedStepIngredients, tool);
                        CreateRecipeAdapterUtils.addUniqueMold(molds, tool);
                    } else if (convertedStepIngredients.stream()
                            .noneMatch(ingredient -> AdapterUtils.areIngredientsEqual(ingredient, tool))) {
                        convertedStepIngredients.add(tool);
                    }
                }

                Map<Ingredient, Long> stepItems = new LinkedHashMap<>();
                for (Ingredient ingredient : convertedStepIngredients) {
                    AdapterUtils.mergeIngredient(stepItems, ingredient, 1L);
                }
                List<SizedFluidIngredient> stepFluidsWithLoops = new ArrayList<>();
                for (SizedFluidIngredient fluid : stepFluids) {
                    if (fluid == null || fluid.ingredient() == null || fluid.ingredient().isEmpty()
                            || fluid.amount() <= 0) {
                        RateLimitedLogger.warn(LOGGER, "Skipping empty fluid declaration in Create assembly step of {}", originalId);
                        return null;
                    }
                    long amount = Math.multiplyExact((long) fluid.amount(), loops);
                    if (amount > Integer.MAX_VALUE) {
                        RateLimitedLogger.warn(LOGGER, "Skipping overflowing fluid declaration in Create assembly recipe {}", originalId);
                        return null;
                    }
                    stepFluidsWithLoops.add(new SizedFluidIngredient(fluid.ingredient(), (int) amount));
                }

                int stepTime = step.getProcessingDuration();
                if (stepTime < 0) {
                    RateLimitedLogger.warn(LOGGER, "Skipping negative processing time declaration in {}", originalId);
                    return null;
                }
                if (stepTime == 0) stepTime = AdapterUtils.DEFAULT_PROCESS_TIME;
                long stepTotalTime = Math.multiplyExact((long) stepTime, loops);
                long newTotalTime = Math.addExact(totalTime, stepTotalTime);
                long stepEnergy = Math.multiplyExact((long) AdapterUtils.DEFAULT_ENERGY, loops);
                long newTotalEnergy = Math.addExact(totalEnergy, stepEnergy);

                for (Map.Entry<Ingredient, Long> entry : stepItems.entrySet()) {
                    long amount = Math.multiplyExact(entry.getValue(), loops);
                    AdapterUtils.mergeIngredient(itemRequirements, entry.getKey(), amount);
                }
                fluidRequirements.addAll(stepFluidsWithLoops);
                for (ItemLike machine : machines) {
                    if (machine != null) CreateRecipeAdapterUtils.addUniqueMold(molds, Ingredient.of(machine));
                }
                totalTime = newTotalTime;
                totalEnergy = newTotalEnergy;
                validSteps++;
            } catch (RuntimeException exception) {
                RateLimitedLogger.warn(LOGGER, "Skipping unsupported Create assembly step in {}", originalId, exception);
            }
        }

        double totalWeight = 0.0D;
        if (source.resultPool == null || source.resultPool.isEmpty()) return null;
        for (var result : source.resultPool) {
            if (result == null || result.getStack().isEmpty() || !Float.isFinite(result.getChance())
                    || result.getChance() < 0) return null;
            totalWeight += result.getChance();
        }
        if (!(totalWeight > 0) || !Double.isFinite(totalWeight)) return null;
        var weighted = new ArrayList<com.sorrowmist.useless.content.recipe.ExpectedOutputScaler.WeightedItemOutput>();
        for (var result : source.resultPool) {
            weighted.add(new com.sorrowmist.useless.content.recipe.ExpectedOutputScaler.WeightedItemOutput(
                    result.getStack(), result.getStack().getCount(), result.getStack().getCount(),
                    result.getChance() / totalWeight));
        }
        var scaled = com.sorrowmist.useless.content.recipe.ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty()) return null;
        int batch = scaled.get().operations();
        List<ItemStack> outputs = scaled.get().outputs();
        var inputs = CreateRecipeAdapterUtils.scaleItemInputs(itemRequirements, batch, originalId, LOGGER);
        var batchFluids = CreateRecipeAdapterUtils.scaleFluidInputs(fluidRequirements, batch, originalId, LOGGER);
        if (inputs == null || batchFluids == null) return null;
        fluidRequirements = batchFluids;
        try {
            totalTime = Math.multiplyExact(totalTime, batch);
            totalEnergy = Math.multiplyExact(totalEnergy, batch);
        } catch (ArithmeticException overflow) {
            return null;
        }

        if (validSteps == 0 || molds.isEmpty() || totalTime <= 0 || outputs.isEmpty()) {
            RateLimitedLogger.warn(LOGGER, "Skipping Create sequenced assembly recipe {} without convertible steps/results", originalId);
            return null;
        }

        if (inputs.isEmpty() && fluidRequirements.isEmpty()) {
            RateLimitedLogger.warn(LOGGER, "Skipping Create sequenced assembly recipe {} without valid inputs", originalId);
            return null;
        }
        if (totalTime > Integer.MAX_VALUE) {
            RateLimitedLogger.warn(LOGGER, "Skipping overflowing Create sequenced assembly recipe: {}", originalId);
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                inputs,
                fluidRequirements,
                List.of(),
                outputs,
                List.of(),
                List.of(),
                totalEnergy,
                (int) totalTime,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    private static void removeOneIngredient(List<Ingredient> ingredients, Ingredient target) {
        for (int i = 0; i < ingredients.size(); i++) {
            if (AdapterUtils.areIngredientsEqual(ingredients.get(i), target)) {
                ingredients.remove(i);
                return;
            }
        }
    }

    private static boolean mergeOutput(List<ItemStack> outputs, ItemStack output) {
        for (ItemStack existing : outputs) {
            if (ItemStack.isSameItemSameTags(existing, output)) {
                long count = (long) existing.getCount() + output.getCount();
                if (count > Integer.MAX_VALUE) return false;
                existing.setCount((int) count);
                return true;
            }
        }
        outputs.add(output.copy());
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<SequencedAssemblyRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<SequencedAssemblyRecipe>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipes(level.getRecipeManager())) {
            if (!(holder.value() instanceof SequencedAssemblyRecipe source)) continue;
            AdvancedAlloyFurnaceRecipe converted = convert(holder.id(), source);
            if (converted != null && CreateRecipeAdapterUtils.matchesConverted(
                    converted, mergedInputs, mergedFluids)
                    && converted.molds().stream().anyMatch(moldIngredient ->
                    moldIngredient != null && !moldIngredient.isEmpty()
                            && AdapterUtils.matchesMold(moldIngredient, mold))) {
                result.add((RecipeHolder<SequencedAssemblyRecipe>) (RecipeHolder<?>) holder);
            }
        }
        return result;
    }
}
