package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import snownee.jade.addon.universal.ItemStorageProvider;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ViewGroup;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntFunction;

/**
 * Provides Jade with a small, revisioned snapshot instead of making its generic item iterator
 * inspect every backing slot on every request.
 */
public final class JadePatternStorageSnapshot {
    private static final int MAX_DISPLAY_ITEMS = 54;
    private static final Object LOCK = new Object();
    private static final Map<Object, CachedSnapshot> SNAPSHOTS = new WeakHashMap<>();

    private JadePatternStorageSnapshot() {
    }

    @Nullable
    public static List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        Object target = accessor.getTarget();
        CachedSnapshot snapshot = getSnapshot(target);
        return snapshot == null ? null : snapshot.groups;
    }

    /** Writes the same Jade payload as the universal provider and tells the caller to stop. */
    public static boolean writeData(Accessor<?> accessor) {
        Object target = accessor.getTarget();
        CachedSnapshot snapshot = getSnapshot(target);
        if (snapshot == null) {
            return false;
        }

        CompoundTag serverData = accessor.getServerData();
        serverData.remove("JadeItemStorage");
        serverData.remove("JadeItemStorageUid");
        serverData.merge(snapshot.serialized(accessor));
        return true;
    }

    @Nullable
    private static CachedSnapshot getSnapshot(Object target) {
        if (target instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            IItemHandler handler = furnace.getItemHandler();
            return getSnapshot(target, furnace.getItemStorageRevision(), handler.getSlots(),
                    handler::getStackInSlot);
        }
        if (target instanceof MePatternAssemblyBlockEntity assembly) {
            RecoverableItemStackHandler handler = assembly.getPatterns();
            return getSnapshot(target, assembly.getPatternStorageRevision(), handler.getActiveSlots(),
                    handler::getStackInSlot);
        }
        if (target instanceof PassiveCraftingHatchBlockEntity hatch) {
            RecoverableItemStackHandler handler = hatch.getPatterns();
            return getSnapshot(target, hatch.getPatternStorageRevision(), hatch.getActivePatternSlots(),
                    handler::getStackInSlot);
        }
        return null;
    }

    private static CachedSnapshot getSnapshot(
            Object target, long revision, int slotCount, IntFunction<ItemStack> reader) {
        synchronized (LOCK) {
            CachedSnapshot cached = SNAPSHOTS.get(target);
            if (cached != null && cached.revision == revision && cached.slotCount == slotCount) {
                return cached;
            }

            List<ItemStack> compacted = compact(reader, slotCount);
            CachedSnapshot snapshot = new CachedSnapshot(
                    revision,
                    slotCount,
                    compacted.isEmpty()
                            ? List.of()
                            : List.of(new ViewGroup<>(List.copyOf(compacted))));
            SNAPSHOTS.put(target, snapshot);
            return snapshot;
        }
    }

    private static List<ItemStack> compact(IntFunction<ItemStack> reader, int slotCount) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = reader.apply(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            int existingIndex = -1;
            for (int index = 0; index < result.size(); index++) {
                if (ItemStack.isSameItemSameComponents(result.get(index), stack)) {
                    existingIndex = index;
                    break;
                }
            }
            if (existingIndex >= 0) {
                ItemStack existing = result.get(existingIndex);
                long total = (long) existing.getCount() + stack.getCount();
                existing.setCount((int) Math.min(Integer.MAX_VALUE, total));
            } else if (result.size() < MAX_DISPLAY_ITEMS) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static final class CachedSnapshot {
        private final long revision;
        private final int slotCount;
        private final List<ViewGroup<ItemStack>> groups;
        @Nullable
        private CompoundTag serializedData;

        private CachedSnapshot(long revision, int slotCount, List<ViewGroup<ItemStack>> groups) {
            this.revision = revision;
            this.slotCount = slotCount;
            this.groups = groups;
        }

        private CompoundTag serialized(Accessor<?> accessor) {
            if (serializedData == null) {
                CompoundTag data = new CompoundTag();
                ViewGroup.saveList(data, "JadeItemStorage", groups,
                        stack -> serializeStack(accessor, stack));
                if (!groups.isEmpty()) {
                    data.putString("JadeItemStorageUid",
                            ItemStorageProvider.Extension.INSTANCE.getUid().toString());
                }
                serializedData = data;
            }
            return serializedData.copy();
        }

        private static CompoundTag serializeStack(Accessor<?> accessor, ItemStack original) {
            int count = original.getCount();
            ItemStack stack = count > original.getMaxStackSize()
                    ? original.copyWithCount(1)
                    : original;
            CompoundTag result = asCompound(stack.save(accessor.getLevel().registryAccess()));
            if (count > original.getMaxStackSize()) {
                result.putInt("NewCount", count);
            }
            return result;
        }

        private static CompoundTag asCompound(Tag tag) {
            return tag instanceof CompoundTag compound ? compound : new CompoundTag();
        }
    }
}
