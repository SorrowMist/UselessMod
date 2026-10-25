package com.sorrowmist.useless.content.recipe.adapters.tconstruct;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class TConstructRecipeAdapterTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @Test void reusableCastIsAnAdditionalAndMoldNeverAConsumedInput() {
        var items=new ArrayList<CountedIngredient>();
        var molds=new ArrayList<Ingredient>();
        molds.add(Ingredient.of(Items.CAULDRON));
        var stack=new ItemStack(Items.GOLD_INGOT); stack.getOrCreateTag().putString("cast","wire");
        var cast=StrictNBTIngredient.of(stack);
        TConstructRecipeAdapter.addCast(cast,false,items,molds);
        assertTrue(items.isEmpty()); assertEquals(2,molds.size()); assertSame(cast,molds.get(1));
        assertTrue(molds.get(1).test(stack)); assertFalse(molds.get(1).test(new ItemStack(Items.GOLD_INGOT)));
    }
    @Test void sandAndOtherConsumedCastsAreChargedExactlyOnce() {
        var items=new ArrayList<CountedIngredient>(); var molds=new ArrayList<Ingredient>();
        var cast=Ingredient.of(Items.SAND);
        TConstructRecipeAdapter.addCast(cast,true,items,molds);
        assertEquals(1,items.size()); assertEquals(1,items.get(0).count());
        assertTrue(items.get(0).consumed()); assertTrue(molds.isEmpty()); assertSame(cast,items.get(0).ingredient());
    }
    @Test void compoundsWithDifferentCostsAreSeparateNotCheaperAlternatives() {
        var choices=TConstructRecipeAdapter.fluids(JsonParser.parseString("[{\"fluid\":\"minecraft:water\",\"amount\":90},{\"fluid\":\"minecraft:lava\",\"amount\":180}]"));
        assertEquals(2,choices.size()); assertEquals(90,choices.get(0).amount()); assertEquals(180,choices.get(1).amount());
        assertTrue(choices.get(0).ingredient().test(new FluidStack(Fluids.WATER,1)));
        assertFalse(choices.get(0).ingredient().test(new FluidStack(Fluids.FLOWING_WATER,1)));
        assertFalse(choices.get(0).ingredient().test(new FluidStack(Fluids.LAVA,1)));
    }
    @Test void overlappingAlternativesCannotOfferTheSameFluidAtACheaperCost() {
        assertThrows(IllegalArgumentException.class,()->TConstructRecipeAdapter.fluids(JsonParser.parseString(
                "[{\"fluid\":\"minecraft:water\",\"amount\":180},{\"fluid\":\"minecraft:water\",\"amount\":90}]")));
    }
    @Test void fluidTagsRemainStructuralAcrossSerialization() {
        var fluid=TConstructRecipeAdapter.fluids(JsonParser.parseString("{\"tag\":\"forge:molten_iron\",\"amount\":90}")).get(0);
        assertEquals(90,fluid.amount()); assertEquals("forge:molten_iron",fluid.ingredient().toJson().getAsJsonObject().get("tag").getAsString());
    }
    @Test void neverFlattenDurabilityMaterialsRetexturingOrFuelIntoFixedOutputs() {
        var root="slimeknights.tconstruct.library.recipe.";
        assertTrue(TConstructRecipeAdapter.safeClass(root+"melting.OreMeltingRecipe"));
        assertTrue(TConstructRecipeAdapter.safeClass(root+"casting.ItemCastingRecipe"));
        assertFalse(TConstructRecipeAdapter.safeClass(root+"melting.DamageableMeltingRecipe"));
        assertFalse(TConstructRecipeAdapter.safeClass(root+"melting.MaterialMeltingRecipe"));
        assertFalse(TConstructRecipeAdapter.safeClass(root+"casting.RetexturedCastingRecipe"));
        assertFalse(TConstructRecipeAdapter.safeClass(root+"casting.material.MaterialCastingRecipe"));
        assertFalse(TConstructRecipeAdapter.safeClass(root+"melting.MeltingFuel"));
    }
}
