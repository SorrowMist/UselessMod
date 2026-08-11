package com.sorrowmist.useless.integration.dataenergistics.search;

import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;

import net.minecraft.world.item.ItemStack;

import java.util.List;

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
        if (!data.displayMolds().isEmpty()) {
            return data.displayMolds().stream()
                    .map(key -> key.getDisplayName().getString())
                    .toList();
        }
        return data.displayMold()
                .map(key -> List.of(key.getDisplayName().getString()))
                .orElseGet(List::of);
    }
}
