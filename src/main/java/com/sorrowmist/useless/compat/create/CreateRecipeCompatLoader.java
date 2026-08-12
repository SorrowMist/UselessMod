package com.sorrowmist.useless.compat.create;

import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.create.CreateBlastingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.create.CreateMechanicalCraftingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.create.CreateProcessingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.create.CreateSequencedAssemblyRecipeAdapter;

/** Optional Create entrypoint. This class is only loaded when Create is present. */
public final class CreateRecipeCompatLoader {
    private CreateRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new CreateProcessingRecipeAdapter(), RecipeSourceIds.CREATE);
        manager.registerAdapter(new CreateBlastingRecipeAdapter(), RecipeSourceIds.CREATE);
        manager.registerAdapter(new CreateMechanicalCraftingRecipeAdapter(), RecipeSourceIds.CREATE);
        manager.registerAdapter(new CreateSequencedAssemblyRecipeAdapter(), RecipeSourceIds.CREATE);
    }
}
