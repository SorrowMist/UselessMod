package com.sorrowmist.useless.content.recipe.adapters.ae.extendedae;

import com.glodblock.github.extendedae.common.EPPItemAndBlock;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import com.glodblock.github.extendedae.recipe.util.IngredientStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Native 1.20.1 cutter inputs include the fluid cost (normally 100 mB water). */
public class CircuitCutterRecipeAdapter implements IRecipeAdapter<CircuitCutterRecipe> {
    @Override public Class<CircuitCutterRecipe> getRecipeClass() { return CircuitCutterRecipe.class; }
    @Override public ItemStack getMoldItem() { return new ItemStack(EPPItemAndBlock.CIRCUIT_CUTTER); }

    static SizedFluidIngredient fluid(IngredientStack.Fluid source) {
        if (source == null || source.getAmount() <= 0
                || !(source.getIngredient() instanceof com.glodblock.github.extendedae.recipe.util.FluidIngredient nativeIngredient))
            return null;
        // Native EAE compares fluid families and ignores NBT. Keep tags structural across reloads.
        var json=nativeIngredient.toJson().getAsJsonObject().deepCopy();
        json.remove("nbt");
        json.addProperty("type",json.has("tag") ? "useless_mod:same_fluid_tag" : "useless_mod:same_fluid");
        var ingredient=FluidIngredient.fromJson(json);
        return ingredient.isEmpty() ? null : new SizedFluidIngredient(ingredient,source.getAmount());
    }

    @Override public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CircuitCutterRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;
        var source=holder.value();
        var input=source.getInput();
        if (input == null || input.getAmount() <= 0 || !(input.getIngredient() instanceof Ingredient ingredient)
                || AdapterUtils.isIngredientEmpty(ingredient) || source.output == null || source.output.isEmpty()) return null;
        var fluid=fluid(source.getFluid());
        // A malformed mandatory input must never turn into a cheaper recipe.
        if (fluid == null) return null;
        return new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(ingredient,input.getAmount())),List.of(fluid),
                List.of(source.output.copy()),List.of(),
                (long)AdapterUtils.EXTENDEDAE_ENERGY_MULTIPLIER * AdapterUtils.DEFAULT_PROCESS_TIME * AdapterUtils.AE_TO_FE_CONVERSION,
                AdapterUtils.DEFAULT_PROCESS_TIME,Ingredient.EMPTY,0,AdapterUtils.toMoldIngredient(getMoldItem()),AlloyFurnaceMode.NORMAL);
    }

    public boolean matches(AdvancedAlloyFurnaceRecipe recipe, Map<Ingredient,Long> items, Map<FluidStack,Long> fluids) {
        if (recipe == null) return false;
        Map<Ingredient,Long> required=new LinkedHashMap<>();
        for (var input:recipe.inputs()) AdapterUtils.mergeIngredient(required,input.ingredient(),input.count());
        return AdapterUtils.matchesRequired(items,required)
                && FluidIngredientAllocator.matchesLong(recipe.inputFluids(),fluids,1L);
    }

    @Override public List<RecipeHolder<CircuitCutterRecipe>> findMatchingRecipes(Level level,Map<Ingredient,Long> items,
            Map<FluidStack,Long> fluids,ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<CircuitCutterRecipe>> matches=new ArrayList<>();
        for (var holder:RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(),CircuitCutterRecipe.TYPE))
            if (matches(convert(holder,level),items,fluids)) matches.add(holder);
        return matches;
    }
}
