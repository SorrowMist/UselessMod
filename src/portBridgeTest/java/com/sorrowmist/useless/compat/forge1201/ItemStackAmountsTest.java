package com.sorrowmist.useless.compat.forge1201;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemStackAmountsTest {
    @Test void smeltingTwoPerInputDoesNotClampAwayHalfTheOutput() {
        RecipeTestBootstrap.initialize();
        ItemStack result=new ItemStack(Items.IRON_INGOT,2);
        result.getOrCreateTag().putString("custom","kept");
        var stacks=ItemStackAmounts.multiply(result,64);
        assertEquals(128,stacks.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(2,stacks.size());
        assertTrue(stacks.stream().allMatch(s->s.getCount()<=s.getMaxStackSize()));
        assertEquals("kept",stacks.get(1).getTag().getString("custom"));
        stacks.get(0).getTag().putString("custom","changed");
        assertEquals("kept",stacks.get(1).getTag().getString("custom"));
        assertEquals("kept",result.getTag().getString("custom"));
        assertEquals(2,result.getCount());
    }
    @Test void partialFinalStackAndZeroInputsAreExact() {
        RecipeTestBootstrap.initialize();
        var result=new ItemStack(Items.IRON_INGOT,3);
        var stacks=ItemStackAmounts.multiply(result,23);
        assertEquals(69,stacks.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(5,stacks.get(1).getCount());
        assertTrue(ItemStackAmounts.multiply(result,0).isEmpty());
        assertTrue(ItemStackAmounts.multiply(ItemStack.EMPTY,64).isEmpty());
    }
}
