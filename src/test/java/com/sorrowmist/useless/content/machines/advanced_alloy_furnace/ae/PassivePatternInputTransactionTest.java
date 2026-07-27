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
        KeyCounter available = counter(gold, 12);

        var planned = PassivePatternInputTransaction.plan(pattern, 4, null, available);

        assertEquals(PassivePatternInputTransaction.Failure.NONE, planned.failure());
        assertEquals(12, planned.inputs()[0].get(gold));
        assertEquals(12, planned.consumed().get(gold));
    }

    @Test
    void relaxedItemIdInputKeepsTheActualStoredComponentKey() {
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Stored variant"));
        AEItemKey canonical = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey stored = AEItemKey.of(namedIron);
        IPatternDetails pattern = pattern(input(1,
                key -> key instanceof AEItemKey itemKey && itemKey.getItem() == Items.IRON_INGOT,
                canonical));

        var planned = PassivePatternInputTransaction.plan(
                pattern, 1, null, counter(stored, 1));

        assertEquals(PassivePatternInputTransaction.Failure.NONE, planned.failure());
        assertEquals(1, planned.inputs()[0].get(stored));
        assertEquals(0, planned.inputs()[0].get(canonical));
    }

    @Test
    void supportsFluidKeys() {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        IPatternDetails pattern = pattern(input(1_000, water::equals, water));

        var planned = PassivePatternInputTransaction.plan(
                pattern, 2, null, counter(water, 2_000));

        assertEquals(PassivePatternInputTransaction.Failure.NONE, planned.failure());
        assertEquals(2_000, planned.inputs()[0].get(water));
    }

    @Test
    void sharedInventoryGivesEarlierSlotsPriorityWithoutPartialConsumption() {
        AEItemKey iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        IPatternDetails pattern = pattern(input(2, iron::equals, iron));
        TestStorage storage = new TestStorage(counter(iron, 3));
        KeyCounter snapshot = storage.getAvailableStacks();

        List<PassivePatternInputTransaction.Result> results =
                PassivePatternInputTransaction.extractAll(
                        List.of(pattern, pattern), 1, null, storage, SOURCE, snapshot,
                        (key, amount) -> { });

        assertEquals(PassivePatternInputTransaction.Failure.NONE, results.get(0).failure());
        assertEquals(PassivePatternInputTransaction.Failure.MISSING_INPUT,
                results.get(1).failure());
        assertEquals(1, storage.amount(iron));
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
                        List.of(missing, available), 1, null, storage, SOURCE,
                        storage.getAvailableStacks(), (key, amount) -> { });

        assertEquals(PassivePatternInputTransaction.Failure.MISSING_INPUT,
                results.get(0).failure());
        assertEquals(PassivePatternInputTransaction.Failure.NONE, results.get(1).failure());
        assertEquals(0, storage.amount(iron));
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
                List.of(pattern), 1, null, storage, SOURCE, storage.getAvailableStacks(),
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

        var planned = PassivePatternInputTransaction.plan(
                pattern, 2, null, counter(iron, Long.MAX_VALUE));

        assertEquals(PassivePatternInputTransaction.Failure.AMOUNT_OVERFLOW,
                planned.failure());
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
            contents.forEach(out::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("test storage");
        }

        private long amount(AEKey key) {
            return contents.getOrDefault(key, 0L);
        }
    }
}
