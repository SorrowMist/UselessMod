package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishingLootResourcePathTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @Test void nativeMinecraftFishingUsesPluralLootTablesDirectory() throws Exception {
        String path = "data/minecraft/loot_tables/gameplay/fishing.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Native Minecraft resource must be in the test classpath");
        }
        ResourceManager manager = mock(ResourceManager.class);
        when(manager.getResource(any())).thenReturn(Optional.empty());
        Method reader = FishingLootRecipeAdapter.class.getDeclaredMethod("readLootTable",ResourceManager.class,ResourceLocation.class);
        reader.setAccessible(true);
        assertNull(reader.invoke(null,manager,new ResourceLocation("minecraft","gameplay/fishing")));
        verify(manager).getResource(new ResourceLocation("minecraft","loot_tables/gameplay/fishing.json"));
    }

    @Test void externalFishingResourceRoundTripsToTheActualTableId() throws Exception {
        Method convert = FishingLootRecipeAdapter.class.getDeclaredMethod("toTableId",ResourceLocation.class);
        convert.setAccessible(true);
        assertEquals(new ResourceLocation("example","gameplay/fishing/fish"),convert.invoke(null,new ResourceLocation("example","loot_tables/gameplay/fishing/fish.json")));
        assertNull(convert.invoke(null,new ResourceLocation("example","loot_table/gameplay/fishing/fish.json")));
        assertNull(convert.invoke(null,new ResourceLocation("example","recipes/fish.json")));
    }
}
