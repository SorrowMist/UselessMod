package com.sorrowmist.useless.compat.kubejs;

import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.sorrowmist.useless.compat.forge1201.recipe.AE2Codecs;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.util.JsonIO;

/** Uses AE2 15's native key format, including addon resource keys and long quantities. */
public final class GenericStackRecipeComponent implements RecipeComponent<GenericStack> {
    public static final GenericStackRecipeComponent GENERIC_STACK=new GenericStackRecipeComponent();
    private GenericStackRecipeComponent() {}
    @Override public Class<?> componentClass(){return GenericStack.class;}
    @Override public String componentType(){return "useless_mod:generic_stack";}
    @Override public JsonElement write(RecipeJS recipe,GenericStack stack){
        if(stack==null || stack.amount()<=0) throw new IllegalArgumentException("AE stack amount must be positive");
        return AE2Codecs.GENERIC_STACK.encodeStart(JsonOps.INSTANCE,stack).getOrThrow(false,message->{throw new IllegalArgumentException(message);});
    }
    @Override public GenericStack read(RecipeJS recipe,Object from){
        if(from instanceof GenericStack stack) {
            if(stack.amount()<=0) throw new IllegalArgumentException("AE stack amount must be positive");
            return stack;
        }
        var value=AE2Codecs.GENERIC_STACK.parse(JsonOps.INSTANCE,JsonIO.of(from)).getOrThrow(false,message->{throw new IllegalArgumentException(message);});
        if(value.amount()<=0) throw new IllegalArgumentException("AE stack amount must be positive");
        return value;
    }
    @Override public boolean hasPriority(RecipeJS recipe,Object from){return from instanceof java.util.Map<?,?> || from instanceof JsonElement || from instanceof GenericStack;}
}
