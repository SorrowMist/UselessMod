package com.sorrowmist.useless.compat.forge1201.enderio;

import com.mojang.serialization.MapCodec;
import com.enderio.base.common.item.tool.SoulVialItem;
import com.sorrowmist.useless.compat.forge1201.recipe.CodecIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.CodecIngredientSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** An already-filled vial, retaining captured entity NBT instead of enumerating exact snapshots. */
public final class FilledSoulIngredient extends CodecIngredient<FilledSoulIngredient> {
    public static final MapCodec<FilledSoulIngredient> CODEC = ResourceLocation.CODEC.fieldOf("item")
            .xmap(FilledSoulIngredient::new, value -> value.item);
    public static final CodecIngredientSerializer<FilledSoulIngredient> TYPE = new CodecIngredientSerializer<>(CODEC.codec());
    private final ResourceLocation item;
    public FilledSoulIngredient(ResourceLocation item) { this.item = java.util.Objects.requireNonNull(item); }
    public static Ingredient of(Item item) { return new FilledSoulIngredient(BuiltInRegistries.ITEM.getKey(item)); }
    @Override public boolean test(ItemStack stack) {
        return stack != null && !stack.isEmpty() && item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                && !NativeSoulStorage.getBoundSoul(stack).isEmpty();
    }
    @Override public ItemStack[] getItems() {
        return SoulVialItem.getAllFilled().stream().filter(this::test).map(ItemStack::copy).toArray(ItemStack[]::new);
    }
    @Override public CodecIngredientSerializer<FilledSoulIngredient> getSerializer() { return TYPE; }
    @Override public boolean equals(Object other) { return other instanceof FilledSoulIngredient value && item.equals(value.item); }
    @Override public int hashCode() { return item.hashCode(); }
}
