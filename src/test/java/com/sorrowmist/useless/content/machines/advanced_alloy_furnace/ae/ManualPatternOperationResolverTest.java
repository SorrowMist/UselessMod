package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualPatternOperationResolverTest {
    private static HolderLookup.Provider registries;
    private static Level level;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(ServerLevel.class);
    }

    @Test
    void manualTenThousandFoldPatternUsesTenThousandRecipeOperations() {
        CraftingTask task = new CraftingTask(
                1, pattern(10_000L), inputs(10_000L), 1L, context(recipe()));

        assertTrue(task.canStartNow());

        var saved = task.save(registries);
        assertEquals(10_000L, saved.getLong("CraftCount"));
        assertEquals(10_000L, saved.getLong("PatternOperationsPerPush"));
        assertTrue(saved.getBoolean("PatternOperationsResolved"));
        assertEquals(1L, CraftingTask.calculatePatternOutputRuns(10_000L, 10_000L));
    }

    @Test
    void rejectsAHighOutputPatternWhoseInputsOnlyCoverOneOperation() {
        CraftingTask task = new CraftingTask(
                2, pattern(10_000L), inputs(1L), 1L, context(recipe()));

        assertFalse(task.canStartNow());
    }

    @Test
    void manualMultiplierComposesWithSmartDoublingWithoutDoublingOutputsAgain() {
        // The task manager unwraps a smart-doubling wrapper before constructing the task, leaving
        // its 100 operations per push as craftCount and the manual 10x pattern as the definition.
        CraftingTask task = new CraftingTask(
                3, pattern(10L), inputs(1_000L), 100L, context(recipe()));

        assertTrue(task.canStartNow());

        var saved = task.save(registries);
        assertEquals(1_000L, saved.getLong("CraftCount"));
        assertEquals(10L, saved.getLong("PatternOperationsPerPush"));
        assertEquals(100L, CraftingTask.calculatePatternOutputRuns(1_000L, 10L));
    }

    private static CraftingTaskContext context(AdvancedAlloyFurnaceRecipe recipe) {
        return (CraftingTaskContext) Proxy.newProxyInstance(
                CraftingTaskContext.class.getClassLoader(),
                new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getLevel" -> level;
                    case "resolveTaskRecipe" -> recipe;
                    default -> method.isDefault()
                            ? InvocationHandler.invokeDefault(proxy, method,
                            arguments == null ? new Object[0] : arguments)
                            : defaultValue(method.getReturnType());
                });
    }

    private static AdvancedAlloyFurnaceRecipe recipe() {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "manual_pattern"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1L)),
                List.of(), List.of(), List.of(new ItemStack(Items.GOLD_INGOT)), List.of(), List.of(),
                1L, 1, Ingredient.EMPTY, 0, Ingredient.EMPTY, AlloyFurnaceMode.NORMAL);
    }

    private static KeyCounter[] inputs(long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT)), amount);
        return new KeyCounter[]{counter};
    }

    private static IPatternDetails pattern(long multiplier) {
        AEItemKey definition = Objects.requireNonNull(AEItemKey.of(Items.PAPER));
        AEItemKey input = Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT));
        AEItemKey output = Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT));
        return new IPatternDetails() {
            private final IInput[] inputs = {new TestInput(input, multiplier)};

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
                return List.of(new GenericStack(output, multiplier));
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }

    private record TestInput(AEItemKey key, long multiplier) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, 1L)};
        }

        @Override
        public long getMultiplier() {
            return multiplier;
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
}
