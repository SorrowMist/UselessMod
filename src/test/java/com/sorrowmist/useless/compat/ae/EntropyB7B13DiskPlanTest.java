package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntropyB7B13DiskPlanTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private static final IActionSource SOURCE = IActionSource.empty();

    private static KeyCounter available(long iron, long gold) {
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.IRON_INGOT), iron);
        counter.add(AEItemKey.of(Items.GOLD_INGOT), gold);
        return counter;
    }
    private static MEStorage storage() {
        MEStorage storage = mock(MEStorage.class);
        when(storage.extract(any(), anyLong(), eq(Actionable.SIMULATE), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return storage;
    }
    private static CountedIngredient iron(long count) { return new CountedIngredient(Ingredient.of(Items.IRON_INGOT), count); }

    @Test void duplicateInputsCannotSpendTheSameDiskItemsTwice() {
        var storage = storage();
        assertNull(AeEntropyTransmutationHandler.planExtraction(storage, available(8, 0), List.of(iron(8), iron(8)), SOURCE));
        var plan = AeEntropyTransmutationHandler.planExtraction(storage, available(16, 0), List.of(iron(8), iron(8)), SOURCE);
        assertNotNull(plan);
        assertEquals(16, plan.get(0).amount());
        verify(storage, never()).extract(any(), anyLong(), eq(Actionable.MODULATE), any());
    }

    @Test void overlappingIngredientsAreReassignedRatherThanGreedilyRejected() {
        var inputs = List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 8), iron(8));
        var plan = AeEntropyTransmutationHandler.planExtraction(storage(), available(8, 8), inputs, SOURCE);
        assertNotNull(plan);
        assertEquals(16, plan.stream().mapToLong(AeEntropyTransmutationHandler.Extraction::amount).sum());
        assertNull(AeEntropyTransmutationHandler.planExtraction(storage(), available(8, 7), inputs, SOURCE));
    }

    @Test void retainedInputsReserveTheirItemsButAreNeverExtracted() {
        var inputs = List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 8),
                new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1, false));
        var plan = AeEntropyTransmutationHandler.planExtraction(storage(), available(1, 8), inputs, SOURCE);
        assertNotNull(plan);
        assertEquals(1, plan.size());
        assertEquals(AEItemKey.of(Items.GOLD_INGOT), plan.get(0).key());
        assertEquals(8, plan.get(0).amount());
        assertNull(AeEntropyTransmutationHandler.planExtraction(storage(), available(1, 7), inputs, SOURCE));
    }

    @Test void longAmountsDoNotNarrowToIntOrOverflowWhenDemandsAreSummed() {
        var plan = AeEntropyTransmutationHandler.planExtraction(storage(), available(3_000_000_000L, 0),
                List.of(iron(3_000_000_000L)), SOURCE);
        assertNotNull(plan);
        assertEquals(3_000_000_000L, plan.get(0).amount());
        assertNull(AeEntropyTransmutationHandler.planExtraction(storage(), available(Long.MAX_VALUE, 0),
                List.of(iron(Long.MAX_VALUE), iron(Long.MAX_VALUE)), SOURCE));
        var maximum = AeEntropyTransmutationHandler.planExtraction(storage(), available(Long.MAX_VALUE, Long.MAX_VALUE),
                List.of(iron(Long.MAX_VALUE), new CountedIngredient(Ingredient.of(Items.GOLD_INGOT), Long.MAX_VALUE)), SOURCE);
        assertNotNull(maximum);
        assertEquals(2, maximum.size());
    }

    @Test void availableButUnextractableItemsDoNotProduceAPreview() {
        var storage = storage();
        when(storage.extract(any(), anyLong(), eq(Actionable.SIMULATE), any())).thenReturn(7L);
        assertNull(AeEntropyTransmutationHandler.planExtraction(storage, available(64, 0), List.of(iron(8)), SOURCE));
    }

    @Test void strictNbtInputsAllocateOnlyMatchingAeKeys() {
        var exact = new ItemStack(Items.IRON_INGOT);
        exact.getOrCreateTag().putString("serial", "required");
        var counter = available(64, 0);
        counter.add(AEItemKey.of(exact), 8);
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "forge:nbt");
        json.addProperty("item", "minecraft:iron_ingot");
        json.addProperty("nbt", exact.getTag().toString());
        var plan = AeEntropyTransmutationHandler.planExtraction(storage(), counter,
                List.of(new CountedIngredient(Ingredient.fromJson(json), 8)), SOURCE);
        assertNotNull(plan);
        assertEquals(AEItemKey.of(exact), plan.get(0).key());
        assertEquals(8, plan.get(0).amount());
    }

    @Test void forgedOrAdventureRequestsAreRejectedBeforeReadingAnyChunkOrInventory() {
        ServerLevel level = mock(ServerLevel.class);
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.isAlive()).thenReturn(true);
        when(player.mayBuild()).thenReturn(false);
        assertNull(AeEntropyTransmutationHandler.previewOutput(level, BlockPos.ZERO, player));
        assertFalse(AeEntropyTransmutationHandler.tryTransmute(level, BlockPos.ZERO, player));
        verify(level, never()).getBlockEntity(any());
        verify(level, never()).hasChunkAt(any());
    }

    @Test void previewIsReadOnlyAndSuccessfulClickConsumesBeforeReplacingTheChest() {
        var scenario = scenario(false, false);
        assertNotNull(AeEntropyTransmutationHandler.previewOutput(scenario.level, BlockPos.ZERO, scenario.player));
        assertEquals(16, scenario.amount.get());
        assertTrue(AeEntropyTransmutationHandler.tryTransmute(scenario.level, BlockPos.ZERO, scenario.player));
        assertEquals(8, scenario.amount.get());
        verify(scenario.level).setBlock(eq(BlockPos.ZERO), any(), eq(3));
    }

    @Test void shortExtractionAndFailedPlacementBothRefundAndNeverGrantAnOutput() {
        var shortExtraction = scenario(true, false);
        assertFalse(AeEntropyTransmutationHandler.tryTransmute(shortExtraction.level, BlockPos.ZERO, shortExtraction.player));
        assertEquals(16, shortExtraction.amount.get());
        verify(shortExtraction.level, never()).setBlock(any(), any(), anyInt());
        var failedPlacement = scenario(false, true);
        assertFalse(AeEntropyTransmutationHandler.tryTransmute(failedPlacement.level, BlockPos.ZERO, failedPlacement.player));
        assertEquals(16, failedPlacement.amount.get());
    }

    @Test void serverChecksLoadedChunkAndActualSightBeforeReturningAPreview() {
        var scenario = scenario(false, false);
        when(scenario.level.hasChunkAt(BlockPos.ZERO)).thenReturn(false);
        assertNull(AeEntropyTransmutationHandler.previewOutput(scenario.level, BlockPos.ZERO, scenario.player));
        verify(scenario.level, never()).getBlockEntity(any());
        when(scenario.level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        when(scenario.level.clip(any())).thenReturn(new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.core.Direction.NORTH, new BlockPos(0, 0, 1), false));
        assertNull(AeEntropyTransmutationHandler.previewOutput(scenario.level, BlockPos.ZERO, scenario.player));
        verify(scenario.level, never()).getBlockEntity(any());
    }

    private record Scenario(ServerLevel level, Player player, java.util.concurrent.atomic.AtomicLong amount) {}
    private static Scenario scenario(boolean shortExtraction, boolean failedPlacement) {
        var level = mock(ServerLevel.class);
        var player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.isAlive()).thenReturn(true);
        when(player.mayBuild()).thenReturn(true);
        when(player.isShiftKeyDown()).thenReturn(true);
        var tool = mock(ItemStack.class);
        when(tool.getItem()).thenReturn(mock(appeng.items.tools.powered.EntropyManipulatorItem.class));
        when(player.getMainHandItem()).thenReturn(tool);
        when(player.getBlockReach()).thenReturn(5.0);
        when(player.canReach(any(BlockPos.class), anyDouble())).thenReturn(true);
        when(player.getEyePosition()).thenReturn(new net.minecraft.world.phys.Vec3(0.5, 1.6, -2));
        when(player.getLookAngle()).thenReturn(new net.minecraft.world.phys.Vec3(0, 0, 1));
        var border = mock(net.minecraft.world.level.border.WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.mayInteract(eq(player), any())).thenReturn(true);
        when(level.clip(any())).thenReturn(new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.core.Direction.NORTH, BlockPos.ZERO, false));
        var blockEntity = mock(net.minecraft.world.level.block.entity.BlockEntity.class,
                withSettings().extraInterfaces(appeng.api.implementations.blockentities.IMEChest.class));
        var chest = (appeng.api.implementations.blockentities.IMEChest) blockEntity;
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(blockEntity);
        when(chest.isPowered()).thenReturn(true);
        MEStorage storage = mock(MEStorage.class);
        when(chest.getCellInventory(0)).thenReturn(storage);
        var amount = new java.util.concurrent.atomic.AtomicLong(16);
        when(storage.getAvailableStacks()).thenAnswer(invocation -> available(amount.get(), 0));
        when(storage.extract(any(), anyLong(), any(), any())).thenAnswer(invocation -> {
            long requested = invocation.getArgument(1);
            long result = Math.min(requested, amount.get());
            if (invocation.getArgument(2) == Actionable.MODULATE) {
                if (shortExtraction) result--;
                amount.addAndGet(-result);
            }
            return result;
        });
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(invocation -> {
            long result = invocation.getArgument(1);
            amount.addAndGet(result);
            return result;
        });
        var manager = mock(net.minecraft.world.item.crafting.RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getAllRecipesFor(com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe.TYPE))
                .thenReturn(List.of(new com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe(
                        new net.minecraft.resources.ResourceLocation("test", "entropy_b7b13_click"), List.of(iron(8)), new ItemStack(Items.FURNACE))));
        when(level.setBlock(any(), any(), anyInt())).thenReturn(!failedPlacement);
        return new Scenario(level, player, amount);
    }

    @Test void allSmallOverlappingCasesAgreeWithExhaustiveFeasibility() {
        for (int iron = 0; iron < 5; iron++) for (int gold = 0; gold < 5; gold++) {
            for (int either = 1; either < 5; either++) for (int exact = 1; exact < 5; exact++) {
                boolean expected = iron >= exact && iron + gold >= either + exact;
                var inputs = List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), either), iron(exact));
                assertEquals(expected, AeEntropyTransmutationHandler.planExtraction(storage(), available(iron, gold), inputs, SOURCE) != null,
                        "iron=" + iron + ", gold=" + gold + ", either=" + either + ", exact=" + exact);
            }
        }
    }
}
