package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuOwner;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuState;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.Ae2OverloadPatternDetails;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternSupport;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    void completePatternDefinitionSeparatesOtherwiseIdenticalOverloadLayouts() {
        IdentifiedOverloadPatternDetails sword = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD), null);
        IdentifiedOverloadPatternDetails pickaxe = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_PICKAXE), null);
        IdentifiedOverloadPatternDetails shovel = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SHOVEL), null);
        IdentifiedOverloadPatternDetails swordReloaded = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD), null);

        assertNotEquals(sword.overloadPatternIdentity(), pickaxe.overloadPatternIdentity());
        assertNotEquals(sword.overloadPatternIdentity(), shovel.overloadPatternIdentity());
        assertNotEquals(pickaxe.overloadPatternIdentity(), shovel.overloadPatternIdentity());
        assertEquals(sword.overloadPatternIdentity(), swordReloaded.overloadPatternIdentity());
    }

    @Test
    void cpuTracksSameLayoutOutputsIndependentlyAndRestoresThem() {
        IdentifiedOverloadPatternDetails sword = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD), null);
        IdentifiedOverloadPatternDetails pickaxe = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_PICKAXE), null);
        IdentifiedOverloadPatternDetails shovel = overloadPattern(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SHOVEL), null);
        Object logic = new Object();
        UUID craftingId = UUID.randomUUID();
        OverloadCpuOwner owner = OverloadCpuOwner.from(craftingId, logic);
        OverloadCpuState state = new OverloadCpuState(owner);

        registerOutput(state, sword);
        registerOutput(state, pickaxe);
        registerOutput(state, shovel);

        assertEquals(3, state.allPending().size());
        assertEquals(1, state.getRemainingForItem(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));
        assertEquals(1, state.getRemainingForItem(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE)));
        assertEquals(1, state.getRemainingForItem(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SHOVEL)));

        OverloadCpuState restored = OverloadCpuState.fromTag(owner, state.toTag(REGISTRIES), REGISTRIES);
        assertEquals(3, restored.allPending().size());
        assertEquals(1, restored.claimByItemId(
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE), 1, true).claimedAmount());
        assertEquals(0, restored.getRemainingForItem(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE)));
        assertEquals(1, restored.getRemainingForItem(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));
        assertEquals(1, restored.getRemainingForItem(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SHOVEL)));
    }

    @Test
    void samePatternAggregatesWhileDifferentPatternsWithSameOutputRemainAmbiguous() {
        ItemStack firstInput = new ItemStack(Items.IRON_INGOT);
        firstInput.set(DataComponents.CUSTOM_NAME, Component.literal("first-definition"));
        ItemStack secondInput = new ItemStack(Items.IRON_INGOT);
        secondInput.set(DataComponents.CUSTOM_NAME, Component.literal("second-definition"));
        IdentifiedOverloadPatternDetails first = overloadPattern(
                firstInput, new ItemStack(Items.DIAMOND_SWORD), null);
        IdentifiedOverloadPatternDetails second = overloadPattern(
                secondInput, new ItemStack(Items.DIAMOND_SWORD), null);

        Object directLogic = new Object();
        OverloadCpuState directState = new OverloadCpuState(
                OverloadCpuOwner.from(UUID.randomUUID(), directLogic));
        registerOutput(directState, first);
        registerOutput(directState, first);
        assertEquals(1, directState.allPending().size());
        assertEquals(2, directState.getRemainingForItem(
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)));

        Object managedLogic = new Object();
        UUID craftingId = UUID.randomUUID();
        OverloadCpuStateManager manager = OverloadCpuStateManager.INSTANCE;
        try {
            OverloadPatternReference firstReference = reference(first);
            manager.registerExpectedOutputs(
                    managedLogic, craftingId, firstReference, first.overloadPatternDetailsView(),
                    first.getOutputs(), null, 1);
            assertFalse(manager.hasAmbiguousOutputRegistration(
                    managedLogic, firstReference, first.overloadPatternDetailsView()));
            assertTrue(manager.hasAmbiguousOutputRegistration(
                    managedLogic, reference(second), second.overloadPatternDetailsView()));
        } finally {
            manager.clear(managedLogic);
        }
    }

    @Test
    void newlyCraftedComponentVariantCanBeExtractedByParentIdOnlyInput() {
        ItemStack jeiInput = new ItemStack(Items.DIAMOND_SWORD);
        jeiInput.set(DataComponents.CUSTOM_NAME, Component.literal("jei-components"));
        ItemStack actualInput = new ItemStack(Items.DIAMOND_SWORD);
        actualInput.set(DataComponents.CUSTOM_NAME, Component.literal("newly-crafted-components"));
        IdentifiedOverloadPatternDetails parent = overloadPattern(
                jeiInput, new ItemStack(Items.NETHER_STAR), new ItemStack(Items.DIAMOND_SWORD));

        ListCraftingInventory inventory = inventoryWith(actualInput);
        KeyCounter[] extracted = CraftingCpuHelper.extractPatternInputs(
                parent, inventory, null, new KeyCounter(), new KeyCounter());

        assertNotNull(extracted);
        AEItemKey actualKey = Objects.requireNonNull(AEItemKey.of(actualInput));
        assertEquals(1, extracted[0].get(actualKey));
        assertEquals(0, inventory.list.get(actualKey));

        IdentifiedOverloadPatternDetails strictParent = overloadPattern(
                jeiInput, new ItemStack(Items.NETHER_STAR), null, MatchMode.STRICT, MatchMode.ID_ONLY);
        ListCraftingInventory strictInventory = inventoryWith(actualInput);
        assertNull(CraftingCpuHelper.extractPatternInputs(
                strictParent, strictInventory, null, new KeyCounter(), new KeyCounter()));
        assertEquals(1, strictInventory.list.get(actualKey));

        ListCraftingInventory unrelatedInventory = inventoryWith(new ItemStack(Items.DIAMOND_PICKAXE));
        assertNull(CraftingCpuHelper.extractPatternInputs(
                parent, unrelatedInventory, null, new KeyCounter(), new KeyCounter()));
    }

    @Test
    void restoredJobMixinTargetsLoadWithoutInjectionFailure() {
        assertDoesNotThrow(() -> Class.forName("appeng.crafting.execution.ExecutingCraftingJob"));
        assertDoesNotThrow(() -> Class.forName(
                "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob"));
    }

    private static ListCraftingInventory inventoryWith(ItemStack stack) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {
        });
        AEItemKey key = Objects.requireNonNull(AEItemKey.of(stack));
        inventory.insert(key, stack.getCount(), Actionable.MODULATE);
        return inventory;
    }

    private static void registerOutput(
            OverloadCpuState state, IdentifiedOverloadPatternDetails pattern) {
        state.registerExpectedOutputs(
                reference(pattern), pattern.overloadPatternDetailsView(), pattern.getOutputs(), null, 1);
    }

    private static OverloadPatternReference reference(IdentifiedOverloadPatternDetails pattern) {
        return new OverloadPatternReference(
                pattern.overloadPatternIdentity(), pattern.overloadPatternDetailsView().sourcePattern());
    }

    private static IdentifiedOverloadPatternDetails overloadPattern(
            ItemStack input, ItemStack output, @Nullable ItemStack canonicalInput) {
        return overloadPattern(input, output, canonicalInput, MatchMode.ID_ONLY, MatchMode.ID_ONLY);
    }

    private static IdentifiedOverloadPatternDetails overloadPattern(
            ItemStack input, ItemStack output, @Nullable ItemStack canonicalInput,
            MatchMode inputMode, MatchMode outputMode) {
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
        var parsed = OverloadPatternSupport.toParsedDefinition(encodedPattern, source, REGISTRIES);
        var encoding = EncodedOverloadPattern.builder()
                .input(0, inputMode)
                .output(0, outputMode)
                .build();
        var details = new OverloadPatternDetails(parsed, encoding);
        var delegate = new Ae2OverloadPatternDetails(source.getDefinition(), details, execution);
        return new IdentifiedOverloadPatternDetails(delegate, REGISTRIES);
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
