package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.google.gson.*;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** Structural conversion of native 10.4 ingredients, without expanding tags to snapshots. */
public final class NativeMekanismIngredients {
    private NativeMekanismIngredients() {}
    public static CountedIngredient item(JsonElement value,long multiplier) {
        var variants=itemVariants(value,multiplier);
        if(variants.size()>1) throw new JsonSyntaxException("Mekanism item alternatives require quantity expansion");
        return variants.isEmpty()?null:variants.get(0);
    }
    public static List<CountedIngredient> itemVariants(JsonElement value,long multiplier) {
        Map<Long,JsonArray> groups=new LinkedHashMap<>();
        collectItem(value,multiplier,groups,0);
        List<CountedIngredient> result=new ArrayList<>();
        for(var entry:groups.entrySet()) {
            Ingredient ingredient=Ingredient.fromJson(entry.getValue());
            if(!ingredient.isEmpty()) result.add(new CountedIngredient(ingredient,entry.getKey()));
        }
        return List.copyOf(result);
    }
    private static void collectItem(JsonElement value,long multiplier,Map<Long,JsonArray> groups,int depth) {
        if(depth>64) throw new JsonSyntaxException("Mekanism ingredient nesting exceeds 64");
        if(value.isJsonArray()) {
            for(var child:value.getAsJsonArray()) collectItem(child,multiplier,groups,depth+1);
            return;
        }
        var object=value.getAsJsonObject();
        long amount=object.has("amount")?object.get("amount").getAsLong():1L;
        if(amount<=0 || multiplier<=0) throw new JsonSyntaxException("Nonpositive Mekanism item amount");
        long count;
        try { count=Math.multiplyExact(amount,multiplier); }
        catch(ArithmeticException error) { throw new JsonSyntaxException("Mekanism item amount overflow",error); }
        if(!object.has("ingredient")) throw new JsonSyntaxException("Missing native item predicate");
        var ingredient=object.get("ingredient");
        var group=groups.computeIfAbsent(count,ignored->new JsonArray());
        if(ingredient.isJsonArray()) ingredient.getAsJsonArray().forEach(group::add);
        else group.add(ingredient);
    }
    public static SizedFluidIngredient fluid(JsonElement value) {
        var variants=fluidVariants(value);
        if(variants.size()>1) throw new JsonSyntaxException("Mekanism fluid alternatives require quantity expansion");
        return variants.isEmpty()?null:variants.get(0);
    }
    public static List<SizedFluidIngredient> fluidVariants(JsonElement value) {
        List<SizedFluidIngredient> alternatives=new ArrayList<>();
        collectFluid(value,alternatives,0);
        Map<Integer,List<FluidIngredient>> groups=new LinkedHashMap<>();
        for(var input:alternatives) groups.computeIfAbsent(input.amount(),ignored->new ArrayList<>()).add(input.ingredient());
        return groups.entrySet().stream().map(entry->new SizedFluidIngredient(FluidIngredient.any(entry.getValue()),entry.getKey())).toList();
    }
    private static void collectFluid(JsonElement value,List<SizedFluidIngredient> result,int depth) {
        if(depth>64) throw new JsonSyntaxException("Mekanism fluid nesting exceeds 64");
        if(value.isJsonArray()) {
            for(var child:value.getAsJsonArray()) collectFluid(child,result,depth+1);
            return;
        }
        var json=value.getAsJsonObject().deepCopy();
        long amount=json.get("amount").getAsLong();
        if(amount<=0 || amount>Integer.MAX_VALUE) throw new JsonSyntaxException("Invalid Mekanism fluid amount");
        json.remove("amount");
        if(json.has("fluid")) {
            // Native SingleFluidStackIngredient uses FluidStack.isFluidEqual:
            // absent NBT is significant too; ordinary fluid-ID ingredients would broaden it.
            json.addProperty("type","useless_mod:nbt_fluid");
            json.addProperty("strict",true);
        }
        result.add(new SizedFluidIngredient(FluidIngredient.fromJson(json),(int)amount));
    }
}
