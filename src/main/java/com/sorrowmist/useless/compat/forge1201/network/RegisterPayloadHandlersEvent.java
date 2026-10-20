package com.sorrowmist.useless.compat.forge1201.network;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** Fired explicitly during common setup, after Forge has attached static mod subscribers. */
public final class RegisterPayloadHandlersEvent extends Event implements IModBusEvent {
    private final String modId;
    public RegisterPayloadHandlersEvent(String modId) { this.modId=modId; }
    public PayloadRegistrar registrar(String version) { return new PayloadRegistrar(modId,version); }
}
