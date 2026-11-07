package com.sorrowmist.useless.command;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.recipe.NativeProcessingPatterns;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Origin121FixA2A3PatternRepairTest {
    private static final ResourceLocation ID = new ResourceLocation("test", "eternal_singularity");
    private Object registryObject;
    private Object oldItem;
    private Field value;
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            Class.forName(ModItems.class.getName());
        }
    }

    @BeforeEach void bindPatternItemWithoutForgeRegistration() throws Exception {
        var delegate = DeferredHolder.class.getDeclaredField("delegate");
        delegate.setAccessible(true);
        registryObject = delegate.get(ModItems.OMNIVERSAL_PATTERN);
        value = registryObject.getClass().getDeclaredField("value");
        value.setAccessible(true);
        oldItem = value.get(registryObject);
        value.set(registryObject, Items.PAPER);
    }

    @AfterEach void restoreRegistration() throws Exception {
        if (value != null) value.set(registryObject, oldItem);
    }

    private static ItemStack pattern() {
        var stack = new ItemStack(Items.PAPER);
        NativeProcessingPatterns.write(stack,
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3_000_000_007L)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        ComponentAccess.set(stack, UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION, ID, "old", "avaritia", false,
                Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(0), List.of()));
        return stack;
    }

    @Test void decodableButSemanticallyStalePatternsNeedRepairToo() {
        var level = mock(Level.class);
        var stack = pattern();
        try (var decode = mockStatic(PatternDetailsHelper.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class)) {
            decode.when(() -> PatternDetailsHelper.decodePattern(stack, level)).thenReturn(mock(OmniversalPatternDetails.class));
            encoding.when(() -> OmniversalPatternEncoding.isStale(stack, level)).thenReturn(true);
            assertTrue(OmniversalPatternRepairService.needsRepair(stack, level));
            encoding.when(() -> OmniversalPatternEncoding.isStale(stack, level)).thenReturn(false);
            assertFalse(OmniversalPatternRepairService.needsRepair(stack, level));
            decode.when(() -> PatternDetailsHelper.decodePattern(stack, level)).thenThrow(new IllegalArgumentException("old"));
            assertTrue(OmniversalPatternRepairService.needsRepair(stack, level));
            assertFalse(OmniversalPatternRepairService.needsRepair(new ItemStack(Items.DIAMOND), level));
            assertFalse(OmniversalPatternRepairService.needsRepair(null, level));
        }
    }

    @Test void stableRecipeIdRebuildsEvenABrokenOldPayloadAndPreservesStackCount() {
        var level = mock(Level.class);
        var stack = pattern();
        stack.setCount(2);
        stack.getTag().remove("in");
        var snapshot = stack.getTag().copy();
        var current = new AlloyFurnaceRecipeCatalog.Entry(new AlloyFurnaceRecipeIdentity(ID, "new"),
                mock(AdvancedAlloyFurnaceRecipe.class), "avaritia");
        var rebuilt = pattern();
        rebuilt.getOrCreateTag().putBoolean("rebuilt", true);
        try (var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class)) {
            catalog.when(() -> AlloyFurnaceRecipeCatalog.entries(level, "avaritia")).thenReturn(List.of(current));
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level)).thenReturn(rebuilt);
            var result = OmniversalPatternRepairService.rebuild(stack, level);
            assertSame(rebuilt, result);
            assertEquals(2, result.getCount());
            assertEquals(snapshot, stack.getTag());
            encoding.verify(() -> OmniversalPatternEncoding.reencodeExisting(any(), any(), any()), never());
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level))
                    .thenThrow(new IllegalArgumentException("unencodable"));
            assertTrue(OmniversalPatternRepairService.rebuild(stack, level).isEmpty());
            assertEquals(snapshot, stack.getTag());
        }
    }

    @Test void identityFallbackReusesNativePayloadWithoutNarrowingLongCounts() {
        var level = mock(Level.class);
        var stack = pattern();
        var current = new AlloyFurnaceRecipeCatalog.Entry(new AlloyFurnaceRecipeIdentity(ID, "new"),
                mock(AdvancedAlloyFurnaceRecipe.class), "avaritia");
        try (var types = mockStatic(AEKeyTypes.class); var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class)) {
            types.when(() -> AEKeyTypes.get(AEKeyType.items().getId())).thenReturn(AEKeyType.items());
            catalog.when(() -> AlloyFurnaceRecipeCatalog.resolvePattern(eq(level), eq("avaritia"), any(), any()))
                    .thenAnswer(call -> {
                        var source = (appeng.crafting.pattern.AEProcessingPattern) call.getArgument(3);
                        assertEquals(3_000_000_007L, source.getInputs()[0].getMultiplier());
                        return Optional.of(current);
                    });
            encoding.when(() -> OmniversalPatternEncoding.reencodeExisting(stack, current, level)).thenReturn(stack.copy());
            assertTrue(ItemStack.isSameItemSameTags(stack, OmniversalPatternRepairService.rebuild(stack, level)));
        }
    }

    @Test void catalogReadinessDefersWithoutReadingOrWritingAnySlot() {
        var level = mock(Level.class);
        @SuppressWarnings("unchecked") IntFunction<ItemStack> getter = mock(IntFunction.class);
        @SuppressWarnings("unchecked") BiConsumer<Integer, ItemStack> setter = mock(BiConsumer.class);
        try (var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class)) {
            assertEquals(0, OmniversalPatternRepairService.repairSlots(12, getter, setter, level));
            verifyNoInteractions(getter, setter);
            assertEquals(0, OmniversalPatternRepairService.repairSlots(0, getter, setter, level));
            assertEquals(0, OmniversalPatternRepairService.repairSlots(12, null, setter, level));
        }
    }

    @Test void slotApiOnlyWritesChangedRebuildsAndRespectsTheActiveRange() {
        var level = mock(Level.class);
        var stale = pattern();
        var identical = pattern();
        var unresolved = pattern();
        var recovery = pattern();
        var fixed = stale.copy();
        fixed.getOrCreateTag().putBoolean("rebuilt", true);
        var slots = new ArrayList<>(List.of(stale, identical, unresolved, ItemStack.EMPTY, recovery));
        var writes = new ArrayList<Integer>();
        try (var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var repair = mockStatic(OmniversalPatternRepairService.class, CALLS_REAL_METHODS)) {
            catalog.when(() -> AlloyFurnaceRecipeCatalog.isReady(level)).thenReturn(true);
            repair.when(() -> OmniversalPatternRepairService.needsRepair(any(), eq(level))).thenReturn(true);
            repair.when(() -> OmniversalPatternRepairService.rebuild(stale, level)).thenReturn(fixed);
            repair.when(() -> OmniversalPatternRepairService.rebuild(identical, level)).thenReturn(identical.copy());
            repair.when(() -> OmniversalPatternRepairService.rebuild(unresolved, level)).thenReturn(ItemStack.EMPTY);
            int repaired = OmniversalPatternRepairService.repairSlots(4, slots::get, (slot, stack) -> {
                slots.set(slot, stack);
                writes.add(slot);
            }, level);
            assertEquals(1, repaired);
            assertEquals(List.of(0), writes);
            assertSame(fixed, slots.get(0));
            assertSame(unresolved, slots.get(2));
            assertSame(recovery, slots.get(4));
            repair.verify(() -> OmniversalPatternRepairService.needsRepair(recovery, level), never());
        }
    }
}
