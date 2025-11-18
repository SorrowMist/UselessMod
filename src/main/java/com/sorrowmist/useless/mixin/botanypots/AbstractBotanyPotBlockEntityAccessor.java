package com.sorrowmist.useless.mixin.botanypots;

import net.darkhax.botanypots.common.impl.block.entity.AbstractBotanyPotBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractBotanyPotBlockEntity.class)
public interface AbstractBotanyPotBlockEntityAccessor {
    @Accessor("items")
    NonNullList<ItemStack> items();
}
