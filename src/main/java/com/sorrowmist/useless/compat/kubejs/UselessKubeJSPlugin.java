package com.sorrowmist.useless.compat.kubejs;

import com.sorrowmist.useless.init.ModRecipeSerializers;
import dev.latvian.mods.kubejs.util.ClassFilter;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RecipeComponentFactoryRegistryEvent;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;

/** Registers the public KubeJS recipe API for Useless Mod recipes. */
public final class UselessKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerClasses(dev.latvian.mods.kubejs.script.ScriptType type, ClassFilter filter) {
        filter.allow("com.sorrowmist.useless.compat.kubejs");
    }

    @Override
    public void registerRecipeComponents(RecipeComponentFactoryRegistryEvent registry) {
        registry.register("useless_mod:generic_stack", GenericStackRecipeComponent.GENERIC_STACK);
    }

    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent registry) {
        registry.register(
                ModRecipeSerializers.ADVANCED_ALLOY_FURNACE_SERIALIZER.getId(),
                AdvancedAlloyFurnaceRecipeSchema.SCHEMA
        );
    }
}
