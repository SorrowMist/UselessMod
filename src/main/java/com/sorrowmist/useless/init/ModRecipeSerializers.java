package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeSerializer;
import com.sorrowmist.useless.content.recipe.BeefUnbreakableRecipe;
import com.sorrowmist.useless.content.recipe.EntropyTransmutationRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, UselessMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, AdvancedAlloyFurnaceRecipeSerializer> ADVANCED_ALLOY_FURNACE_SERIALIZER =
            RECIPE_SERIALIZERS.register("advanced_alloy_furnace",
                                        AdvancedAlloyFurnaceRecipeSerializer::new
            );

    /** 造化杖 + 任意物品 → 该物品的不可破坏版本（特殊配方，无状态）。 */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<BeefUnbreakableRecipe>> BEEF_UNBREAKABLE_SERIALIZER =
            RECIPE_SERIALIZERS.register("beef_unbreakable",
                                        () -> new SimpleCraftingRecipeSerializer<>(BeefUnbreakableRecipe::new)
            );

    /** 熵变转化（熵变机械臂潜行右键 ME 箱子）配方，由事件层驱动。 */
    public static final DeferredHolder<RecipeSerializer<?>, EntropyTransmutationRecipeSerializer> ENTROPY_TRANSMUTATION_SERIALIZER =
            RECIPE_SERIALIZERS.register("entropy_transmutation",
                                        EntropyTransmutationRecipeSerializer::new
            );
}