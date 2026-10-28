package com.sorrowmist.useless.client.gui.ldl.api;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** 一行注册：{@code AppleScreens.register(ModMenuType.FOO.get(), FooScreen::new);}（在 FMLClientSetupEvent.enqueueWork 里）。 */
public final class AppleScreens {
    private AppleScreens() {}

    public static <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void register(
            MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, S> factory) {
        MenuScreens.register(type, factory);
    }
}
