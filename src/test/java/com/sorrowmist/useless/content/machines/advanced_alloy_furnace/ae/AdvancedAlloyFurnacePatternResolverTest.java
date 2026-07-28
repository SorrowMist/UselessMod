package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.compat.ae.DynamicReflectionSupport;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedAlloyFurnacePatternResolverTest {
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.EMPTY;

    @Test
    void omniversalDefinitionKeepsItsDataComponentInAeItemKey() {
        ItemStack processing = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT)), 1)),
                List.of(new GenericStack(Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT)), 1)));
        ItemStack omniversal = new ItemStack(ModItems.OMNIVERSAL_PATTERN.get());
        omniversal.set(AEComponents.ENCODED_PROCESSING_PATTERN,
                processing.get(AEComponents.ENCODED_PROCESSING_PATTERN));
        omniversal.set(UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "recipe"),
                "fingerprint", false, Optional.empty(), List.of(), List.of()));

        AEItemKey key = Objects.requireNonNull(AEItemKey.of(omniversal));
        assertNotNull(key.get(UComponents.OMNIVERSAL_PATTERN_DATA.get()));
        assertTrue(PatternDetailsHelper.isEncodedPattern(omniversal));
        assertDoesNotThrow(() -> DynamicComponentPatternDetails.definitionFingerprint(key, REGISTRIES));
    }

    @Test
    void canonicalChildOutputIsThePrimaryAutocraftingCandidate() {
        ItemStack jeiInput = new ItemStack(Items.DIAMOND_SWORD);
        jeiInput.set(DataComponents.CUSTOM_NAME, Component.literal("jei-components"));
        AEItemKey jeiKey = Objects.requireNonNull(AEItemKey.of(jeiInput));
        IPatternDetails.IInput source = input(new GenericStack(jeiKey, 1), 3L);

        IPatternDetails.IInput resolved = AdvancedAlloyFurnacePatternResolver.prependCanonicalInput(
                source, new ItemStack(Items.DIAMOND_SWORD));
        GenericStack[] candidates = resolved.getPossibleInputs();

        assertEquals(2, candidates.length);
        assertEquals(AEItemKey.of(new ItemStack(Items.DIAMOND_SWORD)), candidates[0].what());
        assertEquals(jeiKey, candidates[1].what());
        assertEquals(3L, resolved.getMultiplier());
    }

    @Test
    void canonicalizesConcreteAeProcessingInputArrayWithoutArrayStoreFailure() {
        ItemStack jeiInput = new ItemStack(Items.DIAMOND_SWORD, 3);
        jeiInput.set(DataComponents.CUSTOM_NAME, Component.literal("jei-components"));
        GenericStack encodedInput = Objects.requireNonNull(GenericStack.fromItemStack(jeiInput));
        GenericStack encodedOutput = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(Items.NETHER_STAR)));
        ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(encodedInput), List.of(encodedOutput));
        AEProcessingPattern source = new AEProcessingPattern(
                Objects.requireNonNull(AEItemKey.of(encodedPattern)));

        assertNotEquals(IPatternDetails.IInput.class,
                source.getInputs().getClass().getComponentType());
        AEProcessingPattern resolved = assertDoesNotThrow(() ->
                AdvancedAlloyFurnacePatternResolver.withCanonicalInputs(
                        source, Map.of(0, new ItemStack(Items.DIAMOND_SWORD))));

        IPatternDetails.IInput[] resolvedInputs = resolved.getInputs();
        GenericStack[] candidates = resolvedInputs[0].getPossibleInputs();
        assertEquals(IPatternDetails.IInput.class, resolvedInputs.getClass().getComponentType());
        assertEquals(2, candidates.length);
        assertEquals(AEItemKey.of(new ItemStack(Items.DIAMOND_SWORD)), candidates[0].what());
        assertEquals(AEItemKey.of(jeiInput), candidates[1].what());
        assertEquals(3L, resolvedInputs[0].getMultiplier());
        assertEquals(source.getSparseInputs(), resolved.getSparseInputs());
        assertEquals(source.getOutputs(), resolved.getOutputs());
    }

    @Test
    void completePatternDefinitionSeparatesOtherwiseIdenticalDynamicLayouts() {
        DynamicComponentPatternDetails sword = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD), null);
        DynamicComponentPatternDetails pickaxe = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_PICKAXE), null);
        DynamicComponentPatternDetails shovel = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SHOVEL), null);
        DynamicComponentPatternDetails swordReloaded = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD), null);

        assertNotEquals(sword.dynamicPatternIdentity(), pickaxe.dynamicPatternIdentity());
        assertNotEquals(sword.dynamicPatternIdentity(), shovel.dynamicPatternIdentity());
        assertNotEquals(pickaxe.dynamicPatternIdentity(), shovel.dynamicPatternIdentity());
        assertEquals(sword.dynamicPatternIdentity(), swordReloaded.dynamicPatternIdentity());
    }

    @Test
    void cpuTracksSameLayoutOutputsIndependentlyAndRestoresThem() {
        DynamicComponentPatternDetails sword = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD), null);
        DynamicComponentPatternDetails pickaxe = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_PICKAXE), null);
        DynamicComponentPatternDetails shovel = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SHOVEL), null);
        Object logic = new Object();
        UUID craftingId = UUID.randomUUID();
        DynamicPatternCpuStateManager manager = DynamicPatternCpuStateManager.INSTANCE;
        manager.registerExpectedOutputs(logic, craftingId, sword, null, 1);
        manager.registerExpectedOutputs(logic, craftingId, pickaxe, null, 1);
        manager.registerExpectedOutputs(logic, craftingId, shovel, null, 1);

        assertEquals(3, manager.snapshotPending(logic).size());
        assertEquals(1, manager.getRemainingForItem(logic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));
        assertEquals(1, manager.getRemainingForItem(logic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE)));
        assertEquals(1, manager.getRemainingForItem(logic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SHOVEL)));

        var saved = manager.writeToTag(logic, REGISTRIES);
        Object restoredLogic = new Object();
        manager.readFromTag(restoredLogic, craftingId, saved, REGISTRIES);
        assertEquals(3, manager.snapshotPending(restoredLogic).size());
        assertEquals(1, manager.claim(
                restoredLogic, AEItemKey.of(Items.DIAMOND_PICKAXE), 1, Actionable.MODULATE).claimedAmount());
        assertEquals(0, manager.getRemainingForItem(
                restoredLogic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE)));
        assertEquals(1, manager.getRemainingForItem(
                restoredLogic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));
        assertEquals(1, manager.getRemainingForItem(
                restoredLogic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SHOVEL)));
        manager.clear(logic);
        manager.clear(restoredLogic);
    }

    @Test
    void scaledDynamicExecutionTracksNewComponentVariantsAcrossReload() {
        ItemStack encodedOutput = new ItemStack(Items.DIAMOND_SWORD);
        encodedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("encoded-components"));
        ItemStack producedOutput = new ItemStack(Items.DIAMOND_SWORD, 10);
        producedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("produced-components"));
        DynamicComponentPatternDetails dynamic = dynamicPattern(
                new ItemStack(Items.IRON_INGOT), encodedOutput, null);
        ScaledProcessingPattern scaled = new ScaledProcessingPattern(dynamic, 10L);

        DynamicPatternExecution.Resolved resolved = DynamicPatternExecution.resolve(scaled);
        assertNotNull(resolved);
        assertEquals(dynamic, resolved.pattern());
        assertEquals(10L, resolved.copies());
        DynamicPatternExecution.Resolved nested = DynamicPatternExecution.resolve(
                new ScaledProcessingPattern(scaled, 3L));
        assertNotNull(nested);
        assertEquals(dynamic, nested.pattern());
        assertEquals(30L, nested.copies());

        DynamicPatternCpuStateManager manager = DynamicPatternCpuStateManager.INSTANCE;
        Object logic = new Object();
        UUID craftingId = UUID.randomUUID();
        manager.registerExpectedOutputs(
                logic, craftingId, resolved.pattern(), null, resolved.copies());
        assertEquals(10L, manager.getRemainingForItem(
                logic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));

        AEItemKey producedKey = Objects.requireNonNull(AEItemKey.of(producedOutput));
        var firstClaim = manager.claim(logic, producedKey, 4L, Actionable.MODULATE);
        assertEquals(4L, firstClaim.claimedAmount());
        assertEquals(AEItemKey.of(encodedOutput), firstClaim.claims().getFirst().exactExpectedKey());

        CompoundTag saved = Objects.requireNonNull(manager.writeToTag(logic, REGISTRIES));
        Object restoredLogic = new Object();
        manager.readFromTag(restoredLogic, craftingId, saved, REGISTRIES);
        assertEquals(6L, manager.getRemainingForItem(
                restoredLogic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));
        assertEquals(6L, manager.claim(
                restoredLogic, producedKey, 6L, Actionable.MODULATE).claimedAmount());
        assertFalse(manager.hasAnyPending(restoredLogic));

        manager.clear(logic);
        manager.clear(restoredLogic);
    }

    @Test
    void samePatternAggregatesWhileDifferentPatternsWithSameOutputRemainAmbiguous() {
        ItemStack firstInput = new ItemStack(Items.IRON_INGOT);
        firstInput.set(DataComponents.CUSTOM_NAME, Component.literal("first-definition"));
        ItemStack secondInput = new ItemStack(Items.IRON_INGOT);
        secondInput.set(DataComponents.CUSTOM_NAME, Component.literal("second-definition"));
        DynamicComponentPatternDetails first = dynamicPattern(
                firstInput, new ItemStack(Items.DIAMOND_SWORD), null);
        DynamicComponentPatternDetails second = dynamicPattern(
                secondInput, new ItemStack(Items.DIAMOND_SWORD), null);

        Object directLogic = new Object();
        DynamicPatternCpuStateManager manager = DynamicPatternCpuStateManager.INSTANCE;
        UUID directCraftingId = UUID.randomUUID();
        manager.registerExpectedOutputs(directLogic, directCraftingId, first, null, 1);
        manager.registerExpectedOutputs(directLogic, directCraftingId, first, null, 1);
        assertEquals(1, manager.snapshotPending(directLogic).size());
        assertEquals(2, manager.getRemainingForItem(
                directLogic, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));

        Object managedLogic = new Object();
        UUID craftingId = UUID.randomUUID();
        try {
            manager.registerExpectedOutputs(managedLogic, craftingId, first, null, 1);
            assertFalse(manager.hasAmbiguousOutputRegistration(managedLogic, first));
            assertTrue(manager.hasAmbiguousOutputRegistration(
                    managedLogic, second));
        } finally {
            manager.clear(directLogic);
            manager.clear(managedLogic);
        }
    }

    @Test
    void newlyCraftedComponentVariantCanBeExtractedByParentIdOnlyInput() {
        ItemStack jeiInput = new ItemStack(Items.DIAMOND_SWORD);
        jeiInput.set(DataComponents.CUSTOM_NAME, Component.literal("jei-components"));
        ItemStack actualInput = new ItemStack(Items.DIAMOND_SWORD);
        actualInput.set(DataComponents.CUSTOM_NAME, Component.literal("newly-crafted-components"));
        DynamicComponentPatternDetails parent = dynamicPattern(
                jeiInput, new ItemStack(Items.NETHER_STAR), new ItemStack(Items.DIAMOND_SWORD));

        ListCraftingInventory inventory = inventoryWith(actualInput);
        KeyCounter[] extracted = CraftingCpuHelper.extractPatternInputs(
                parent, inventory, null, new KeyCounter(), new KeyCounter());

        assertNotNull(extracted);
        AEItemKey actualKey = Objects.requireNonNull(AEItemKey.of(actualInput));
        assertEquals(1, extracted[0].get(actualKey));
        assertEquals(0, inventory.list.get(actualKey));

        DynamicComponentPatternDetails strictParent = dynamicPattern(
                jeiInput, new ItemStack(Items.NETHER_STAR), null, false, true);
        ListCraftingInventory strictInventory = inventoryWith(actualInput);
        assertNull(CraftingCpuHelper.extractPatternInputs(
                strictParent, strictInventory, null, new KeyCounter(), new KeyCounter()));
        assertEquals(1, strictInventory.list.get(actualKey));

        ListCraftingInventory unrelatedInventory = inventoryWith(new ItemStack(Items.DIAMOND_PICKAXE));
        assertNull(CraftingCpuHelper.extractPatternInputs(
                parent, unrelatedInventory, null, new KeyCounter(), new KeyCounter()));
    }

    @Test
    void idOnlyInputKeepsCanonicalChildOutputAsPrimaryPlanningKey() {
        ItemStack jeiInput = new ItemStack(Items.DIAMOND_SWORD);
        jeiInput.set(DataComponents.CUSTOM_NAME, Component.literal("jei-components"));
        ItemStack canonicalOutput = new ItemStack(Items.DIAMOND_SWORD);
        canonicalOutput.set(DataComponents.CUSTOM_NAME, Component.literal("canonical-child-output"));
        DynamicComponentPatternDetails parent = dynamicPattern(
                jeiInput, new ItemStack(Items.NETHER_STAR), canonicalOutput);

        GenericStack[] candidates = parent.getInputs()[0].getPossibleInputs();

        assertEquals(2, candidates.length);
        assertEquals(AEItemKey.of(canonicalOutput), candidates[0].what());
        assertEquals(AEItemKey.of(jeiInput), candidates[1].what());
        assertTrue(parent.getInputs()[0].isValid(AEItemKey.of(Items.DIAMOND_SWORD), null));
    }

    @Test
    void sparseProcessingInputsPushSelectedComponentsInPhysicalSlotOrder() {
        ItemStack jeiSword = new ItemStack(Items.DIAMOND_SWORD);
        jeiSword.set(DataComponents.CUSTOM_NAME, Component.literal("jei-components"));
        ItemStack craftedSword = new ItemStack(Items.DIAMOND_SWORD);
        craftedSword.set(DataComponents.CUSTOM_NAME, Component.literal("newly-crafted-components"));

        GenericStack encodedSword = Objects.requireNonNull(GenericStack.fromItemStack(jeiSword));
        GenericStack encodedIron = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT)));
        GenericStack encodedOutput = Objects.requireNonNull(
                GenericStack.fromItemStack(new ItemStack(Items.NETHER_STAR)));
        ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(encodedSword, encodedIron, encodedSword), List.of(encodedOutput));
        AEProcessingPattern source = new AEProcessingPattern(
                Objects.requireNonNull(AEItemKey.of(encodedPattern)));
        DynamicComponentPatternDetails dynamic = new DynamicComponentPatternDetails(
                source, List.of(0), List.of(0), REGISTRIES);

        AEItemKey craftedKey = Objects.requireNonNull(AEItemKey.of(craftedSword));
        AEItemKey ironKey = Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT));
        KeyCounter swords = new KeyCounter();
        swords.add(craftedKey, 2);
        KeyCounter iron = new KeyCounter();
        iron.add(ironKey, 1);
        List<GenericStack> pushed = new ArrayList<>();

        dynamic.pushInputsToExternalInventory(
                new KeyCounter[]{swords, iron},
                (key, amount) -> pushed.add(new GenericStack(key, amount)));

        assertEquals(List.of(
                new GenericStack(craftedKey, 1),
                new GenericStack(ironKey, 1),
                new GenericStack(craftedKey, 1)), pushed);
    }

    @Test
    void optionalCpuTargetsMayBeAbsent() {
        assertDoesNotThrow(() -> Class.forName("appeng.crafting.execution.ExecutingCraftingJob"));
        assertDoesNotThrow(() -> Class.forName("appeng.crafting.execution.CraftingCpuLogic"));
        assertNull(DynamicReflectionSupport.findClassSafe(
                "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic"));
        assertNull(DynamicReflectionSupport.findClassSafe(
                "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic"));
    }

    private static ListCraftingInventory inventoryWith(ItemStack stack) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {
        });
        AEItemKey key = Objects.requireNonNull(AEItemKey.of(stack));
        inventory.insert(key, stack.getCount(), Actionable.MODULATE);
        return inventory;
    }

    private static DynamicComponentPatternDetails dynamicPattern(
            ItemStack input, ItemStack output, @Nullable ItemStack canonicalInput) {
        return dynamicPattern(input, output, canonicalInput, true, true);
    }

    private static DynamicComponentPatternDetails dynamicPattern(
            ItemStack input, ItemStack output, @Nullable ItemStack canonicalInput,
            boolean inputIdOnly, boolean outputIdOnly) {
        GenericStack encodedInput = Objects.requireNonNull(GenericStack.fromItemStack(input));
        GenericStack encodedOutput = Objects.requireNonNull(GenericStack.fromItemStack(output));
        ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(encodedInput), List.of(encodedOutput));
        AEProcessingPattern source = new AEProcessingPattern(
                Objects.requireNonNull(AEItemKey.of(encodedPattern)));
        AEProcessingPattern execution = canonicalInput == null
                ? source
                : AdvancedAlloyFurnacePatternResolver.withCanonicalInputs(
                source, Map.of(0, canonicalInput));
        return new DynamicComponentPatternDetails(
                execution,
                inputIdOnly ? List.of(0) : List.of(),
                outputIdOnly ? List.of(0) : List.of(),
                REGISTRIES);
    }

    private static IPatternDetails.IInput input(GenericStack possibleInput, long multiplier) {
        return new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return new GenericStack[]{possibleInput};
            }

            @Override
            public long getMultiplier() {
                return multiplier;
            }

            @Override
            public boolean isValid(AEKey input, Level level) {
                return input.equals(possibleInput.what());
            }

            @Override
            @Nullable
            public AEKey getRemainingKey(AEKey template) {
                return null;
            }
        };
    }
}
