package com.sorrowmist.useless.content.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Matches independent mold requirements against the mold slots of a hub. */
public final class MoldMatcher {
    private MoldMatcher() {
    }

    public static boolean matches(List<Ingredient> requirements, List<ItemStack> available) {
        return matches(requirements, sparseSlots(available));
    }

    /** Matches against a sparse, physical-slot-indexed mold map. */
    public static boolean matches(
            List<Ingredient> requirements, Map<Integer, ItemStack> available) {
        return prepare(available).matches(requirements);
    }

    /**
     * Builds a reusable matcher for one immutable mold-inventory snapshot. The prepared index is
     * useful when many patterns are checked against the same hub: concrete ingredients only visit
     * slots containing one of their possible items, and each ingredient's matching slots are
     * cached for the lifetime of this matcher.
     */
    public static PreparedMolds prepare(Map<Integer, ItemStack> available) {
        return new PreparedMolds(available);
    }

    public static final class PreparedMolds {
        private final Map<Integer, ItemStack> available;
        private final List<Integer> occupiedSlots;
        private final Map<Item, List<Integer>> slotsByItem;
        private final Map<Ingredient, List<Integer>> matchingSlotsCache = new HashMap<>();

        private PreparedMolds(Map<Integer, ItemStack> available) {
            Map<Integer, ItemStack> normalized = new LinkedHashMap<>();
            if (available != null) {
                for (Map.Entry<Integer, ItemStack> entry : available.entrySet()) {
                    Integer slot = entry.getKey();
                    ItemStack mold = entry.getValue();
                    if (slot != null && slot >= 0 && mold != null && !mold.isEmpty()) {
                        normalized.put(slot, mold.copy());
                    }
                }
            }
            this.available = Collections.unmodifiableMap(normalized);
            this.occupiedSlots = List.copyOf(normalized.keySet());

            Map<Item, List<Integer>> indexed = new HashMap<>();
            for (Map.Entry<Integer, ItemStack> entry : normalized.entrySet()) {
                indexed.computeIfAbsent(entry.getValue().getItem(), ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
            indexed.replaceAll((ignored, slots) -> List.copyOf(slots));
            this.slotsByItem = Map.copyOf(indexed);
        }

        public boolean matches(List<Ingredient> requirements) {
            List<Ingredient> normalized = normalizeRequirements(requirements);
            if (normalized.isEmpty()) return true;
            if (occupiedSlots.isEmpty() || normalized.size() > occupiedSlots.size()) return false;

            Map<Integer, Integer> requirementBySlot = new HashMap<>();
            for (int requirement = 0; requirement < normalized.size(); requirement++) {
                if (!augment(normalized, requirement, requirementBySlot, new HashSet<>())) {
                    return false;
                }
            }
            return true;
        }

        private boolean augment(
                List<Ingredient> requirements, int requirement,
                Map<Integer, Integer> requirementBySlot, Set<Integer> visited) {
            for (int slot : matchingSlots(requirements.get(requirement))) {
                if (!visited.add(slot)) continue;
                int previous = requirementBySlot.getOrDefault(slot, -1);
                if (previous < 0 || augment(requirements, previous, requirementBySlot, visited)) {
                    requirementBySlot.put(slot, requirement);
                    return true;
                }
            }
            return false;
        }

        private List<Integer> matchingSlots(Ingredient requirement) {
            if (requirement == null || requirement.isEmpty()) return List.of();
            return matchingSlotsCache.computeIfAbsent(requirement, this::findMatchingSlots);
        }

        private List<Integer> findMatchingSlots(Ingredient requirement) {
            // Custom ingredients can accept items that are not present in getItems(), so their
            // predicate must retain the complete sparse-slot scan for correctness.
            if (requirement.isCustom()) return scanAll(requirement);

            ItemStack[] representatives;
            try {
                representatives = requirement.getItems();
            } catch (RuntimeException ignored) {
                return scanAll(requirement);
            }
            if (representatives == null || representatives.length == 0) {
                return scanAll(requirement);
            }

            Set<Integer> possibleSlots = new LinkedHashSet<>();
            for (ItemStack representative : representatives) {
                if (representative == null || representative.isEmpty()) continue;
                possibleSlots.addAll(slotsByItem.getOrDefault(
                        representative.getItem(), List.of()));
            }

            List<Integer> result = new ArrayList<>();
            for (int slot : possibleSlots) {
                ItemStack mold = available.get(slot);
                if (AdapterUtils.matchesMold(requirement, mold)) result.add(slot);
            }
            return List.copyOf(result);
        }

        private List<Integer> scanAll(Ingredient requirement) {
            List<Integer> result = new ArrayList<>();
            for (int slot : occupiedSlots) {
                if (AdapterUtils.matchesMold(requirement, available.get(slot))) result.add(slot);
            }
            return List.copyOf(result);
        }
    }

    /**
     * Returns true when a complete assignment exists that uses {@code availableSlot} for one of
     * the requirements. This is what makes the hub's physical slot order meaningful when several
     * recipes have identical processing inputs and outputs.
     */
    public static boolean matchesWithAnchor(
            List<Ingredient> requirements, List<ItemStack> available, int availableSlot) {
        return matchesWithAnchor(requirements, sparseSlots(available), availableSlot);
    }

    /**
     * Matches while reserving one physical mold slot for a requirement. The map is sparse, so empty
     * slots never enter the assignment search.
     */
    public static boolean matchesWithAnchor(
            List<Ingredient> requirements, Map<Integer, ItemStack> available, int availableSlot) {
        List<Ingredient> normalized = normalizeRequirements(requirements);
        if (normalized.isEmpty()) return false;
        if (available == null || availableSlot < 0 || !available.containsKey(availableSlot)
                || available.get(availableSlot) == null || available.get(availableSlot).isEmpty()
                || normalized.size() > available.size()) {
            return false;
        }

        for (int anchoredRequirement = 0; anchoredRequirement < normalized.size(); anchoredRequirement++) {
            if (!AdapterUtils.matchesMold(normalized.get(anchoredRequirement), available.get(availableSlot))) {
                continue;
            }

            Map<Integer, Integer> requirementBySlot = new HashMap<>();
            requirementBySlot.put(availableSlot, anchoredRequirement);
            boolean complete = true;
            for (int requirement = 0; requirement < normalized.size(); requirement++) {
                if (requirement == anchoredRequirement) continue;
                Set<Integer> visited = new HashSet<>();
                visited.add(availableSlot);
                if (!augment(normalized, available, requirement, requirementBySlot, visited)) {
                    complete = false;
                    break;
                }
            }
            if (complete) return true;
        }
        return false;
    }

    private static boolean augment(
            List<Ingredient> requirements, Map<Integer, ItemStack> available,
            int requirement, Map<Integer, Integer> requirementBySlot, Set<Integer> visited) {
        Ingredient needed = requirements.get(requirement);
        for (Map.Entry<Integer, ItemStack> entry : available.entrySet()) {
            Integer slot = entry.getKey();
            ItemStack mold = entry.getValue();
            if (slot == null || visited.contains(slot) || mold == null || mold.isEmpty()
                    || !AdapterUtils.matchesMold(needed, mold)) continue;
            visited.add(slot);
            int previous = requirementBySlot.getOrDefault(slot, -1);
            if (previous < 0 || augment(requirements, available, previous, requirementBySlot, visited)) {
                requirementBySlot.put(slot, requirement);
                return true;
            }
        }
        return false;
    }

    /** Creates an insertion-ordered map containing only non-empty physical mold slots. */
    public static Map<Integer, ItemStack> sparseSlots(List<ItemStack> available) {
        if (available == null || available.isEmpty()) return Map.of();
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (int slot = 0; slot < available.size(); slot++) {
            ItemStack mold = available.get(slot);
            if (mold != null && !mold.isEmpty()) result.put(slot, mold);
        }
        return Collections.unmodifiableMap(result);
    }

    public static List<Ingredient> normalizeRequirements(List<Ingredient> requirements) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        List<Ingredient> normalized = new ArrayList<>(requirements.size());
        for (Ingredient requirement : requirements) {
            if (requirement != null && !requirement.isEmpty()) normalized.add(requirement);
        }
        return List.copyOf(normalized);
    }
}
