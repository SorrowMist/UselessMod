package com.sorrowmist.useless.compat.forge1201.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.BiFunction;

/** Registers real Forge providers, not an alternate lookup map invisible to other mods. */
public final class BlockCapabilityRegistry {
    private final Map<BlockEntityType<?>, Map<Capability<?>, BiFunction<BlockEntity, Direction, ?>>> entries = new IdentityHashMap<>();

    public synchronized <T, E extends BlockEntity> void register(Capability<T> capability, BlockEntityType<E> type,
                                                                 BiFunction<E, Direction, T> factory) {
        Objects.requireNonNull(capability); Objects.requireNonNull(type); Objects.requireNonNull(factory);
        var byCapability = entries.computeIfAbsent(type, ignored -> new IdentityHashMap<>());
        if (byCapability.containsKey(capability)) throw new IllegalStateException("Duplicate block capability registration: " + type);
        byCapability.put(capability, (entity, side) -> factory.apply((E) entity, side));
    }

    @Nullable public synchronized Provider provider(BlockEntity entity) {
        var factories = entries.get(entity.getType());
        return factories == null ? null : new Provider(entity, new IdentityHashMap<>(factories));
    }

    public static final class Provider implements ICapabilityProvider {
        private final BlockEntity entity;
        private final Map<Capability<?>, BiFunction<BlockEntity, Direction, ?>> factories;
        private final Map<Key, Entry> cache = new HashMap<>();
        private Provider(BlockEntity entity, Map<Capability<?>, BiFunction<BlockEntity, Direction, ?>> factories) {
            this.entity=entity; this.factories=factories;
        }
        @Override public synchronized <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            var factory = factories.get(capability);
            if (factory == null || entity.isRemoved()) return LazyOptional.empty();
            Key key = new Key(capability, side);
            Object value = factory.apply(entity, side);
            Entry previous = cache.get(key);
            if (previous != null && previous.value == value) return previous.optional.cast();
            // Sided access can change when the GUI changes I/O settings. Invalidate the old view.
            cache.remove(key);
            if (previous != null) previous.optional.invalidate();
            if (value == null) return LazyOptional.empty();
            var optional = LazyOptional.of(() -> value);
            cache.put(key, new Entry(value, optional));
            return optional.cast();
        }
        public synchronized void invalidate() {
            List<Entry> old = List.copyOf(cache.values());
            cache.clear();
            old.forEach(entry -> entry.optional.invalidate());
        }
        private record Key(Capability<?> capability, @Nullable Direction side) {}
        private record Entry(Object value, LazyOptional<?> optional) {}
    }
}
