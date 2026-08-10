package com.sorrowmist.useless.integration.dataenergistics.search;

import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Extracts the encoded internal mold as a separate Trinity pattern-search candidate.
 */
public final class OmniversalPatternMoldSearchTerms {

    private OmniversalPatternMoldSearchTerms() {}

    /**
     * Returns the mold display name stored in one Omniversal Pattern, when that pattern needs a mold.
     *
     * @param encodedPattern candidate encoded pattern stack
     * @return one independent mold-name candidate, or no candidates for every other pattern
     */
    public static List<String> searchTerms(ItemStack encodedPattern) {
        OmniversalPatternData data = encodedPattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        return data == null ? List.of() : searchTerms(data);
    }

    /**
     * Returns one display candidate for metadata that has already been decoded from an Omniversal Pattern.
     *
     * @param data persisted Omniversal Pattern metadata
     * @return one independent mold-name candidate, or no candidate when no mold is required
     */
    static List<String> searchTerms(OmniversalPatternData data) {
        if (!data.requiresMold()) {
            return List.of();
        }
        return data.displayMold()
                .map(key -> key.getDisplayName().getString())
                .stream()
                .toList();
    }
}
