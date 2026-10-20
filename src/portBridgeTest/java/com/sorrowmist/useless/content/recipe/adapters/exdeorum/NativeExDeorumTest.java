package com.sorrowmist.useless.content.recipe.adapters.exdeorum;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.world.level.storage.loot.providers.number.*;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import thedarkcolour.exdeorum.loot.SummationGenerator;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeExDeorumTest {
    @BeforeAll static void init(){RecipeTestBootstrap.initialize();}
    @Test void uniformUsesBothBoundsWithoutNativePrecedenceBug(){
        assertEquals(4,ExDeorumRecipeAdapterUtils.expectedAmount(UniformGenerator.between(2,6)).orElseThrow());
    }
    @Test void binomialAndSummationRetainExpectedQuantities(){
        var sum=new SummationGenerator(new NumberProvider[]{ConstantValue.exactly(2),BinomialDistributionGenerator.binomial(8,0.25f)});
        assertEquals(4,ExDeorumRecipeAdapterUtils.expectedAmount(sum).orElseThrow());
    }
    @Test void unknownContextProviderIsRejected(){
        assertTrue(NativeNumberExpectations.expected(com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:score\",\"score\":\"test\"}")).isEmpty());
    }
    @Test void itemNbtIsCopiedWithoutSharingRecipeState(){
        var tag=new CompoundTag(); tag.putString("marker","original");
        var stack=ExDeorumRecipeAdapterUtils.resultStack(Items.DIAMOND,tag);
        assertEquals("original",stack.getTag().getString("marker"));
        stack.getTag().putString("marker","changed"); assertEquals("original",tag.getString("marker"));
    }
}
