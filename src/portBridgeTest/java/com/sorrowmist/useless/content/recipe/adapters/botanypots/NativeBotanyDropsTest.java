package com.sorrowmist.useless.content.recipe.adapters.botanypots;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import net.darkhax.botanypots.data.recipes.crop.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeBotanyDropsTest {
    @BeforeAll static void init(){RecipeTestBootstrap.initialize();}
    @Test void rollsMultiplyTheEntireNbtStack(){
        var stack=new ItemStack(Items.APPLE,3); stack.getOrCreateTag().putString("marker","crop");
        var drops=BotanyPotsRecipeAdapter.collectDrops(List.of(new HarvestEntry(1,stack,2,4)));
        var scaled=ExpectedOutputScaler.scale(drops).orElseThrow();
        assertEquals(1,scaled.operations());assertEquals(9,scaled.outputs().get(0).getCount());
        assertEquals("crop",scaled.outputs().get(0).getTag().getString("marker"));
        scaled.outputs().get(0).getTag().putString("marker","changed");assertEquals("crop",stack.getTag().getString("marker"));
    }
    @Test void probabilisticHarvestScalesWaterTimeAndEnergyTogether(){
        var crop=mock(BasicCrop.class);
        when(crop.getSeed()).thenReturn(Ingredient.of(Items.WHEAT_SEEDS));
        when(crop.getGrowthTicks()).thenReturn(100);
        when(crop.getResults()).thenReturn(List.of(new HarvestEntry(0.25f,new ItemStack(Items.WHEAT),1,1)));
        var recipe=BotanyPotsRecipeAdapter.convertCrop(new ResourceLocation("test:crop"),crop,Ingredient.of(Items.FLOWER_POT));
        assertNotNull(recipe); assertEquals(4000,recipe.inputFluids().get(0).amount());
        assertEquals(400,recipe.processTime()); assertEquals(1,recipe.outputs().get(0).getCount());
        assertTrue(recipe.molds().get(1).test(new ItemStack(Items.WHEAT_SEEDS)));
    }
    @Test void emptyRequiredPotCannotProduceRecipe(){
        var crop=mock(BasicCrop.class);
        when(crop.getSeed()).thenReturn(Ingredient.of(Items.WHEAT_SEEDS));
        when(crop.getGrowthTicks()).thenReturn(100);
        when(crop.getResults()).thenReturn(List.of(new HarvestEntry(1,new ItemStack(Items.WHEAT),1,1)));
        assertNull(BotanyPotsRecipeAdapter.convertCrop(new ResourceLocation("test:crop"),crop,Ingredient.EMPTY));
    }
}
