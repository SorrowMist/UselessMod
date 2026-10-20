package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.google.gson.*;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeMekanismIngredientsTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void nativeItemCountAndPartialNbtStayStructural() {
        CompoundTag nbt=new CompoundTag(); nbt.putString("quality","pure");
        var predicate=PartialNBTIngredient.of(Items.IRON_INGOT,nbt);
        var source=IngredientCreatorAccess.item().from(predicate,4);
        var converted=MekanismChemicalRecipeSupport.item(source);
        assertEquals(4,converted.count());
        ItemStack matching=new ItemStack(Items.IRON_INGOT,4); matching.setTag(nbt.copy()); matching.getTag().putInt("extra",5);
        assertEquals(source.testType(matching),converted.ingredient().test(matching));
        assertFalse(converted.ingredient().test(new ItemStack(Items.IRON_INGOT,4)));
        assertEquals(12,MekanismChemicalRecipeSupport.countedItem(source,3).count());
    }
    @Test void sameCostNativeAlternativesKeepBothPredicates() {
        var source=IngredientCreatorAccess.item().createMulti(
                IngredientCreatorAccess.item().from(Ingredient.of(Items.IRON_INGOT),2),
                IngredientCreatorAccess.item().from(Ingredient.of(Items.GOLD_INGOT),2));
        var converted=MekanismChemicalRecipeSupport.item(source);
        assertEquals(2,converted.count());
        assertTrue(converted.ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(converted.ingredient().test(new ItemStack(Items.GOLD_INGOT)));
        assertFalse(converted.ingredient().test(new ItemStack(Items.DIAMOND)));
    }
    @Test void nativeFluidExactNbtIncludesAbsenceAndAdditionalTags() {
        var source=IngredientCreatorAccess.fluid().from(new FluidStack(Fluids.WATER,500));
        var converted=MekanismChemicalRecipeSupport.fluidIngredients(source).get(0);
        assertEquals(500,converted.amount());
        var input=new FluidStack(Fluids.WATER,500);
        assertEquals(source.testType(input),converted.ingredient().test(input));
        input.getOrCreateTag().putString("extra","yes");
        assertFalse(source.testType(input)); assertFalse(converted.ingredient().test(input));
        var tagged=IngredientCreatorAccess.fluid().from(input);
        var restored=MekanismChemicalRecipeSupport.fluidIngredients(tagged).get(0).ingredient();
        assertTrue(restored.test(input));
        input.getOrCreateTag().putInt("additional",1);
        assertFalse(restored.test(input));
    }
    @Test void differingAlternativeCountsAndOverflowsNeverBecomeCheaper() {
        var source=IngredientCreatorAccess.item().createMulti(
                IngredientCreatorAccess.item().from(Ingredient.of(Items.IRON_INGOT),2),
                IngredientCreatorAccess.item().from(Ingredient.of(Items.GOLD_INGOT),3));
        assertThrows(JsonSyntaxException.class,()->MekanismChemicalRecipeSupport.item(source));
        assertThrows(JsonSyntaxException.class,()->NativeMekanismIngredients.item(
                JsonParser.parseString("{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"amount\":9223372036854775807}"),2));
        assertThrows(JsonSyntaxException.class,()->NativeMekanismIngredients.fluid(
                JsonParser.parseString("{\"fluid\":\"minecraft:water\",\"amount\":2147483648}")));
    }
    @Test void fractionalEnergyMultiplierRoundsUpInsteadOfTruncating() {
        assertEquals(3,MekanismChemicalRecipeSupport.energyCost(1,2,mekanism.api.math.FloatingLong.parseFloatingLong("1.25")));
    }
}
