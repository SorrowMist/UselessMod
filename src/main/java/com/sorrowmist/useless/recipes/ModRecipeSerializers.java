// ModRecipeSerializers.java
package com.sorrowmist.useless.recipes;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, UselessMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<AdvancedAlloyFurnaceRecipe>> ADVANCED_ALLOY_FURNACE_SERIALIZER =
            RECIPE_SERIALIZERS.register("advanced_alloy_furnace",
                    () -> new AdvancedAlloyFurnaceRecipe.Serializer());
}