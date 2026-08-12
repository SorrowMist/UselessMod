package com.sorrowmist.useless.content.recipe.adapters.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
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
            LOGGER.warn("Skipping invalid Create sequenced assembly recipe: {}", originalId);
            return null;
        }

        Ingredient initial = source.getIngredient();
        if (initial == null || initial.isEmpty()) {
            LOGGER.warn("Skipping Create sequenced assembly recipe {} without an initial input", originalId);
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
                LOGGER.warn("Skipping invalid step in Create sequenced assembly recipe {}", originalId);
                continue;
            }
            try {
                ProcessingRecipe<?, ?> step = wrapper.getRecipe();
                IAssemblyRecipe assembly = wrapper.getAsAssemblyRecipe();
                if (!assembly.supportsAssembly()) {
                    LOGGER.warn("Skipping Create assembly step that does not support assembly in {}", originalId);
                    continue;
                }
                List<Ingredient> stepIngredients = new ArrayList<>();
                assembly.addAssemblyIngredients(stepIngredients);
                List<SizedFluidIngredient> stepFluids = new ArrayList<>();
                assembly.addAssemblyFluidIngredients(stepFluids);
                Set<ItemLike> machines = new LinkedHashSet<>();
                assembly.addRequiredMachines(machines);
                if (machines.isEmpty()) {
                    LOGGER.warn("Skipping Create assembly step without a required machine in {}", originalId);
                    continue;
                }

                List<Ingredient> convertedStepIngredients = new ArrayList<>();
                for (Ingredient ingredient : stepIngredients) {
                    if (ingredient == null || ingredient.isEmpty()) {
                        LOGGER.warn("Skipping empty material declaration in Create assembly step of {}", originalId);
                        continue;
                    }
                    convertedStepIngredients.add(ingredient);
                }

                if (step instanceof ItemApplicationRecipe application) {
                    Ingredient tool = application.getRequiredHeldItem();
                    if (tool == null || tool.isEmpty()) {
                        LOGGER.warn("Skipping empty deployer tool declaration in {}", originalId);
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
                        LOGGER.warn("Skipping empty fluid declaration in Create assembly step of {}", originalId);
                        continue;
                    }
                    long amount = Math.multiplyExact((long) fluid.amount(), loops);
                    if (amount > Integer.MAX_VALUE) {
                        LOGGER.warn("Skipping overflowing fluid declaration in Create assembly recipe {}", originalId);
                        continue;
                    }
                    stepFluidsWithLoops.add(new SizedFluidIngredient(fluid.ingredient(), (int) amount));
                }

                int stepTime = step.getProcessingDuration();
                if (stepTime < 0) {
                    LOGGER.warn("Skipping negative processing time declaration in {}", originalId);
                    continue;
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
                LOGGER.warn("Skipping unsupported Create assembly step in {}", originalId, exception);
            }
        }

        List<ItemStack> outputs = new ArrayList<>();
        if (source.resultPool != null) {
            for (var result : source.resultPool) {
                if (result == null || result.getStack().isEmpty() || result.getStack().getCount() <= 0) {
                    LOGGER.warn("Skipping invalid result-pool declaration in {}", originalId);
                    continue;
                }
                if (!mergeOutput(outputs, result.getStack())) {
                    LOGGER.warn("Skipping overflowing result-pool declaration in {}", originalId);
                }
            }
        }

        if (validSteps == 0 || molds.isEmpty() || totalTime <= 0 || outputs.isEmpty()) {
            LOGGER.warn("Skipping Create sequenced assembly recipe {} without convertible steps/results", originalId);
            return null;
        }

        List<CountedIngredient> inputs = itemRequirements.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() > 0)
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        if (inputs.isEmpty() && fluidRequirements.isEmpty()) {
            LOGGER.warn("Skipping Create sequenced assembly recipe {} without valid inputs", originalId);
            return null;
        }
        if (totalTime > Integer.MAX_VALUE) {
            LOGGER.warn("Skipping overflowing Create sequenced assembly recipe: {}", originalId);
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
            if (ItemStack.isSameItemSameComponents(existing, output)) {
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
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
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
