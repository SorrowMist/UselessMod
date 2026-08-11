package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts Ender IO SAG Mill recipes into deterministic expected-output batches. */
public final class SagMillingRecipeAdapter implements IRecipeAdapter<SagMillingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double EPSILON = 1.0E-9;

    @Override
    public Class<SagMillingRecipe> getRecipeClass() {
        return SagMillingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EIOBlocks.SAG_MILL.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SagMillingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        SagMillingRecipe source = holder.value();
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        AdvancedAlloyFurnaceRecipe base = convertRecipe(
                AdapterUtils.convertedId(holder.id()),
                source,
                GrindingBallMultipliers.IDENTITY,
                1.0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())));
        if (base != null) {
            result.add(base);
        }

        for (Map.Entry<Item, GrindingBallData> entry : grindingBallData().entrySet()) {
            Item ball = entry.getKey();
            GrindingBallData data = entry.getValue();
            ResourceLocation ballId = BuiltInRegistries.ITEM.getKey(ball);
            if (ballId == null || data == null) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe variant = convertRecipe(
                    grindingBallVariantId(holder.id(), ballId),
                    source,
                    new GrindingBallMultipliers(data.outputMultiplier(), data.bonusMultiplier()),
                    data.powerUse(),
                    List.of(
                            AdapterUtils.toMoldIngredient(getMoldItem()),
                            AdapterUtils.toMoldIngredient(new ItemStack(ball))));
            if (variant != null) {
                result.add(variant);
            }
        }
        return List.copyOf(result);
    }

    /** Package-private seam used by tests to provide deterministic data-map values. */
    static List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SagMillingRecipe> holder, Level level,
            GrindingBallMultipliers grindingBallMultipliers) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = convertRecipe(
                AdapterUtils.convertedId(holder.id()), holder.value(), grindingBallMultipliers,
                1.0, List.of(AdapterUtils.toMoldIngredient(new ItemStack(EIOBlocks.SAG_MILL.get()))));
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<SagMillingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<SagMillingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<SagMillingRecipe> holder : recipeManager.getAllRecipesFor(
                EIORecipes.SAG_MILLING.type().get())) {
            SagMillingRecipe source = holder.value();
            AdvancedAlloyFurnaceRecipe converted = convertRecipe(
                    AdapterUtils.convertedId(holder.id()), source,
                    GrindingBallMultipliers.IDENTITY, 1.0,
                    List.of(AdapterUtils.toMoldIngredient(new ItemStack(EIOBlocks.SAG_MILL.get()))));
            if (converted == null || converted.inputs().isEmpty()) {
                continue;
            }

            CountedIngredient input = converted.inputs().getFirst();
            if (AdapterUtils.hasMatchingIngredient(mergedInputs, input.ingredient(), input.count())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertRecipe(
            ResourceLocation convertedId,
            SagMillingRecipe source,
            @Nullable GrindingBallMultipliers grindingBallMultipliers,
            double powerUse,
            List<Ingredient> molds) {
        if (source == null || source.input() == null || source.input().isEmpty()
                || source.energy() < 0 || !isFiniteNonNegative(powerUse)
                || molds == null || molds.isEmpty()) {
            return null;
        }

        GrindingBallMultipliers multipliers = grindingBallMultipliers == null
                ? GrindingBallMultipliers.IDENTITY
                : grindingBallMultipliers;
        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = scaleOutputs(source, multipliers);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
            LOGGER.warn("Skipping unsupported Ender IO SAG Mill recipe: {}", convertedId);
            return null;
        }

        int operations = scaled.get().operations();
        OptionalInt energy = scaledEnergy(source.energy(), powerUse, operations);
        OptionalInt processTime = ExpectedOutputScaler.multiplyToInt(
                AdapterUtils.DEFAULT_PROCESS_TIME, operations);
        if (energy.isEmpty() || processTime.isEmpty()) {
            LOGGER.warn("Skipping overflowing Ender IO SAG Mill recipe: {}", convertedId);
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                List.of(new CountedIngredient(source.input(), operations)),
                List.<SizedFluidIngredient>of(),
                List.of(),
                scaled.get().outputs(),
                List.of(),
                List.of(),
                energy.getAsInt(),
                processTime.getAsInt(),
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL
        );
    }

    private static Optional<ExpectedOutputScaler.ScaledOutputs> scaleOutputs(
            SagMillingRecipe source, GrindingBallMultipliers grindingBallMultipliers) {
        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        boolean applyOutputMultiplier = source.bonusType().canMultiply();
        boolean applyBonusMultiplier = source.bonusType() == SagMillingRecipe.BonusType.CHANCE_ONLY;
        double outputMultiplier = applyOutputMultiplier
                ? grindingBallMultipliers.outputMultiplier()
                : 1.0;
        double bonusMultiplier = applyBonusMultiplier
                ? grindingBallMultipliers.bonusMultiplier()
                : 1.0;

        if (!isFiniteNonNegative(outputMultiplier) || !isFiniteNonNegative(bonusMultiplier)
                || outputMultiplier > Integer.MAX_VALUE) {
            return Optional.empty();
        }

        for (SagMillingRecipe.OutputItem output : source.outputs()) {
            if (output == null) {
                continue;
            }
            ItemStack stack = output.getItemStack();
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                continue;
            }

            double chance = output.chance();
            if (!Double.isFinite(chance)) {
                return Optional.empty();
            }
            if (applyBonusMultiplier) {
                chance *= bonusMultiplier;
            }
            if (!Double.isFinite(chance)) {
                return Optional.empty();
            }
            chance = clampChance(chance);

            int wholeRolls = (int) Math.floor(outputMultiplier);
            double fractionalRoll = outputMultiplier - wholeRolls;
            if (wholeRolls > 0) {
                long count = (long) stack.getCount() * wholeRolls;
                if (count > Integer.MAX_VALUE) {
                    return Optional.empty();
                }
                weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        stack.copyWithCount((int) count), (int) count, (int) count, chance));
            }

            if (fractionalRoll > EPSILON) {
                double fractionalChance = chance * fractionalRoll;
                if (!Double.isFinite(fractionalChance)) {
                    return Optional.empty();
                }
                weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        stack.copy(), stack.getCount(), stack.getCount(), fractionalChance));
            }
        }

        return ExpectedOutputScaler.scale(weightedOutputs);
    }

    private static Map<Item, GrindingBallData> grindingBallData() {
        Map<Item, GrindingBallData> result = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM.getDataMap(GrindingBallData.DATA_MAP_TYPE)
                .keySet().stream().map(BuiltInRegistries.ITEM::get).toList()) {
            if (item == null) {
                continue;
            }
            GrindingBallData data = item.builtInRegistryHolder().getData(GrindingBallData.DATA_MAP_TYPE);
            if (data != null) {
                result.put(item, data);
            }
        }
        return result;
    }

    private static OptionalInt scaledEnergy(int baseEnergy, double powerUse, int operations) {
        if (baseEnergy < 0 || !isFiniteNonNegative(powerUse) || operations < 0) {
            return OptionalInt.empty();
        }
        double scaled = (double) baseEnergy * powerUse;
        if (!Double.isFinite(scaled) || scaled > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        long perOperation = (long) scaled;
        return ExpectedOutputScaler.multiplyToInt(perOperation, operations);
    }

    private static ResourceLocation grindingBallVariantId(
            ResourceLocation sourceId, ResourceLocation ballId) {
        String suffix = ballId.getNamespace() + "_" + ballId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(
                sourceId.getNamespace(),
                AdapterUtils.convertedId(sourceId).getPath() + "_with_grinding_ball_" + suffix);
    }

    private static double clampChance(double chance) {
        return Math.max(0.0, Math.min(1.0, chance));
    }

    private static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    record GrindingBallMultipliers(double outputMultiplier, double bonusMultiplier) {
        private static final GrindingBallMultipliers IDENTITY = new GrindingBallMultipliers(1.0, 1.0);

        GrindingBallMultipliers {
            if (!isFiniteNonNegative(outputMultiplier) || !isFiniteNonNegative(bonusMultiplier)) {
                throw new IllegalArgumentException("Grinding ball multipliers must be finite and non-negative");
            }
        }
    }
}
