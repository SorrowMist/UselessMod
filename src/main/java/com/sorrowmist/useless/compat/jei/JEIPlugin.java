package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.recipes.ModRecipeTypes;
import com.sorrowmist.useless.registry.ModIngots;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new CatalystInfoCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Objects.requireNonNull(Minecraft.getInstance().level).getRecipeManager();

        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE,
                recipeManager.getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()));

        // 添加催化剂信息
        List<CatalystInfoCategory.CatalystInfo> catalystInfos = new ArrayList<>();
        catalystInfos.add(new CatalystInfoCategory.CatalystInfo());
        registration.addRecipes(CatalystInfoCategory.TYPE, catalystInfos);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK.get()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE,
                CatalystInfoCategory.TYPE
        );

        // 添加各个等级的催化剂作为配方催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_1.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_2.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_3.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_4.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_5.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_6.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_7.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_8.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_9.get()),
                CatalystInfoCategory.TYPE
        );
    }
}