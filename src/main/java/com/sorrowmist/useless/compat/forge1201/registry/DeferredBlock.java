package com.sorrowmist.useless.compat.forge1201.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;

public final class DeferredBlock<T extends Block> extends DeferredHolder<Block,T> implements ItemLike {
    DeferredBlock(RegistryObject<T> value) { super(value); }
    @Override public Item asItem() { return get().asItem(); }
}
