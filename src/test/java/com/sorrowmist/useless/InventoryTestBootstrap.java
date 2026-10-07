package com.sorrowmist.useless;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Installs the launcher context needed by Minecraft's inventory and fluid classes in unit tests. */
public final class InventoryTestBootstrap {
    private InventoryTestBootstrap() {
    }

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }
}
