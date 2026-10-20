package com.sorrowmist.useless.compat.forge1201;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Exact quantity expansion without int overflow, clamping loss, or shared mutable tags. */
public final class ItemStackAmounts {
    private ItemStackAmounts() {}
    public static List<ItemStack> multiply(ItemStack result,int inputs) {
        if (result.isEmpty() || inputs<=0) return List.of();
        long remaining=(long)result.getCount()*inputs;
        int limit=Math.max(1,result.getMaxStackSize());
        List<ItemStack> stacks=new ArrayList<>();
        while(remaining>0) {
            int count=(int)Math.min(remaining,limit);
            stacks.add(result.copyWithCount(count));
            remaining-=count;
        }
        return stacks;
    }
}
