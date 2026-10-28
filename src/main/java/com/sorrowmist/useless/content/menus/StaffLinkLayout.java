package com.sorrowmist.useless.content.menus;

/** Shared design-08 geometry; menu slots, manual hit tests and client controls must agree. */
public final class StaffLinkLayout {
    public static final int PANEL_WIDTH = 632;
    public static final int PANEL_HEIGHT = 430;
    public static final int CONTENT_LEFT = 20;
    public static final int CONTENT_RIGHT = PANEL_WIDTH - 12;
    public static final int LEFT_WIDTH = 144;
    public static final int LEFT_RIGHT = CONTENT_LEFT + LEFT_WIDTH;
    public static final int CONFIG_LEFT = 180;
    public static final int CONFIG_WIDTH = 224;
    public static final int CONFIG_RIGHT = CONFIG_LEFT + CONFIG_WIDTH;
    public static final int INSET_PAD = 8;
    public static final int SMALL_FIELD_HEIGHT = 18;
    public static final int ROW_HEIGHT = 18;
    public static final int ACTION_HEIGHT = 20;

    public static final int NETWORK_ROW_Y = 46;
    public static final int NETWORK_SWITCH_WIDTH = 12;
    public static final int NETWORK_FIELD_WIDTH = 78;
    public static final int NEW_BUTTON_WIDTH = 30;
    public static final int PREV_BUTTON_X = CONTENT_LEFT;
    public static final int NETWORK_FIELD_X = PREV_BUTTON_X + NETWORK_SWITCH_WIDTH + 4;
    public static final int NEXT_BUTTON_X = NETWORK_FIELD_X + NETWORK_FIELD_WIDTH + 4;
    public static final int NEW_BUTTON_X = NEXT_BUTTON_X + NETWORK_SWITCH_WIDTH + 4;

    public static final int SEARCH_ROW_Y = 88;
    public static final int LIST_ACTION_Y = 116;
    public static final int SELECT_ALL_WIDTH = 40;
    public static final int CLEAR_SELECTION_WIDTH = 32;
    public static final int CLEAR_SELECTION_X = LEFT_RIGHT - CLEAR_SELECTION_WIDTH;
    public static final int SELECT_ALL_X = CLEAR_SELECTION_X - 4 - SELECT_ALL_WIDTH;
    public static final int LIST_TOP = 138;
    public static final int LIST_ROW_HEIGHT = 24;
    public static final int LIST_VISIBLE_ROWS = 8;
    public static final int LIST_BOTTOM = LIST_TOP + LIST_ROW_HEIGHT * LIST_VISIBLE_ROWS;
    public static final int LIST_FIRST_ROW_Y = LIST_TOP + 3;
    public static final int LIST_INSET_BOTTOM = LIST_BOTTOM + 4;
    public static final int LIST_UNBIND_WIDTH = 12;
    public static final int LIST_UNBIND_X = LEFT_RIGHT - LIST_UNBIND_WIDTH;
    public static final int LIST_MOVE_WIDTH = 9;
    public static final int LIST_MOVE_X = LIST_UNBIND_X - 2 * LIST_MOVE_WIDTH;
    public static final int LIST_CHECK_X = LIST_MOVE_X - 8;
    public static final int LIST_VALUE_RIGHT = LIST_CHECK_X - 4;
    public static final int LIST_GLYPH_X = CONTENT_LEFT;
    public static final int LIST_NAME_X = CONTENT_LEFT + 20;
    public static final int SELECTION_COUNT_Y = LIST_INSET_BOTTOM + 4;
    public static final int NAME_LABEL_Y = 82;
    public static final int NAME_ROW_Y = 98;
    public static final int STATUS_Y = 354;
    public static final int NOTICE_Y = 406;

    public static final int ROUTE_ROW_Y = 56;
    public static final int ROUTE_BUTTON_WIDTH = 22;
    public static final int ROUTE_BUTTON_STEP = 25;
    public static final int CONFIG_INSET_TOP = 124;
    public static final int FLOW_ROW_Y = 128;
    public static final int MEDIUM_ROW_Y = 152;
    public static final int ENABLED_ROW_Y = 176;
    public static final int SIDE_ROW_Y = 200;
    public static final int CONFIG_INSET_BOTTOM = 224;
    public static final int MEDIUM_BUTTON_X = CONFIG_LEFT;
    public static final int MEDIUM_MENU_Y = MEDIUM_ROW_Y + ROW_HEIGHT + 1;
    public static final int MEDIUM_MENU_WIDTH = CONFIG_WIDTH;
    public static final int MEDIUM_MENU_ROW_HEIGHT = ROW_HEIGHT;
    public static final int NUMERIC_INSET_TOP = 252;
    public static final int NUMERIC_FIRST_ROW_Y = 260;
    public static final int NUMERIC_ROW_STEP = 40;
    public static final int NUMERIC_ROWS = 3;
    public static final int NUMERIC_FIELD_WIDTH = 114;
    public static final int NUMERIC_FIELD_X = CONFIG_RIGHT - NUMERIC_FIELD_WIDTH - 6;
    public static final int NUMERIC_INSET_BOTTOM = 386;
    public static final int FILTER_X = CONFIG_LEFT;
    public static final int FILTER_Y = 224;
    public static final int FILTER_BUTTON_WIDTH = CONFIG_WIDTH;
    public static final int ACTION_ROW_Y = 398;
    public static final int DISSOLVE_WIDTH = 72;
    public static final int DISSOLVE_X = CONFIG_RIGHT - DISSOLVE_WIDTH;
    public static final int APPLY_ALL_X = CONFIG_LEFT;
    public static final int APPLY_ALL_WIDTH = DISSOLVE_X - 8 - APPLY_ALL_X;

    public static final int SLOT_SIZE = 16;
    public static final int SLOT_STEP = 22;
    public static final int INVENTORY_COLUMNS = 9;
    public static final int INVENTORY_ROWS = 3;
    public static final int INVENTORY_WIDTH = (INVENTORY_COLUMNS - 1) * SLOT_STEP + SLOT_SIZE;
    public static final int INVENTORY_LEFT = 412;
    public static final int INVENTORY_CARD_WIDTH = 208;
    public static final int INVENTORY_INSET_TOP = 40;
    public static final int PLAYER_INVENTORY_X = 420;
    public static final int PLAYER_INVENTORY_Y = 60;
    public static final int PLAYER_HOTBAR_Y = 128;
    public static final int INVENTORY_LABEL_X = 420;
    public static final int INVENTORY_LABEL_Y = 48;
    public static final int INVENTORY_INSET_BOTTOM = 160;

    private StaffLinkLayout() {}

    public static int routeButtonX(int route, int routeCount) {
        return CONFIG_LEFT + route * ROUTE_BUTTON_STEP;
    }

    public static int numericRowY(int row) { return NUMERIC_FIRST_ROW_Y + row * NUMERIC_ROW_STEP; }

    public static int anchorRowAt(double localX, double localY) {
        if (localX < CONTENT_LEFT || localX >= LEFT_RIGHT || localY < LIST_TOP || localY >= LIST_BOTTOM) return -1;
        return (int) ((localY - LIST_TOP) / LIST_ROW_HEIGHT);
    }

    public static int moveButtonAt(double localX) {
        if (localX >= LIST_MOVE_X && localX < LIST_MOVE_X + LIST_MOVE_WIDTH) return -1;
        if (localX >= LIST_MOVE_X + LIST_MOVE_WIDTH && localX < LIST_UNBIND_X) return 1;
        return 0;
    }
}
