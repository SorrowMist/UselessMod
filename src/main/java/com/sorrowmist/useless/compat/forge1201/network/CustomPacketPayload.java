package com.sorrowmist.useless.compat.forge1201.network;

import net.minecraft.resources.ResourceLocation;
/** Named application payload; transported by a direction-checked Forge SimpleChannel. */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
