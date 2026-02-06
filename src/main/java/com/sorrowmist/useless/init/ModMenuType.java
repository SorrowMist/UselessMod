package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenuType {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, UselessMod.MODID);
    public static final Supplier<MenuType<AdvancedAlloyFurnaceMenu>> ADVANCED_ALLOY_FURNACE_MENU =
            MENU_TYPES.register("advanced_alloy_furnace_menu",
                                () -> IMenuTypeExtension.create(AdvancedAlloyFurnaceMenu::new)
            );

    private ModMenuType() {}

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
