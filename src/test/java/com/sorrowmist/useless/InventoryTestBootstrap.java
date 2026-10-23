package com.sorrowmist.useless;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Installs the launcher context needed by Minecraft's inventory and fluid classes in unit tests. */
public final class InventoryTestBootstrap {
    private static boolean initialized;
    private InventoryTestBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        // Forge 47.4 initializes handshake listeners here; plain JUnit has no event transformer.
        try (var network = mockStatic(net.minecraftforge.network.NetworkHooks.class);
             var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("minecraft:item"),
                net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE);
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("forge:nbt"),
                net.minecraftforge.common.crafting.StrictNBTIngredient.Serializer.INSTANCE);
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("cofh_core:count"),
                cofh.lib.util.crafting.IngredientWithCount.Serializer.INSTANCE);
        initialized = true;
    }
}
