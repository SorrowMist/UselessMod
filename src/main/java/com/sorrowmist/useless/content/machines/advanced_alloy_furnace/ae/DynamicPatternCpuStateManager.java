package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** CPU-side state for outputs whose components are determined at execution time. */
public final class DynamicPatternCpuStateManager {
    public static final DynamicPatternCpuStateManager INSTANCE = new DynamicPatternCpuStateManager();
    public static final String NBT_KEY = "uselessModDynamicComponentState";

    private final Map<Object, CpuState> states = new WeakHashMap<>();

    private DynamicPatternCpuStateManager() {
    }

    public synchronized boolean hasAnyPending(Object logic) {
        Objects.requireNonNull(logic, "logic");
        CpuState state = states.get(logic);
        return state != null && !state.isEmpty();
    }

    public synchronized boolean hasAmbiguousOutputRegistration(
            Object logic, DynamicComponentPattern pattern) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(pattern, "pattern");
        CpuState state = states.get(logic);
        return state != null && state.hasAmbiguous(pattern);
    }

    public synchronized void registerExpectedOutputs(
            Object logic,
            UUID craftingId,
            DynamicComponentPattern pattern,
            @Nullable AEKey finalOutputKey,
            long pushedCopies) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(craftingId, "craftingId");
        Objects.requireNonNull(pattern, "pattern");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }

        CpuState state = states.get(logic);
        if (state == null || !craftingId.equals(state.craftingId)) {
            state = new CpuState(craftingId);
            states.put(logic, state);
        }
        state.register(pattern, finalOutputKey, pushedCopies);
    }

    public synchronized ClaimResult claim(
            Object logic, AEKey incoming, long amount, Actionable actionable) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(actionable, "actionable");
        if (!(incoming instanceof AEItemKey itemKey) || amount <= 0) {
            return ClaimResult.EMPTY;
        }

        CpuState state = states.get(logic);
        if (state == null) {
            return ClaimResult.EMPTY;
        }
        ClaimResult result = state.claim(itemKey.getId(), amount, actionable == Actionable.MODULATE);
        if (actionable == Actionable.MODULATE && state.isEmpty()) {
            states.remove(logic);
        }
        return result;
    }

    public synchronized void clear(Object logic) {
        Objects.requireNonNull(logic, "logic");
        states.remove(logic);
    }

    @Nullable
    public synchronized CompoundTag writeToTag(Object logic, HolderLookup.Provider registries) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(registries, "registries");
        CpuState state = states.get(logic);
        return state == null || state.isEmpty() ? null : state.toTag(registries);
    }

    public synchronized void readFromTag(
            Object logic, UUID craftingId, CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(craftingId, "craftingId");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(registries, "registries");
        if (tag.isEmpty()) {
            states.remove(logic);
            return;
        }
        states.put(logic, CpuState.fromTag(craftingId, tag, registries));
    }

    public synchronized long getRemainingForItem(Object logic, ResourceLocation itemId) {
        CpuState state = states.get(logic);
        return state == null ? 0 : state.remainingForItem(itemId);
    }

    public synchronized List<PendingSnapshot> snapshotPending(Object logic) {
        CpuState state = states.get(logic);
        return state == null ? List.of() : state.snapshots();
    }

    public record PendingSnapshot(
            String patternIdentity,
            int outputSlot,
            ResourceLocation itemId,
            AEKey exactExpectedKey,
            long remainingAmount,
            boolean routesToRequester,
            long registeredOrder) {
    }

    public record Claim(
            long claimedAmount,
            boolean routesToRequester,
            AEKey exactExpectedKey) {
        public Claim {
            if (claimedAmount <= 0) {
                throw new IllegalArgumentException("claimedAmount must be > 0");
            }
            Objects.requireNonNull(exactExpectedKey, "exactExpectedKey");
        }
    }

    public record ClaimResult(long claimedAmount, List<Claim> claims) {
        public static final ClaimResult EMPTY = new ClaimResult(0, List.of());

        public ClaimResult {
            if (claimedAmount < 0) {
                throw new IllegalArgumentException("claimedAmount must be >= 0");
            }
            claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        }

        public boolean claimedAnything() {
            return claimedAmount > 0;
        }

        public long claimedForRequester() {
            long result = 0;
            for (Claim claim : claims) {
                if (claim.routesToRequester()) {
                    result = saturatingAdd(result, claim.claimedAmount());
                }
            }
            return result;
        }

        public long claimedForInventory() {
            return claimedAmount - claimedForRequester();
        }
    }

    private static final class CpuState {
        private static final String TAG_NEXT_SEQUENCE = "NextSequence";
        private static final String TAG_PENDING = "Pending";
        private static final String TAG_PATTERN_IDENTITY = "PatternIdentity";
        private static final String TAG_OUTPUT_SLOT = "OutputSlot";
        private static final String TAG_ITEM_ID = "ItemId";
        private static final String TAG_EXACT_TEMPLATE = "ExactTemplate";
        private static final String TAG_REMAINING = "RemainingAmount";
        private static final String TAG_ROUTES_TO_REQUESTER = "RoutesToRequester";
        private static final String TAG_REGISTERED_ORDER = "RegisteredOrder";

        private final UUID craftingId;
        private final Map<PendingKey, PendingOutput> pendingByKey = new LinkedHashMap<>();
        private final Map<ResourceLocation, LinkedHashSet<PendingKey>> pendingByItem = new LinkedHashMap<>();
        private long nextSequence = 1L;

        private CpuState(UUID craftingId) {
            this.craftingId = craftingId;
        }

        private boolean isEmpty() {
            return pendingByKey.isEmpty();
        }

        private boolean hasAmbiguous(DynamicComponentPattern pattern) {
            Map<ResourceLocation, PendingKey> incoming = new LinkedHashMap<>();
            for (int slot = 0; slot < pattern.getOutputs().size(); slot++) {
                if (!pattern.isItemIdOutput(slot)) {
                    continue;
                }
                GenericStack output = pattern.getOutputs().get(slot);
                if (!(output.what() instanceof AEItemKey itemKey)) {
                    continue;
                }
                PendingKey key = new PendingKey(pattern.dynamicPatternIdentity(), slot);
                PendingKey batchExisting = incoming.putIfAbsent(itemKey.getId(), key);
                if (batchExisting != null && !batchExisting.equals(key)) {
                    return true;
                }
                for (PendingKey existing : pendingByItem.getOrDefault(itemKey.getId(), new LinkedHashSet<>())) {
                    if (!existing.equals(key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void register(
                DynamicComponentPattern pattern, @Nullable AEKey finalOutputKey, long pushedCopies) {
            List<GenericStack> outputs = pattern.getOutputs();
            for (int slot = 0; slot < outputs.size(); slot++) {
                if (!pattern.isItemIdOutput(slot)) {
                    continue;
                }
                GenericStack output = outputs.get(slot);
                if (!(output.what() instanceof AEItemKey itemKey) || output.amount() <= 0) {
                    continue;
                }
                long amount = saturatingMultiply(output.amount(), pushedCopies);
                boolean routesToRequester = finalOutputKey instanceof AEItemKey finalItem
                        && finalItem.getItem() == itemKey.getItem();
                PendingKey key = new PendingKey(pattern.dynamicPatternIdentity(), slot);
                PendingOutput existing = pendingByKey.get(key);
                if (existing != null) {
                    if (!existing.itemId.equals(itemKey.getId())
                            || !existing.exactExpectedKey.equals(output.what())
                            || existing.routesToRequester != routesToRequester) {
                        throw new IllegalStateException("Dynamic pattern identity changed its output definition");
                    }
                    existing.addExpected(amount);
                    continue;
                }

                PendingOutput pending = new PendingOutput(
                        key,
                        itemKey.getId(),
                        output.what(),
                        amount,
                        routesToRequester,
                        nextSequence++);
                pendingByKey.put(key, pending);
                pendingByItem.computeIfAbsent(itemKey.getId(), ignored -> new LinkedHashSet<>()).add(key);
            }
        }

        private ClaimResult claim(ResourceLocation itemId, long amount, boolean mutate) {
            LinkedHashSet<PendingKey> keys = pendingByItem.get(itemId);
            if (keys == null || keys.isEmpty()) {
                return ClaimResult.EMPTY;
            }

            long remaining = amount;
            List<Claim> claims = new ArrayList<>();
            List<PendingOutput> satisfied = new ArrayList<>();
            Collection<PendingOutput> ordered = keys.stream()
                    .map(pendingByKey::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(output -> output.registeredOrder))
                    .toList();
            for (PendingOutput pending : ordered) {
                if (remaining <= 0) {
                    break;
                }
                long claimed = Math.min(pending.remainingAmount, remaining);
                if (claimed <= 0) {
                    continue;
                }
                if (mutate) {
                    pending.remainingAmount -= claimed;
                    if (pending.remainingAmount <= 0) {
                        satisfied.add(pending);
                    }
                }
                claims.add(new Claim(claimed, pending.routesToRequester, pending.exactExpectedKey));
                remaining -= claimed;
            }
            if (mutate) {
                for (PendingOutput pending : satisfied) {
                    remove(pending);
                }
            }
            long claimed = amount - remaining;
            return claimed <= 0 ? ClaimResult.EMPTY : new ClaimResult(claimed, claims);
        }

        private void remove(PendingOutput pending) {
            pendingByKey.remove(pending.key);
            LinkedHashSet<PendingKey> keys = pendingByItem.get(pending.itemId);
            if (keys != null) {
                keys.remove(pending.key);
                if (keys.isEmpty()) {
                    pendingByItem.remove(pending.itemId);
                }
            }
        }

        private long remainingForItem(ResourceLocation itemId) {
            long result = 0;
            for (PendingKey key : pendingByItem.getOrDefault(itemId, new LinkedHashSet<>())) {
                PendingOutput pending = pendingByKey.get(key);
                if (pending != null) {
                    result = saturatingAdd(result, pending.remainingAmount);
                }
            }
            return result;
        }

        private List<PendingSnapshot> snapshots() {
            return pendingByKey.values().stream()
                    .map(pending -> new PendingSnapshot(
                            pending.key.patternIdentity,
                            pending.key.outputSlot,
                            pending.itemId,
                            pending.exactExpectedKey,
                            pending.remainingAmount,
                            pending.routesToRequester,
                            pending.registeredOrder))
                    .toList();
        }

        private CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_NEXT_SEQUENCE, nextSequence);
            ListTag pendingList = new ListTag();
            for (PendingOutput pending : pendingByKey.values()) {
                CompoundTag entry = new CompoundTag();
                entry.putString(TAG_PATTERN_IDENTITY, pending.key.patternIdentity);
                entry.putInt(TAG_OUTPUT_SLOT, pending.key.outputSlot);
                entry.putString(TAG_ITEM_ID, pending.itemId.toString());
                entry.put(TAG_EXACT_TEMPLATE, pending.exactExpectedKey.toTagGeneric(registries));
                entry.putLong(TAG_REMAINING, pending.remainingAmount);
                entry.putBoolean(TAG_ROUTES_TO_REQUESTER, pending.routesToRequester);
                entry.putLong(TAG_REGISTERED_ORDER, pending.registeredOrder);
                pendingList.add(entry);
            }
            tag.put(TAG_PENDING, pendingList);
            return tag;
        }

        private static CpuState fromTag(
                UUID craftingId, CompoundTag tag, HolderLookup.Provider registries) {
            CpuState state = new CpuState(craftingId);
            state.nextSequence = Math.max(1L, tag.getLong(TAG_NEXT_SEQUENCE));
            ListTag pendingList = tag.getList(TAG_PENDING, Tag.TAG_COMPOUND);
            for (int index = 0; index < pendingList.size(); index++) {
                CompoundTag entry = pendingList.getCompound(index);
                if (!entry.contains(TAG_EXACT_TEMPLATE, Tag.TAG_COMPOUND)) {
                    continue;
                }
                AEKey exact = AEKey.fromTagGeneric(registries, entry.getCompound(TAG_EXACT_TEMPLATE));
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getString(TAG_ITEM_ID));
                long remaining = entry.getLong(TAG_REMAINING);
                if (exact == null || itemId == null || remaining <= 0) {
                    continue;
                }
                PendingKey key = new PendingKey(
                        entry.getString(TAG_PATTERN_IDENTITY), entry.getInt(TAG_OUTPUT_SLOT));
                PendingOutput pending = new PendingOutput(
                        key,
                        itemId,
                        exact,
                        remaining,
                        entry.getBoolean(TAG_ROUTES_TO_REQUESTER),
                        Math.max(1L, entry.getLong(TAG_REGISTERED_ORDER)));
                state.pendingByKey.put(key, pending);
                state.pendingByItem.computeIfAbsent(itemId, ignored -> new LinkedHashSet<>()).add(key);
                state.nextSequence = Math.max(state.nextSequence, pending.registeredOrder + 1);
            }
            return state;
        }

        private record PendingKey(String patternIdentity, int outputSlot) {
        }

        private static final class PendingOutput {
            private final PendingKey key;
            private final ResourceLocation itemId;
            private final AEKey exactExpectedKey;
            private final boolean routesToRequester;
            private final long registeredOrder;
            private long remainingAmount;

            private PendingOutput(
                    PendingKey key,
                    ResourceLocation itemId,
                    AEKey exactExpectedKey,
                    long remainingAmount,
                    boolean routesToRequester,
                    long registeredOrder) {
                this.key = key;
                this.itemId = itemId;
                this.exactExpectedKey = exactExpectedKey;
                this.remainingAmount = remainingAmount;
                this.routesToRequester = routesToRequester;
                this.registeredOrder = registeredOrder;
            }

            private void addExpected(long amount) {
                remainingAmount = saturatingAdd(remainingAmount, amount);
            }
        }
    }

    private static long saturatingAdd(long first, long second) {
        return second > 0 && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static long saturatingMultiply(long first, long second) {
        if (first <= 0 || second <= 0) {
            return 0;
        }
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }
}
