package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import java.util.*;
import java.util.function.Supplier;

/** Expands native OR inputs with different amounts without flattening them to AND requirements. */
final class NativeMekanismVariants {
    private static final ThreadLocal<Map<Object,Object>> ACTIVE=new ThreadLocal<>();
    private record Choice(Object ingredient,List<?> alternatives) {}
    private NativeMekanismVariants() {}

    static boolean needsExpansion(Object recipe) {
        return ACTIVE.get()==null && choices(recipe).stream().anyMatch(choice->choice.alternatives().size()>1);
    }
    static Object selected(Object ingredient) {
        var active=ACTIVE.get(); return active==null?null:active.get(ingredient);
    }
    private static List<Choice> choices(Object recipe) {
        if(recipe==null) return List.of();
        List<Choice> choices=new ArrayList<>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        var methods=Arrays.stream(recipe.getClass().getMethods())
                .filter(method->!method.isBridge() && !method.isSynthetic() && method.getParameterCount()==0
                        && (ItemStackIngredient.class.isAssignableFrom(method.getReturnType())
                        || FluidStackIngredient.class.isAssignableFrom(method.getReturnType())))
                .sorted(Comparator.comparing(java.lang.reflect.Method::getName)).toList();
        for(var method:methods) {
            try {
                Object input=method.invoke(recipe);
                if(input==null || !seen.add(input)) continue;
                List<?> variants=input instanceof ItemStackIngredient item
                        ? NativeMekanismIngredients.itemVariants(item.serialize(),1L)
                        : NativeMekanismIngredients.fluidVariants(((FluidStackIngredient)input).serialize());
                choices.add(new Choice(input,variants));
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot inspect native Mekanism ingredient "+method,error);
            }
        }
        return choices;
    }
    static List<AdvancedAlloyFurnaceRecipe> expand(Object recipe,Supplier<List<AdvancedAlloyFurnaceRecipe>> convert) {
        var choices=choices(recipe);
        long total=1;
        for(var choice:choices) {
            total=Math.multiplyExact(total,choice.alternatives().size());
            if(total>4096) throw new IllegalArgumentException("Mekanism recipe exceeds 4096 quantity variants");
        }
        List<AdvancedAlloyFurnaceRecipe> result=new ArrayList<>();
        try {
            for(int variant=0;variant<total;variant++) {
                var selected=new IdentityHashMap<Object,Object>();
                int cursor=variant;
                for(var choice:choices) {
                    int size=choice.alternatives().size();
                    selected.put(choice.ingredient(),choice.alternatives().get(cursor%size)); cursor/=size;
                }
                ACTIVE.set(selected);
                for(var converted:convert.get()) {
                    var id=MekanismChemicalRecipeSupport.variantId(converted.id(),"native_quantity_"+variant);
                    result.add(new AdvancedAlloyFurnaceRecipe(id,converted.inputs(),converted.inputFluids(),converted.keyInputs(),
                            converted.outputs(),converted.outputFluids(),converted.keyOutputs(),converted.energy(),converted.processTime(),
                            converted.catalyst(),converted.catalystUses(),converted.molds(),converted.mode(),converted.tier()));
                }
            }
        } finally { ACTIVE.remove(); }
        return List.copyOf(result);
    }
}
