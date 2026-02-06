package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, UselessMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, AdvancedAlloyFurnaceRecipeSerializer> ADVANCED_ALLOY_FURNACE_SERIALIZER =
            RECIPE_SERIALIZERS.register("advanced_alloy_furnace",
                                        AdvancedAlloyFurnaceRecipeSerializer::new
            );
}