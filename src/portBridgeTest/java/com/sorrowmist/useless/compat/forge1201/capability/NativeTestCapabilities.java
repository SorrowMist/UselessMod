package com.sorrowmist.useless.compat.forge1201.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

/** Native tokens without the launcher's CapabilityToken bytecode transformation. */
final class NativeTestCapabilities {
    static final Capability<IEnergyStorage> ENERGY = create(IEnergyStorage.class.getName());
    static final Capability<IItemHandler> ITEM_HANDLER = create(IItemHandler.class.getName());

    private NativeTestCapabilities() {
    }

    @SuppressWarnings("unchecked")
    private static <T> Capability<T> create(String name) {
        try {
            var constructor = Capability.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return (Capability<T>) constructor.newInstance(name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create native Forge capability test token", exception);
        }
    }
}
