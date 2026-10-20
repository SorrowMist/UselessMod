package com.sorrowmist.useless.compat.forge1201.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;

public final class DeferredItem<T extends Item> extends DeferredHolder<Item,T> implements ItemLike {
    DeferredItem(RegistryObject<T> value) { super(value); }
    @Override public Item asItem() { return get(); }
    public ItemStack toStack() { return new ItemStack(get()); }
    public ItemStack toStack(int count) { return new ItemStack(get(), count); }
}
