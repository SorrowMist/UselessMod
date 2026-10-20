package com.sorrowmist.useless.compat.forge1201.capability;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import java.util.function.BiFunction;

/** Port-only registration event. Providers are attached using Forge's public capability event. */
public final class RegisterCapabilitiesEvent extends Event implements IModBusEvent {
    private static final BlockCapabilityRegistry REGISTRY = new BlockCapabilityRegistry();
    private static boolean installed;
    public static synchronized void initialize() {
        if (installed) return;
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, RegisterCapabilitiesEvent::attach);
        installed=true;
    }
    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        var provider=REGISTRY.provider(event.getObject());
        if (provider != null) {
            event.addCapability(new ResourceLocation("useless_mod", "forge1201_block_capabilities"), provider);
            event.addListener(provider::invalidate);
        }
    }
    public <T, E extends BlockEntity> void registerBlockEntity(Capability<T> capability, BlockEntityType<E> type,
                                                              BiFunction<E, Direction, T> factory) {
        REGISTRY.register(capability, type, factory);
    }
}
