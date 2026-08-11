package com.sorrowmist.useless.integration.dataenergistics.search;

import appeng.api.stacks.AEItemKey;

import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.integration.dataenergistics.DataEnergisticsIntegrationTestBootstrap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OmniversalPatternMoldSearchTermsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        DataEnergisticsIntegrationTestBootstrap.initialize();
    }

    @Test
    void exposesTheStoredMoldAsAnIndependentSearchCandidate() {
        OmniversalPatternData ironMold = data(itemKey(Items.IRON_INGOT), true);
        OmniversalPatternData goldMold = data(itemKey(Items.GOLD_INGOT), true);

        List<String> ironTerms = OmniversalPatternMoldSearchTerms.searchTerms(ironMold);
        List<String> goldTerms = OmniversalPatternMoldSearchTerms.searchTerms(goldMold);

        assertEquals(List.of(itemKey(Items.IRON_INGOT).getDisplayName().getString()), ironTerms);
        assertEquals(List.of(itemKey(Items.GOLD_INGOT).getDisplayName().getString()), goldTerms);
        assertNotEquals(ironTerms, goldTerms);
    }

    @Test
    void ignoresPatternsThatDoNotNeedAMold() {
        assertEquals(
                List.of(),
                OmniversalPatternMoldSearchTerms.searchTerms(data(itemKey(Items.IRON_INGOT), false)));
    }

    @Test
    void exposesEveryDisplayedMoldForMultiMoldMetadata() {
        OmniversalPatternData multi = new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "multi_mold"),
                "fingerprint",
                true,
                Optional.empty(),
                List.of(itemKey(Items.IRON_INGOT), itemKey(Items.GOLD_INGOT)),
                List.of(),
                List.of());

        assertEquals(List.of(
                itemKey(Items.IRON_INGOT).getDisplayName().getString(),
                itemKey(Items.GOLD_INGOT).getDisplayName().getString()),
                OmniversalPatternMoldSearchTerms.searchTerms(multi));
    }

    @Test
    void replacesRepresentativeForTagBackedMultiMold() {
        TagKey<Item> hoes = TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "all_hoes"));
        OmniversalPatternData multi = new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "tag_mold"),
                "fingerprint",
                true,
                Optional.empty(),
                List.of(itemKey(Items.CRAFTING_TABLE), itemKey(Items.NETHERITE_HOE)),
                List.of(),
                List.of(),
                List.of(new OmniversalPatternData.MoldTagInputSlot(1, hoes)),
                List.of(),
                List.of());

        assertEquals(List.of(
                itemKey(Items.CRAFTING_TABLE).getDisplayName().getString(),
                "#useless_mod_test:all_hoes"),
                OmniversalPatternMoldSearchTerms.searchTerms(multi));
    }

    @Test
    void hidesRepresentativeForTagBackedSingleMold() {
        TagKey<Item> hoes = TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "all_hoes_single"));
        OmniversalPatternData single = new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "single_tag_mold"),
                "fingerprint",
                true,
                Optional.of(itemKey(Items.NETHERITE_HOE)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OmniversalPatternData.MoldTagInputSlot(0, hoes)),
                List.of(),
                List.of());

        assertEquals(List.of("#useless_mod_test:all_hoes_single"),
                OmniversalPatternMoldSearchTerms.searchTerms(single));
    }

    private static OmniversalPatternData data(AEItemKey mold, boolean requiresMold) {
        return new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "molded_alloy"),
                "fingerprint",
                requiresMold,
                Optional.of(mold),
                List.of(),
                List.of());
    }

    private static AEItemKey itemKey(Item item) {
        return Objects.requireNonNull(AEItemKey.of(item));
    }
}
