// ModRecipeTypes.java
package com.sorrowmist.useless.recipes;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, UselessMod.MOD_ID);

    public static final RegistryObject<RecipeType<AdvancedAlloyFurnaceRecipe>> ADVANCED_ALLOY_FURNACE_TYPE =
            RECIPE_TYPES.register("advanced_alloy_furnace", () -> new RecipeType<AdvancedAlloyFurnaceRecipe>() {
                @Override
                public String toString() {
                    return "useless_mod:advanced_alloy_furnace";
                }
            });
}