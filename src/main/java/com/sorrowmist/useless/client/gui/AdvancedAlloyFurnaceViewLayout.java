package com.sorrowmist.useless.client.gui;

/**
 * Animation-free HTML-01 geometry in GUI pixels (CSS / 2, rounded once).
 * Client display only: the menu's persisted slot indices and server coordinates stay unchanged.
 */
public final class AdvancedAlloyFurnaceViewLayout {
    private AdvancedAlloyFurnaceViewLayout() {}

    public static final int WINDOW_WIDTH = 564, WINDOW_HEIGHT = 407;
    public static final int WINDOW_PAD = 12, CONTENT_TOP = 40, CARD_PAD = 8;
    public static final int SLOT_CELL = 20, SLOT_PITCH = 22, SLOT_ITEM_INSET = 2;
    public static final int COL_A_X = 12, COL_A_WIDTH = 212;
    public static final int COL_B_X = 232, COL_B_WIDTH = 167;
    public static final int COL_C_X = 407, COL_C_WIDTH = 144;
    public static final int CONTENT_A_X = 21, CONTENT_A_RIGHT = 217;
    public static final int CONTENT_B_X = 241, CONTENT_B_RIGHT = 391;
    public static final int CONTENT_C_X = 416, CONTENT_C_RIGHT = 543;

    public static final int PATTERN_CARD_X = COL_A_X, PATTERN_CARD_Y = 40;
    public static final int PATTERN_CARD_W = COL_A_WIDTH, PATTERN_CARD_H = 98;
    public static final int PATTERN_CAPTION_Y = 49, PATTERN_GRID_Y = 66;
    public static final int PATTERN_GRID_COLUMNS = 9, PATTERN_GRID_ROWS = 3;
    public static final int PATTERN_SLOTS_PER_PAGE = 27;
    public static final int PATTERN_PAGER_X = 160, PATTERN_PAGER_Y = 49;
    public static final int PATTERN_PAGER_W = 56, PATTERN_PAGER_H = 13;
    public static final int INVENTORY_CARD_X = COL_A_X, INVENTORY_CARD_Y = 146;
    public static final int INVENTORY_CARD_W = COL_A_WIDTH, INVENTORY_CARD_H = 117;
    public static final int INVENTORY_CAPTION_Y = 155;
    public static final int INVENTORY_COLUMNS = 9, INVENTORY_ROWS = 3;
    public static final int PLAYER_INVENTORY_X = 23, PLAYER_INVENTORY_Y = 170, PLAYER_HOTBAR_Y = 237;

    public static final int MID_CARD_X = COL_B_X, MID_CARD_Y = 40;
    public static final int MID_CARD_W = COL_B_WIDTH, MID_CARD_H = 318;
    public static final int IN_CAPTION_Y = 49, IN_GRID_Y = 62;
    public static final int IO_GRID_COLUMNS = 3, IO_GRID_ROWS = 3;
    public static final int TANK_COLUMNS = 3, TANK_ROWS = 3, TANK_COUNT_PER_SIDE = 9;
    public static final int TANK_GRID_X = 327, TANK_INNER_SIZE = 16;
    public static final int MID_SEPARATOR_1_Y = 134;
    public static final int PROC_CAPTION_Y = 152, PROC_MOLD_X = 265;
    public static final int PROC_SLOT_Y = 164;
    public static final int PROC_PROGRESS_X = 293, PROC_PROGRESS_Y = 143;
    public static final int PROC_PROGRESS_W = 98, PROC_PROGRESS_H = 42;
    public static final int MID_SEPARATOR_2_Y = 192;
    public static final int OUT_CAPTION_Y = 201, OUT_GRID_Y = 213;
    public static final int MID_SEPARATOR_3_Y = 284;
    public static final int STATS_Y = 285, STAT_ROWS = 3, STAT_ROW_H = 22;

    public static final int FACE_CARD_X = COL_C_X, FACE_CARD_Y = 40;
    public static final int FACE_CARD_W = COL_C_WIDTH, FACE_CARD_H = 138;
    public static final int FACE_CAPTION_Y = 49, FACE_GRID_Y = 62;
    public static final int FACE_CELL_W = 61, FACE_CELL_H = 34;
    public static final int FACE_CELL_STEP_X = 63, FACE_CELL_STEP_Y = 38, FACE_COUNT = 6;
    public static final int SWITCH_LIST_X = COL_C_X, SWITCH_LIST_Y = 187;
    public static final int SWITCH_LIST_W = COL_C_WIDTH, SWITCH_LIST_H = 88;
    public static final int SWITCH_ROW_H = 22, SWITCH_ROWS = 4;
    public static final int SWITCH_LABEL_X = SWITCH_LIST_X + 28;
    public static final int SWITCH_X = 522, SWITCH_W = 22, SWITCH_H = 13;
    public static final int ENERGY_CARD_X = COL_C_X, ENERGY_CARD_Y = 283;
    public static final int ENERGY_CARD_W = COL_C_WIDTH, ENERGY_CARD_H = 71;
    public static final int ENERGY_ROW_Y = 292, ENERGY_ROW_H = 54;
    public static final int ENERGY_BAR_H = 6;
    public static final int CANCEL_BUTTON_X = COL_C_X, CANCEL_BUTTON_Y = 362;
    public static final int CANCEL_BUTTON_W = COL_C_WIDTH, CANCEL_BUTTON_H = 20, CANCEL_FOOT_Y = 386;
    public static final int HEADER_ROW_Y = 7, HEADER_BADGE_H = 14;
    public static final int REDSTONE_BUTTON_SIZE = 14, REDSTONE_BUTTON_X = 539;
    public static final int HEADER_CANVAS_X = 372, HEADER_CANVAS_W = 183;

    public static final int FACE_MENU_W = 116, FACE_MENU_PAD = 4;
    public static final int FACE_MENU_HEADER_H = 14, FACE_MENU_ROW_H = 16, FACE_MENU_ROWS = 6;
    public static final int FACE_MENU_H = 120;
    public static final int AE_POP_X = 158, AE_POP_Y = 74, AE_POP_W = 238, AE_POP_PAD = 10;
    public static final int AE_HEADER_ICON = 20, AE_PROGRESS_Y = 38, AE_ROWS_Y = 50;
    public static final int AE_ROW_H = 26, AE_MAX_ROWS = 5, AE_FOOTER_H = 20;
    public static final int AE_CANCEL_W = 110, AE_DONE_W = 56, AE_CLOSE_SIZE = 14;

    public static int gridExtent(int cells) { return cells * SLOT_PITCH - 2; }
    public static int inputSlotX(int i) { return CONTENT_B_X + 2 + i % 3 * SLOT_PITCH; }
    public static int inputSlotY(int i) { return IN_GRID_Y + 2 + i / 3 * SLOT_PITCH; }
    public static int outputSlotX(int i) { return inputSlotX(i); }
    public static int outputSlotY(int i) { return OUT_GRID_Y + 2 + i / 3 * SLOT_PITCH; }
    public static int patternSlotX(int i) { return CONTENT_A_X + 2 + i % 9 * SLOT_PITCH; }
    public static int patternSlotY(int i) { return PATTERN_GRID_Y + 2 + i / 9 * SLOT_PITCH; }
    public static int playerInventorySlotX(int col) { return PLAYER_INVENTORY_X + col * SLOT_PITCH; }
    public static int playerInventorySlotY(int row) { return PLAYER_INVENTORY_Y + row * SLOT_PITCH; }
    public static int tankCellX(int i) { return TANK_GRID_X + i % 3 * SLOT_PITCH; }
    public static int tankCellY(boolean out, int i) { return (out ? OUT_GRID_Y : IN_GRID_Y) + i / 3 * SLOT_PITCH; }
    public static int tankInnerX(int i) { return tankCellX(i) + 2; }
    public static int tankInnerY(boolean out, int i) { return tankCellY(out, i) + 2; }
    public static int tankIndexAt(double x, double y, boolean out) {
        for (int i = 0; i < TANK_COUNT_PER_SIDE; i++)
            if (hits(x, y, tankCellX(i), tankCellY(out, i), SLOT_CELL, SLOT_CELL)) return i;
        return -1;
    }
    public static int faceCellX(int i) { return CONTENT_C_X + i % 2 * FACE_CELL_STEP_X; }
    public static int faceCellY(int i) { return FACE_GRID_Y + i / 2 * FACE_CELL_STEP_Y; }
    public static int faceCellAt(double x, double y) {
        for (int i = 0; i < FACE_COUNT; i++)
            if (hits(x, y, faceCellX(i), faceCellY(i), FACE_CELL_W, FACE_CELL_H)) return i;
        return -1;
    }
    public static int autoIoTooltipRowAt(double x, double y) {
        if (!hits(x, y, SWITCH_LIST_X, SWITCH_LIST_Y, SWITCH_LIST_W, 2 * SWITCH_ROW_H)) return -1;
        return (int) ((y - SWITCH_LIST_Y) / SWITCH_ROW_H);
    }
    public static boolean parallelInfoTooltipAt(double x, double y) {
        return hits(x, y, PROC_PROGRESS_X + PROC_PROGRESS_W - 12, PROC_PROGRESS_Y - 1, 12, 12);
    }
    public static boolean progressTooltipAt(double x, double y) {
        return hits(x, y, PROC_PROGRESS_X, PROC_PROGRESS_Y, PROC_PROGRESS_W, PROC_PROGRESS_H)
                && !parallelInfoTooltipAt(x, y);
    }
    public static int faceMenuX(int face) {
        return Math.max(WINDOW_PAD, Math.min(faceCellX(face), WINDOW_WIDTH - WINDOW_PAD - FACE_MENU_W));
    }
    public static int faceMenuY(int face) { return faceCellY(face) + FACE_CELL_H + 2; }
    public static int faceMenuRowAt(double y) {
        int row = (int) Math.floor((y - FACE_MENU_PAD - FACE_MENU_HEADER_H) / FACE_MENU_ROW_H);
        return row >= 0 && row < FACE_MENU_ROWS ? row : -1;
    }
    private static boolean hits(double x, double y, int rx, int ry, int w, int h) {
        return x >= rx && x < rx + w && y >= ry && y < ry + h;
    }
}
