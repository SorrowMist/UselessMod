package com.sorrowmist.useless.content.recipe.adapters.create;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Shared conversion rules for the optional Create adapters. */
final class CreateRecipeAdapterUtils {
    private static final Set<String> MACHINE_MOLD_PATHS = Set.of(
            "spout",
            "mechanical_press",
            "mechanical_mixer",
            "basin",
            "millstone",
            "crushing_wheel",
            "encased_fan",
            "mechanical_crafter",
            "deployer",
            "mechanical_saw",
            "blaze_burner"
    );

    private CreateRecipeAdapterUtils() {
    }

    static boolean isCreateMachineMold(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "create".equals(id.getNamespace()) && MACHINE_MOLD_PATHS.contains(id.getPath());
    }

    static boolean isCreateMold(@Nullable ItemStack stack, String path) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "create".equals(id.getNamespace()) && path.equals(id.getPath());
    }

    static SizedFluidIngredient nativeFluid(com.simibubi.create.foundation.fluid.FluidIngredient source) {
        if (source == null || source.getRequiredAmount() <= 0) return null;
        com.google.gson.JsonObject json;
        try {
            json = source.serialize().deepCopy();
        } catch (NullPointerException networkTag) {
            // Create sends tag members, not the tag ID, to clients in 1.20.1.
            if (!(source instanceof com.simibubi.create.foundation.fluid.FluidIngredient.FluidTagIngredient)) throw networkTag;
            var choices = source.getMatchingFluidStacks().stream()
                    .map(com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient::single).toList();
            if (choices.isEmpty()) return null;
            return new SizedFluidIngredient(com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.any(choices),
                    source.getRequiredAmount());
        }
        if (json.has("fluidTag")) json.add("tag", json.remove("fluidTag"));
        if (json.has("nbt")) json.addProperty("type", "useless_mod:merge_nbt_fluid");
        return new SizedFluidIngredient(com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.fromJson(json),
                source.getRequiredAmount());
    }

    static boolean supportsProcessing(ProcessingRecipe<?> recipe) {
        return recipe instanceof FillingRecipe
                || recipe instanceof PressingRecipe
                || recipe instanceof CompactingRecipe
                || recipe instanceof MixingRecipe
                || recipe instanceof MillingRecipe
                || recipe instanceof CrushingRecipe
                || recipe instanceof HauntingRecipe
                || recipe instanceof SplashingRecipe;
    }

    static List<Ingredient> processingMolds(ProcessingRecipe<?> recipe) {
        List<Ingredient> molds = new ArrayList<>();
        if (recipe instanceof FillingRecipe) {
            addBlockMold(molds, "spout");
        } else if (recipe instanceof PressingRecipe) {
            addBlockMold(molds, "mechanical_press");
        } else if (recipe instanceof CompactingRecipe) {
            addBlockMold(molds, "mechanical_press");
            addBlockMold(molds, "basin");
        } else if (recipe instanceof MixingRecipe) {
            addBlockMold(molds, "mechanical_mixer");
            addBlockMold(molds, "basin");
        } else if (recipe instanceof MillingRecipe) {
            addBlockMold(molds, "millstone");
        } else if (recipe instanceof CrushingRecipe) {
            addBlockMold(molds, "crushing_wheel");
        } else if (recipe instanceof HauntingRecipe || recipe instanceof SplashingRecipe) {
            addBlockMold(molds, "encased_fan");
        } else {
            return List.of();
        }

        if (recipe instanceof BasinRecipe basin
                && basin.getRequiredHeat() != HeatCondition.NONE) {
            addBlockMold(molds, "blaze_burner");
        }
        return List.copyOf(molds);
    }

    static Ingredient blockMold(@Nullable ItemLike block) {
        if (block == null || block.asItem() == Items.AIR) return Ingredient.EMPTY;
        return Ingredient.of(block);
    }

    static Block createBlock(String path) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation("create", path));
    }

    static ItemStack createBlockItem(String path) {
        Block block = createBlock(path);
        return block == null || block == Blocks.AIR || block.asItem() == Items.AIR
                ? ItemStack.EMPTY
                : block.asItem().getDefaultInstance();
    }

    private static void addBlockMold(List<Ingredient> molds, String path) {
        Ingredient mold = blockMold(createBlock(path));
        if (!mold.isEmpty()) molds.add(mold);
    }

    static boolean hasMoldForProcessing(ProcessingRecipe<?> recipe, @Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        return processingMolds(recipe).stream().anyMatch(required -> AdapterUtils.matchesMold(required, mold));
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe convertProcessing(
            ResourceLocation originalId, ProcessingRecipe<?> source,
            List<Ingredient> molds, Logger logger) {
        if (source == null || originalId == null || molds == null || molds.isEmpty()) return null;
        if (!supportsProcessing(source)) return null;

        int declaredTime = source.getProcessingDuration();
        if (declaredTime < 0) {
            RateLimitedLogger.warn(logger, "Skipping Create recipe {} with a negative processing time", originalId);
            return null;
        }
        int baseTime = declaredTime > 0 ? declaredTime : AdapterUtils.DEFAULT_PROCESS_TIME;

        Map<Ingredient, Long> itemRequirements = new LinkedHashMap<>();
        if (source.getIngredients() != null) {
            for (Ingredient ingredient : source.getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) {
                    RateLimitedLogger.warn(logger, "Skipping empty Create item input declaration in {}", originalId);
                    return null;
                }
                AdapterUtils.mergeIngredient(itemRequirements, ingredient, 1L);
            }
        }

        List<SizedFluidIngredient> sourceFluidInputs = new ArrayList<>();
        if (source.getFluidIngredients() != null) {
            for (var nativeIngredient : source.getFluidIngredients()) {
                SizedFluidIngredient ingredient = CreateRecipeAdapterUtils.nativeFluid(nativeIngredient);
                if (ingredient == null || ingredient.ingredient() == null
                        || ingredient.ingredient().isEmpty() || ingredient.amount() <= 0) {
                    RateLimitedLogger.warn(logger, "Skipping empty Create fluid input declaration in {}", originalId);
                    return null;
                }
                sourceFluidInputs.add(ingredient);
            }
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        if (source.getRollableResults() != null) {
            for (ProcessingOutput output : source.getRollableResults()) {
                if (output == null || output.getStack().isEmpty() || output.getStack().getCount() <= 0
                        || !Float.isFinite(output.getChance())) {
                    RateLimitedLogger.warn(logger, "Skipping invalid Create item output declaration in {}", originalId);
                    return null;
                }
                ItemStack stack = output.getStack();
                weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        stack, stack.getCount(), stack.getCount(), output.getChance()));
            }
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(weightedOutputs);
        if (scaled.isEmpty()) {
            RateLimitedLogger.warn(logger, "Skipping Create recipe {} because its item output batch cannot be represented", originalId);
            return null;
        }
        int operations = scaled.get().operations();

        List<CountedIngredient> inputs = scaleItemInputs(itemRequirements, operations, originalId, logger);
        List<SizedFluidIngredient> inputFluids = scaleFluidInputs(
                sourceFluidInputs, operations, originalId, logger);
        List<FluidStack> outputFluids = scaleFluidOutputs(
                source.getFluidResults(), operations, originalId, logger);
        if (inputs == null || inputFluids == null || outputFluids == null) return null;
        if (inputs.isEmpty() && inputFluids.isEmpty()) {
            RateLimitedLogger.warn(logger, "Skipping Create recipe {} because it has no valid inputs", originalId);
            return null;
        }
        if (scaled.get().outputs().isEmpty() && outputFluids.isEmpty()) {
            RateLimitedLogger.warn(logger, "Skipping Create recipe {} because it has no valid outputs", originalId);
            return null;
        }

        OptionalLong energy = multiplyToLong(AdapterUtils.DEFAULT_ENERGY, operations);
        OptionalLong processTime = multiplyToLong(baseTime, operations);
        if (energy.isEmpty() || processTime.isEmpty() || processTime.getAsLong() > Integer.MAX_VALUE) {
            RateLimitedLogger.warn(logger, "Skipping overflowing Create recipe {}", originalId);
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                inputs,
                inputFluids,
                List.of(),
                scaled.get().outputs(),
                outputFluids,
                List.of(),
                energy.getAsLong(),
                (int) processTime.getAsLong(),
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL
        );
    }

    static List<CountedIngredient> scaleItemInputs(
            Map<Ingredient, Long> requirements, long operations,
            ResourceLocation id, Logger logger) {
        Map<Ingredient, Long> scaled = new LinkedHashMap<>();
        for (Map.Entry<Ingredient, Long> entry : requirements.entrySet()) {
            OptionalLong count = multiplyToLong(entry.getValue(), operations);
            if (count.isEmpty() || count.getAsLong() <= 0) {
                RateLimitedLogger.warn(logger, "Skipping overflowing Create item input declaration in {}", id);
                return null;
            }
            AdapterUtils.mergeIngredient(scaled, entry.getKey(), count.getAsLong());
        }
        return scaled.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    static List<SizedFluidIngredient> scaleFluidInputs(
            List<SizedFluidIngredient> inputs, long operations,
            ResourceLocation id, Logger logger) {
        List<SizedFluidIngredient> result = new ArrayList<>();
        for (SizedFluidIngredient input : inputs) {
            OptionalLong amount = multiplyToLong(input.amount(), operations);
            if (amount.isEmpty() || amount.getAsLong() <= 0 || amount.getAsLong() > Integer.MAX_VALUE) {
                RateLimitedLogger.warn(logger, "Skipping overflowing Create fluid input declaration in {}", id);
                return null;
            }
            result.add(new SizedFluidIngredient(input.ingredient(), (int) amount.getAsLong()));
        }
        return List.copyOf(result);
    }

    static List<FluidStack> scaleFluidOutputs(
            @Nullable List<FluidStack> outputs, long operations,
            ResourceLocation id, Logger logger) {
        if (outputs == null || outputs.isEmpty()) return List.of();
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack output : outputs) {
            if (output == null || output.isEmpty() || output.getAmount() <= 0) {
                RateLimitedLogger.warn(logger, "Skipping empty Create fluid output declaration in {}", id);
                return null;
            }
            OptionalLong amount = multiplyToLong(output.getAmount(), operations);
            if (amount.isEmpty() || amount.getAsLong() <= 0 || amount.getAsLong() > Integer.MAX_VALUE) {
                RateLimitedLogger.warn(logger, "Skipping overflowing Create fluid output declaration in {}", id);
                return null;
            }
            result.add(new net.minecraftforge.fluids.FluidStack(output, (int) amount.getAsLong()));
        }
        return List.copyOf(result);
    }

    static OptionalLong multiplyToLong(long left, long right) {
        if (left < 0 || right < 0) return OptionalLong.empty();
        try {
            return OptionalLong.of(Math.multiplyExact(left, right));
        } catch (ArithmeticException exception) {
            return OptionalLong.empty();
        }
    }

    static void addUniqueMold(List<Ingredient> molds, Ingredient mold) {
        if (mold == null || mold.isEmpty()) return;
        if (molds.stream().noneMatch(existing -> AdapterUtils.areIngredientsEqual(existing, mold))) {
            molds.add(mold);
        }
    }

    static List<Ingredient> machinesToMolds(Set<ItemLike> machines) {
        List<Ingredient> molds = new ArrayList<>();
        for (ItemLike machine : new LinkedHashSet<>(machines)) {
            if (machine != null) addUniqueMold(molds, Ingredient.of(machine));
        }
        return molds;
    }

    static boolean matchesConverted(
            AdvancedAlloyFurnaceRecipe recipe,
            Map<Ingredient, Long> mergedInputs,
            @Nullable Map<FluidStack, Long> mergedFluids) {
        if (recipe == null) return false;
        Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input != null && input.ingredient() != null && input.count() > 0) {
                AdapterUtils.mergeIngredient(requiredItems, input.ingredient(), input.count());
            }
        }
        return AdapterUtils.matchesRequired(mergedInputs == null ? Map.of() : mergedInputs, requiredItems)
                && AdapterUtils.matchesLongFluidIngredients(
                mergedFluids == null ? Map.of() : mergedFluids, recipe.inputFluids());
    }
}
