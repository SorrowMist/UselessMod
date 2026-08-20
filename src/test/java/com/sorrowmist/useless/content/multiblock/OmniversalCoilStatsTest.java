package com.sorrowmist.useless.content.multiblock;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution.AlloyFurnaceRecipeExecutor;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniversalCoilStatsTest {
    private static final AdvancedAlloyFurnaceRecipe NORMAL_RECIPE = new AdvancedAlloyFurnaceRecipe(
            ResourceLocation.fromNamespaceAndPath("test", "coil_stats"),
            List.of(), List.of(), List.of(), List.of(),
            1_000L, 1_000,
            Ingredient.EMPTY, 0, Ingredient.EMPTY, AlloyFurnaceMode.NORMAL);

    @Test
    void uselessCoilsProvideQuarticParallelAndHalveEnergyAtEachTier() {
        for (int tier = UselessCoilBlock.MIN_TIER; tier < UselessCoilBlock.USEFUL_TIER; tier++) {
            OmniversalCoilStats stats = OmniversalCoilStats.forTier(tier);
            CatalystType expectedType = CatalystType.uselessIngotTier(tier);
            var effect = stats.resolveEffect(NORMAL_RECIPE);
            long expectedParallel = 1L << (tier * 2);
            int expectedEnergyDivisor = 1 << tier;
            long timeDivisor = 1L << tier;
            int expectedProcessTime = (int) ((NORMAL_RECIPE.processTime() + timeDivisor - 1L)
                    / timeDivisor);

            assertEquals(expectedType, stats.catalystType());
            assertEquals(expectedParallel, stats.singleTaskParallel());
            assertEquals(expectedParallel, effect.catalystParallel());
            assertEquals(expectedParallel, effect.recipeParallel());
            assertEquals(expectedEnergyDivisor, stats.energyDivisor());
            assertEquals(expectedEnergyDivisor, effect.energyDivisor());
            assertEquals(expectedProcessTime, effect.processTime());
            assertTrue(effect.energyMultipliesWithParallel());
            assertEquals((1_000L * expectedParallel + expectedEnergyDivisor - 1L)
                            / expectedEnergyDivisor,
                    AlloyFurnaceRecipeExecutor.calculateTargetTotalEnergy(
                            NORMAL_RECIPE.energy(), expectedParallel, effect));
            assertEquals(tier + 1, stats.threads());
            assertTrue((long) stats.threads() * stats.singleTaskParallel()
                    > stats.singleTaskParallel());
        }
    }

    @Test
    void coilProcessTimeUsesPowerOfTwoReductionWithCeiling() {
        int baseTime = 1_001;
        for (int tier = UselessCoilBlock.MIN_TIER; tier < UselessCoilBlock.USEFUL_TIER; tier++) {
            long divisor = 1L << tier;
            int expected = (int) ((baseTime + divisor - 1L) / divisor);
            assertEquals(expected, OmniversalCoilStats.forTier(tier).processTime(baseTime));
        }
        assertEquals(1, OmniversalCoilStats.forTier(UselessCoilBlock.USEFUL_TIER)
                .processTime(baseTime));
    }

    @Test
    void usefulCoilKeepsItsOneTickAndLongMaxParallelSpecialRules() {
        OmniversalCoilStats stats = OmniversalCoilStats.forTier(UselessCoilBlock.USEFUL_TIER);
        var effect = stats.resolveEffect(NORMAL_RECIPE);

        assertEquals(CatalystType.USEFUL_INGOT, stats.catalystType());
        assertEquals(Long.MAX_VALUE, stats.singleTaskParallel());
        assertEquals(Long.MAX_VALUE, effect.catalystParallel());
        assertEquals(Long.MAX_VALUE, effect.recipeParallel());
        assertEquals(1_024, stats.energyDivisor());
        assertEquals(1_024, effect.energyDivisor());
        assertEquals(11, stats.threads());
        assertEquals(1, effect.processTime());
        assertFalse(effect.energyMultipliesWithParallel());
        assertEquals(Long.MAX_VALUE,
                AlloyFurnaceParallelCalculator.calculateAeTaskParallel(NORMAL_RECIPE, effect));
        assertEquals(1L, AlloyFurnaceRecipeExecutor.calculateTargetTotalEnergy(
                NORMAL_RECIPE.energy(), 1_000_000, effect));
        assertEquals(Long.MAX_VALUE, stats.energyCapacity());
        assertEquals(Long.MAX_VALUE, stats.maxReceive());
    }

    @Test
    void fixedEnergyCurveRetainsLongTierNineValues() {
        OmniversalCoilStats stats = OmniversalCoilStats.forTier(9);
        assertEquals(3_276_800_000L, stats.energyCapacity());
        assertEquals(327_680_000L, stats.maxReceive());
    }

    @Test
    void rejectsUnsupportedTiers() {
        assertThrows(IllegalArgumentException.class, () -> OmniversalCoilStats.forTier(0));
        assertThrows(IllegalArgumentException.class, () -> OmniversalCoilStats.forTier(11));
    }
}
