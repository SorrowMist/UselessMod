package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.recipe.NativeProcessingPatterns;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Origin121FixA2PatternSemanticsTest {
    private static final ResourceLocation ID = new ResourceLocation("test", "eternal_singularity");
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static MockedStatic<AEKeyTypes> types() {
        var types = mockStatic(AEKeyTypes.class);
        types.when(() -> AEKeyTypes.get(AEKeyType.items().getId())).thenReturn(AEKeyType.items());
        return types;
    }

    private static ItemStack subtype(String id) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putString("SingularityId", id);
        return stack;
    }

    private static void metadata(ItemStack stack, int version, String fingerprint, List<Integer> slots) {
        ComponentAccess.set(stack, UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                version, ID, fingerprint, "avaritia", false, Optional.empty(), List.of(),
                List.of(), List.of(), List.of(), slots, List.of()));
    }

    private static ItemStack pattern(List<Integer> slots) {
        var stack = new ItemStack(Items.PAPER);
        NativeProcessingPatterns.write(stack,
                List.of(new GenericStack(AEItemKey.of(subtype("iron")), 3_000_000_007L)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 3_000_000_009L)));
        metadata(stack, OmniversalPatternData.CURRENT_VERSION, "old-fingerprint", slots);
        return stack;
    }

    private static AlloyFurnaceRecipeCatalog.Entry entry(String fingerprint) {
        return new AlloyFurnaceRecipeCatalog.Entry(new AlloyFurnaceRecipeIdentity(ID, fingerprint),
                mock(AdvancedAlloyFurnaceRecipe.class), "avaritia");
    }

    @Test void payloadAndIdOnlySlotDeltaMatterButFingerprintOrderingAndUnrelatedNbtDoNot() {
        var old = pattern(List.of(2, 0));
        var rebuilt = old.copy();
        metadata(rebuilt, OmniversalPatternData.CURRENT_VERSION, "new-fingerprint", List.of(0, 2));
        old.getOrCreateTag().putString("foreign:display", "keep this out of semantic comparison");
        assertTrue(OmniversalPatternEncoding.hasSameSemantics(old, rebuilt));
        metadata(rebuilt, OmniversalPatternData.CURRENT_VERSION, "new-fingerprint", List.of());
        assertFalse(OmniversalPatternEncoding.hasSameSemantics(old, rebuilt));
    }

    @Test void nativePayloadComparisonKeepsSubtypeNbtAndLongAmountsExactWithoutMutation() {
        var old = pattern(List.of());
        var snapshot = old.getTag().copy();
        var rebuilt = old.copy();
        var input = rebuilt.getTag().getList("in", 10).getCompound(0);
        input.putLong("#", input.getLong("#") + 1L);
        assertFalse(OmniversalPatternEncoding.hasSameSemantics(old, rebuilt));
        assertEquals(snapshot, old.getTag());
        rebuilt = old.copy();
        NativeProcessingPatterns.write(rebuilt,
                List.of(new GenericStack(AEItemKey.of(subtype("gold")), 3_000_000_007L)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 3_000_000_009L)));
        assertFalse(OmniversalPatternEncoding.hasSameSemantics(old, rebuilt));
        assertEquals(snapshot, old.getTag());
    }

    @Test void stableRecipeIdWinsEvenWhenOldFingerprintStillResolves() {
        var level = mock(Level.class);
        var old = pattern(List.of(0));
        var rebuilt = pattern(List.of());
        var current = entry("current");
        try (var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class, CALLS_REAL_METHODS)) {
            catalog.when(() -> AlloyFurnaceRecipeCatalog.entries(level, "avaritia")).thenReturn(List.of(current));
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level)).thenReturn(rebuilt);
            assertTrue(OmniversalPatternEncoding.isStale(old, level));
            assertFalse(OmniversalPatternEncoding.isStale(rebuilt, level));
            catalog.verify(() -> AlloyFurnaceRecipeCatalog.resolvePattern(any(), anyString(), any(), any()), never());
        }
    }

    @Test void missingSourceFallsBackToUniqueGlobalRecipeId() {
        var level = mock(Level.class);
        var old = pattern(List.of(0));
        var rebuilt = pattern(List.of());
        var current = entry("current");
        try (var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class, CALLS_REAL_METHODS)) {
            catalog.when(() -> AlloyFurnaceRecipeCatalog.entries(level)).thenReturn(List.of(current));
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level)).thenReturn(rebuilt);
            assertTrue(OmniversalPatternEncoding.isStale(old, level));
        }
    }

    @Test void legacyFallbackUsesLegacyFingerprintRules() {
        var level = mock(Level.class);
        var old = pattern(List.of(0));
        metadata(old, 1, "legacy", List.of(0));
        var current = entry("current");
        try (var types = types(); var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class, CALLS_REAL_METHODS)) {
            catalog.when(() -> AlloyFurnaceRecipeCatalog.resolveLegacyPattern(
                    eq(level), eq("avaritia"), any(), any(), eq(1))).thenReturn(Optional.of(current));
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level)).thenReturn(pattern(List.of()));
            assertTrue(OmniversalPatternEncoding.isStale(old, level));
            catalog.verify(() -> AlloyFurnaceRecipeCatalog.resolvePattern(any(), anyString(), any(), any()), never());
        }
    }

    @Test void unresolvedAmbiguousAndUnencodableRecipesAreNotReportedAsSemanticRepairs() {
        var level = mock(Level.class);
        var old = pattern(List.of(0));
        var current = entry("current");
        try (var types = types(); var catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
             var encoding = mockStatic(OmniversalPatternEncoding.class, CALLS_REAL_METHODS)) {
            assertFalse(OmniversalPatternEncoding.isStale(old, level));
            catalog.when(() -> AlloyFurnaceRecipeCatalog.entries(level, "avaritia"))
                    .thenReturn(List.of(current, entry("ambiguous")));
            assertFalse(OmniversalPatternEncoding.isStale(old, level));
            catalog.when(() -> AlloyFurnaceRecipeCatalog.entries(level, "avaritia")).thenReturn(List.of(current));
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level)).thenReturn(ItemStack.EMPTY);
            assertFalse(OmniversalPatternEncoding.isStale(old, level));
            encoding.when(() -> OmniversalPatternEncoding.reencodeFromRecipe(current, level))
                    .thenThrow(new IllegalArgumentException("exceeds native slots"));
            assertFalse(OmniversalPatternEncoding.isStale(old, level));
        }
    }

    @Test void strictNbtRecipeInputsDoNotAcquireItemIdOnlySlots() {
        try (var types = types()) {
            var source = new AEProcessingPattern(AEItemKey.of(pattern(List.of())));
            var recipe = mock(AdvancedAlloyFurnaceRecipe.class);
            when(recipe.inputs()).thenReturn(List.of(new CountedIngredient(StrictNBTIngredient.of(subtype("iron")), 1)));
            assertEquals(List.of(), OmniversalPatternEncoding.resolveItemIdInputSlots(recipe, source, List.of()));
            when(recipe.inputs()).thenReturn(List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)));
            assertEquals(List.of(0), OmniversalPatternEncoding.resolveItemIdInputSlots(recipe, source, List.of()));
        }
    }
}
