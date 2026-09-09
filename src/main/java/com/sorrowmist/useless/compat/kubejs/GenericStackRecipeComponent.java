package com.sorrowmist.useless.compat.kubejs;

import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.type.TypeInfo;

/** Converts a KubeJS object into an AE2 GenericStack using its registry-aware codec. */
public final class GenericStackRecipeComponent implements RecipeComponent<GenericStack> {
    public static final RecipeComponentType<GenericStack> GENERIC_STACK =
            RecipeComponentType.unit(KubeJS.id("generic_stack"), GenericStackRecipeComponent::new);

    private final RecipeComponentType<?> type;

    private GenericStackRecipeComponent(RecipeComponentType<?> type) {
        this.type = type;
    }

    @Override
    public RecipeComponentType<?> type() {
        return type;
    }

    @Override
    public com.mojang.serialization.Codec<GenericStack> codec() {
        return GenericStack.CODEC;
    }

    @Override
    public TypeInfo typeInfo() {
        return TypeInfo.RAW_MAP;
    }

    @Override
    public GenericStack wrap(RecipeScriptContext cx, Object from) {
        if (from instanceof GenericStack stack) {
            return stack;
        }

        JsonElement json = JsonUtils.of(cx.cx(), from);
        return GenericStack.CODEC.parse(cx.ops().json(), json).getOrThrow();
    }

    @Override
    public boolean hasPriority(RecipeMatchContext cx, Object from) {
        return from instanceof java.util.Map<?, ?> || from instanceof JsonElement;
    }

    @Override
    public boolean isEmpty(GenericStack value) {
        return value == null || value.amount() <= 0;
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
