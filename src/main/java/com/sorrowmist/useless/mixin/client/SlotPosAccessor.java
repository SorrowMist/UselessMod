package com.sorrowmist.useless.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Apple UI API：客户端按布局重新摆放槽位（与 AE2 的做法相同，只改客户端显示坐标，服务端不受影响）。 */
@Mixin(Slot.class)
public interface SlotPosAccessor {
    @Mutable @Accessor("x") void useless$setX(int x);
    @Mutable @Accessor("y") void useless$setY(int y);
}
