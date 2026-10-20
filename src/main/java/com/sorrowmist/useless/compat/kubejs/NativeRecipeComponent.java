package com.sorrowmist.useless.compat.kubejs;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.util.JsonIO;
import java.util.function.Function;

/** Registry-independent recipe JSON with the native 2001 KubeJS component contract. */
final class NativeRecipeComponent<T> implements RecipeComponent<T> {
    private final Class<T> type; private final Codec<T> codec; private final Function<Object,T> reader;
    NativeRecipeComponent(Class<T> type,Codec<T> codec,Function<Object,T> reader){this.type=type;this.codec=codec;this.reader=reader;}
    NativeRecipeComponent(Class<T> type,Codec<T> codec){this(type,codec,from->codec.parse(JsonOps.INSTANCE,JsonIO.of(from)).getOrThrow(false,message->{throw new IllegalArgumentException(message);}));}
    @Override public Class<?> componentClass(){return type;}
    @Override public T read(RecipeJS recipe,Object from){return type.isInstance(from)?type.cast(from):reader.apply(from);}
    @Override public JsonElement write(RecipeJS recipe,T value){return codec.encodeStart(JsonOps.INSTANCE,value).getOrThrow(false,message->{throw new IllegalArgumentException(message);});}
}
