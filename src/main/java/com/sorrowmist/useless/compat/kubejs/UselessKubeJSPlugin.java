package com.sorrowmist.useless.compat.kubejs;

import com.sorrowmist.useless.init.ModRecipeSerializers;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;

/** Registers the public KubeJS recipe API for Useless Mod recipes. */
public final class UselessKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("com.sorrowmist.useless.compat.kubejs");
    }

    @Override
    public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
        registry.register(GenericStackRecipeComponent.GENERIC_STACK);
    }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        registry.register(
                ModRecipeSerializers.ADVANCED_ALLOY_FURNACE_SERIALIZER.getId(),
                AdvancedAlloyFurnaceRecipeSchema.SCHEMA
        );
    }
}
