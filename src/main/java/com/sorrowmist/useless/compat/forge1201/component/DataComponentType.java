package com.sorrowmist.useless.compat.forge1201.component;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;

/** A namespaced NBT key, not a nonexistent Minecraft 1.20.1 data-component registry. */
public final class DataComponentType<T> {
    private final Codec<T> codec;
    private ResourceLocation id;
    private DataComponentType(Codec<T> codec) { this.codec=java.util.Objects.requireNonNull(codec,"Persistent codec"); }
    public Codec<T> codec() { return codec; }
    public ResourceLocation id() { return java.util.Objects.requireNonNull(id,"Unregistered component key"); }
    public void bind(ResourceLocation id) {
        if(this.id!=null && !this.id.equals(id)) throw new IllegalStateException("Component already bound");
        this.id=id;
    }
    public static <T> Builder<T> builder() { return new Builder<>(); }
    public static final class Builder<T> {
        private Codec<T> codec;
        public Builder<T> persistent(Codec<T> codec) { this.codec=codec; return this; }
        public Builder<T> networkSynchronized(StreamCodec<?,T> codec) { return this; }
        public Builder<T> cacheEncoding() { return this; }
        public DataComponentType<T> build() { return new DataComponentType<>(codec); }
    }
}
