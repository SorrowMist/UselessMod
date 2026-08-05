package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;

import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@JeiPlugin
public final class JEIPlugin implements IModPlugin {
    private static final ResourceLocation UID = UselessMod.id("jei_plugin");
    private static IJeiRuntime runtime;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        GenericStackJeiIngredientProviders.initialize();
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(guiHelper));
        registration.addRecipeCategories(new CatalystInfoCategory(guiHelper));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        List<AdvancedAlloyFurnaceRecipe> recipes = level == null
                ? List.of()
                : AlloyFurnaceRecipeCatalog.recipes(level);
        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, recipes);
        registration.addRecipes(CatalystInfoCategory.TYPE,
                List.of(new CatalystInfoCategory.CatalystInfo()));
    }


    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // Only this category encodes omniversal patterns. Every other page in a pattern encoding
        // terminal keeps falling through to ae2jeiintegration's universal handler, which JEI consults
        // only when no category-specific handler is registered for the open menu.
        registration.addRecipeTransferHandler(
                new OmniversalPatternJeiTransferHandler<>(
                        PatternEncodingTermMenu.class,
                        PatternEncodingTermMenu.TYPE,
                        registration.getTransferHelper()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE);
        registerWirelessTransferHandler(registration);
    }

    /**
     * The wireless pattern encoding terminal is a {@link PatternEncodingTermMenu} subclass, and JEI
     * matches handlers on the menu's exact class, so it needs its own registration. ae2wtlib is only
     * a runtime dependency here, hence the reflective lookup; without it the wireless terminal would
     * silently fall back to ae2jeiintegration's universal handler and encode a plain pattern.
     */
    @SuppressWarnings("unchecked")
    private static void registerWirelessTransferHandler(IRecipeTransferRegistration registration) {
        if (!ModList.get().isLoaded("ae2wtlib")) return;
        try {
            Class<?> menuClass = Class.forName("de.mari_023.ae2wtlib.wet.WETMenu");
            if (!PatternEncodingTermMenu.class.isAssignableFrom(menuClass)) return;
            var menuType = (MenuType<PatternEncodingTermMenu>) menuClass.getField("TYPE").get(null);
            registration.addRecipeTransferHandler(
                    new OmniversalPatternJeiTransferHandler<>(
                            (Class<PatternEncodingTermMenu>) menuClass,
                            menuType,
                            registration.getTransferHelper()),
                    AdvancedAlloyFurnaceRecipeCategory.TYPE);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            UselessMod.LOGGER.warn(
                    "Could not register the omniversal pattern transfer handler for ae2wtlib's wireless "
                            + "pattern encoding terminal; it will encode plain patterns instead.",
                    exception);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack singleBlockFurnace = new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
        registration.addRecipeCatalyst(singleBlockFurnace, AdvancedAlloyFurnaceRecipeCategory.TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE);
        registration.addRecipeCatalyst(singleBlockFurnace, CatalystInfoCategory.TYPE);

        BuiltInRegistries.ITEM.getTag(ModTags.CATALYSTS).ifPresent(tag -> {
            for (var holder : tag) {
                registration.addRecipeCatalyst(new ItemStack(holder.value()), CatalystInfoCategory.TYPE);
            }
        });
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static IJeiRuntime getRuntime() {
        return runtime;
    }

    public static void showAdvancedAlloyFurnaceRecipes() {
        if (runtime != null) {
            runtime.getRecipesGui().showTypes(List.of(AdvancedAlloyFurnaceRecipeCategory.TYPE));
        }
    }

    public static boolean isAvailable() {
        return runtime != null;
    }
}
