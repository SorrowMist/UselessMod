package com.sorrowmist.useless.content.blockentities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stores one paged-menu position for each player who used a block entity. */
public final class PagedMenuPageMemory {
    private static final String TAG = "PagedMenuPages";

    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Runnable markChanged;

    public PagedMenuPageMemory(Runnable markChanged) {
        this.markChanged = Objects.requireNonNull(markChanged, "markChanged");
    }

    public int get(UUID playerId) {
        return Math.max(0, pages.getOrDefault(playerId, 0));
    }

    public void set(UUID playerId, int page) {
        Objects.requireNonNull(playerId, "playerId");
        int normalized = Math.max(0, page);
        Integer previous = pages.get(playerId);
        if (normalized == 0) {
            if (previous == null) return;
            pages.remove(playerId);
            markChanged.run();
            return;
        }
        if (previous != null && previous == normalized) return;
        pages.put(playerId, normalized);
        markChanged.run();
    }

    public void load(CompoundTag tag) {
        pages.clear();
        if (!tag.contains(TAG, Tag.TAG_COMPOUND)) return;

        CompoundTag savedPages = tag.getCompound(TAG);
        for (String key : savedPages.getAllKeys()) {
            try {
                int page = savedPages.getInt(key);
                if (page > 0) {
                    pages.put(UUID.fromString(key), page);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed player keys from edited or damaged NBT.
            }
        }
    }

    public void save(CompoundTag tag) {
        if (pages.isEmpty()) {
            tag.remove(TAG);
            return;
        }

        CompoundTag savedPages = new CompoundTag();
        pages.forEach((playerId, page) -> savedPages.putInt(playerId.toString(), page));
        tag.put(TAG, savedPages);
    }
}
