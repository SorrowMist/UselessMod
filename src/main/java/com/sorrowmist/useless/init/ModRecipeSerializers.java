package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeSerializer;
import com.sorrowmist.useless.content.recipe.BeefUnbreakableRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;


public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, UselessMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, AdvancedAlloyFurnaceRecipeSerializer> ADVANCED_ALLOY_FURNACE_SERIALIZER =
            RECIPE_SERIALIZERS.register("advanced_alloy_furnace",
                                        () -> AdvancedAlloyFurnaceRecipeSerializer.INSTANCE
            );

    /** 造化杖 + 任意物品 → 该物品的不可破坏版本（特殊配方，无状态）。 */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<BeefUnbreakableRecipe>> BEEF_UNBREAKABLE_SERIALIZER =
            RECIPE_SERIALIZERS.register("beef_unbreakable",
                                        () -> new SimpleCraftingRecipeSerializer<>(BeefUnbreakableRecipe::new)
            );
}
