package com.sorrowmist.useless.mixin.botanypots;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=BlockEntityBotanyPot.class,remap=false)
public interface AbstractBotanyPotBlockEntityAccessor {
    @Accessor("growthTime") int uselessMod$getGrowthTime();
}
