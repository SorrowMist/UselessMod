package com.sorrowmist.useless.compat.bloodmagic;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.bloodmagic.BloodMagicRecipeAdapter;
import wayoftime.bloodmagic.recipe.RecipeAlchemyTable;
import wayoftime.bloodmagic.recipe.RecipeBloodAltar;
import wayoftime.bloodmagic.recipe.RecipeTartaricForge;

public final class BloodMagicRecipeCompatLoader {
    private BloodMagicRecipeCompatLoader() {
    }

    public static void register() {
        var manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new BloodMagicRecipeAdapter<>(RecipeBloodAltar.class, "altar", "bm_altar_"));
        manager.registerAdapter(new BloodMagicRecipeAdapter<>(RecipeTartaricForge.class, "soulforge", "bm_soulforge_"));
        manager.registerAdapter(new BloodMagicRecipeAdapter<>(RecipeAlchemyTable.class, "alchemytable", "bm_alchemytable_"));
    }
}
