package com.sorrowmist.useless.content.recipe.adapters;

import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.compat.neoecoae.NeoEcoWorkstationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterGuards;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

public class RecipeAdapterCompatRegistryMigrationTest {
    private static Item workstation;
    private AlloyFurnaceRecipeManager manager;
    private MockedStatic<AlloyFurnaceRecipeManager> singleton;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        workstation = InventoryTestBootstrap.registerItem(
                new ResourceLocation("neoecoae", "integrated_working_station"),
                () -> new Item(new Item.Properties()));
    }

    @BeforeEach void isolateManager() throws Exception {
        AdapterGuards.clearFailures();
        var constructor = AlloyFurnaceRecipeManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
        singleton = mockStatic(AlloyFurnaceRecipeManager.class);
        singleton.when(AlloyFurnaceRecipeManager::getInstance).thenReturn(manager);
    }

    @AfterEach void closeManager() {
        singleton.close();
        AdapterGuards.clearFailures();
    }

    @Test void nativeNeoEcoEntryPointRegistersThePublicApiAdapterWithItsSourceAndMold() throws Exception {
        var entryPoint = RecipeAdapterCompatRegistry.class.getDeclaredMethod("registerNeoECOAE");
        entryPoint.setAccessible(true);
        entryPoint.invoke(null);

        var adapters = manager.getRegisteredAdapters();
        assertEquals(1, adapters.size());
        var adapter = assertInstanceOf(NeoEcoWorkstationRecipeAdapter.class, adapters.get(0));
        assertEquals(IntegratedWorkingStationRecipe.class, adapter.getRecipeClass());
        assertEquals("neoecoae", manager.getAdapterSourceId(adapter));
        assertTrue(adapter.getMoldItem().is(workstation));
        assertTrue(AdapterGuards.disabledMods().isEmpty());
    }

    @Test void reflectiveConstructorRegistrationAcceptsAnAdapterImplementingOnlyThePublicApi() throws Exception {
        register("registerByName", PublicApiAdapter.class);

        var adapters = manager.getRegisteredAdapters();
        assertEquals(1, adapters.size());
        assertEquals(PublicApiAdapter.class, adapters.get(0).getClass());
        assertEquals("registry_test", manager.getAdapterSourceId(adapters.get(0)));
    }

    @Test void reflectiveConstructorRegistrationStillAcceptsTheLegacyBridge() throws Exception {
        register("registerByName", LegacyAdapter.class);

        var adapters = manager.getRegisteredAdapters();
        assertEquals(1, adapters.size());
        assertEquals(LegacyAdapter.class, adapters.get(0).getClass());
        assertEquals("registry_test", manager.getAdapterSourceId(adapters.get(0)));
    }

    @Test void reflectiveFactoryRegistrationAcceptsBothPublicApiAndLegacyAdapters() throws Exception {
        register("registerFactory", MixedAdapterFactory.class);

        var adapters = manager.getRegisteredAdapters();
        assertEquals(List.of(PublicApiAdapter.class, LegacyAdapter.class),
                adapters.stream().map(Object::getClass).toList());
        assertTrue(adapters.stream().allMatch(adapter ->
                "registry_test".equals(manager.getAdapterSourceId(adapter))));
        assertTrue(AdapterGuards.disabledMods().isEmpty());
    }

    private static void register(String entryPoint, Class<?> implementation) throws Exception {
        var method = RecipeAdapterCompatRegistry.class.getDeclaredMethod(entryPoint, String.class);
        method.setAccessible(true);
        method.invoke(null, implementation.getName());
    }

    public static class PublicApiAdapter implements IRecipeAdapter<SmeltingRecipe> {
        @Override public String sourceId() { return "registry_test"; }
        @Override public Class<SmeltingRecipe> getRecipeClass() { return SmeltingRecipe.class; }
        @Override public ItemStack getMoldItem() { return new ItemStack(Items.STONE); }
    }

    @SuppressWarnings("deprecation")
    public static final class LegacyAdapter extends PublicApiAdapter
            implements com.sorrowmist.useless.content.recipe.IRecipeAdapter<SmeltingRecipe> {
    }

    public static final class MixedAdapterFactory {
        public static List<IRecipeAdapter<?>> createAdapters() {
            return List.of(new PublicApiAdapter(), new LegacyAdapter());
        }
    }
}
