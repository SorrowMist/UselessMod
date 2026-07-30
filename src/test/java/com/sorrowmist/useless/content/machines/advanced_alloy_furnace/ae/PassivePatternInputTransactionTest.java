package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassivePatternInputTransactionTest {
    private static final IActionSource SOURCE = IActionSource.empty();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void usesSubstitutesAndScalesTheSelectedInput() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey gold = AEItemKey.of(new ItemStack(Items.GOLD_INGOT));
        IPatternDetails pattern = pattern(input(3, key -> key.equals(iron) || key.equals(gold),
                iron, gold));
        TestStorage storage = new TestStorage(counter(gold, 12));

        var result = extract(pattern, 4, storage);

        assertEquals(PassivePatternInputTransaction.Failure.NONE, result.failure());
        assertEquals(12, result.inputs()[0].get(gold));
        assertEquals(0, storage.amount(gold));
        assertNoInventoryEnumeration(storage);
    }

    @Test
    void relaxedItemIdInputKeepsTheActualStoredComponentKey() {
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Stored variant"));
        AEItemKey canonical = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey stored = AEItemKey.of(namedIron);
        IPatternDetails pattern = dynamicPattern(true, input(1,
                key -> {
                    if (key instanceof AEItemKey itemKey && itemKey.getItem() != Items.IRON_INGOT) {
                        throw new AssertionError("Item-id lookup returned an unrelated item");
                    }
                    return key instanceof AEItemKey itemKey && itemKey.getItem() == Items.IRON_INGOT;
                },
                canonical));

        KeyCounter contents = counter(stored, 1);
        contents.add(AEItemKey.of(new ItemStack(Items.GOLD_INGOT)), 64);
        TestStorage storage = new TestStorage(contents);
        var result = extract(pattern, 1, storage);

        assertEquals(PassivePatternInputTransaction.Failure.NONE, result.failure());
        assertEquals(1, result.inputs()[0].get(stored));
        assertEquals(0, result.inputs()[0].get(canonical));
        assertEquals(0, storage.availableStackRequests);
        assertEquals(1, storage.cachedInventoryRequests);
    }

    @Test
    void exactDynamicInputDoesNotScanUnrelatedNetworkKeys() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey gold = AEItemKey.of(new ItemStack(Items.GOLD_INGOT));
        IPatternDetails pattern = dynamicPattern(false, input(1, key -> {
            if (key.equals(gold)) {
                throw new AssertionError("Exact component slot scanned an unrelated network key");
            }
            return key.equals(iron);
        }, iron));

        TestStorage storage = new TestStorage(counter(gold, 1));
        var result = extract(pattern, 1, storage);

        assertEquals(PassivePatternInputTransaction.Failure.MISSING_INPUT, result.failure());
        assertNoInventoryEnumeration(storage);
    }

    @Test
    void supportsFluidKeys() {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        IPatternDetails pattern = pattern(input(1_000, water::equals, water));

        TestStorage storage = new TestStorage(counter(water, 2_000));
        var result = extract(pattern, 2, storage);

        assertEquals(PassivePatternInputTransaction.Failure.NONE, result.failure());
        assertEquals(2_000, result.inputs()[0].get(water));
        assertNoInventoryEnumeration(storage);
    }

    @Test
    void sharedInventoryGivesEarlierSlotsPriorityWithoutPartialConsumption() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        IPatternDetails pattern = pattern(input(2, iron::equals, iron));
        TestStorage storage = new TestStorage(counter(iron, 3));

        List<PassivePatternInputTransaction.Result> results =
                PassivePatternInputTransaction.extractAll(
                        List.of(pattern, pattern), 1, null, storage, storage::cachedInventory, SOURCE,
                        (key, amount) -> { });

        assertEquals(PassivePatternInputTransaction.Failure.NONE, results.get(0).failure());
        assertEquals(PassivePatternInputTransaction.Failure.MISSING_INPUT,
                results.get(1).failure());
        assertEquals(1, storage.amount(iron));
        assertNoInventoryEnumeration(storage);
    }

    @Test
    void missingPatternDoesNotReserveMaterialsFromLaterSlots() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey diamond = AEItemKey.of(new ItemStack(Items.DIAMOND));
        IPatternDetails missing = pattern(input(2, diamond::equals, diamond));
        IPatternDetails available = pattern(input(2, iron::equals, iron));
        TestStorage storage = new TestStorage(counter(iron, 2));

        List<PassivePatternInputTransaction.Result> results =
                PassivePatternInputTransaction.extractAll(
                        List.of(missing, available), 1, null, storage, storage::cachedInventory, SOURCE,
                        (key, amount) -> { });

        assertEquals(PassivePatternInputTransaction.Failure.MISSING_INPUT,
                results.get(0).failure());
        assertEquals(PassivePatternInputTransaction.Failure.NONE, results.get(1).failure());
        assertEquals(0, storage.amount(iron));
        assertNoInventoryEnumeration(storage);
    }

    @Test
    void partialCommitRollsBackEveryAlreadyExtractedKey() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey gold = AEItemKey.of(new ItemStack(Items.GOLD_INGOT));
        IPatternDetails pattern = pattern(
                input(2, iron::equals, iron), input(2, gold::equals, gold));
        KeyCounter contents = counter(iron, 2);
        contents.add(gold, 2);
        TestStorage storage = new TestStorage(contents);
        storage.failModulationCall = 2;
        KeyCounter unreturned = new KeyCounter();

        var result = PassivePatternInputTransaction.extractAll(
                List.of(pattern), 1, null, storage, storage::cachedInventory, SOURCE,
                unreturned::add).getFirst();

        assertEquals(PassivePatternInputTransaction.Failure.STORAGE_CHANGED, result.failure());
        assertEquals(2, storage.amount(iron));
        assertEquals(2, storage.amount(gold));
        assertTrue(unreturned.isEmpty());
    }

    @Test
    void rejectsMultiplicationOverflowBeforeExtraction() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        IPatternDetails pattern = pattern(input(Long.MAX_VALUE, iron::equals, iron));

        TestStorage storage = new TestStorage(counter(iron, Long.MAX_VALUE));
        var result = extract(pattern, 2, storage);

        assertEquals(PassivePatternInputTransaction.Failure.AMOUNT_OVERFLOW,
                result.failure());
        assertEquals(Long.MAX_VALUE, storage.amount(iron));
    }

    @Test
    void acceptsLongMaxOperationsWhenTheScaledAmountStillFits() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        IPatternDetails pattern = pattern(input(1L, iron::equals, iron));

        TestStorage storage = new TestStorage(counter(iron, Long.MAX_VALUE));
        var result = extract(pattern, Long.MAX_VALUE, storage);

        assertEquals(PassivePatternInputTransaction.Failure.NONE, result.failure());
        assertEquals(Long.MAX_VALUE, result.inputs()[0].get(iron));
        assertEquals(0, storage.amount(iron));
    }

    private static PassivePatternInputTransaction.Result extract(
            IPatternDetails pattern, long operations, TestStorage storage) {
        return PassivePatternInputTransaction.extract(
                pattern, operations, null, storage, storage::cachedInventory, SOURCE,
                (key, amount) -> { });
    }

    private static void assertNoInventoryEnumeration(TestStorage storage) {
        assertEquals(0, storage.availableStackRequests);
        assertEquals(0, storage.cachedInventoryRequests);
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                return AEItemKey.of(new ItemStack(Items.PAPER));
            }

            @Override
            public IInput[] getInputs() {
                return inputs;
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(
                        AEItemKey.of(new ItemStack(Items.PAPER)), 1));
            }
        };
    }

    private static DynamicComponentPattern dynamicPattern(
            boolean itemIdInput, IPatternDetails.IInput... inputs) {
        return new DynamicComponentPattern() {
            @Override
            public String dynamicPatternIdentity() {
                return "test:passive_input";
            }

            @Override
            public boolean isItemIdInput(int slot) {
                return itemIdInput;
            }

            @Override
            public boolean isItemIdOutput(int slot) {
                return false;
            }

            @Override
            public boolean usesDynamicOutputs() {
                return false;
            }

            @Override
            public AEItemKey getDefinition() {
                return AEItemKey.of(new ItemStack(Items.PAPER));
            }

            @Override
            public IInput[] getInputs() {
                return inputs;
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(
                        AEItemKey.of(new ItemStack(Items.PAPER)), 1));
            }
        };
    }

    private static IPatternDetails.IInput input(
            long multiplier, Predicate<AEKey> validator, AEKey... possible) {
        GenericStack[] stacks = new GenericStack[possible.length];
        for (int index = 0; index < possible.length; index++) {
            stacks[index] = new GenericStack(possible[index], multiplier);
        }
        return new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return stacks;
            }

            @Override
            public long getMultiplier() {
                return multiplier;
            }

            @Override
            public boolean isValid(AEKey input, Level level) {
                return validator.test(input);
            }

            @Override
            public AEKey getRemainingKey(AEKey template) {
                return null;
            }
        };
    }

    private static final class TestStorage implements MEStorage {
        private final Map<AEKey, Long> contents = new LinkedHashMap<>();
        private int modulationCalls;
        private int failModulationCall;
        private int availableStackRequests;
        private int cachedInventoryRequests;

        private TestStorage(KeyCounter initial) {
            for (var entry : initial) {
                contents.put(entry.getKey(), entry.getLongValue());
            }
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                contents.merge(what, amount, Long::sum);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, amount(what));
            if (mode == Actionable.MODULATE) {
                modulationCalls++;
                if (modulationCalls == failModulationCall && extracted > 0) {
                    extracted--;
                }
                contents.put(what, amount(what) - extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            availableStackRequests++;
            contents.forEach(out::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("test storage");
        }

        private long amount(AEKey key) {
            return contents.getOrDefault(key, 0L);
        }

        private KeyCounter cachedInventory() {
            cachedInventoryRequests++;
            KeyCounter result = new KeyCounter();
            contents.forEach(result::add);
            return result;
        }
    }
}
