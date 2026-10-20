package com.sorrowmist.useless.compat.forge1201.recipe;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;
/** Real native crafting container, including empty slots, dimensions and remainder semantics. */
public final class CraftingInputs {
    private CraftingInputs() {}
    public static CraftingContainer of(int width,int height,List<ItemStack> stacks) {
        if(width<=0 || height<=0 || Math.multiplyExact(width,height)!=stacks.size())
            throw new IllegalArgumentException("Crafting grid dimensions do not match slot count");
        NonNullList<ItemStack> copy=NonNullList.withSize(stacks.size(),ItemStack.EMPTY);
        for(int i=0;i<stacks.size();i++) copy.set(i,stacks.get(i).copy());
        return new TransientCraftingContainer(new AbstractContainerMenu(null,-1) {
            @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return false; }
        },width,height,copy);
    }
}
