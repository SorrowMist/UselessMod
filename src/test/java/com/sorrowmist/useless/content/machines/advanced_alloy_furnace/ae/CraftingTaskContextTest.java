package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CraftingTaskContextTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requiredMoldWithNoItemSlotsDoesNotReadSlotZero() {
        CraftingTaskContext context = (CraftingTaskContext) Proxy.newProxyInstance(
                CraftingTaskContext.class.getClassLoader(),
                new Class<?>[]{CraftingTaskContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getItemHandler" -> new ItemStackHandler(0);
                    case "getMoldSlot" -> 0;
                    default -> method.isDefault()
                            ? InvocationHandler.invokeDefault(proxy, method,
                                    arguments == null ? new Object[0] : arguments)
                            : defaultValue(method.getReturnType());
                });

        CraftingTaskContext.TaskAvailability availability = context.getTaskAvailability(recipeRequiringMold());

        assertFalse(availability.available());
        assertEquals("gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                availability.statusKey());
    }

    private static AdvancedAlloyFurnaceRecipe recipeRequiringMold() {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "required_mold"),
                List.of(), List.of(), List.of(new ItemStack(Items.IRON_INGOT)), List.of(),
                1L, 1, Ingredient.EMPTY, 0, Ingredient.of(Items.IRON_INGOT), AlloyFurnaceMode.NORMAL);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
