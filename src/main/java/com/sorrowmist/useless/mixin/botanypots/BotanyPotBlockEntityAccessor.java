package com.sorrowmist.useless.mixin.botanypots;

import net.darkhax.bookshelf.common.api.util.TickAccumulator;
import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BotanyPotBlockEntity.class)
public interface BotanyPotBlockEntityAccessor {
    @Accessor
    TickAccumulator getGrowCooldown();

}
