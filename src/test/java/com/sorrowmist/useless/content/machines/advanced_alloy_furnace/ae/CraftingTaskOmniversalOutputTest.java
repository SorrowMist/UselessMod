package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTaskOmniversalOutputTest {
    private static Level level;
    private static Unsafe unsafe;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        level = (Level) unsafe.allocateInstance(ServerLevel.class);
    }

    @Test
    void hiddenSecondaryOutputIsStillGeneratedFromBoundRecipe() throws Exception {
        AdvancedAlloyFurnaceRecipe recipe = recipe();
        AEProcessingPattern source = processingPattern(
                new ItemStack(Items.IRON_INGOT), List.of(new ItemStack(Items.DIAMOND)));
        OmniversalPatternDetails pattern = omniversalPattern(source, recipe);

        KeyCounter input = new KeyCounter();
        input.add(Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT)), 1L);
        CraftingTaskContext context = context();
        CraftingTask task = new CraftingTask(
                1, pattern, new KeyCounter[]{input}, 1L, context);

        assertTrue(task.canStartNow());

        Method generate = CraftingTask.class.getDeclaredMethod("generatePendingOutputs", long.class);
        generate.setAccessible(true);
        generate.invoke(task, 1L);

        List<?> pending = pendingOutputKeys(task);
        assertEquals(2, pending.size());
        assertOutput(pending.get(0), Items.GOLD_INGOT, 3L);
        assertOutput(pending.get(1), Items.DIAMOND, 1L);
    }

    private static CraftingTaskContext context() {
        return (CraftingTaskContext) Proxy.newProxyInstance(
                CraftingTaskContext.class.getClassLoader(),
                new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getLevel" -> level;
                    case "supportsLongAeAmounts" -> true;
                    default -> method.isDefault()
                            ? InvocationHandler.invokeDefault(proxy, method,
                            arguments == null ? new Object[0] : arguments)
                            : defaultValue(method.getReturnType());
                });
    }

    private static OmniversalPatternDetails omniversalPattern(
            AEProcessingPattern source, AdvancedAlloyFurnaceRecipe recipe) throws Exception {
        OmniversalPatternDetails pattern = (OmniversalPatternDetails)
                unsafe.allocateInstance(OmniversalPatternDetails.class);
        setObject(DynamicComponentPatternDetails.class, pattern, "definition", source.getDefinition());
        setObject(DynamicComponentPatternDetails.class, pattern, "source", source);
        setObject(DynamicComponentPatternDetails.class, pattern, "inputs", source.getInputs());
        setObject(DynamicComponentPatternDetails.class, pattern, "outputs", source.getOutputs());
        setObject(DynamicComponentPatternDetails.class, pattern, "itemIdInputs",
                new boolean[source.getInputs().length]);
        setObject(DynamicComponentPatternDetails.class, pattern, "itemIdOutputs",
                new boolean[source.getOutputs().size()]);
        setBoolean(DynamicComponentPatternDetails.class, pattern, "hasInputMatchers", false);
        setObject(DynamicComponentPatternDetails.class, pattern, "identity", "test:omniversal");
        setObject(OmniversalPatternDetails.class, pattern, "data", new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION, recipe.id(), "test-fingerprint",
                false, Optional.empty(), List.of(), List.of()));
        setObject(OmniversalPatternDetails.class, pattern, "recipe", recipe);
        return pattern;
    }

    private static void setObject(Class<?> declaringClass, Object target,
                                  String fieldName, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static void setBoolean(Class<?> declaringClass, Object target,
                                   String fieldName, boolean value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        unsafe.putBoolean(target, unsafe.objectFieldOffset(field), value);
    }

    private static AEProcessingPattern processingPattern(ItemStack input, List<ItemStack> outputs) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(Objects.requireNonNull(GenericStack.fromItemStack(input))),
                outputs.stream().map(stack -> Objects.requireNonNull(GenericStack.fromItemStack(stack))).toList());
        return new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(encoded)));
    }

    private static AdvancedAlloyFurnaceRecipe recipe() {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "omniversal_outputs"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1L)),
                List.of(), List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.DIAMOND)),
                List.of(), List.of(),
                1L, 20, Ingredient.EMPTY, 0, Ingredient.EMPTY, AlloyFurnaceMode.NORMAL);
    }

    @SuppressWarnings("unchecked")
    private static List<?> pendingOutputKeys(CraftingTask task) throws ReflectiveOperationException {
        Field field = CraftingTask.class.getDeclaredField("pendingOutputKeys");
        field.setAccessible(true);
        return (List<?>) field.get(task);
    }

    private static void assertOutput(Object pending, net.minecraft.world.item.Item item, long amount)
            throws ReflectiveOperationException {
        Field key = pending.getClass().getDeclaredField("key");
        Field count = pending.getClass().getDeclaredField("amount");
        key.setAccessible(true);
        count.setAccessible(true);
        assertEquals(AEItemKey.of(item), key.get(pending));
        assertEquals(amount, count.getLong(pending));
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
}
