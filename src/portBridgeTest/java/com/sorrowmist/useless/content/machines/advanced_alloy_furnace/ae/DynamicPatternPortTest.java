package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicPatternPortTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static ItemStack named(String value) {
        var item = new ItemStack(Items.IRON_INGOT);
        item.getOrCreateTag().putString("foreign:state", value);
        return item;
    }
    private static MockedStatic<AEKeyTypes> types() {
        // Unit JVM has no AE2 registry event; keep actual AE2 key types and NBT parsers.
        var types = mockStatic(AEKeyTypes.class);
        types.when(() -> AEKeyTypes.get(AEKeyType.items().getId())).thenReturn(AEKeyType.items());
        types.when(() -> AEKeyTypes.get(AEKeyType.fluids().getId())).thenReturn(AEKeyType.fluids());
        return types;
    }
    private static AEProcessingPattern pattern(GenericStack... inputs) {
        var stack = new ItemStack(Items.PAPER);
        var in = new ListTag();
        for (var input : inputs) in.add(input == null ? new CompoundTag() : GenericStack.writeTag(input));
        var out = new ListTag();
        out.add(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.DIAMOND), 3_000_000_007L)));
        stack.getOrCreateTag().put("in", in);
        stack.getOrCreateTag().put("out", out);
        return new AEProcessingPattern(AEItemKey.of(stack));
    }
    @Test void nativeArrayApiKeepsSparseHolesLongCountsAndDefensiveOutputs() {
        try (var ignored = types()) {
            var key = AEItemKey.of(named("template"));
            var source = pattern(new GenericStack(key, 3_000_000_001L), null, new GenericStack(key, 2));
            var view = PatternStackView.fromPattern(source);
            assertEquals(3_000_000_003L, view.inputs().get(0).amount());
            var dynamic = new DynamicComponentPatternDetails(source, Set.of(0), Set.of(0), RegistryAccess.EMPTY);
            assertTrue(dynamic.isItemIdInput(0)); assertTrue(dynamic.usesDynamicOutputs());
            assertEquals(3_000_000_007L, dynamic.getOutputs()[0].amount());
            dynamic.getOutputs()[0] = null;
            dynamic.getInputs()[0] = null;
            assertNotNull(dynamic.getOutputs()[0]); assertNotNull(dynamic.getInputs()[0]);
            var actual = AEItemKey.of(named("runtime"));
            var held = new KeyCounter(); held.add(actual, 3_000_000_003L);
            var pushed = new KeyCounter();
            dynamic.pushInputsToExternalInventory(new KeyCounter[]{held}, pushed::add);
            assertEquals(3_000_000_003L, pushed.get(actual));
            assertEquals(3_000_000_003L, held.get(actual));
        }
    }
    @Test void overlappingDynamicPredicatesAreAllocatedWithoutDoubleCounting() {
        try (var ignored = types()) {
            var a = AEItemKey.of(named("a")); var b = AEItemKey.of(named("b"));
            var source = pattern(new GenericStack(a, 1), null, new GenericStack(b, 1));
            var dynamic = new DynamicComponentPatternDetails(source, Set.of(0, 1), Set.of(),
                    Map.of(0, key -> key instanceof AEItemKey item && item.getItem() == Items.IRON_INGOT,
                           1, key -> key.equals(a)), RegistryAccess.EMPTY);
            var held = new KeyCounter(); held.add(a, 1); held.add(b, 1);
            var pushed = new KeyCounter();
            dynamic.pushInputsToExternalInventory(new KeyCounter[]{held}, pushed::add);
            assertEquals(1, pushed.get(a)); assertEquals(1, pushed.get(b));
            var insufficient = new KeyCounter(); insufficient.add(b, 2);
            var rejected = new KeyCounter();
            assertThrows(IllegalStateException.class, () -> dynamic.pushInputsToExternalInventory(
                    new KeyCounter[]{insufficient}, rejected::add));
            assertTrue(rejected.isEmpty()); // Do not transfer anything before complete allocation.
        }
    }
    @Test void onlyExplicitSlotsRelaxNbtAndInvalidSlotsAreRejected() {
        try (var ignored = types()) {
            var a = AEItemKey.of(named("a")); var b = AEItemKey.of(named("b"));
            var source = pattern(new GenericStack(a, 4), new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2));
            var dynamic = new DynamicComponentPatternDetails(source, Set.of(0), Set.of(), RegistryAccess.EMPTY);
            assertTrue(dynamic.getInputs()[0].isValid(b, null));
            assertFalse(dynamic.getInputs()[1].isValid(AEItemKey.of(Items.DIAMOND), null));
            assertEquals(4, dynamic.getInputs()[0].getMultiplier());
            assertFalse(dynamic.isItemIdInput(1));
            assertThrows(IllegalArgumentException.class, () -> new DynamicComponentPatternDetails(
                    source, Set.of(2), Set.of(), RegistryAccess.EMPTY));
        }
    }
    @Test void identityIsCanonicalButRetainsForeignNbtAndLongPatternAmounts() {
        try (var ignored = types()) {
            var a = new ItemStack(Items.PAPER); var b = new ItemStack(Items.PAPER);
            a.getOrCreateTag().putInt("z", 17); a.getOrCreateTag().putLong("a", 3_000_000_007L);
            b.getOrCreateTag().putLong("a", 3_000_000_007L); b.getOrCreateTag().putInt("z", 17);
            var first = DynamicComponentPatternDetails.definitionFingerprint(AEItemKey.of(a), RegistryAccess.EMPTY);
            assertEquals(first, DynamicComponentPatternDetails.definitionFingerprint(AEItemKey.of(b), RegistryAccess.EMPTY));
            b.getOrCreateTag().putLong("a", 7);
            assertNotEquals(first, DynamicComponentPatternDetails.definitionFingerprint(AEItemKey.of(b), RegistryAccess.EMPTY));
        }
    }
}
