package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Neutral dynamic-pattern description shared by optional recipe integrations. */
public record DynamicPatternProfile(
        Map<Integer, ItemStack> canonicalInputs,
        Set<Integer> idOnlyInputSlots,
        Set<Integer> idOnlyOutputSlots,
        Map<Integer, DynamicComponentPatternDetails.InputMatcher> inputMatchers) {

    public DynamicPatternProfile(Set<Integer> idOnlyInputSlots, Set<Integer> idOnlyOutputSlots) {
        this(Map.of(), idOnlyInputSlots, idOnlyOutputSlots, Map.of());
    }

    public DynamicPatternProfile {
        Map<Integer, ItemStack> inputCopies = new LinkedHashMap<>();
        if (canonicalInputs != null) {
            for (Map.Entry<Integer, ItemStack> entry : canonicalInputs.entrySet()) {
                if (entry.getKey() == null || entry.getKey() < 0
                        || entry.getValue() == null || entry.getValue().isEmpty()) {
                    throw new IllegalArgumentException("Canonical pattern inputs must be non-empty and non-negative");
                }
                inputCopies.put(entry.getKey(), entry.getValue().copyWithCount(1));
            }
        }
        canonicalInputs = Map.copyOf(inputCopies);
        idOnlyInputSlots = Set.copyOf(new LinkedHashSet<>(idOnlyInputSlots == null
                ? Set.of() : idOnlyInputSlots));
        idOnlyOutputSlots = Set.copyOf(new LinkedHashSet<>(idOnlyOutputSlots == null
                ? Set.of() : idOnlyOutputSlots));
        inputMatchers = inputMatchers == null ? Map.of() : Map.copyOf(inputMatchers);
        if (idOnlyInputSlots.stream().anyMatch(slot -> slot == null || slot < 0)
                || idOnlyOutputSlots.stream().anyMatch(slot -> slot == null || slot < 0)
                || !idOnlyInputSlots.containsAll(canonicalInputs.keySet())
                || inputMatchers.keySet().stream().anyMatch(slot -> slot == null || slot < 0)) {
            throw new IllegalArgumentException("Dynamic pattern slots must be non-negative and consistent");
        }
    }
}
