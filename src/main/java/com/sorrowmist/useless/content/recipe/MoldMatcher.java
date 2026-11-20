package com.sorrowmist.useless.content.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

/** 将模具需求按物品数量与枢纽库存进行匹配。 */
public final class MoldMatcher {
    private MoldMatcher() {
    }

    public static boolean matches(List<Ingredient> requirements, List<ItemStack> available) {
        return matches(requirements, sparseSlots(available));
    }

    /** 在按物理槽位索引的稀疏模具库存中匹配需求。 */
    public static boolean matches(
            List<Ingredient> requirements, Map<Integer, ItemStack> available) {
        return prepare(available).matches(requirements);
    }

    /**
     * 为一份不可变模具库存快照构建可复用的匹配器。
     * 常见成分只检查包含候选物品的槽位，并在匹配器生命周期内缓存匹配结果。
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
            if (occupiedSlots.isEmpty()) return false;

            Map<Integer, List<Integer>> requirementsBySlot = new HashMap<>();
            for (int requirement = 0; requirement < normalized.size(); requirement++) {
                if (!augment(normalized, requirement, requirementsBySlot, new HashSet<>())) {
                    return false;
                }
            }
            return true;
        }

        private boolean augment(
                List<Ingredient> requirements, int requirement,
                Map<Integer, List<Integer>> requirementsBySlot, Set<Integer> visited) {
            for (int slot : matchingSlots(requirements.get(requirement))) {
                if (!visited.add(slot)) continue;
                List<Integer> assigned = requirementsBySlot.computeIfAbsent(
                        slot, ignored -> new ArrayList<>());
                int capacity = Math.min(requirements.size(), available.get(slot).getCount());
                if (assigned.size() < capacity) {
                    assigned.add(requirement);
                    return true;
                }
                for (int index = 0; index < assigned.size(); index++) {
                    int previous = assigned.remove(index);
                    if (augment(requirements, previous, requirementsBySlot, visited)) {
                        assigned.add(requirement);
                        return true;
                    }
                    assigned.add(index, previous);
                }
            }
            return false;
        }

        private List<Integer> matchingSlots(Ingredient requirement) {
            if (requirement == null || requirement.isEmpty()) return List.of();
            return matchingSlotsCache.computeIfAbsent(requirement, this::findMatchingSlots);
        }

        private List<Integer> findMatchingSlots(Ingredient requirement) {
            // 自定义成分可能接受 getItems() 未列出的物品，因此必须扫描全部非空槽位以保持匹配语义。
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

    /** 检查完整匹配是否能将 {@code availableSlot} 中的一个物品分配给任一需求。 */
    public static boolean matchesWithAnchor(
            List<Ingredient> requirements, List<ItemStack> available, int availableSlot) {
        return matchesWithAnchor(requirements, sparseSlots(available), availableSlot);
    }

    /** 匹配时为一个需求预留指定槽位中的一个物品。 */
    public static boolean matchesWithAnchor(
            List<Ingredient> requirements, Map<Integer, ItemStack> available, int availableSlot) {
        List<Ingredient> normalized = normalizeRequirements(requirements);
        if (normalized.isEmpty()) return false;
        if (available == null || availableSlot < 0 || !available.containsKey(availableSlot)
                || available.get(availableSlot) == null || available.get(availableSlot).isEmpty()
                || available.get(availableSlot).getCount() <= 0) {
            return false;
        }

        for (int anchoredRequirement = 0; anchoredRequirement < normalized.size(); anchoredRequirement++) {
            if (!AdapterUtils.matchesMold(normalized.get(anchoredRequirement), available.get(availableSlot))) {
                continue;
            }
            List<Ingredient> remainingRequirements = new ArrayList<>(normalized);
            remainingRequirements.remove(anchoredRequirement);
            Map<Integer, ItemStack> remainingMolds = new LinkedHashMap<>(available);
            ItemStack anchoredStack = available.get(availableSlot);
            if (anchoredStack.getCount() == 1) {
                remainingMolds.remove(availableSlot);
            } else {
                remainingMolds.put(availableSlot, anchoredStack.copyWithCount(anchoredStack.getCount() - 1));
            }
            if (matches(remainingRequirements, remainingMolds)) return true;
        }
        return false;
    }

    /** 按插入顺序创建仅包含非空模具槽位的映射。 */
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
