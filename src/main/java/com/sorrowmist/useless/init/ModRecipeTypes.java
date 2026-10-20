package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;


public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister
            .create(Registries.RECIPE_TYPE, UselessMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AdvancedAlloyFurnaceRecipe>> ADVANCED_ALLOY_FURNACE_TYPE =
            RECIPE_TYPES.register("advanced_alloy_furnace", () -> AdvancedAlloyFurnaceRecipe.TYPE);
}
