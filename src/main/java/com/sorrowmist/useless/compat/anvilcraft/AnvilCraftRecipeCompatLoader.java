package com.sorrowmist.useless.compat.anvilcraft;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.Multiblock4DRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.MultiblockRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.AnvilBlockRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.AnvilItemRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.ChargerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.BlockSmearRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.FluidMixingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.CauldronRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.SqueezingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.MultipleSmithingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.MultiblockConversionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.ProceduralRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.FrostSmithingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.JewelCraftingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.MassInjectRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.anvilcraft.AnvilCollisionRecipeAdapter;

public final class AnvilCraftRecipeCompatLoader {
    private AnvilCraftRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new AnvilCollisionRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new Multiblock4DRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new MultiblockRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new AnvilBlockRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new AnvilItemRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ChargerRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new BlockSmearRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new FluidMixingRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new CauldronRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new SqueezingRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new MultipleSmithingRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new MultiblockConversionRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ProceduralRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new FrostSmithingRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new JewelCraftingRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new MassInjectRecipeAdapter(), RecipeSourceIds.ANVILCRAFT);
    }
}
