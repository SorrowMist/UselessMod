package com.sorrowmist.useless.content.recipe.adapters.ae.advancedae;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import java.util.*;

/** Supports both the native 1.2 ingredient classes and the 1.3 addon-lib API. */
public class ReactionChamberRecipeAdapter implements IRecipeAdapter<ReactionChamberRecipe> {
    @Override public Class<ReactionChamberRecipe> getRecipeClass() { return ReactionChamberRecipe.class; }
    @Override public ItemStack getMoldItem() {
        // The block-definition class/field descriptor also changed between 1.2 and 1.3.
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                new net.minecraft.resources.ResourceLocation("advanced_ae", "reaction_chamber")));
    }

    @Override public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<ReactionChamberRecipe> holder,Level level) {
        if (holder == null || holder.value() == null) return null;
        var source=holder.value();
        List<CountedIngredient> items=new ArrayList<>();
        if (source.getInputs() == null) return null;
        // Do not link IngredientStack or getFluid's return descriptor: both moved in 1.3.
        for (Object input:source.getInputs()) {
            if (input == null) return null;
            Object value = nativeValue(input, "getIngredient");
            int amount = ((Number) nativeValue(input, "getAmount")).intValue();
            if (amount <= 0 || !(value instanceof Ingredient ingredient) || AdapterUtils.isIngredientEmpty(ingredient)) return null;
            items.add(new CountedIngredient(ingredient,amount));
        }
        List<SizedFluidIngredient> fluids=new ArrayList<>();
        Object fluid=nativeValue(source,"getFluid");
        // Both native versions compare exact fluid IDs, ignoring NBT; zero is the no-fluid sentinel.
        if (fluid != null) {
            int amount=((Number)nativeValue(fluid,"getAmount")).intValue();
            if (amount != 0) {
                Object value=nativeValue(fluid,"getIngredient");
                if (amount < 0 || !(value instanceof net.minecraft.world.level.material.Fluid liquid) || liquid == Fluids.EMPTY) return null;
                fluids.add(new SizedFluidIngredient(FluidIngredient.single(liquid),amount));
            }
        }
        if (items.isEmpty() && fluids.isEmpty()) return null;
        var output=source.output;
        if (output == null || output.what() == null || output.amount() <= 0 || output.amount() > Integer.MAX_VALUE
                || source.getEnergy() < 0) return null;
        List<ItemStack> outputs=new ArrayList<>();
        List<FluidStack> outputFluids=new ArrayList<>();
        if (output.what() instanceof AEItemKey item) outputs.add(item.toStack((int)output.amount()));
        else if (output.what() instanceof AEFluidKey liquid) outputFluids.add(liquid.toStack((int)output.amount()));
        else return null;
        return new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),items,fluids,outputs,outputFluids,
                source.getEnergy(),AdapterUtils.ADVANCEDAE_REACTION_CHAMBER_PROCESS_TIME,Ingredient.EMPTY,0,
                AdapterUtils.toMoldIngredient(getMoldItem()),AlloyFurnaceMode.NORMAL);
    }

    static Object nativeValue(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unsupported AdvancedAE ingredient API: " + method, exception);
        }
    }

    public boolean matches(AdvancedAlloyFurnaceRecipe recipe,Map<Ingredient,Long> items,Map<FluidStack,Long> fluids) {
        if (recipe == null) return false;
        Map<Ingredient,Long> required=new LinkedHashMap<>();
        for (var input:recipe.inputs()) AdapterUtils.mergeIngredient(required,input.ingredient(),input.count());
        return AdapterUtils.matchesRequired(items,required) && FluidIngredientAllocator.matchesLong(recipe.inputFluids(),fluids,1L);
    }
    @Override public List<RecipeHolder<ReactionChamberRecipe>> findMatchingRecipes(Level level,Map<Ingredient,Long> items,
            Map<FluidStack,Long> fluids,ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<ReactionChamberRecipe>> result=new ArrayList<>();
        for (var holder:RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(),ReactionChamberRecipe.TYPE))
            if (matches(convert(holder,level),items,fluids)) result.add(holder);
        return result;
    }
}
