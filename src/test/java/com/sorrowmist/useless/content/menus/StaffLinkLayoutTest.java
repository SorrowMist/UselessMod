package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import org.junit.jupiter.api.Test;

import static com.sorrowmist.useless.content.menus.StaffLinkLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffLinkLayoutTest {
    @Test
    void columnsAndSectionsStayInsidePanelWithoutOverlapping() {
        assertTrue(CONTENT_LEFT > INSET_PAD);
        assertTrue(LEFT_RIGHT + INSET_PAD <= CONFIG_LEFT - INSET_PAD);
        assertTrue(CONFIG_RIGHT < INVENTORY_LEFT);
        assertEquals(CONTENT_RIGHT, INVENTORY_LEFT + INVENTORY_CARD_WIDTH);
        assertTrue(CONTENT_RIGHT + INSET_PAD < PANEL_WIDTH);
        assertTrue(NETWORK_ROW_Y + SMALL_FIELD_HEIGHT < SEARCH_ROW_Y);
        assertTrue(SEARCH_ROW_Y + SMALL_FIELD_HEIGHT < LIST_ACTION_Y);
        assertTrue(LIST_ACTION_Y + SMALL_FIELD_HEIGHT < LIST_TOP);
        assertEquals(LEFT_RIGHT, CLEAR_SELECTION_X + CLEAR_SELECTION_WIDTH);
        assertTrue(SELECT_ALL_X + SELECT_ALL_WIDTH < CLEAR_SELECTION_X);
        assertTrue(LIST_BOTTOM < LIST_INSET_BOTTOM);
        assertTrue(LIST_INSET_BOTTOM < SELECTION_COUNT_Y);
        assertTrue(SELECTION_COUNT_Y + 9 < STATUS_Y);
        assertTrue(NAME_LABEL_Y + 9 < NAME_ROW_Y);
        assertTrue(NAME_ROW_Y + SMALL_FIELD_HEIGHT < CONFIG_INSET_TOP);
        assertTrue(NOTICE_Y + 9 < PANEL_HEIGHT);

        int previousBottom = CONFIG_INSET_TOP;
        for (int y : new int[]{FLOW_ROW_Y, MEDIUM_ROW_Y, ENABLED_ROW_Y, SIDE_ROW_Y}) {
            assertTrue(y >= previousBottom);
            assertTrue(y + ROW_HEIGHT <= CONFIG_INSET_BOTTOM);
            previousBottom = y + ROW_HEIGHT;
        }
        assertTrue(CONFIG_INSET_BOTTOM <= FILTER_Y);
        assertTrue(FILTER_Y + ACTION_HEIGHT < NUMERIC_INSET_TOP);
        assertTrue(NUMERIC_INSET_BOTTOM < ACTION_ROW_Y);
        assertTrue(ACTION_ROW_Y + ACTION_HEIGHT < PANEL_HEIGHT);
        assertTrue(INVENTORY_INSET_BOTTOM < PANEL_HEIGHT);
        assertEquals(CONFIG_RIGHT, FILTER_X + FILTER_BUTTON_WIDTH);
        assertTrue(APPLY_ALL_X + APPLY_ALL_WIDTH < DISSOLVE_X);
        assertEquals(CONFIG_RIGHT, DISSOLVE_X + DISSOLVE_WIDTH);
    }

    @Test
    void networkControlsAndAllNineRoutesFitInTheirRows() {
        assertTrue(PREV_BUTTON_X >= CONTENT_LEFT);
        assertTrue(PREV_BUTTON_X + NETWORK_SWITCH_WIDTH < NETWORK_FIELD_X);
        assertTrue(NETWORK_FIELD_X + NETWORK_FIELD_WIDTH < NEXT_BUTTON_X);
        assertTrue(NEXT_BUTTON_X + NETWORK_SWITCH_WIDTH < NEW_BUTTON_X);
        assertEquals(LEFT_RIGHT, NEW_BUTTON_X + NEW_BUTTON_WIDTH);

        int count = StaffLinkRoute.ROUTE_COUNT;
        assertEquals(9, count);
        assertEquals(CONFIG_LEFT, routeButtonX(0, count));
        assertTrue(ROUTE_ROW_Y > 40 + 9, "The title has its own row above the nine routes");
        for (int route = 0; route < count; route++) {
            assertTrue(routeButtonX(route, count) >= CONFIG_LEFT);
            assertTrue(routeButtonX(route, count) + ROUTE_BUTTON_WIDTH <= CONFIG_RIGHT);
            if (route + 1 < count) {
                assertTrue(routeButtonX(route, count) + ROUTE_BUTTON_WIDTH < routeButtonX(route + 1, count));
            }
        }
        assertTrue(CONFIG_RIGHT - (routeButtonX(count - 1, count) + ROUTE_BUTTON_WIDTH) <= 2);
    }

    @Test
    void numericFieldsAreStackedAndRightAligned() {
        assertTrue(NUMERIC_FIELD_X > CONFIG_LEFT);
        assertEquals(CONFIG_RIGHT - 6, NUMERIC_FIELD_X + NUMERIC_FIELD_WIDTH);
        for (int row = 0; row < NUMERIC_ROWS; row++) {
            assertTrue(numericRowY(row) >= NUMERIC_INSET_TOP);
            assertTrue(numericRowY(row) + ROW_HEIGHT <= NUMERIC_INSET_BOTTOM);
            if (row + 1 < NUMERIC_ROWS) {
                assertTrue(numericRowY(row) + ROW_HEIGHT + 16 < numericRowY(row + 1), "Reserve space for a two-line hint");
            }
        }
    }

    @Test
    void listHitBoxesMatchEveryVisibleRowAndExcludeRightColumn() {
        for (int row = 0; row < LIST_VISIBLE_ROWS; row++) {
            double top = LIST_TOP + row * LIST_ROW_HEIGHT;
            assertEquals(row, anchorRowAt(CONTENT_LEFT, top));
            assertEquals(row, anchorRowAt(LEFT_RIGHT - 0.01, top + LIST_ROW_HEIGHT - 0.01));
            assertEquals(row, anchorRowAt(LIST_NAME_X, LIST_FIRST_ROW_Y + row * LIST_ROW_HEIGHT));
        }
        assertEquals(-1, anchorRowAt(CONTENT_LEFT - 0.01, LIST_TOP));
        assertEquals(-1, anchorRowAt(LEFT_RIGHT, LIST_TOP));
        assertEquals(-1, anchorRowAt(CONFIG_LEFT, LIST_FIRST_ROW_Y));
        assertEquals(-1, anchorRowAt(CONTENT_LEFT, LIST_TOP - 0.01));
        assertEquals(-1, anchorRowAt(CONTENT_LEFT, LIST_BOTTOM));
        assertEquals(-1, anchorRowAt(CONTENT_LEFT, NAME_ROW_Y));
        assertEquals(-1, anchorRowAt(PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y));
    }

    @Test
    void reorderAndUnbindRegionsStayAtRowEnd() {
        assertTrue(LIST_NAME_X < LIST_VALUE_RIGHT);
        assertTrue(LIST_VALUE_RIGHT < LIST_CHECK_X);
        assertTrue(LIST_CHECK_X < LIST_MOVE_X);
        assertEquals(0, moveButtonAt(LIST_NAME_X));
        assertEquals(0, moveButtonAt(LIST_CHECK_X));
        assertEquals(0, moveButtonAt(LIST_MOVE_X - 0.01));
        assertEquals(-1, moveButtonAt(LIST_MOVE_X));
        assertEquals(-1, moveButtonAt(LIST_MOVE_X + LIST_MOVE_WIDTH - 0.01));
        assertEquals(1, moveButtonAt(LIST_MOVE_X + LIST_MOVE_WIDTH));
        assertEquals(1, moveButtonAt(LIST_UNBIND_X - 0.01));
        assertEquals(0, moveButtonAt(LIST_UNBIND_X));
        assertEquals(LEFT_RIGHT, LIST_UNBIND_X + LIST_UNBIND_WIDTH);
    }

    @Test
    void allThirtySixNativeItemRegionsFitInsideTwentyPixelCellsInTheirOwnColumn() {
        assertEquals(16, SLOT_SIZE);
        assertEquals(22, SLOT_STEP);
        assertEquals(9, INVENTORY_COLUMNS);
        assertEquals(3, INVENTORY_ROWS);
        assertEquals(192, INVENTORY_WIDTH);
        assertTrue(INVENTORY_LABEL_Y >= INVENTORY_INSET_TOP);
        assertTrue(INVENTORY_LABEL_Y + 9 < PLAYER_INVENTORY_Y);
        int leftPadding = PLAYER_INVENTORY_X - INVENTORY_LEFT;
        int rightPadding = CONTENT_RIGHT - PLAYER_INVENTORY_X - INVENTORY_WIDTH;
        assertTrue(leftPadding > 0);
        assertTrue(Math.abs(leftPadding - rightPadding) <= 1);
        int slots = 0;
        for (int row = 0; row <= INVENTORY_ROWS; row++) {
            int y = row == INVENTORY_ROWS ? PLAYER_HOTBAR_Y : PLAYER_INVENTORY_Y + row * SLOT_STEP;
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                int x = PLAYER_INVENTORY_X + column * SLOT_STEP;
                assertTrue(x - 2 >= INVENTORY_LEFT && x + SLOT_SIZE + 2 <= CONTENT_RIGHT);
                assertTrue(y > INVENTORY_INSET_TOP && y + SLOT_SIZE < INVENTORY_INSET_BOTTOM);
                assertEquals(-1, anchorRowAt(x, y));
                slots++;
            }
        }
        assertEquals(36, slots);
        assertTrue(PLAYER_INVENTORY_Y + (INVENTORY_ROWS - 1) * SLOT_STEP + SLOT_SIZE < PLAYER_HOTBAR_Y);
    }
}
