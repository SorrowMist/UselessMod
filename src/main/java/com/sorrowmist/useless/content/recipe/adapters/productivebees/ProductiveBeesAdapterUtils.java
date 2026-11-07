package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.productivebees.ProductiveBeesCentrifugeIngredient;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Native Productive Bees 1.20.1-12.6.0 data; no ProductiveLib recipe API is involved. */
final class ProductiveBeesAdapterUtils {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation GENE_BOTTLE = new ResourceLocation("productivebees", "gene_bottle");
    private static final ResourceLocation HONEY_TREAT = new ResourceLocation("productivebees", "honey_treat");
    private static final int CENTRIFUGE_OUTPUT_SLOTS = 9;
    private static final int CENTRIFUGE_TANK_CAPACITY = 10_000;

    private ProductiveBeesAdapterUtils() {
    }

    static Ingredient centrifugeInput(Ingredient source) {
        ProductiveBeesCentrifugeIngredient input = ProductiveBeesCentrifugeIngredient.of(source);
        for (ItemStack member : source.getItems()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(member.getItem());
            // These items enter entirely different, NBT-dependent branches before recipe lookup.
            require(!GENE_BOTTLE.equals(id) && !HONEY_TREAT.equals(id),
                    "Gene bottles and honey treats use native dynamic processing, not a static centrifuge recipe");
        }
        require(input.getItems().length > 0, "Unmatchable native centrifuge ingredient");
        return input;
    }

    static Optional<ExpectedOutputScaler.ScaledOutputs> centrifugeOutputs(CentrifugeRecipe source) {
        require(source.itemOutput != null && source.itemOutput.size() <= CENTRIFUGE_OUTPUT_SLOTS,
                "Missing outputs or more outputs than the native centrifuge can hold");
        for (Map.Entry<Ingredient, IntArrayTag> entry : source.itemOutput.entrySet()) {
            require(ProductiveBeesCentrifugeIngredient.isStaticPredicate(entry.getKey()) && !entry.getKey().isEmpty(),
                    "Unknown/dynamic or unresolved centrifuge output ingredient");
            int[] range = range(entry.getValue());
            // Limit support to per-operation outputs fitting one native slot. Batches may exceed
            // this count; they stand for repeated operations, not one oversized native operation.
            require(range[1] <= 64, "Centrifuge output exceeds the supported native single-slot range");
        }
        // This is the actual runtime output map (native preferred-tag-source selection), NOT
        // assemble/getResultItem/JEI display results. Unresolved rows must not become partial loot.
        Map<ItemStack, IntArrayTag> outputs = source.getRecipeOutputs();
        require(outputs != null && outputs.size() == source.itemOutput.size(), "Unresolved native centrifuge output");
        for (Map.Entry<ItemStack, IntArrayTag> entry : outputs.entrySet()) {
            require(entry.getKey() != null && !entry.getKey().isEmpty(), "Empty native centrifuge output");
            // The shared furnace output codec preserves ordinary NBT but not ForgeCaps. Do not
            // enable a recipe whose output would change after catalogue/packet serialization.
            require(!entry.getKey().save(new CompoundTag()).contains("ForgeCaps"),
                    "Capability-bearing centrifuge outputs are not losslessly serializable by the furnace");
            require(range(entry.getValue())[1] <= entry.getKey().getMaxStackSize(),
                    "Centrifuge output exceeds its native item slot capacity");
        }
        return scaleOutputs(outputs);
    }

    /**
     * The tuples are [min, max, integer chance]. The 12.6.0 machine uses nextInt(100) <= chance:
     * 0 means 1%, 49 means 50%, and both 99 and 100 mean 100%. Do not 'fix' that native behavior.
     * It also SETS the output count to the rolled range, rather than multiplying the template count.
     */
    static Optional<ExpectedOutputScaler.ScaledOutputs> scaleOutputs(Map<ItemStack, IntArrayTag> outputs) {
        if (outputs == null) return Optional.empty();
        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        try {
            for (Map.Entry<ItemStack, IntArrayTag> entry : outputs.entrySet()) {
                require(entry.getKey() != null && !entry.getKey().isEmpty(), "Empty native output stack");
                int[] value = range(entry.getValue());
                double chance = (Math.min(value[2], 99) + 1) / 100.0;
                weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        entry.getKey(), value[0], value[1], chance));
            }
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return ExpectedOutputScaler.scale(weightedOutputs);
    }

    static FluidStack centrifugeFluid(CentrifugeRecipe source, int operations) {
        if (source.fluidOutput == null) return FluidStack.EMPTY;
        require(source.fluidOutput.getFirst() != null && !source.fluidOutput.getFirst().isBlank()
                        && source.fluidOutput.getSecond() != null && source.fluidOutput.getSecond() > 0
                        && source.fluidOutput.getSecond() <= CENTRIFUGE_TANK_CAPACITY,
                "Invalid/oversized native centrifuge fluid output");
        Pair<Fluid, Integer> output = source.getFluidOutputs();
        require(output != null && output.getFirst() != null && output.getFirst() != Fluids.EMPTY
                        && output.getSecond() != null && output.getSecond() > 0,
                "Unresolved native centrifuge fluid output");
        OptionalInt amount = ExpectedOutputScaler.multiplyToInt(output.getSecond(), operations);
        require(amount.isPresent(), "Overflowing centrifuge fluid batch");
        return new FluidStack(output.getFirst(), amount.getAsInt());
    }

    static List<AdvancedAlloyFurnaceRecipe> unsupported(RecipeHolder<?> holder, String reason) {
        if (holder != null) {
            RateLimitedLogger.warn(LOGGER, "Skipping unsupported Productive Bees recipe {}: {}", holder.id(), reason);
        }
        return List.of();
    }

    static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }

    private static int[] range(IntArrayTag tuple) {
        require(tuple != null, "Missing native output tuple");
        int[] values = tuple.getAsIntArray();
        require(values.length == 3 && values[0] >= 0 && values[1] >= values[0]
                        && values[1] <= 16_777_216 && values[2] >= 0 && values[2] <= 100,
                "Malformed native output range/chance (or inexact native int-to-float range)");
        return values;
    }
}
