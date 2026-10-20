package com.sorrowmist.useless.compat.forge1201.registry;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;

/** Keep all game registrations on Forge's native DeferredRegister. */
public class DeferredRegister<R> {
    protected final net.minecraftforge.registries.DeferredRegister<R> delegate;
    protected final String namespace;
    protected final Collection<DeferredHolder<R,? extends R>> entries = new ArrayList<>();
    protected DeferredRegister(ResourceKey<? extends Registry<R>> key, String namespace) {
        this.delegate = net.minecraftforge.registries.DeferredRegister.create(key, namespace);
        this.namespace = namespace;
    }
    protected DeferredRegister(String namespace) { this.delegate=null; this.namespace=namespace; }
    public static <R> DeferredRegister<R> createLocal(String namespace) { return new DeferredRegister<>(namespace); }
    public static <R> DeferredRegister<R> create(ResourceKey<? extends Registry<R>> key, String namespace) {
        return new DeferredRegister<>(key, namespace);
    }
    public static Items createItems(String namespace) { return new Items(namespace); }
    public static Blocks createBlocks(String namespace) { return new Blocks(namespace); }
    public <T extends R> DeferredHolder<R,T> register(String name, Supplier<? extends T> factory) {
        DeferredHolder<R,T> holder;
        if(delegate==null) {
            ResourceLocation id=new ResourceLocation(namespace,name);
            holder=new DeferredHolder<>(id,()-> {
                T value=factory.get();
                if(value instanceof com.sorrowmist.useless.compat.forge1201.component.DataComponentType<?> key) key.bind(id);
                return value;
            });
        } else holder = new DeferredHolder<>(delegate.register(name, factory));
        entries.add(holder); return holder;
    }
    public void register(IEventBus bus) { if(delegate!=null) delegate.register(bus); else entries.forEach(DeferredHolder::get); }
    public Collection<DeferredHolder<R,? extends R>> getEntries() { return java.util.List.copyOf(entries); }
    public String getNamespace() { return namespace; }
    public static final class Items extends DeferredRegister<Item> {
        private Items(String namespace) { super(Registries.ITEM, namespace); }
        @Override public <T extends Item> DeferredItem<T> register(String name, Supplier<? extends T> factory) {
            DeferredItem<T> holder = new DeferredItem<>(delegate.register(name, factory));
            entries.add(holder); return holder;
        }
    }
    public static final class Blocks extends DeferredRegister<Block> {
        private Blocks(String namespace) { super(Registries.BLOCK, namespace); }
        @Override public <T extends Block> DeferredBlock<T> register(String name, Supplier<? extends T> factory) {
            DeferredBlock<T> holder = new DeferredBlock<>(delegate.register(name, factory));
            entries.add(holder); return holder;
        }
    }
}
