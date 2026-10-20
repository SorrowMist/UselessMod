package com.sorrowmist.useless.content.recipe.adapters.botanypots;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.*;
import net.darkhax.botanypots.BotanyPotHelper;
import net.darkhax.botanypots.block.BlockBotanyPot;
import net.darkhax.botanypots.data.recipes.crop.BasicCrop;
import net.darkhax.botanypots.data.recipes.crop.Crop;
import net.darkhax.botanypots.data.recipes.crop.HarvestEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

/** Native BotanyPots 13 crops: retain seed predicates, stack NBT and harvest probabilities. */
public final class BotanyPotsRecipeAdapter implements IRecipeAdapter<BotanyPotsCropSyntheticRecipe> {
    private static volatile Ingredient potMoldCache;
    @Override public String sourceId(){return RecipeSourceIds.BOTANY_POTS;}
    @Override public Class<BotanyPotsCropSyntheticRecipe> getRecipeClass(){return BotanyPotsCropSyntheticRecipe.class;}
    @Override public ItemStack getMoldItem(){return null;}
    @Override public List<RecipeHolder<BotanyPotsCropSyntheticRecipe>> getGeneratedRecipes(Level level){
        if(level==null) return List.of();
        List<RecipeHolder<BotanyPotsCropSyntheticRecipe>> result=new ArrayList<>();
        for(RecipeHolder<Crop> holder:RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(),BotanyPotHelper.CROP_TYPE.get())){
            if(!(holder.value() instanceof BasicCrop crop)) continue;
            var converted=convertCrop(holder.id(),crop,potMold());
            if(converted!=null) result.add(new RecipeHolder<>(converted.id(),new BotanyPotsCropSyntheticRecipe(converted)));
        }
        return List.copyOf(result);
    }
    @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BotanyPotsCropSyntheticRecipe> holder,Level level){
        return holder==null || holder.value().convertedRecipe()==null?List.of():List.of(holder.value().convertedRecipe());
    }
    static AdvancedAlloyFurnaceRecipe convertCrop(net.minecraft.resources.ResourceLocation source,BasicCrop crop,Ingredient pot){
        if(crop.getSeed().isEmpty() || pot.isEmpty()) return null;
        var scaled=ExpectedOutputScaler.scale(collectDrops(crop.getResults()));
        if(scaled.isEmpty() || scaled.get().outputs().isEmpty()) return null;
        var batch=scaled.get();
        return new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(source),List.of(),
                List.of(new LongSizedFluidIngredient(FluidIngredient.single(Fluids.WATER),Math.multiplyExact(1000L,batch.operations()))),
                List.of(),batch.outputs(),List.of(),List.of(),Math.multiplyExact((long)AdapterUtils.DEFAULT_ENERGY,batch.operations()),
                AdapterUtils.safeInt(Math.multiplyExact((long)Math.max(1,crop.getGrowthTicks()),batch.operations())),
                Ingredient.EMPTY,0,List.of(pot,crop.getSeed()),AlloyFurnaceMode.NORMAL);
    }
    static List<ExpectedOutputScaler.WeightedItemOutput> collectDrops(List<HarvestEntry> entries){
        List<ExpectedOutputScaler.WeightedItemOutput> result=new ArrayList<>();
        for(var entry:entries){
            var stack=entry.getItem();
            if(stack==null || stack.isEmpty()) continue;
            // Native generateDrops rolls min..max copies of the entire item stack.
            int min=Math.multiplyExact(entry.getMinRolls(),stack.getCount());
            int max=Math.multiplyExact(entry.getMaxRolls(),stack.getCount());
            result.add(new ExpectedOutputScaler.WeightedItemOutput(stack.copyWithCount(1),min,max,entry.getChance()));
        }
        return List.copyOf(result);
    }
    private static Ingredient potMold(){
        var cached=potMoldCache; if(cached!=null) return cached;
        var stacks=new ArrayList<ItemStack>();
        for(var block:BuiltInRegistries.BLOCK) if(block instanceof BlockBotanyPot && block.asItem()!=Items.AIR) stacks.add(new ItemStack(block));
        return potMoldCache=stacks.isEmpty()?Ingredient.EMPTY:Ingredient.of(stacks.stream());
    }
}
