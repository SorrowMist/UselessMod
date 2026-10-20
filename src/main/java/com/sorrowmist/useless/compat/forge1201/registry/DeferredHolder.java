package com.sorrowmist.useless.compat.forge1201.registry;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

/** Typed supplier backed by Forge registration, not a second game registry. */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    protected final RegistryObject<T> delegate;
    protected final Supplier<T> local;
    protected final ResourceLocation id;
    protected DeferredHolder(RegistryObject<T> delegate) {
        this.delegate = delegate; this.id = delegate.getId(); this.local = null;
    }
    protected DeferredHolder(ResourceLocation id, Supplier<T> supplier) {
        this.delegate = null; this.id = id;
        this.local = net.minecraftforge.common.util.Lazy.of(supplier);
    }
    @Override public T get() { return delegate != null ? delegate.get() : local.get(); }
    public T value() { return get(); }
    public ResourceLocation getId() { return id; }
    public boolean isBound() { return delegate == null || delegate.isPresent(); }
}
