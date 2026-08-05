package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A read-only, Mekanism-independent view of a chemical stack.
 *
 * <p>The value returned by {@link #typeKey()} is deliberately opaque.  The
 * optional integration owns its concrete type and is the only code that may
 * interpret it.</p>
 */
public interface ChemicalStackView {
    ChemicalStackView EMPTY = new ChemicalStackView() {
        @Override
        public Object typeKey() {
            return EmptyType.INSTANCE;
        }

        @Override
        public long amount() {
            return 0L;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ChemicalStackView copyWithAmount(long amount) {
            return this;
        }

        @Override
        public Component displayName() {
            return Component.empty();
        }

        @Override
        public @Nullable ResourceLocation icon() {
            return null;
        }

        @Override
        public int tintColor() {
            return 0xFFFFFFFF;
        }
    };

    Object typeKey();

    long amount();

    boolean isEmpty();

    ChemicalStackView copyWithAmount(long amount);

    Component displayName();

    /** Optional client-side icon. */
    @Nullable
    default ResourceLocation icon() {
        return null;
    }

    /** ARGB tint used when drawing {@link #icon()}. */
    default int tintColor() {
        return 0xFFFFFFFF;
    }

    default boolean isSameType(@Nullable ChemicalStackView other) {
        return other != null && !this.isEmpty() && !other.isEmpty()
                && this.typeKey().equals(other.typeKey());
    }

    enum EmptyType {
        INSTANCE
    }
}
