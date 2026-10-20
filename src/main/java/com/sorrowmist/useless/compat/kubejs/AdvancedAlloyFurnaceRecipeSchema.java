package com.sorrowmist.useless.compat.kubejs;

import com.google.gson.JsonObject;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.util.JsonIO;
import net.minecraft.world.item.crafting.Ingredient;

/** KubeJS 2001 schema for the native 1.20.1 recipe serializer. */
public final class AdvancedAlloyFurnaceRecipeSchema {
    private static final RecipeComponent<Ingredient> INGREDIENT=new NativeRecipeComponent<>(Ingredient.class,RecipeCodecs.INGREDIENT,from->InputItem.of(from).ingredient);
    private static final RecipeComponent<FluidIngredient> FLUID=new NativeRecipeComponent<>(FluidIngredient.class,FluidIngredient.CODEC,from->{
        var json=JsonIO.of(from);
        if(json.isJsonPrimitive()){
            var object=new JsonObject(); String id=json.getAsString();
            object.addProperty(id.startsWith("#")?"tag":"fluid",id.startsWith("#")?id.substring(1):id); json=object;
        }
        return FluidIngredient.fromJson(json);
    });
    private static final RecipeComponentBuilder COUNTED=RecipeComponent.builder(
            INGREDIENT.key("ingredient"),NumberComponent.LONG.key("count").optional(1L));
    private static final RecipeComponentBuilder SIZED_FLUID=RecipeComponent.builder(
            FLUID.key("ingredient"),NumberComponent.LONG.key("amount"));
    public static final RecipeKey<String> ID=StringComponent.ANY.key("id");
    public static final RecipeKey<?> INGREDIENTS=COUNTED.asArray().key("ingredients").allowEmpty();
    public static final RecipeKey<?> INPUT_FLUIDS=SIZED_FLUID.asArray().key("input_fluids").defaultOptional().allowEmpty();
    public static final RecipeKey<?> KEY_INPUTS=GenericStackRecipeComponent.GENERIC_STACK.asArray().key("key_inputs").defaultOptional().allowEmpty();
    public static final RecipeKey<?> OUTPUTS=ItemComponents.OUTPUT_ARRAY.key("outputs").defaultOptional().allowEmpty();
    public static final RecipeKey<?> OUTPUT_FLUIDS=FluidComponents.OUTPUT_ARRAY.key("output_fluids").defaultOptional().allowEmpty();
    public static final RecipeKey<?> KEY_OUTPUTS=GenericStackRecipeComponent.GENERIC_STACK.asArray().key("key_outputs").defaultOptional().allowEmpty();
    public static final RecipeKey<Ingredient> MOLD=INGREDIENT.key("mold").optional(Ingredient.EMPTY).allowEmpty();
    public static final RecipeKey<?> MOLDS=INGREDIENT.asArray().key("molds").defaultOptional().allowEmpty();
    public static final RecipeKey<Integer> TIER=NumberComponent.ANY_INT.key("tier").optional(AdvancedAlloyFurnaceRecipe.NO_EXPLICIT_TIER);
    public static final RecipeKey<Long> ENERGY=NumberComponent.LONG.key("energy").optional(2000L);
    public static final RecipeKey<Integer> PROCESS_TIME=NumberComponent.INT.key("process_time").optional(200);
    public static final RecipeKey<Ingredient> CATALYST=INGREDIENT.key("catalyst").optional(Ingredient.EMPTY).allowEmpty();
    public static final RecipeKey<Integer> CATALYST_USES=NumberComponent.INT.key("catalyst_uses").optional(0);
    public static final RecipeKey<String> MODE=StringComponent.ANY.key("mode").optional("normal");
    private AdvancedAlloyFurnaceRecipeSchema() {}
    public static final RecipeSchema SCHEMA=new RecipeSchema(ID,INGREDIENTS,INPUT_FLUIDS,KEY_INPUTS,OUTPUTS,OUTPUT_FLUIDS,KEY_OUTPUTS,
            MOLD,MOLDS,TIER,ENERGY,PROCESS_TIME,CATALYST,CATALYST_USES,MODE)
            .constructor(ID,INGREDIENTS,OUTPUTS).constructor(ID,INGREDIENTS,OUTPUTS,MOLD)
            .constructor(ID,INGREDIENTS,OUTPUTS,MOLD,TIER).constructor(ID,INGREDIENTS,OUTPUTS,MOLD,MOLDS,TIER);
}
