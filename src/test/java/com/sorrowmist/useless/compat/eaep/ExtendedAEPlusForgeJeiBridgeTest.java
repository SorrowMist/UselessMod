package com.sorrowmist.useless.compat.eaep;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtendedAEPlusForgeJeiBridgeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void actual164SearchApiStoresAndConsumesTheFurnacePreset() {
        ModList mods = mock(ModList.class);
        when(mods.isLoaded("extendedae_plus")).thenReturn(true);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            try {
                assertTrue(ExtendedAEPlusJeiCompat.presetAlloyFurnaceSearchKey());
                assertEquals("Omni Alloy Furnace", RecipeTypeNameConfig.peekLastProviderSearchKey());
                assertEquals("Omni Alloy Furnace", RecipeTypeNameConfig.consumeLastProviderSearchKey());
                assertNull(RecipeTypeNameConfig.peekLastProviderSearchKey());
                when(mods.isLoaded("extendedae_plus")).thenReturn(false);
                assertFalse(ExtendedAEPlusJeiCompat.presetAlloyFurnaceSearchKey());
                assertNull(RecipeTypeNameConfig.peekLastProviderSearchKey());
            } finally {
                RecipeTypeNameConfig.consumeLastProviderSearchKey();
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void simulationAndInvalidTransfersNeverChangeTheOneShotSearch() {
        var delegate = (IRecipeTransferHandler<PatternEncodingTermMenu, AlloyFurnaceRecipeCatalog.Entry>)
                mock(IRecipeTransferHandler.class);
        var wrapper = new ExtendedAEPlusJeiTransferHandler<>(delegate);
        var slots = mock(IRecipeSlotsView.class);
        var error = mock(IRecipeTransferError.class);
        try (var search = mockStatic(ExtendedAEPlusJeiCompat.class)) {
            assertNull(wrapper.transferRecipe(null, null, slots, null, false, false));
            when(delegate.transferRecipe(null, null, slots, null, false, false)).thenReturn(error);
            assertSame(error, wrapper.transferRecipe(null, null, slots, null, false, true));
            search.verifyNoInteractions();
            verify(delegate, never()).transferRecipe(any(), any(), any(), any(), anyBoolean(), eq(true));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void committedTransferPresetsBeforeEncodingAndStillDelegatesWhenEaepIsAbsent() {
        var delegate = (IRecipeTransferHandler<PatternEncodingTermMenu, AlloyFurnaceRecipeCatalog.Entry>)
                mock(IRecipeTransferHandler.class);
        var wrapper = new ExtendedAEPlusJeiTransferHandler<>(delegate);
        var slots = mock(IRecipeSlotsView.class);
        var preset = new AtomicBoolean();
        when(delegate.transferRecipe(null, null, slots, null, true, true)).thenAnswer(invocation -> {
            assertTrue(preset.get(), "Encoding must see the already-preset provider filter");
            return null;
        });
        try (var search = mockStatic(ExtendedAEPlusJeiCompat.class)) {
            search.when(ExtendedAEPlusJeiCompat::presetAlloyFurnaceSearchKey).thenAnswer(invocation -> {
                preset.set(true);
                return false; // Missing optional mod must not suppress the original handler.
            });
            assertNull(wrapper.transferRecipe(null, null, slots, null, true, true));
            var order = inOrder(delegate);
            order.verify(delegate).transferRecipe(null, null, slots, null, true, false);
            order.verify(delegate).transferRecipe(null, null, slots, null, true, true);
            search.verify(ExtendedAEPlusJeiCompat::presetAlloyFurnaceSearchKey);
        }
    }

    @Test
    void bridgeLoadsWithoutAnyEaepClassesEvenWhenAnOldVersionIsReportedLoaded() throws Exception {
        ModList mods = mock(ModList.class);
        when(mods.isLoaded("extendedae_plus")).thenReturn(true);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            String bridge = ExtendedAEPlusJeiCompat.class.getName();
            ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("com.extendedae_plus.")) throw new ClassNotFoundException(name);
                    if (name.startsWith(bridge)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded != null) return loaded;
                        try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            return defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    return super.loadClass(name, resolve);
                }
            };
            assertEquals(false, isolated.loadClass(bridge).getMethod("presetAlloyFurnaceSearchKey").invoke(null));
        }
    }
}
