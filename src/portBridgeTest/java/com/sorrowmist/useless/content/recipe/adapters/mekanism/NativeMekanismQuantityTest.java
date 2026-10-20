package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeMekanismQuantityTest {
    @BeforeAll static void bootstrap(){RecipeTestBootstrap.initialize();}
    public static final class Inputs {
        ItemStackIngredient item; FluidStackIngredient fluid;
        public ItemStackIngredient getItemInput(){return item;}
        public FluidStackIngredient getFluidInput(){return fluid;}
    }
    static Inputs inputs(){
        var inputs=new Inputs();
        inputs.item=mock(ItemStackIngredient.class);
        when(inputs.item.serialize()).thenReturn(JsonParser.parseString("[{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"amount\":2},{\"ingredient\":{\"item\":\"minecraft:gold_ingot\"},\"amount\":7}]"));
        inputs.fluid=mock(FluidStackIngredient.class);
        when(inputs.fluid.serialize()).thenReturn(JsonParser.parseString("[{\"fluid\":\"minecraft:water\",\"amount\":200},{\"fluid\":\"minecraft:lava\",\"amount\":700}]"));
        return inputs;
    }
    static AdvancedAlloyFurnaceRecipe convert(Inputs input){
        return MekanismChemicalRecipeSupport.recipe(new ResourceLocation("test:quantities"),
                MekanismChemicalRecipeSupport.items(input.item),MekanismChemicalRecipeSupport.fluidIngredients(input.fluid),
                List.of(),List.of(new ItemStack(Items.DIAMOND)),List.of(),List.of(),1,1,null);
    }
    @Test void differingItemAndFluidQuantitiesProduceIndependentOrVariants(){
        var inputs=inputs();
        assertTrue(NativeMekanismVariants.needsExpansion(inputs));
        var recipes=NativeMekanismVariants.expand(inputs,()->List.of(convert(inputs)));
        assertEquals(4,recipes.size());
        var quantities=new HashSet<String>(); var ids=new HashSet<ResourceLocation>();
        for(var recipe:recipes){
            assertEquals(1,recipe.inputs().size()); assertEquals(1,recipe.inputFluids().size());
            quantities.add(recipe.inputs().get(0).count()+":"+recipe.inputFluids().get(0).amount()); ids.add(recipe.id());
        }
        assertEquals(Set.of("2:200","2:700","7:200","7:700"),quantities); assertEquals(4,ids.size());
        assertTrue(NativeMekanismVariants.needsExpansion(inputs));
    }
    @Test void matchingRespectsEachAlternativesOwnQuantity(){
        var inputs=inputs();
        assertFalse(MekanismChemicalRecipeSupport.matchesItem(Map.of(Ingredient.of(Items.GOLD_INGOT),6L),inputs.item));
        assertTrue(MekanismChemicalRecipeSupport.matchesItem(Map.of(Ingredient.of(Items.GOLD_INGOT),7L),inputs.item));
        assertFalse(MekanismChemicalRecipeSupport.matchesFluid(Map.of(new FluidStack(Fluids.LAVA,1),699L),inputs.fluid));
        assertTrue(MekanismChemicalRecipeSupport.matchesFluid(Map.of(new FluidStack(Fluids.LAVA,1),700L),inputs.fluid));
    }
    @Test void failedConversionAlwaysClearsQuantityContext(){
        var inputs=inputs();
        assertThrows(IllegalStateException.class,()->NativeMekanismVariants.expand(inputs,()->{throw new IllegalStateException("test");}));
        assertTrue(NativeMekanismVariants.needsExpansion(inputs));
    }
    @Test void fluidBatchUsesSameFactorOnBothSidesWithoutSaturation(){
        var recipe=MekanismChemicalRecipeSupport.recipe(new ResourceLocation("test:batch"),List.of(),
                List.of(new FluidStack(Fluids.WATER,3_000_000)),List.of(),List.of(),
                List.of(new FluidStack(Fluids.LAVA,6_000_000)),List.of(),1,1,null);
        assertEquals(1_071_000_000L,recipe.inputFluids().get(0).amount());
        assertEquals(2_142_000_000,recipe.outputFluids().get(0).getAmount());
    }
    @Test void invalidRequiredFluidAndArithmeticOverflowCannotCreateFreeOutputs(){
        assertThrows(IllegalArgumentException.class,()->MekanismChemicalRecipeSupport.recipe(new ResourceLocation("test:invalid"),
                List.of(),List.of(FluidStack.EMPTY),List.of(),List.of(),List.of(new FluidStack(Fluids.LAVA,1000)),List.of(),1,1,null));
        assertThrows(ArithmeticException.class,()->MekanismChemicalRecipeSupport.saturatingMultiply(Long.MAX_VALUE,2));
    }
}
