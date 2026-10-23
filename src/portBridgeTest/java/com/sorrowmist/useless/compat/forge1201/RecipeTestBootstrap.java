package com.sorrowmist.useless.compat.forge1201;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import java.util.List;
import static org.mockito.Mockito.*;

public final class RecipeTestBootstrap {
    private static boolean initialized;
    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        // Network event listeners require Forge's transformer, exercised by the GameTests instead.
        try (var network = mockStatic(net.minecraftforge.network.NetworkHooks.class);
             var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("forge:nbt"),net.minecraftforge.common.crafting.StrictNBTIngredient.Serializer.INSTANCE);
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("forge:partial_nbt"),net.minecraftforge.common.crafting.PartialNBTIngredient.Serializer.INSTANCE);
        net.minecraftforge.common.crafting.CraftingHelper.register(new net.minecraft.resources.ResourceLocation("minecraft:item"),net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE);
        initialized = true;
    }
}
