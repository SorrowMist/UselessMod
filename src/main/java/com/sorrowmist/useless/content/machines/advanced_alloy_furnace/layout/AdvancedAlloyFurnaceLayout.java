package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout;

/**
 * 高级合金炉的槽位、流体槽与界面几何定义。
 *
 * <p>该类是机器数据的<b>唯一</b>数据源：机器槽位编号、菜单槽位坐标、界面卡片 / 储罐 / 按钮热区
 * 全部写在这里，{@code AdvancedAlloyFurnaceMenu} 与 {@code AdvancedAlloyFurnaceScreen} 都只引用本类，
 * 避免出现「槽位挪了、热区没挪」这类错位。本类不依赖任何客户端类，因此槽位检查可以在单元测试里直接跑。</p>
 *
 * <p>位置单位为 1x GUI 像素，相对窗口左上角（即 {@code leftPos / topPos}）。
 * 设计稿宽 1128px，这里整体取半尺寸 564：底格 20、间距 2（步长 22），原版 16×16 物品区落在底格 +2 处。</p>
 */
public final class AdvancedAlloyFurnaceLayout {
    // ==================== 机器槽位索引（存档 / 网络协议的一部分，不要改动顺序）====================
    public static final int INPUT_SLOTS_START = 0;
    public static final int INPUT_SLOTS_COUNT = 9;
    public static final int OUTPUT_SLOTS_START = 9;
    public static final int OUTPUT_SLOTS_COUNT = 9;
    public static final int CATALYST_SLOT = 18;
    public static final int MOLD_SLOT = 19;
    public static final int PATTERN_SLOTS_START = 20;
    public static final int PATTERN_SLOTS_COUNT = 108;
    public static final int PATTERN_SLOTS_END = PATTERN_SLOTS_START + PATTERN_SLOTS_COUNT - 1;
    public static final int TOTAL_SLOTS = 128;
    public static final int FLUID_TANK_COUNT = 6;
    public static final int CHEMICAL_TANK_COUNT = 3;
    public static final int CHEMICAL_TANK_START = FLUID_TANK_COUNT;

    // ==================== 窗口骨架 ====================
    /** 窗口宽（设计稿 1128 的一半）。 */
    public static final int WINDOW_WIDTH = 564;
    /** 窗口高：由最高的右栏（取消 AE 区块）决定。 */
    public static final int WINDOW_HEIGHT = 380;
    /** 标题栏高度（与共享 AppleWindow 的标题栏一致）。 */
    public static final int TITLE_BAR = 28;
    /** 窗口四周留白。 */
    public static final int WINDOW_PAD = 12;
    /** 三栏之间的间距。 */
    public static final int COLUMN_GAP = 8;
    /** 卡片内边距。 */
    public static final int CARD_PAD = 8;
    /** 首行卡片的顶边。 */
    public static final int CONTENT_TOP = TITLE_BAR + WINDOW_PAD;

    // ==================== 槽位格子 ====================
    /** 槽位底格边长。 */
    public static final int SLOT_CELL = 20;
    /** 相邻底格步长：底格 + 2px 间距。 */
    public static final int SLOT_PITCH = 22;
    /** 原版 16×16 物品区在底格内的偏移（(20-16)/2）。 */
    public static final int SLOT_ITEM_INSET = 2;

    // ==================== 三栏 ====================
    public static final int COL_A_X = WINDOW_PAD;                                    // 12
    public static final int COL_A_WIDTH = 212;
    public static final int COL_B_X = COL_A_X + COL_A_WIDTH + COLUMN_GAP;            // 232
    public static final int COL_B_WIDTH = 160;
    public static final int COL_C_X = COL_B_X + COL_B_WIDTH + COLUMN_GAP;            // 400
    public static final int COL_C_WIDTH = WINDOW_WIDTH - WINDOW_PAD - COL_C_X;        // 152

    /** 各栏卡片内容区左右边界（卡片内边距之后）。 */
    public static final int CONTENT_A_X = COL_A_X + CARD_PAD;                        // 20
    public static final int CONTENT_A_RIGHT = COL_A_X + COL_A_WIDTH - CARD_PAD;      // 216
    public static final int CONTENT_B_X = COL_B_X + CARD_PAD;                        // 240
    public static final int CONTENT_B_RIGHT = COL_B_X + COL_B_WIDTH - CARD_PAD;      // 384
    public static final int CONTENT_C_X = COL_C_X + CARD_PAD;                        // 408
    public static final int CONTENT_C_RIGHT = COL_C_X + COL_C_WIDTH - CARD_PAD;      // 544

    // ==================== 左栏：样板卡 + 物品栏卡 ====================
    /** 卡片标题（caption）在卡片内的文字基线偏移。 */
    public static final int CAPTION_OFFSET = 7;
    /** 卡片内容（网格）在卡片内的顶边偏移：标题行高度。 */
    public static final int GRID_OFFSET = 20;

    public static final int PATTERN_CARD_X = COL_A_X;
    public static final int PATTERN_CARD_Y = CONTENT_TOP;                             // 40
    public static final int PATTERN_CARD_W = COL_A_WIDTH;
    public static final int PATTERN_CAPTION_Y = PATTERN_CARD_Y + CAPTION_OFFSET;      // 47
    public static final int PATTERN_GRID_Y = PATTERN_CARD_Y + GRID_OFFSET;            // 60
    public static final int PATTERN_GRID_COLUMNS = 9;
    public static final int PATTERN_GRID_ROWS = 3;
    public static final int PATTERN_SLOTS_PER_PAGE = PATTERN_GRID_COLUMNS * PATTERN_GRID_ROWS; // 27
    public static final int PATTERN_CARD_H = GRID_OFFSET + gridExtent(PATTERN_GRID_ROWS) + CARD_PAD; // 92
    public static final int PATTERN_PAGER_X = CONTENT_A_RIGHT - 48;                   // 168
    public static final int PATTERN_PAGER_Y = PATTERN_CARD_Y + 4;                     // 44
    public static final int PATTERN_PAGER_W = 48;
    public static final int PATTERN_PAGER_H = 13;
    /** 样板槽物品区（= 底格 + 2）。 */
    public static final int PATTERN_SLOTS_X = CONTENT_A_X + SLOT_ITEM_INSET;          // 22
    public static final int PATTERN_SLOTS_Y = PATTERN_GRID_Y + SLOT_ITEM_INSET;       // 62

    public static final int INVENTORY_CARD_X = COL_A_X;
    public static final int INVENTORY_CARD_Y = PATTERN_CARD_Y + PATTERN_CARD_H + COLUMN_GAP; // 140
    public static final int INVENTORY_CARD_W = COL_A_WIDTH;
    public static final int INVENTORY_CAPTION_Y = INVENTORY_CARD_Y + CAPTION_OFFSET;  // 147
    public static final int INVENTORY_GRID_Y = INVENTORY_CARD_Y + GRID_OFFSET;        // 160
    public static final int INVENTORY_COLUMNS = 9;
    public static final int INVENTORY_ROWS = 3;
    /** 3 行背包与快捷栏之间的空隙。 */
    public static final int HOTBAR_GAP = 8;
    public static final int PLAYER_INVENTORY_X = CONTENT_A_X + SLOT_ITEM_INSET;       // 22
    public static final int PLAYER_INVENTORY_Y = INVENTORY_GRID_Y + SLOT_ITEM_INSET;  // 162
    public static final int PLAYER_HOTBAR_Y = INVENTORY_GRID_Y + gridExtent(INVENTORY_ROWS)
            + HOTBAR_GAP + SLOT_ITEM_INSET;                                           // 234
    public static final int INVENTORY_CARD_H = (PLAYER_HOTBAR_Y + SLOT_CELL - SLOT_ITEM_INSET)
            + CARD_PAD - INVENTORY_CARD_Y;                                            // 120

    // ==================== 中栏：输入 / 冶炼 / 输出 / 并行数 ====================
    public static final int MID_CARD_X = COL_B_X;
    public static final int MID_CARD_Y = CONTENT_TOP;                                 // 40
    public static final int MID_CARD_W = COL_B_WIDTH;

    /** 输入区：9 个物品槽 3×3 + 9 个储罐 3×3。 */
    public static final int IN_CAPTION_Y = MID_CARD_Y + CAPTION_OFFSET;               // 47
    public static final int IO_GRID_COLUMNS = 3;
    public static final int IO_GRID_ROWS = 3;
    public static final int IN_GRID_Y = MID_CARD_Y + GRID_OFFSET;                     // 60
    public static final int INPUT_SLOTS_X = CONTENT_B_X + SLOT_ITEM_INSET;            // 242
    public static final int INPUT_SLOTS_Y = IN_GRID_Y + SLOT_ITEM_INSET;              // 62

    /** 储罐组：与物品格同尺寸，3×3，位于输入区右侧。 */
    public static final int TANK_COLUMNS = 3;
    public static final int TANK_ROWS = 3;
    public static final int TANK_COUNT_PER_SIDE = TANK_COLUMNS * TANK_ROWS;           // 9 = 6 流体 + 3 化学
    public static final int TANK_GROUP_DX = 76;                                       // 相对内容区左边
    public static final int TANK_GRID_X = CONTENT_B_X + TANK_GROUP_DX;                // 316
    public static final int TANK_INNER_SIZE = SLOT_CELL - 2 * SLOT_ITEM_INSET;        // 16

    public static final int IN_GRID_BOTTOM = IN_GRID_Y + gridExtent(IO_GRID_ROWS);    // 124
    public static final int MID_SEPARATOR_1_Y = IN_GRID_BOTTOM + 6;                   // 130

    /** 冶炼区：催化剂 / 模具 / 进度 / 查看配方。 */
    public static final int PROC_CAPTION_Y = MID_SEPARATOR_1_Y + 8;                   // 138
    public static final int PROC_SLOT_X = CONTENT_B_X;                                // 240
    public static final int PROC_MOLD_X = CONTENT_B_X + 28;                           // 268
    public static final int PROC_SLOT_Y = PROC_CAPTION_Y + 12;                        // 150
    public static final int CATALYST_SLOT_X = PROC_SLOT_X + SLOT_ITEM_INSET;          // 242
    public static final int CATALYST_SLOT_Y = PROC_SLOT_Y + SLOT_ITEM_INSET;          // 152
    public static final int MOLD_SLOT_X = PROC_MOLD_X + SLOT_ITEM_INSET;              // 270
    public static final int MOLD_SLOT_Y = PROC_SLOT_Y + SLOT_ITEM_INSET;              // 152
    public static final int PROC_PROGRESS_X = PROC_MOLD_X + 28;                        // 296
    public static final int PROC_PROGRESS_W = CONTENT_B_RIGHT - PROC_PROGRESS_X;       // 88
    public static final int PROC_PROGRESS_Y = PROC_CAPTION_Y;                         // 138
    public static final int PROC_PROGRESS_H = 45;
    public static final int PROC_BOTTOM = PROC_PROGRESS_Y + PROC_PROGRESS_H;          // 183
    public static final int MID_SEPARATOR_2_Y = PROC_BOTTOM + 5;                      // 188

    /** 输出区。 */
    public static final int OUT_CAPTION_Y = MID_SEPARATOR_2_Y + CAPTION_OFFSET;        // 195
    public static final int OUT_GRID_Y = MID_SEPARATOR_2_Y + GRID_OFFSET;             // 208
    public static final int OUTPUT_SLOTS_X = CONTENT_B_X + SLOT_ITEM_INSET;            // 242
    public static final int OUTPUT_SLOTS_Y = OUT_GRID_Y + SLOT_ITEM_INSET;             // 210
    public static final int OUT_GRID_BOTTOM = OUT_GRID_Y + gridExtent(IO_GRID_ROWS);   // 272
    public static final int MID_SEPARATOR_3_Y = OUT_GRID_BOTTOM + 6;                   // 278

    /** 并行数三行。 */
    public static final int STATS_Y = MID_SEPARATOR_3_Y + 8;                           // 286
    public static final int STAT_ROWS = 3;
    public static final int STAT_ROW_H = 17;
    public static final int MID_CARD_H = STATS_Y + STAT_ROWS * STAT_ROW_H + CARD_PAD - MID_CARD_Y; // 305

    // ==================== 右栏：面配置 / 开关 / 能量 / 取消 ====================
    public static final int FACE_CARD_X = COL_C_X;
    public static final int FACE_CARD_Y = CONTENT_TOP;                                 // 40
    public static final int FACE_CARD_W = COL_C_WIDTH;
    public static final int FACE_CAPTION_Y = FACE_CARD_Y + CAPTION_OFFSET;             // 47
    public static final int FACE_GRID_Y = FACE_CARD_Y + GRID_OFFSET;                   // 60
    public static final int FACE_CELL_W = 66;
    public static final int FACE_CELL_H = 30;
    public static final int FACE_CELL_STEP_X = 70;
    public static final int FACE_CELL_STEP_Y = 34;
    public static final int FACE_COUNT = 6;
    public static final int FACE_CARD_H = GRID_OFFSET + (FACE_COUNT / 2 - 1) * FACE_CELL_STEP_Y
            + FACE_CELL_H + CARD_PAD;                                                  // 126

    /** 开关分组列表：自动输出 / 自动输入 / 产物回 AE / 红石控制。 */
    public static final int SWITCH_LIST_X = CONTENT_C_X;
    public static final int SWITCH_LIST_Y = FACE_CARD_Y + FACE_CARD_H + COLUMN_GAP;     // 174
    public static final int SWITCH_LIST_W = CONTENT_C_RIGHT - CONTENT_C_X;              // 136
    public static final int SWITCH_ROW_H = 20;
    public static final int SWITCH_ROWS = 4;
    public static final int SWITCH_LIST_H = SWITCH_ROWS * SWITCH_ROW_H;                 // 80
    public static final int SWITCH_ICON_X = CONTENT_C_X + 4;                            // 412
    public static final int SWITCH_LABEL_X = CONTENT_C_X + 20;                          // 428
    public static final int SWITCH_W = 22;
    public static final int SWITCH_H = 12;
    public static final int SWITCH_X = CONTENT_C_RIGHT - 4 - SWITCH_W;                  // 518

    /** 能量卡。 */
    public static final int ENERGY_CARD_X = COL_C_X;
    public static final int ENERGY_CARD_Y = SWITCH_LIST_Y + SWITCH_LIST_H + COLUMN_GAP; // 262
    public static final int ENERGY_CARD_W = COL_C_WIDTH;
    public static final int ENERGY_CARD_H = 64;
    public static final int ENERGY_ROW_Y = ENERGY_CARD_Y + 5;                           // 267
    public static final int ENERGY_ROW_H = 51;
    public static final int ENERGY_BAR_Y = ENERGY_CARD_Y + 38;                           // 300
    public static final int ENERGY_BAR_H = 6;

    /** 取消 AE 合成任务。 */
    public static final int CANCEL_BUTTON_X = CONTENT_C_X;                               // 408
    public static final int CANCEL_BUTTON_Y = ENERGY_CARD_Y + ENERGY_CARD_H + COLUMN_GAP; // 334
    public static final int CANCEL_BUTTON_W = SWITCH_LIST_W;                             // 136
    public static final int CANCEL_BUTTON_H = 20;
    public static final int CANCEL_FOOT_Y = CANCEL_BUTTON_Y + CANCEL_BUTTON_H + 5;       // 359

    // ==================== 标题栏右侧 ====================
    public static final int HEADER_ROW_Y = 7;
    public static final int HEADER_BADGE_H = 14;
    public static final int REDSTONE_BUTTON_SIZE = 14;
    public static final int REDSTONE_BUTTON_X = WINDOW_WIDTH - WINDOW_PAD - REDSTONE_BUTTON_SIZE; // 538
    public static final int HEADER_CANVAS_X = 372;
    public static final int HEADER_CANVAS_W = CONTENT_C_RIGHT - HEADER_CANVAS_X;          // 172

    // ==================== 面模式下拉菜单 ====================
    public static final int FACE_MENU_W = 116;
    public static final int FACE_MENU_PAD = 4;
    public static final int FACE_MENU_HEADER_H = 14;
    public static final int FACE_MENU_ROW_H = 16;
    public static final int FACE_MENU_ROWS = 6;
    public static final int FACE_MENU_H = FACE_MENU_PAD + FACE_MENU_HEADER_H
            + FACE_MENU_ROWS * FACE_MENU_ROW_H + FACE_MENU_PAD;                           // 120

    // ==================== AE 任务弹窗 ====================
    public static final int AE_POP_X = 158;
    public static final int AE_POP_Y = 74;
    public static final int AE_POP_W = 238;
    public static final int AE_POP_PAD = 10;
    public static final int AE_HEADER_ICON = 20;
    public static final int AE_PROGRESS_Y = 38;
    public static final int AE_ROWS_Y = 50;
    public static final int AE_ROW_H = 26;
    public static final int AE_MAX_ROWS = 5;
    public static final int AE_FOOTER_H = 20;
    public static final int AE_CANCEL_W = 110;
    public static final int AE_DONE_W = 56;
    public static final int AE_CLOSE_SIZE = 14;

    private AdvancedAlloyFurnaceLayout() {
    }

    // ==================== 通用几何 ====================

    /** 若干格子在给定步长下占用的边长（首尾格含整个底格）。 */
    public static int gridExtent(int cells) {
        return cells * SLOT_PITCH - (SLOT_PITCH - SLOT_CELL);
    }

    // ==================== 槽位坐标（菜单与界面共用）====================

    /** 单个输入槽物品区左上角 X。 */
    public static int inputSlotX(int index) {
        return INPUT_SLOTS_X + (index % IO_GRID_COLUMNS) * SLOT_PITCH;
    }

    /** 单个输入槽物品区左上角 Y。 */
    public static int inputSlotY(int index) {
        return INPUT_SLOTS_Y + (index / IO_GRID_COLUMNS) * SLOT_PITCH;
    }

    /** 单个输出槽物品区左上角 X。 */
    public static int outputSlotX(int index) {
        return OUTPUT_SLOTS_X + (index % IO_GRID_COLUMNS) * SLOT_PITCH;
    }

    /** 单个输出槽物品区左上角 Y。 */
    public static int outputSlotY(int index) {
        return OUTPUT_SLOTS_Y + (index / IO_GRID_COLUMNS) * SLOT_PITCH;
    }

    /** 当页第 index 个样板槽物品区左上角 X（9 列 3 行）。 */
    public static int patternSlotX(int index) {
        return PATTERN_SLOTS_X + (index % PATTERN_GRID_COLUMNS) * SLOT_PITCH;
    }

    /** 当页第 index 个样板槽物品区左上角 Y。 */
    public static int patternSlotY(int index) {
        return PATTERN_SLOTS_Y + (index / PATTERN_GRID_COLUMNS) * SLOT_PITCH;
    }

    /** 玩家背包第 row 行第 column 列物品区左上角 X。 */
    public static int playerInventorySlotX(int column) {
        return PLAYER_INVENTORY_X + column * SLOT_PITCH;
    }

    /** 玩家背包第 row 行物品区左上角 Y（0..2）。 */
    public static int playerInventorySlotY(int row) {
        return PLAYER_INVENTORY_Y + row * SLOT_PITCH;
    }

    /** 快捷栏第 column 格物品区左上角 X。 */
    public static int hotbarSlotX(int column) {
        return playerInventorySlotX(column);
    }

    // ==================== 储罐坐标与命中 ====================

    /** 储罐底格左上角 X（按 3×3 阅读顺序）。 */
    public static int tankCellX(int index) {
        return TANK_GRID_X + (index % TANK_COLUMNS) * SLOT_PITCH;
    }

    /** 储罐底格左上角 Y；输入 / 输出共用同一 3×3 排布。 */
    public static int tankCellY(boolean output, int index) {
        int top = output ? OUT_GRID_Y : IN_GRID_Y;
        return top + (index / TANK_COLUMNS) * SLOT_PITCH;
    }

    /** 储罐内部流体区的左上角 X（底格 + 2）。 */
    public static int tankInnerX(int index) {
        return tankCellX(index) + SLOT_ITEM_INSET;
    }

    /** 储罐内部流体区的左上角 Y（底格 + 2）。 */
    public static int tankInnerY(boolean output, int index) {
        return tankCellY(output, index) + SLOT_ITEM_INSET;
    }

    /** 命中哪个储罐：0..8，未命中 -1。参数为窗口局部坐标。 */
    public static int tankIndexAt(double localX, double localY, boolean output) {
        for (int i = 0; i < TANK_COUNT_PER_SIDE; i++) {
            if (hits(localX, localY, tankCellX(i), tankCellY(output, i), SLOT_CELL, SLOT_CELL)) {
                return i;
            }
        }
        return -1;
    }

    // ==================== 面配置命中 ====================

    /** 第 face 个逻辑面（FurnaceFace ordinal）底格左上角 X。 */
    public static int faceCellX(int face) {
        return CONTENT_C_X + (face % 2) * FACE_CELL_STEP_X;
    }

    /** 第 face 个逻辑面底格左上角 Y。 */
    public static int faceCellY(int face) {
        return FACE_GRID_Y + (face / 2) * FACE_CELL_STEP_Y;
    }

    /** 命中哪个逻辑面：0..5，未命中 -1。参数为窗口局部坐标。 */
    public static int faceCellAt(double localX, double localY) {
        for (int face = 0; face < FACE_COUNT; face++) {
            if (hits(localX, localY, faceCellX(face), faceCellY(face), FACE_CELL_W, FACE_CELL_H)) {
                return face;
            }
        }
        return -1;
    }

    // ==================== 面模式下拉 ====================

    /** 下拉菜单左上角 X：与所属面底格左对齐，并夹在窗口内。 */
    public static int faceMenuX(int face) {
        int x = faceCellX(face);
        return Math.max(WINDOW_PAD, Math.min(x, WINDOW_WIDTH - WINDOW_PAD - FACE_MENU_W));
    }

    /** 下拉菜单左上角 Y：贴在所属面底格下方 2px。 */
    public static int faceMenuY(int face) {
        return faceCellY(face) + FACE_CELL_H + 2;
    }

    /** 下拉菜单内的行号：0..5，未命中 -1。参数为相对菜单左上角的 Y。 */
    public static int faceMenuRowAt(double localY) {
        int row = (int) Math.floor((localY - FACE_MENU_PAD - FACE_MENU_HEADER_H) / FACE_MENU_ROW_H);
        return row >= 0 && row < FACE_MENU_ROWS ? row : -1;
    }

    // ==================== AE 任务弹窗 ====================

    /** 弹窗高度：标题 + 进度 + rows 行任务 + 底部按钮。 */
    public static int aePopoverHeight(int rows) {
        int visible = Math.max(1, Math.min(rows, AE_MAX_ROWS));
        return AE_ROWS_Y + visible * AE_ROW_H + 6 + AE_FOOTER_H + AE_POP_PAD;
    }

    /** 弹窗内第几行任务：0..AE_MAX_ROWS-1，未命中 -1。参数为相对弹窗左上角的 Y。 */
    public static int aeRowAt(double localY) {
        int row = (int) Math.floor((localY - AE_ROWS_Y) / AE_ROW_H);
        return row >= 0 && row < AE_MAX_ROWS ? row : -1;
    }

    /** 任务行文案：进度 / 上限 + 百分比。 */
    public static String aeTaskProgressText(int progress, int max) {
        if (max <= 0) {
            return "0/0 (0%)";
        }
        int clamped = Math.max(0, Math.min(progress, max));
        return clamped + "/" + max + " (" + percent(clamped, max) + "%)";
    }

    // ==================== 数值 ====================

    /** 存储比例，钳在 0..1；容量无效时为 0。 */
    public static double ratio(long value, long max) {
        if (max <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) value / (double) max));
    }

    /** 百分比整数（四舍五入），钳在 0..100。 */
    public static int percent(long value, long max) {
        return (int) Math.round(ratio(value, max) * 100.0D);
    }

    private static boolean hits(double localX, double localY, int x, int y, int w, int h) {
        return localX >= x && localX < x + w && localY >= y && localY < y + h;
    }
}
