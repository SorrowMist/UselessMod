package com.sorrowmist.useless.compat.forge1201.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

/** HNN 5 matrix matching: extra top-level keys are allowed, nested values must be exactly equal.
 * Native PartialNBTIngredient recursively allows extra nested keys, which is a different predicate. */
public final class TopLevelNbtIngredient extends CodecIngredient<TopLevelNbtIngredient> {
    public static final MapCodec<TopLevelNbtIngredient> CODEC = RecipeCodecs.ITEM_STACK.fieldOf("input")
            .xmap(TopLevelNbtIngredient::new,TopLevelNbtIngredient::input);
    public static final CodecIngredientSerializer<TopLevelNbtIngredient> TYPE=new CodecIngredientSerializer<>(CODEC.codec());
    private final ItemStack input;
    public TopLevelNbtIngredient(ItemStack input) {
        if(input==null||input.isEmpty()) throw new IllegalArgumentException("A matrix ingredient requires an item");
        this.input=input.copyWithCount(1);
    }
    public ItemStack input() {return input.copy();}
    @Override public boolean test(ItemStack stack) {
        if(stack==null||stack.isEmpty()||!stack.is(input.getItem())) return false;
        if(!input.hasTag()) return true;
        CompoundTag actual=stack.getTag();
        if(actual==null) return false;
        CompoundTag required=input.getTag();
        for(String key:required.getAllKeys()) if(!Objects.equals(required.get(key),actual.get(key))) return false;
        return true;
    }
    @Override public ItemStack[] getItems() {return new ItemStack[]{input.copy()};}
    @Override public CodecIngredientSerializer<TopLevelNbtIngredient> getSerializer() {return TYPE;}
    @Override public boolean equals(Object other) {
        return other instanceof TopLevelNbtIngredient ingredient && ItemStack.isSameItemSameTags(input,ingredient.input);
    }
    @Override public int hashCode() {return Objects.hash(input.getItem(),input.getTag());}
}
