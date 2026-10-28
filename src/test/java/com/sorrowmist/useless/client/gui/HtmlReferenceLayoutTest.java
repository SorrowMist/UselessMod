package com.sorrowmist.useless.client.gui;

import org.junit.jupiter.api.Test;

import static com.sorrowmist.useless.client.gui.AdvancedAlloyFurnaceViewLayout.*;
import static org.junit.jupiter.api.Assertions.*;

/** Measured reference geometry, independent of rendering and of the server's legacy coordinates. */
class HtmlReferenceLayoutTest {
    @Test
    void advancedFurnaceUsesMeasuredColumnAndWindowSizes() {
        assertEquals(564, WINDOW_WIDTH);
        assertEquals(407, WINDOW_HEIGHT);
        assertEquals(167, COL_B_WIDTH);
        assertEquals(144, COL_C_WIDTH);
        assertEquals(98, PATTERN_CARD_H);
        assertEquals(146, INVENTORY_CARD_Y);
        assertEquals(117, INVENTORY_CARD_H);
        assertEquals(318, MID_CARD_H);
        assertTrue(CANCEL_FOOT_Y + 9 <= WINDOW_HEIGHT - 12);
        assertEquals(380, com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.WINDOW_HEIGHT,
                "the client view must not move the server menu's slots");
    }

    @Test
    void advancedSlotAndTankHitGeometryFitsTheSameCards() {
        for (int i = 0; i < 27; i++) {
            assertTrue(patternSlotX(i) - 2 >= PATTERN_CARD_X + 8);
            assertTrue(patternSlotX(i) + 18 <= CONTENT_A_RIGHT);
            assertTrue(patternSlotY(i) + 18 <= PATTERN_CARD_Y + PATTERN_CARD_H - 8);
        }
        for (int i = 0; i < 9; i++) {
            assertTrue(inputSlotX(i) + 18 < TANK_GRID_X);
            assertTrue(tankCellX(i) + 20 <= CONTENT_B_RIGHT);
            assertEquals(i, tankIndexAt(tankCellX(i) + 10, tankCellY(false, i) + 10, false));
            assertEquals(i, tankIndexAt(tankCellX(i) + 10, tankCellY(true, i) + 10, true));
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(i, faceCellAt(faceCellX(i) + 10, faceCellY(i) + 10));
            assertTrue(faceCellX(i) + FACE_CELL_W <= FACE_CARD_X + FACE_CARD_W - 8);
            assertTrue(faceCellY(i) + FACE_CELL_H <= FACE_CARD_Y + FACE_CARD_H - 6);
            assertTrue(faceMenuX(i) + FACE_MENU_W <= WINDOW_WIDTH - WINDOW_PAD);
        }
    }

    @Test
    void chainGroupFootersFollowTheMeasuredContentAndErrorSpace() {
        var empty = ChainGroupScreen.referenceGeometry(0, 0, false);
        assertEquals(269, empty.height());
        assertEquals(241, empty.groupFooterY());
        assertEquals(111, empty.fieldY());
        assertEquals(135, empty.dropY());
        var full = ChainGroupScreen.referenceGeometry(4, 8, false);
        assertEquals(301, full.height());
        assertEquals(149, full.groupFooterY());
        assertEquals(237, full.fieldY());
        assertEquals(261, full.dropY());
        var error = ChainGroupScreen.referenceGeometry(4, 8, true);
        assertEquals(334, error.height());
        assertEquals(full.dropY() + 33, error.dropY());
        assertEquals(full.entryHeight(), ChainGroupScreen.referenceGeometry(4, 200, false).entryHeight(),
                "long lists scroll rather than pushing controls beyond the window");
    }

    @Test
    void multiblockTaskAndEditingStatesHaveTheReferenceHeights() {
        assertEquals(318, MultiblockAlloyFurnaceScreen.referenceGeometry(false, false).height());
        assertEquals(336, MultiblockAlloyFurnaceScreen.referenceGeometry(true, false).height());
        assertEquals(369, MultiblockAlloyFurnaceScreen.referenceGeometry(true, true).height());
        for (boolean tasks : new boolean[]{false, true}) for (boolean edit : new boolean[]{false, true}) {
            var g = MultiblockAlloyFurnaceScreen.referenceGeometry(tasks, edit);
            assertEquals(g.taskCardY() + g.taskCardHeight() + 8, g.controlsY());
            assertEquals(g.controlsY() + 16 + 12, g.height());
            assertTrue(g.taskListY() + (tasks ? 78 : 50) <= g.taskCardY() + g.taskCardHeight() - 4);
        }
    }

    @Test
    void dimensionActionsStayAtBottomRightWithAndWithoutTeleport() {
        var normal = DimensionConfigScreen.actionGeometry(false);
        assertEquals(452, normal.applyX() + 52);
        assertEquals(normal.cancelX() + 52 + 8, normal.applyX());
        assertEquals(359 - 12, normal.y() + 22);
        var teleport = DimensionConfigScreen.actionGeometry(true);
        assertEquals(452, teleport.teleportX() + 96);
        assertEquals(teleport.cancelX() + 52 + 8, teleport.applyX());
        assertEquals(teleport.applyX() + 52 + 8, teleport.teleportX());
        assertTrue(teleport.cancelX() >= 232);
        assertEquals(normal.y(), teleport.y());
    }

    @Test
    void wirelessNameAndFilterControlsFitInsideTheirCards() {
        assertEquals(StaffLinkViewLayout.CONFIG_RIGHT - 8,
                StaffLinkViewLayout.NAME_SAVE_X + StaffLinkViewLayout.NAME_SAVE_WIDTH);
        assertEquals(StaffLinkViewLayout.NAME_FIELD_X + StaffLinkViewLayout.NAME_FIELD_WIDTH + 4,
                StaffLinkViewLayout.NAME_SAVE_X);
        assertEquals(StaffLinkViewLayout.CONFIG_INSET_BOTTOM, StaffLinkViewLayout.FILTER_Y + 22);
        assertEquals(8, StaffLinkViewLayout.NUMERIC_INSET_TOP - StaffLinkViewLayout.CONFIG_INSET_BOTTOM);
        assertTrue(StaffLinkViewLayout.SELECTION_COUNT_Y >= StaffLinkViewLayout.LIST_BOTTOM + 6);
        assertTrue(StaffLinkViewLayout.STATUS_Y >= StaffLinkViewLayout.SELECTION_COUNT_Y + 27);
    }

    @Test
    void wirelessRowsAndInventoryTargetsShareMeasuredViewCoordinates() {
        assertEquals(466, StaffLinkViewLayout.PANEL_HEIGHT);
        assertEquals(168, StaffLinkViewLayout.LEFT_WIDTH);
        assertEquals(211, StaffLinkViewLayout.CONFIG_WIDTH);
        for (int row = 0; row < StaffLinkViewLayout.LIST_VISIBLE_ROWS; row++) {
            int y = StaffLinkViewLayout.LIST_TOP + row * StaffLinkViewLayout.LIST_ROW_HEIGHT;
            assertEquals(row, StaffLinkViewLayout.anchorRowAt(14, y + 10));
            assertEquals(-1, StaffLinkViewLayout.anchorRowAt(StaffLinkViewLayout.CONFIG_LEFT, y + 10));
        }
        assertEquals(-1, StaffLinkViewLayout.anchorRowAt(14, StaffLinkViewLayout.LIST_BOTTOM));
        for (int i = 0; i < 36; i++) {
            int x = StaffLinkViewLayout.PLAYER_INVENTORY_X + i % 9 * 22;
            int y = i < 27 ? StaffLinkViewLayout.PLAYER_INVENTORY_Y + i / 9 * 22 : StaffLinkViewLayout.PLAYER_HOTBAR_Y;
            assertTrue(x - 2 >= StaffLinkViewLayout.INVENTORY_LEFT);
            assertTrue(x + 18 <= StaffLinkViewLayout.CONTENT_RIGHT);
            assertTrue(y + 18 <= StaffLinkViewLayout.INVENTORY_INSET_BOTTOM - 7);
        }
    }
}
