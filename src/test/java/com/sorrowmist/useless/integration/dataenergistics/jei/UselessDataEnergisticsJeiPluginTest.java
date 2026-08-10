package com.sorrowmist.useless.integration.dataenergistics.jei;

import appeng.menu.me.items.PatternEncodingTermMenu;

import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiRegistry;
import com.fish_dan_.data_energistics.api.entrypoint.jei.JeiRecipeTransferHandlerFactory;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory;
import com.sorrowmist.useless.integration.dataenergistics.DataEnergisticsIntegrationTestBootstrap;

import mezz.jei.api.recipe.RecipeType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UselessDataEnergisticsJeiPluginTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        DataEnergisticsIntegrationTestBootstrap.initialize();
    }

    @Test
    void registersTheExactMoldAwareTransferThroughTheGenericJeiSurface() {
        RecordingRegistry registry = new RecordingRegistry();
        MenuType<PatternEncodingTermMenu> menuType = new MenuType<>(
                (containerId, inventory) -> {
                    throw new AssertionError("The registration test must not create a menu");
                },
                FeatureFlags.VANILLA_SET);

        UselessDataEnergisticsJeiPlugin.registerTransfer(
                registry,
                PatternEncodingTermMenu.class,
                menuType);

        assertEquals(UselessMod.id("omniversal_pattern_transfer"), registry.registrationId);
        assertEquals(PatternEncodingTermMenu.class, registry.menuClass);
        assertEquals(menuType, registry.menuType);
        assertEquals(AdvancedAlloyFurnaceRecipeCategory.TYPE, registry.recipeType);
        assertNotNull(registry.factory);
    }

    private static final class RecordingRegistry implements DataEnergisticsJeiRegistry {
        private ResourceLocation registrationId;
        private Class<?> menuClass;
        private MenuType<?> menuType;
        private RecipeType<?> recipeType;
        private JeiRecipeTransferHandlerFactory<?, ?> factory;

        @Override
        public <T extends AbstractContainerMenu, R> void registerRecipeTransferHandler(
                @NotNull ResourceLocation registrationId,
                @NotNull Class<T> menuClass,
                @NotNull MenuType<T> menuType,
                @NotNull RecipeType<R> recipeType,
                @NotNull JeiRecipeTransferHandlerFactory<T, R> factory) {
            this.registrationId = registrationId;
            this.menuClass = menuClass;
            this.menuType = menuType;
            this.recipeType = recipeType;
            this.factory = factory;
        }
    }
}
