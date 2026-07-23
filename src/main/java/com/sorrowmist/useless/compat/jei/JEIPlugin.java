package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        registration.addRecipeTransferHandler(
                new OmniversalPatternJeiTransferHandler(registration.getTransferHelper()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE);
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
