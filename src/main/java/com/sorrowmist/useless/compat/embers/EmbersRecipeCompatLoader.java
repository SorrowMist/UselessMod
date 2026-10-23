package com.sorrowmist.useless.compat.embers;

import com.rekindled.embers.recipe.IAlchemyRecipe;
import com.rekindled.embers.recipe.IMeltingRecipe;
import com.rekindled.embers.recipe.IMixingRecipe;
import com.rekindled.embers.recipe.IStampingRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.embers.EmbersRecipeAdapter;

public final class EmbersRecipeCompatLoader {
    private EmbersRecipeCompatLoader() {
    }

    public static void register() {
        var manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new EmbersRecipeAdapter<>(IAlchemyRecipe.class,
                "alchemy_tablet", "embers_alchemy_", false));
        manager.registerAdapter(new EmbersRecipeAdapter<>(IMeltingRecipe.class,
                "geologic_separator", "embers_geo_separator_", true));
        manager.registerAdapter(new EmbersRecipeAdapter<>(IMeltingRecipe.class,
                "melter", "embers_melter_", false));
        manager.registerAdapter(new EmbersRecipeAdapter<>(IStampingRecipe.class,
                "stamper", "embers_stamping_", false));
        manager.registerAdapter(new EmbersRecipeAdapter<>(IMixingRecipe.class,
                "mixer_centrifuge", "embers_mixing_", false));
    }
}
