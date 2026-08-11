package com.sorrowmist.useless.integration.dataenergistics.search;

import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts encoded internal molds as separate Trinity pattern-search candidates.
 */
public final class OmniversalPatternMoldSearchTerms {

    private OmniversalPatternMoldSearchTerms() {}

    /**
     * Returns mold display names stored in one Omniversal Pattern, when that pattern needs molds.
     *
     * @param encodedPattern candidate encoded pattern stack
     * @return independent mold-name candidates, or no candidates for every other pattern
     */
    public static List<String> searchTerms(ItemStack encodedPattern) {
        OmniversalPatternData data = encodedPattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        return data == null ? List.of() : searchTerms(data);
    }

    /**
     * Returns display candidates for metadata that has already been decoded from an Omniversal Pattern.
     *
     * @param data persisted Omniversal Pattern metadata
     * @return independent mold-name candidates, or no candidates when no mold is required
     */
    static List<String> searchTerms(OmniversalPatternData data) {
        if (!data.requiresMold()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Set<Integer> tagMoldSlots = new HashSet<>();
        data.moldTagInputSlots().forEach(slot -> tagMoldSlots.add(slot.moldSlot()));
        if (!data.displayMolds().isEmpty()) {
            for (int moldSlot = 0; moldSlot < data.displayMolds().size(); moldSlot++) {
                if (tagMoldSlots.contains(moldSlot)) continue;
                terms.add(data.displayMolds().get(moldSlot).getDisplayName().getString());
            }
        } else if (!tagMoldSlots.contains(0)) {
            data.displayMold().map(key -> key.getDisplayName().getString()).ifPresent(terms::add);
        }
        data.moldTagInputSlots().stream()
                .map(slot -> "#" + slot.tag().location())
                .forEach(terms::add);
        return terms.stream().distinct().toList();
    }
}
