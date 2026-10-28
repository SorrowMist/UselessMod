package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import org.junit.jupiter.api.Test;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 设计稿 01 的半尺寸几何自检：三栏、槽位、储罐、面配置、弹层全部落在窗口内且互不重叠。
 */
class AdvancedAlloyFurnaceLayoutTest {

    @Test
    void windowIsTheHalfDesignWidthAndTheThreeColumnsFillIt() {
        assertEquals(564, WINDOW_WIDTH);
        assertEquals(12, WINDOW_PAD);
        assertEquals(28, TITLE_BAR);
        assertEquals(40, CONTENT_TOP);

        assertEquals(WINDOW_PAD, COL_A_X);
        assertEquals(212, COL_A_WIDTH);
        assertEquals(COL_A_X + COL_A_WIDTH + COLUMN_GAP, COL_B_X);
        assertEquals(160, COL_B_WIDTH);
        assertEquals(COL_B_X + COL_B_WIDTH + COLUMN_GAP, COL_C_X);
        assertEquals(WINDOW_WIDTH - WINDOW_PAD, COL_C_X + COL_C_WIDTH);

        // 卡片内容区 = 卡片内缩 8
        assertEquals(COL_A_X + CARD_PAD, CONTENT_A_X);
        assertEquals(COL_A_X + COL_A_WIDTH - CARD_PAD, CONTENT_A_RIGHT);
        assertEquals(COL_B_X + CARD_PAD, CONTENT_B_X);
        assertEquals(COL_B_X + COL_B_WIDTH - CARD_PAD, CONTENT_B_RIGHT);
        assertEquals(COL_C_X + CARD_PAD, CONTENT_C_X);
        assertEquals(COL_C_X + COL_C_WIDTH - CARD_PAD, CONTENT_C_RIGHT);

        // 9 列槽位正好铺满左栏内容区
        assertEquals(CONTENT_A_RIGHT - CONTENT_A_X, gridExtent(PATTERN_GRID_COLUMNS));
    }

    @Test
    void slotCellsAreTwentyWithTwoPixelGapsAndTheVanillaItemRegionIsInsetByTwo() {
        assertEquals(20, SLOT_CELL);
        assertEquals(22, SLOT_PITCH);
        assertEquals(2, SLOT_ITEM_INSET);
        assertEquals(PATTERN_GRID_Y + SLOT_ITEM_INSET, PATTERN_SLOTS_Y);
        assertEquals(INPUT_SLOTS_X, CONTENT_B_X + SLOT_ITEM_INSET);

        for (int i = 1; i < PATTERN_GRID_COLUMNS; i++) {
            assertEquals(SLOT_PITCH, patternSlotX(i) - patternSlotX(i - 1));
        }
        for (int i = 1; i < IO_GRID_COLUMNS; i++) {
            assertEquals(SLOT_PITCH, inputSlotX(i) - inputSlotX(i - 1));
        }
        assertEquals(SLOT_PITCH, inputSlotY(IO_GRID_COLUMNS) - inputSlotY(0));
        assertEquals(SLOT_PITCH, outputSlotY(IO_GRID_COLUMNS) - outputSlotY(0));
    }

    @Test
    void patternGridIsNineByThreeAndStaysInsideItsCard() {
        assertEquals(9, PATTERN_GRID_COLUMNS);
        assertEquals(3, PATTERN_GRID_ROWS);
        assertEquals(27, PATTERN_SLOTS_PER_PAGE);

        int cardBottom = PATTERN_CARD_Y + PATTERN_CARD_H;
        for (int i = 0; i < PATTERN_SLOTS_PER_PAGE; i++) {
            int x = patternSlotX(i), y = patternSlotY(i);
            assertTrue(x >= CONTENT_A_X, "pattern slot column inside content");
            assertTrue(x + 16 <= CONTENT_A_RIGHT, "pattern slot column inside content");
            assertTrue(y >= PATTERN_SLOTS_Y);
            assertTrue(y + 16 < cardBottom, "pattern slot row inside card");
        }
        // 最后一行底格 + 内边距 == 卡片底边
        assertEquals(cardBottom, patternSlotY(PATTERN_SLOTS_PER_PAGE - 1) + 16 + SLOT_ITEM_INSET + CARD_PAD);
        assertTrue(PATTERN_CARD_Y + PATTERN_CARD_H < INVENTORY_CARD_Y);
    }

    @Test
    void playerInventoryKeepsNineByThreePlusHotbarAtPitchTwentyTwo() {
        assertEquals(9, INVENTORY_COLUMNS);
        assertEquals(3, INVENTORY_ROWS);
        int cardBottom = INVENTORY_CARD_Y + INVENTORY_CARD_H;

        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                int x = playerInventorySlotX(column);
                int y = playerInventorySlotY(row);
                assertTrue(x >= CONTENT_A_X && x + 16 <= CONTENT_A_RIGHT);
                assertTrue(y > INVENTORY_CARD_Y);
                assertEquals(x, hotbarSlotX(column));
            }
            assertEquals(SLOT_PITCH, playerInventorySlotY(row + 1) - playerInventorySlotY(row));
        }
        // 背包最后一行与快捷栏之间留出 8px 空隙，快捷栏仍在卡片内
        int lastRowBottom = playerInventorySlotY(INVENTORY_ROWS - 1) + 16 + SLOT_ITEM_INSET;
        assertEquals(HOTBAR_GAP, PLAYER_HOTBAR_Y - SLOT_ITEM_INSET - lastRowBottom);
        assertTrue(PLAYER_HOTBAR_Y + 16 < cardBottom);
        assertEquals(cardBottom - CARD_PAD, PLAYER_HOTBAR_Y + 16 + SLOT_ITEM_INSET);
    }

    @Test
    void middleColumnStacksInputProcessOutputAndParallelStats() {
        assertEquals(9, INPUT_SLOTS_COUNT);
        assertEquals(9, OUTPUT_SLOTS_COUNT);

        // 输入 / 输出 3x3 都在中栏内容区里
        for (int i = 0; i < INPUT_SLOTS_COUNT; i++) {
            assertTrue(inputSlotX(i) >= CONTENT_B_X && inputSlotX(i) + 16 <= CONTENT_B_RIGHT);
            assertTrue(outputSlotX(i) >= CONTENT_B_X && outputSlotX(i) + 16 <= CONTENT_B_RIGHT);
        }
        // 物品格与储罐组不重叠，且整组落在内容区
        int slotGridRight = INPUT_SLOTS_X + gridExtent(IO_GRID_COLUMNS);
        assertTrue(slotGridRight <= TANK_GRID_X);
        assertEquals(TANK_GRID_X + gridExtent(TANK_COLUMNS), CONTENT_B_RIGHT - 4);

        // 冶炼区：催化剂 / 模具 / 进度条 依次排开
        assertTrue(CATALYST_SLOT_X + 16 < MOLD_SLOT_X);
        assertTrue(MOLD_SLOT_X + 16 <= PROC_PROGRESS_X);
        assertEquals(PROC_PROGRESS_X + PROC_PROGRESS_W, CONTENT_B_RIGHT);

        // 纵向顺序：输入 → 分隔线 → 冶炼 → 分隔线 → 输出 → 分隔线 → 并行数
        assertTrue(IN_GRID_BOTTOM < MID_SEPARATOR_1_Y);
        assertTrue(MID_SEPARATOR_1_Y < PROC_CAPTION_Y);
        assertTrue(PROC_SLOT_Y + SLOT_CELL <= PROC_BOTTOM);
        assertTrue(PROC_BOTTOM < MID_SEPARATOR_2_Y);
        assertTrue(MID_SEPARATOR_2_Y < OUT_CAPTION_Y);
        assertTrue(OUT_GRID_BOTTOM < MID_SEPARATOR_3_Y);
        assertTrue(MID_SEPARATOR_3_Y < STATS_Y);
        assertTrue(STATS_Y + STAT_ROWS * STAT_ROW_H <= MID_CARD_Y + MID_CARD_H);
        assertTrue(MID_CARD_Y + MID_CARD_H <= WINDOW_HEIGHT - WINDOW_PAD);
    }

    @Test
    void everyTankSitsInAThreeByThreeGridAndMatchesTheRealTankCounts() {
        assertEquals(3, TANK_COLUMNS);
        assertEquals(3, TANK_ROWS);
        assertEquals(FLUID_TANK_COUNT + CHEMICAL_TANK_COUNT, TANK_COUNT_PER_SIDE);
        assertEquals(9, TANK_COUNT_PER_SIDE);

        for (int i = 0; i < TANK_COUNT_PER_SIDE; i++) {
            int cellX = tankCellX(i), cellY = tankCellY(false, i);
            assertTrue(cellX >= TANK_GRID_X && cellX + SLOT_CELL <= CONTENT_B_RIGHT);
            assertEquals(tankCellX(i), tankCellX(i % TANK_COLUMNS));
            if (i >= TANK_COLUMNS) {
                assertEquals(SLOT_PITCH, cellY - tankCellY(false, i - TANK_COLUMNS));
            }
            // 流体区居中在底格内
            assertEquals(cellX + SLOT_ITEM_INSET, tankInnerX(i));
            assertEquals(cellY + SLOT_ITEM_INSET, tankInnerY(false, i));
            assertEquals(SLOT_CELL - 2 * SLOT_ITEM_INSET, TANK_INNER_SIZE);

            // 命中中心返回自身，输入 / 输出各自独立
            assertEquals(i, tankIndexAt(cellX + 10, cellY + 10, false));
            assertEquals(i, tankIndexAt(tankCellX(i) + 10, tankCellY(true, i) + 10, true));
            assertEquals(cellY - IN_GRID_Y, tankCellY(true, i) - OUT_GRID_Y);
        }
        assertEquals(-1, tankIndexAt(TANK_GRID_X - 1, IN_GRID_Y, false));
        assertEquals(-1, tankIndexAt(TANK_GRID_X, IN_GRID_Y - 1, false));
        assertEquals(-1, tankIndexAt(CONTENT_B_RIGHT, IN_GRID_Y, false));
        // 储罐组与输出区互不干扰
        assertEquals(-1, tankIndexAt(tankCellX(0) + 10, tankCellY(true, 0) + 10, false));
    }

    @Test
    void sixFaceCellsAreLaidOutInTwoColumnsAndMatchFurnaceFaceOrder() {
        assertEquals(6, FACE_COUNT);
        assertEquals(FurnaceFace.COUNT, FACE_COUNT);

        for (int face = 0; face < FACE_COUNT; face++) {
            int x = faceCellX(face), y = faceCellY(face);
            assertTrue(x >= CONTENT_C_X && x + FACE_CELL_W <= CONTENT_C_RIGHT);
            assertTrue(y >= FACE_GRID_Y && y + FACE_CELL_H <= FACE_CARD_Y + FACE_CARD_H);
            assertEquals(face % 2 == 0 ? CONTENT_C_X : CONTENT_C_X + FACE_CELL_STEP_X, x);
            // 命中自身中心
            assertEquals(face, faceCellAt(x + FACE_CELL_W / 2.0, y + FACE_CELL_H / 2.0));
            // 两列之间的 4px 缝隙不属于任何面
            assertEquals(-1, faceCellAt(x + FACE_CELL_W + 1, y + FACE_CELL_H / 2.0));
        }
        assertEquals(-1, faceCellAt(CONTENT_C_X - 1, FACE_GRID_Y));
        assertEquals(-1, faceCellAt(CONTENT_C_X, FACE_CARD_Y + FACE_CARD_H));
        // 设计稿顺序：顶面/左面 → 前面/右面 → 底面/后面
        assertEquals(FurnaceFace.FRONT.ordinal(), 2);
        assertEquals(FurnaceFace.BACK.ordinal(), 5);
    }

    @Test
    void faceDropdownOpenUnderTheFaceStaysInsideTheWindowAndMapsSixRows() {
        assertEquals(6, FACE_MENU_ROWS);
        for (int face = 0; face < FACE_COUNT; face++) {
            int x = faceMenuX(face), y = faceMenuY(face);
            assertTrue(x >= WINDOW_PAD);
            assertTrue(x + FACE_MENU_W <= WINDOW_WIDTH - WINDOW_PAD);
            assertTrue(y + FACE_MENU_H <= WINDOW_HEIGHT);
            assertEquals(faceCellY(face) + FACE_CELL_H + 2, y);
        }
        // 行命中：表头不选中任何行
        assertEquals(-1, faceMenuRowAt(FACE_MENU_PAD + 2));
        for (int row = 0; row < FACE_MENU_ROWS; row++) {
            double rowTop = FACE_MENU_PAD + FACE_MENU_HEADER_H + row * FACE_MENU_ROW_H;
            assertEquals(row, faceMenuRowAt(rowTop));
            assertEquals(row, faceMenuRowAt(rowTop + FACE_MENU_ROW_H - 0.01));
        }
        assertEquals(-1, faceMenuRowAt(FACE_MENU_H - FACE_MENU_PAD + 1));
    }

    @Test
    void rightColumnStacksFacesSwitchesEnergyAndCancelInsideTheWindow() {
        assertEquals(FACE_CARD_Y + FACE_CARD_H + COLUMN_GAP, SWITCH_LIST_Y);
        assertEquals(SWITCH_LIST_Y + SWITCH_LIST_H + COLUMN_GAP, ENERGY_CARD_Y);
        assertEquals(ENERGY_CARD_Y + ENERGY_CARD_H + COLUMN_GAP, CANCEL_BUTTON_Y);
        assertTrue(CANCEL_BUTTON_Y + CANCEL_BUTTON_H < CANCEL_FOOT_Y);
        assertTrue(CANCEL_FOOT_Y + 9 <= WINDOW_HEIGHT - WINDOW_PAD);
        assertEquals(CONTENT_C_RIGHT, SWITCH_LIST_X + SWITCH_LIST_W);
        assertEquals(CONTENT_C_X, CANCEL_BUTTON_X);
        assertEquals(SWITCH_LIST_W, CANCEL_BUTTON_W);
        // 开关在行内、行内留有标签宽度
        assertTrue(SWITCH_X > SWITCH_LABEL_X);
        assertEquals(CONTENT_C_RIGHT - 4, SWITCH_X + SWITCH_W);
        assertTrue(SWITCH_H <= SWITCH_ROW_H);
        // 能量条在能量卡内
        assertTrue(ENERGY_ROW_Y >= ENERGY_CARD_Y);
        assertTrue(ENERGY_ROW_Y + ENERGY_ROW_H <= ENERGY_CARD_Y + ENERGY_CARD_H);
        assertTrue(ENERGY_BAR_Y >= ENERGY_ROW_Y && ENERGY_BAR_Y + ENERGY_BAR_H <= ENERGY_ROW_Y + ENERGY_ROW_H);
    }

    @Test
    void headerControlsSitInsideTheTitleBar() {
        assertTrue(REDSTONE_BUTTON_X + REDSTONE_BUTTON_SIZE <= WINDOW_WIDTH - WINDOW_PAD);
        assertTrue(REDSTONE_BUTTON_SIZE <= HEADER_BADGE_H);
        assertTrue(HEADER_ROW_Y + HEADER_BADGE_H <= TITLE_BAR);
        assertEquals(CONTENT_C_RIGHT, HEADER_CANVAS_X + HEADER_CANVAS_W);
    }

    @Test
    void aeTaskPopoverGrowsByRowAndStaysInsideTheWindow() {
        assertEquals(aePopoverHeight(1), aePopoverHeight(0));
        assertTrue(aePopoverHeight(3) > aePopoverHeight(2));
        assertTrue(aePopoverHeight(3) > aePopoverHeight(1));
        assertEquals(aePopoverHeight(3) + AE_ROW_H, aePopoverHeight(4));
        assertEquals(aePopoverHeight(AE_MAX_ROWS), aePopoverHeight(99));
        assertTrue(AE_POP_Y + aePopoverHeight(AE_MAX_ROWS) <= WINDOW_HEIGHT);
        assertTrue(AE_POP_X + AE_POP_W <= WINDOW_WIDTH - WINDOW_PAD);

        // 行命中：标题与进度区不属于任何任务行
        assertEquals(-1, aeRowAt(AE_ROWS_Y - 1));
        for (int row = 0; row < AE_MAX_ROWS; row++) {
            double top = AE_ROWS_Y + row * AE_ROW_H;
            assertEquals(row, aeRowAt(top));
            assertEquals(row, aeRowAt(top + AE_ROW_H - 0.01));
        }
        assertEquals(-1, aeRowAt(AE_ROWS_Y + AE_MAX_ROWS * AE_ROW_H));
    }

    @Test
    void taskProgressTextAndPercentagesRoundLikeTheTooltips() {
        assertEquals("10/16 (63%)", aeTaskProgressText(10, 16));
        assertEquals("0/8 (0%)", aeTaskProgressText(0, 8));
        assertEquals("16/16 (100%)", aeTaskProgressText(16, 16));
        assertEquals("16/16 (100%)", aeTaskProgressText(99, 16));
        assertEquals("0/0 (0%)", aeTaskProgressText(3, 0));
        assertEquals(30, percent(12, 40));
        assertEquals(0, percent(0, 0));
        assertEquals(100, percent(40, 40));
        assertEquals(0.0D, ratio(5, 0));
        assertEquals(0.5D, ratio(5, 10), 1.0E-9D);
        assertEquals(72, percent(7_200_000L, 10_000_000L));
    }
}
