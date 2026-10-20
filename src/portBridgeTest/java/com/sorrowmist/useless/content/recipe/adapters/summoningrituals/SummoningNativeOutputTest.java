package com.sorrowmist.useless.content.recipe.adapters.summoningrituals;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SummoningNativeOutputTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void ordinaryStackNbtIsCopiedIndependently() {
        var original=new ItemStack(Items.PAPER,9); original.getOrCreateTag().putInt("quality",5);
        var result=SummoningRitualsAltarRecipeAdapter.nativeItemOutput(original,new CompoundTag());
        assertEquals(9,result.getCount()); assertEquals(5,result.getTag().getInt("quality"));
        result.getTag().putInt("quality",2); assertEquals(5,original.getTag().getInt("quality"));
    }
    @Test void itemEntityReplacementUsesNativeFourItemSpawnBatches() {
        var replacement=new ItemStack(Items.DIAMOND,2); replacement.getOrCreateTag().putInt("marker",17);
        var entityData=new CompoundTag(); entityData.put("Item",replacement.save(new CompoundTag()));
        var result=SummoningRitualsAltarRecipeAdapter.nativeItemOutput(new ItemStack(Items.PAPER,9),entityData);
        assertTrue(result.is(Items.DIAMOND)); assertEquals(6,result.getCount());
        assertEquals(17,result.getTag().getInt("marker"));
        assertEquals(2,entityData.getCompound("Item").getByte("Count"));
    }
    @Test void entityLifetimeOrOwnershipIsNotMistakenForItemTags() {
        var data=new CompoundTag(); data.putInt("Age",5999);
        assertNull(SummoningRitualsAltarRecipeAdapter.nativeItemOutput(new ItemStack(Items.PAPER),data));
    }
}
