package com.sorrowmist.useless.compat.forge1201.recipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
/** Uses Forge's registered serializers so native crafting, network sync and JEI agree. */
public final class DataComponentIngredient {
    private DataComponentIngredient() {}
    public static Ingredient of(boolean strict,ItemStack stack) {
        if(stack.isEmpty()) return Ingredient.EMPTY;
        ItemStack copy=stack.copyWithCount(1);
        return strict?StrictNBTIngredient.of(copy):PartialNBTIngredient.of(copy.getItem(),copy.hasTag()?copy.getTag().copy():new CompoundTag());
    }
}
