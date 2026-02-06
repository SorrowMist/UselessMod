package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.init.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(guiHelper));
        registration.addRecipeCategories(new CatalystInfoCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();

        // 获取所有高级合金炉配方
        List<AdvancedAlloyFurnaceRecipe> recipes = recipeManager.getAllRecipesFor(
                ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get())
                .stream()
                .map(RecipeHolder::value)
                .toList();

        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, recipes);

        // 添加催化剂信息
        List<CatalystInfoCategory.CatalystInfo> catalystInfos = new ArrayList<>();
        catalystInfos.add(new CatalystInfoCategory.CatalystInfo());
        registration.addRecipes(CatalystInfoCategory.TYPE, catalystInfos);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 高级合金炉作为配方催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE
        );

        // 添加催化剂信息类别的催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()),
                CatalystInfoCategory.TYPE
        );

        // 添加各个等级的催化剂作为配方催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_1.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_2.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_3.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_4.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_5.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_6.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_7.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_8.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USELESS_INGOT_TIER_9.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.USEFUL_INGOT.get()),
                CatalystInfoCategory.TYPE
        );
    }
}
