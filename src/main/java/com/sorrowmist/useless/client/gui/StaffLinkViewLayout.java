package com.sorrowmist.useless.client.gui;

/** HTML-08 display geometry. Server menu coordinates/slot order remain untouched. */
public final class StaffLinkViewLayout {
    public static final int PANEL_WIDTH = 632, PANEL_HEIGHT = 466;
    public static final int CONTENT_LEFT = 13, CONTENT_RIGHT = 620;
    public static final int LEFT_WIDTH = 168, LEFT_RIGHT = CONTENT_LEFT + LEFT_WIDTH;
    public static final int CONFIG_LEFT = 189, CONFIG_WIDTH = 211, CONFIG_RIGHT = 400;
    public static final int INSET_PAD = 8;
    public static final int SMALL_FIELD_HEIGHT = 16, ROW_HEIGHT = 22, ACTION_HEIGHT = 20;
    public static final int NETWORK_ROW_Y = 49, NETWORK_SWITCH_WIDTH = 13;
    public static final int NETWORK_FIELD_WIDTH = 79, NEW_BUTTON_WIDTH = 34;
    public static final int PREV_BUTTON_X = 21, NETWORK_FIELD_X = 38, NEXT_BUTTON_X = 121, NEW_BUTTON_X = 138;
    public static final int SEARCH_ROW_Y = 107, LIST_ACTION_Y = 94;
    public static final int SELECT_ALL_WIDTH = 28, CLEAR_SELECTION_WIDTH = 28;
    public static final int CLEAR_SELECTION_X = LEFT_RIGHT - CLEAR_SELECTION_WIDTH;
    public static final int SELECT_ALL_X = CLEAR_SELECTION_X - 4 - SELECT_ALL_WIDTH;
    public static final int LIST_TOP = 127, LIST_ROW_HEIGHT = 22, LIST_VISIBLE_ROWS = 11;
    public static final int LIST_FIRST_ROW_Y = LIST_TOP + 2;
    public static final int LIST_BOTTOM = LIST_FIRST_ROW_Y + LIST_ROW_HEIGHT * LIST_VISIBLE_ROWS + 2;
    public static final int LIST_INSET_BOTTOM = LIST_BOTTOM;
    public static final int LIST_UNBIND_WIDTH = 12, LIST_UNBIND_X = LEFT_RIGHT - 16;
    public static final int LIST_MOVE_WIDTH = 8, LIST_MOVE_X = LIST_UNBIND_X - 2 * LIST_MOVE_WIDTH;
    public static final int LIST_CHECK_X = LIST_MOVE_X - 8, LIST_VALUE_RIGHT = LIST_CHECK_X - 3;
    public static final int LIST_GRIP_X = CONTENT_LEFT + 4, LIST_GLYPH_X = 28, LIST_NAME_X = 50;
    public static final int SELECTION_COUNT_Y = LIST_BOTTOM + 6, NAME_LABEL_Y = 89, NAME_ROW_Y = 102;
    public static final int NAME_FIELD_X = CONFIG_LEFT + 8, NAME_SAVE_WIDTH = 32;
    public static final int NAME_SAVE_X = CONFIG_RIGHT - 8 - NAME_SAVE_WIDTH;
    public static final int NAME_FIELD_WIDTH = NAME_SAVE_X - 4 - NAME_FIELD_X;
    public static final int STATUS_Y = SELECTION_COUNT_Y + 28, NOTICE_Y = 445;
    public static final int ROUTE_ROW_Y = 57, ROUTE_BUTTON_WIDTH = 22, ROUTE_BUTTON_STEP = 23;
    public static final int CONFIG_INSET_TOP = 142;
    public static final int FLOW_ROW_Y = 142, MEDIUM_ROW_Y = 164, ENABLED_ROW_Y = 186, SIDE_ROW_Y = 208;
    public static final int CONFIG_INSET_BOTTOM = 252;
    public static final int MEDIUM_BUTTON_X = CONFIG_LEFT;
    public static final int MEDIUM_MENU_Y = MEDIUM_ROW_Y + ROW_HEIGHT + 1;
    public static final int MEDIUM_MENU_WIDTH = CONFIG_WIDTH, MEDIUM_MENU_ROW_HEIGHT = 18;
    public static final int NUMERIC_INSET_TOP = 260, NUMERIC_ROWS = 3;
    public static final int NUMERIC_FIELD_WIDTH = 168, NUMERIC_FIELD_X = 223;
    public static final int NUMERIC_INSET_BOTTOM = 426;
    public static final int FILTER_X = CONFIG_LEFT, FILTER_Y = 230, FILTER_BUTTON_WIDTH = CONFIG_WIDTH;
    public static final int ACTION_ROW_Y = 434, DISSOLVE_WIDTH = 62, DISSOLVE_X = CONFIG_RIGHT - DISSOLVE_WIDTH;
    public static final int APPLY_ALL_X = CONFIG_LEFT, APPLY_ALL_WIDTH = 80;
    public static final int SLOT_SIZE = 16, SLOT_STEP = 22, INVENTORY_COLUMNS = 9, INVENTORY_ROWS = 3;
    public static final int INVENTORY_WIDTH = 192, INVENTORY_LEFT = 408, INVENTORY_CARD_WIDTH = 212;
    public static final int INVENTORY_INSET_TOP = 40, INVENTORY_INSET_BOTTOM = 157;
    public static final int PLAYER_INVENTORY_X = 418, PLAYER_INVENTORY_Y = 64, PLAYER_HOTBAR_Y = 132;
    public static final int INVENTORY_LABEL_X = 418, INVENTORY_LABEL_Y = 49;

    private StaffLinkViewLayout() {}
    public static int routeButtonX(int route, int routeCount) {
        return CONFIG_LEFT + route * CONFIG_WIDTH / Math.max(1, routeCount);
    }
    public static int numericRowY(int row) { return switch (row) { case 0 -> 284; case 1 -> 322; default -> 368; }; }
    public static int anchorRowY(int row) { return LIST_FIRST_ROW_Y + row * LIST_ROW_HEIGHT; }
    public static int anchorRowAt(double x, double y) {
        if (x < CONTENT_LEFT || x >= LEFT_RIGHT || y < LIST_FIRST_ROW_Y || y >= LIST_BOTTOM - 2) return -1;
        return (int) ((y - LIST_FIRST_ROW_Y) / LIST_ROW_HEIGHT);
    }
    public static int moveButtonAt(double x) {
        if (x >= LIST_MOVE_X && x < LIST_MOVE_X + LIST_MOVE_WIDTH) return -1;
        if (x >= LIST_MOVE_X + LIST_MOVE_WIDTH && x < LIST_UNBIND_X) return 1;
        return 0;
    }
}
