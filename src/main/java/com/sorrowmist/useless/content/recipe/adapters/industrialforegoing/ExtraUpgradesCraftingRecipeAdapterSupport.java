package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.yxiao233.ifeu.common.config.machine.PrecisionCraftingTableConfig;
import net.yxiao233.ifeu.common.registry.IFEUContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared conversion and matching logic for Extra Upgrades crafting tables. */
final class ExtraUpgradesCraftingRecipeAdapterSupport {
    private static final int PRECISION_PROCESS_TIME = 200;

    private ExtraUpgradesCraftingRecipeAdapterSupport() {
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe precisionRecipe(
            net.minecraft.resources.ResourceLocation id, List<Ingredient> sourceInputs,
            ItemStack sourceOutput, float chance, ItemStack table) {
        if (sourceInputs == null || sourceOutput == null || sourceOutput.isEmpty()
                || sourceOutput.getCount() <= 0 || !Float.isFinite(chance) || chance <= 0.0F) {
            return null;
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(
                List.of(new ExpectedOutputScaler.WeightedItemOutput(
                        sourceOutput, sourceOutput.getCount(), sourceOutput.getCount(), chance)));
        if (scaled.isEmpty() || scaled.get().operations() <= 0 || scaled.get().outputs().isEmpty()) {
            return null;
        }

        int operations = scaled.get().operations();
        List<CountedIngredient> inputs = countedInputs(sourceInputs, operations);
        Long processTime = multiply(PRECISION_PROCESS_TIME, operations);
        Long energy = multiply(
                IndustrialForegoingRecipeAdapterUtils.energyPerTick(
                        PrecisionCraftingTableConfig.powerPerTick, PRECISION_PROCESS_TIME),
                operations);
        if (processTime == null || processTime <= 0L || processTime > Integer.MAX_VALUE
                || energy == null || energy < 0L) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(),
                scaled.get().outputs(),
                List.of(),
                List.of(),
                energy,
                processTime.intValue(),
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(table)),
                AlloyFurnaceMode.NORMAL);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe fluidRecipe(
            net.minecraft.resources.ResourceLocation id, List<Ingredient> sourceInputs,
            FluidStack sourceFluid, ItemStack sourceOutput, ItemStack table) {
        if (sourceFluid == null || sourceFluid.isEmpty() || sourceFluid.getAmount() <= 0
                || sourceOutput == null || sourceOutput.isEmpty() || sourceOutput.getCount() <= 0) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                id,
                countedInputs(sourceInputs, 1),
                List.of(LongSizedFluidIngredient.from(sourceFluid)),
                List.of(),
                List.of(sourceOutput.copy()),
                List.of(),
                List.of(),
                0L,
                1,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(table)),
                AlloyFurnaceMode.NORMAL);
    }

    static boolean matches(
            AdvancedAlloyFurnaceRecipe recipe,
            @Nullable Map<Ingredient, Long> mergedInputs,
            @Nullable Map<FluidStack, Long> mergedFluids) {
        if (recipe == null) return false;

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input != null && input.ingredient() != null && !input.ingredient().isEmpty()
                    && input.count() > 0L) {
                AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count());
            }
        }
        return AdapterUtils.matchesRequired(
                mergedInputs == null ? Map.of() : mergedInputs, requirements)
                && FluidIngredientAllocator.matchesLong(
                recipe.inputFluids(), mergedFluids == null ? Map.of() : mergedFluids, 1L);
    }

    private static List<CountedIngredient> countedInputs(
            List<Ingredient> sourceInputs, int multiplier) {
        if (sourceInputs == null || sourceInputs.isEmpty() || multiplier <= 0) return List.of();

        Map<Ingredient, Long> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : sourceInputs) {
            if (ingredient == null || ingredient.isEmpty() || isAirPlaceholder(ingredient)) continue;
            AdapterUtils.mergeIngredient(counts, ingredient, multiplier);
        }

        List<CountedIngredient> result = new ArrayList<>(counts.size());
        for (Map.Entry<Ingredient, Long> entry : counts.entrySet()) {
            result.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    private static boolean isAirPlaceholder(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return false;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty() || !item.is(IFEUContents.AIR.get())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Long multiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
