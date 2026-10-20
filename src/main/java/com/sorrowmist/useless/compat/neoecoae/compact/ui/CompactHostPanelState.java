package com.sorrowmist.useless.compat.neoecoae.compact.ui;

import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;

/** Supplies the ordinary ECO artwork with the compact host's built-in level-nine hardware. */
public final class CompactHostPanelState {
    private static final int SLOT_COUNT = 11;
    // ECO panel tiers encode L4/L6/L9 as 1/2/3.
    private static final int LEVEL_NINE = 3;

    private record Codec(Method write, Method read) {}

    // These panel classes do not exist in the oldest supported ECO release. Keep their
    // linkage inside the optional mixin, using the panel's own public serialization API.
    private static final ClassValue<Codec> CODECS = new ClassValue<>() {
        @Override
        protected Codec computeValue(Class<?> type) {
            try {
                return new Codec(type.getMethod("toTag"), type.getMethod("fromTag", CompoundTag.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported ECO host panel " + type.getName(), e);
            }
        }
    };

    private CompactHostPanelState() {}

    public static Object levelNine(Object panel, boolean computation) {
        if (panel == null) return null;
        Codec codec = CODECS.get(panel.getClass());
        try {
            CompoundTag tag = (CompoundTag) codec.write().invoke(panel);
            if (!tag.getBoolean("formed")) return panel;
            fillHardware(tag, computation);
            return codec.read().invoke(null, tag);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot populate compact ECO host panel", e);
        }
    }

    static void fillHardware(CompoundTag tag, boolean computation) {
        int cells = tag.getInt("cells");
        int tiers = 0;
        for (int cell = 0; cell < SLOT_COUNT; cell++) {
            if ((cells & (1 << cell)) != 0) tiers |= LEVEL_NINE << (cell * 2);
        }
        tag.putInt("host", LEVEL_NINE);
        tag.putInt("up", tiers);
        tag.putInt("low", tiers);
        if (computation) {
            tag.putInt("core", tiers);
            // Newer ECO versions carry explicit members as well as tier bits. Rebuild them
            // through ECO's legacy decoder, which selects its original L9 atlas textures.
            tag.remove("upperMembers");
            tag.remove("coreMembers");
            tag.remove("lowerMembers");
        }
    }
}
