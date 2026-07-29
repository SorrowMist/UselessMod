package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingAEBatchProgressSyncTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void retryIsRequeuedBeforeProgressIsSynchronized() throws ReflectiveOperationException {
        AdvancedAlloyFurnaceAeManager manager = new AdvancedAlloyFurnaceAeManager(hostWithNoTaskCapacity());
        var batch = new AdvancedAlloyFurnaceAeManager.PendingAEBatch(pattern(), 1L);
        KeyCounter input = new KeyCounter();
        input.add(Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT)), 1L);
        batch.add(new KeyCounter[]{input});
        batch.ripeTimer = 1;
        pendingBatches(manager).put(new Object(), batch);

        List<Integer> synchronizedTaskCounts = new ArrayList<>();
        manager.flushAEBatches(() -> synchronizedTaskCounts.add(
                manager.getAETaskProgressList().size()));

        assertEquals(List.of(1), synchronizedTaskCounts);
        assertEquals(1, manager.getAETaskProgressList().size());
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, AdvancedAlloyFurnaceAeManager.PendingAEBatch> pendingBatches(
            AdvancedAlloyFurnaceAeManager manager) throws ReflectiveOperationException {
        Field field = AdvancedAlloyFurnaceAeManager.class.getDeclaredField("aePendingBatches");
        field.setAccessible(true);
        return (Map<Object, AdvancedAlloyFurnaceAeManager.PendingAEBatch>) field.get(manager);
    }

    private static AlloyFurnaceAeHost hostWithNoTaskCapacity() {
        return (AlloyFurnaceAeHost) Proxy.newProxyInstance(
                AlloyFurnaceAeHost.class.getClassLoader(),
                new Class<?>[]{AlloyFurnaceAeHost.class},
                (ignored, method, arguments) -> method.getName().equals("getMaxAETaskCount")
                        ? 0 : defaultValue(method.getReturnType()));
    }

    private static IPatternDetails pattern() {
        AEItemKey definition = Objects.requireNonNull(AEItemKey.of(Items.PAPER));
        AEItemKey input = Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT));
        AEItemKey output = Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT));
        return new IPatternDetails() {
            private final IInput[] inputs = {new TestInput(input)};

            @Override
            public AEItemKey getDefinition() {
                return definition;
            }

            @Override
            public IInput[] getInputs() {
                return inputs;
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(output, 1L));
            }
        };
    }

    private record TestInput(AEItemKey key) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, 1L)};
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.equals(input);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }
}
