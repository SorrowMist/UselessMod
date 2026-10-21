package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister
            .create(Registries.RECIPE_TYPE, UselessMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AdvancedAlloyFurnaceRecipe>> ADVANCED_ALLOY_FURNACE_TYPE =
            RECIPE_TYPES.register("advanced_alloy_furnace", () -> new RecipeType<AdvancedAlloyFurnaceRecipe>() {
                @Override
                public String toString() {
                    return "useless_mod:advanced_alloy_furnace";
                }
            });

    /** 熵变转化（熵变机械臂潜行右键 ME 箱子）配方类型，由事件层驱动。 */
    public static final DeferredHolder<RecipeType<?>, RecipeType<EntropyTransmutationRecipe>> ENTROPY_TRANSMUTATION_TYPE =
            RECIPE_TYPES.register("entropy_transmutation", () -> new RecipeType<EntropyTransmutationRecipe>() {
                @Override
                public String toString() {
                    return "useless_mod:entropy_transmutation";
                }
            });
}