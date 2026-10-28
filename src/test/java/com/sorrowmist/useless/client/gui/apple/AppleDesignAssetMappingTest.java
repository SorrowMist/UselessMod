package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组件到原画切片的映射契约：每个 Apple 控件只能引用真实存在的设计资源，
 * 且 2x / 4x 贴图必须随 mod 一起打包，避免运行期抛 Unknown design slice 或紫黑格。
 */
class AppleDesignAssetMappingTest {
    private static final String[] BADGE_COLORS = {"blue", "green", "orange", "red", "purple", "teal", "gray"};
    private static final String[] TILE_COLORS = {"blue", "green", "orange", "red", "purple", "teal", "gray", "indigo"};
    private static final int[] TILE_SIZES = {20, 28, 40, 56};
    private static final int[] SLOT_SIZES = {18, 32, 36, 40};
    private static final String[] BUTTON_KINDS = {"primary", "secondary", "tint", "danger"};
    private static final String[] BUTTON_STATES = {"default", "hover", "pressed", "disabled"};

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private static void assertSliceAndTexture(String key) {
        Set<String> keys = DesignAssets.keys();
        assertTrue(keys.contains(key), "missing slice geometry: " + key);
        for (String density : List.of("2x", "4x")) {
            String path = "/assets/useless_mod/textures/gui/design/" + DesignAssets.slice(key).path()
                    + "_" + density + ".png";
            assertNotNull(AppleDesignAssetMappingTest.class.getResource(path),
                    "missing artwork: " + path);
        }
        DesignAssets.Slice slice = DesignAssets.slice(key);
        assertTrue(slice.size()[0] >= slice.body()[2] + slice.body()[0], key + " body wider than artwork");
        assertTrue(slice.size()[1] >= slice.body()[3] + slice.body()[1], key + " body taller than artwork");
        assertTrue(slice.corner() <= Math.max(slice.body()[2], slice.body()[3]) / 2f, key + " corner too round");
    }

    @Test
    void everyCatalogEntryHasReadableArtworkAtAllRuntimeDensities() throws Exception {
        for (String key : DesignAssets.keys()) {
            assertEquals(DesignAssets.resource(key, 2), DesignAssets.resource(key, 1),
                    "low GUI scales must use the bundled 2x artwork");
            for (int density : List.of(2, 4)) {
                var resource = DesignAssets.resource(key, density);
                try (var stream = getClass().getResourceAsStream("/assets/useless_mod/" + resource.getPath())) {
                    assertNotNull(stream, key + " at " + density + "x");
                    var image = javax.imageio.ImageIO.read(stream);
                    assertNotNull(image, key);
                    assertTrue(image.getWidth() > 0 && image.getHeight() > 0, key);
                }
            }
        }
    }

    @Test
    void referenceItemIconsResolveAtEveryGuiDensityWithoutInventedDensitySuffixes() throws Exception {
        for (String name : List.of("ore_generator_block", "omniversal_pattern_converter", "endless_beaf_item")) {
            String key = "reference_" + name;
            DesignAssets.Slice slice = DesignAssets.slice(key);
            assertEquals("reference/" + name, slice.path());
            assertEquals(0, slice.corner());
            var one = DesignAssets.resource(key, 1);
            assertEquals(one, DesignAssets.resource(key, 2));
            assertEquals(one, DesignAssets.resource(key, 4));
            assertEquals("textures/gui/design/reference/" + name + ".png", one.getPath());
            try (var stream = getClass().getResourceAsStream("/assets/useless_mod/" + one.getPath())) {
                assertNotNull(stream);
                var image = javax.imageio.ImageIO.read(stream);
                assertEquals(slice.size()[0], image.getWidth());
                assertEquals(slice.size()[1], image.getHeight());
            }
        }
    }

    @Test
    void pushButtonKindsAndStatesHaveArtworkAtBothDensities() {
        for (String kind : BUTTON_KINDS) {
            for (String state : BUTTON_STATES) {
                // 与 DesignAssets.button 的前缀规则保持一致（h <= 14 用胶囊图标资源）。
                assertSliceAndTexture("button_" + kind + "_" + state);
                assertSliceAndTexture("button_capsule_" + kind + "_sm_" + state);
            }
        }
    }

    @Test
    void switchBadgeSegmentedPopoverMenuAndSlotsResolve() {
        for (String key : List.of("switch_on", "switch_off", "switch_on_disabled", "switch_off_disabled",
                "segmented_track_240x32", "segmented_thumb_116x28", "ninepatch_popup",
                "dropdown_item_selected_188x34")) {
            assertSliceAndTexture(key);
        }
        for (String color : BADGE_COLORS) assertSliceAndTexture("badge_" + color + "_64");
        for (String state : List.of("empty", "hover", "locked"))
            for (int size : SLOT_SIZES) assertSliceAndTexture("slot_" + state + "_" + size);
        for (String color : TILE_COLORS)
            for (int size : TILE_SIZES) assertSliceAndTexture("icontile_" + color + "_" + size);
    }

    @Test
    void badgePaletteHasSevenColorsAndIndigoFoldsIntoPurple() {
        List<String> names = new ArrayList<>();
        for (String color : BADGE_COLORS) names.add("badge_" + color + "_64");
        assertEquals(7, names.size());
        for (String name : names) assertTrue(DesignAssets.keys().contains(name), name);
        assertFalse(DesignAssets.keys().contains("badge_indigo_64"), "indigo badges must reuse purple artwork");
        assertEquals("badge_purple_64", AppleBadge.badgeKey(0xFF5856D6));
        assertEquals("badge_purple_64", AppleBadge.badgeKey(0xFFAF52DE));
        assertEquals("badge_blue_64", AppleBadge.badgeKey(0xFF007AFF));
        assertEquals("badge_gray_64", AppleBadge.badgeKey(0xFF8E8E93));
    }

    @Test
    void switchUsesDisabledTrackForGreyedOutControls() {
        assertEquals("switch_on", AppleSwitch.switchKey(true, true));
        assertEquals("switch_off", AppleSwitch.switchKey(false, true));
        assertEquals("switch_on_disabled", AppleSwitch.switchKey(true, false));
        assertEquals("switch_off_disabled", AppleSwitch.switchKey(false, false));
    }

    @Test
    void iconTilesSnapToTheFourBundledSizes() {
        assertEquals("icontile_blue_20", AppleIconTile.tileKey(0xFF007AFF, 12));
        assertEquals("icontile_blue_20", AppleIconTile.tileKey(0xFF007AFF, 20));
        assertEquals("icontile_green_28", AppleIconTile.tileKey(0xFF34C759, 22));
        assertEquals("icontile_indigo_40", AppleIconTile.tileKey(0xFF5856D6, 40));
        assertEquals("icontile_red_56", AppleIconTile.tileKey(0xFFDD3B30, 64));
        for (String color : TILE_COLORS)
            for (int size : TILE_SIZES) assertTrue(DesignAssets.keys().contains("icontile_" + color + "_" + size));
    }

    @Test
    void slotCellsPickTheNearestBundledSizeWithoutMovingTheItem() {
        assertEquals("slot_empty_18", AppleSlotGrid.slotKey("empty", 18));
        assertEquals("slot_empty_18", AppleSlotGrid.slotKey("empty", 20));
        assertEquals("slot_hover_32", AppleSlotGrid.slotKey("hover", 30));
        assertEquals("slot_hover_36", AppleSlotGrid.slotKey("hover", 34));
        assertEquals("slot_locked_40", AppleSlotGrid.slotKey("locked", 38));
        assertEquals("slot_locked_40", AppleSlotGrid.slotKey("locked", 48));
    }

    @Test
    void semanticColorsMatchTheDesignTokens() {
        assertEquals("blue", DesignAssets.colorName(0xFF007AFF));
        assertEquals("green", DesignAssets.colorName(0xFF34C759));
        assertEquals("orange", DesignAssets.colorName(0xFFFF9F0A));
        assertEquals("red", DesignAssets.colorName(0xFFFF3B30));
        assertEquals("purple", DesignAssets.colorName(0xFFAF52DE));
        assertEquals("teal", DesignAssets.colorName(0xFF30B0C7));
        assertEquals("gray", DesignAssets.colorName(0xFF8E8E93));
        assertEquals("indigo", DesignAssets.colorName(0xFF5856D6));
        // alpha 不参与匹配：半透明强调色仍归入最近的语义色。
        assertEquals("blue", DesignAssets.colorName(0x40007AFF));
    }

    @Test
    void pushButtonFiresOnceOnPressAndOnlyClearsPressedOnRelease() {
        AtomicInteger runs = new AtomicInteger();
        ApplePushButton button = new ApplePushButton(0, 0, 80, 20, ApplePushButton.Kind.PRIMARY,
                () -> Component.literal("ok"), runs::incrementAndGet);
        assertTrue(button.onClick(4, 4));
        assertEquals(1, runs.get(), "press must run the action exactly once");
        assertFalse(button.mouseReleased(4, 4, 0), "release stays available to the native container");
        assertEquals(1, runs.get(), "release must never re-run the action");
        assertFalse(button.mouseReleased(4, 4, 0), "a second release is not a click");
    }
}
