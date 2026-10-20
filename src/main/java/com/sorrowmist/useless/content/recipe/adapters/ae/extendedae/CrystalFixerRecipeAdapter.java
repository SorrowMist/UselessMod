package com.sorrowmist.useless.content.recipe.adapters.ae.extendedae;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.glodblock.github.extendedae.common.EPPItemAndBlock;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Native 1.4 has a hardcoded machine, not CrystalFixerRecipe/RecipeType. */
public class CrystalFixerRecipeAdapter implements IRecipeAdapter<CrystalFixingSyntheticRecipe> {
    @Override public Class<CrystalFixingSyntheticRecipe> getRecipeClass() { return CrystalFixingSyntheticRecipe.class; }
    @Override public ItemStack getMoldItem() { return new ItemStack(EPPItemAndBlock.CRYSTAL_FIXER); }

    /** E[n] = (5 + E[n-1] + ... + E[n-4])/4 for native nextInt(5) progress.
     * Each work call consumes charged quartz with probability 1/10. Round up for
     * this deterministic furnace conversion so integer recipes cannot underpay.
     */
    static int fuelPerRepair() {
        double[] calls=new double[101];
        for (int n=1;n<=100;n++) {
            double total=5;
            for(int step=1;step<=4;step++) total+=calls[Math.max(0,n-step)];
            calls[n]=total/4;
        }
        return (int)Math.ceil(calls[100]/10);
    }

    @Override public List<RecipeHolder<CrystalFixingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        return List.of(repair("quartz_block",AEBlocks.QUARTZ_BLOCK.stack(),AEBlocks.DAMAGED_BUDDING_QUARTZ.stack()),
                repair("damaged",AEBlocks.DAMAGED_BUDDING_QUARTZ.stack(),AEBlocks.CHIPPED_BUDDING_QUARTZ.stack()),
                repair("chipped",AEBlocks.CHIPPED_BUDDING_QUARTZ.stack(),AEBlocks.FLAWED_BUDDING_QUARTZ.stack()));
    }
    private RecipeHolder<CrystalFixingSyntheticRecipe> repair(String stage,ItemStack input,ItemStack output) {
        var id=AdapterUtils.convertedId(new ResourceLocation("expatternprovider","crystal_fixer/"+stage));
        var recipe=new AdvancedAlloyFurnaceRecipe(id,List.of(new CountedIngredient(Ingredient.of(input),1),
                new CountedIngredient(Ingredient.of(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack()),fuelPerRepair())),
                List.of(),List.of(output),List.of(),
                50L * 102 * AdapterUtils.AE_TO_FE_CONVERSION,102,Ingredient.EMPTY,0,
                AdapterUtils.toMoldIngredient(getMoldItem()),AlloyFurnaceMode.NORMAL);
        return new RecipeHolder<>(id,new CrystalFixingSyntheticRecipe(recipe));
    }
    @Override public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CrystalFixingSyntheticRecipe> holder,Level level) {
        return holder == null ? null : holder.value().convertedRecipe();
    }
    @Override public List<RecipeHolder<CrystalFixingSyntheticRecipe>> findMatchingRecipes(Level level,Map<Ingredient,Long> items,
            Map<FluidStack,Long> fluids,ItemStack mold) {
        if(level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<CrystalFixingSyntheticRecipe>> result=new ArrayList<>();
        for(var holder:getGeneratedRecipes(level)) {
            Map<Ingredient,Long> required=new LinkedHashMap<>();
            for(var input:holder.value().convertedRecipe().inputs()) AdapterUtils.mergeIngredient(required,input.ingredient(),input.count());
            if(AdapterUtils.matchesRequired(items,required)) result.add(holder);
        }
        return result;
    }
}
