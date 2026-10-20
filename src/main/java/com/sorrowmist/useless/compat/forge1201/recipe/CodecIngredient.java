package com.sorrowmist.useless.compat.forge1201.recipe;

import com.google.gson.JsonElement;
import net.minecraftforge.common.crafting.AbstractIngredient;

/** Native Forge custom ingredient whose predicates/display stacks may depend on reloadable data. */
public abstract class CodecIngredient<T extends CodecIngredient<T>> extends AbstractIngredient {
    @Override public final boolean isSimple() { return false; }
    // An empty display list before dynamic registries load must not erase a valid predicate.
    @Override public final boolean isEmpty() { return false; }
    @Override public abstract CodecIngredientSerializer<T> getSerializer();
    @SuppressWarnings("unchecked")
    @Override public final JsonElement toJson() { return getSerializer().toJson((T)this); }
}
