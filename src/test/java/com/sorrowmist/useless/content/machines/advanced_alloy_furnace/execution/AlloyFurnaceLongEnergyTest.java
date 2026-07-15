package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution;

import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.energy.EnergyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlloyFurnaceLongEnergyTest {
    private static final ResolvedCatalystEffect NORMAL = new ResolvedCatalystEffect(
            CatalystType.NONE, 1, 1, 4, true, false, 0);

    @Test
    void paysOneProgressStepAcrossMultipleBufferFills() {
        EnergyManager energy = EnergyManager.builder()
                .capacity(10L)
                .maxReceive(10L)
                .maxExtract(0L)
                .initialEnergy(10L)
                .build();

        long accumulated = 0L;
        AlloyFurnaceRecipeExecutor.TickResult first =
                AlloyFurnaceRecipeExecutor.consumeProgressEnergy(energy, 100L, 0, 4, accumulated);
        accumulated += first.energyConsumed();
        assertFalse(first.progressAdvanced());
        assertEquals(10L, accumulated);

        energy.receiveEnergy(10L, false);
        AlloyFurnaceRecipeExecutor.TickResult second =
                AlloyFurnaceRecipeExecutor.consumeProgressEnergy(energy, 100L, 0, 4, accumulated);
        accumulated += second.energyConsumed();
        assertFalse(second.progressAdvanced());
        assertEquals(20L, accumulated);

        energy.receiveEnergy(10L, false);
        AlloyFurnaceRecipeExecutor.TickResult third =
                AlloyFurnaceRecipeExecutor.consumeProgressEnergy(energy, 100L, 0, 4, accumulated);
        accumulated += third.energyConsumed();
        assertTrue(third.progressAdvanced());
        assertEquals(25L, accumulated);
        assertEquals(5L, energy.getEnergyStoredLong());
    }

    @Test
    void calculatesParallelTargetsBeyondIntegerRange() {
        assertEquals(10_000_000_000L,
                AlloyFurnaceRecipeExecutor.calculateTargetTotalEnergy(5_000_000_000L, 2, NORMAL));
        assertEquals(Long.MAX_VALUE,
                AlloyFurnaceRecipeExecutor.energyAtProgress(Long.MAX_VALUE, 200, 200));
    }

    @Test
    void completionConsumesOnlyTheEnergyNeededForTheActualParallel() {
        EnergyManager energy = EnergyManager.builder()
                .capacity(150L)
                .maxExtract(0L)
                .initialEnergy(150L)
                .build();

        AlloyFurnaceRecipeExecutor.CompletionEnergyResult result =
                AlloyFurnaceRecipeExecutor.settleCompletionEnergy(
                        energy, 100L, 4, 100L, NORMAL);

        assertEquals(2, result.actualParallel());
        assertEquals(100L, result.additionalEnergyConsumed());
        assertEquals(50L, energy.getEnergyStoredLong());
    }

    @Test
    void aeBatchParallelNeverOverflowsItsLongEnergyTarget() {
        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("test", "long_parallel"),
                List.of(), List.of(), List.of(), List.of(),
                Long.MAX_VALUE / 2L + 1L, 200,
                Ingredient.EMPTY, 0, Ingredient.EMPTY, AlloyFurnaceMode.NORMAL);

        assertEquals(1,
                AlloyFurnaceParallelCalculator.calculateAeTaskParallel(recipe, NORMAL));
    }
}
