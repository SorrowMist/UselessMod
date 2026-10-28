package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.client.gui.apple.AppleStyle;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 01 界面的纯逻辑自检：面模式与 AE 任务状态的配色映射、能量大数字缩写。
 * 这些是界面里唯一需要「按状态挑颜色 / 文案」的判断，其余都是几何（见 AdvancedAlloyFurnaceLayoutTest）。
 */
class AdvancedAlloyFurnaceScreenTest {

    @Test
    void everyFaceModeKeepsItsOwnAccentColour() {
        assertEquals(AppleStyle.BLUE, AdvancedAlloyFurnaceScreen.faceAccent(FurnaceFaceMode.MATERIAL_INPUT));
        assertEquals(AppleStyle.GREEN, AdvancedAlloyFurnaceScreen.faceAccent(FurnaceFaceMode.MATERIAL_OUTPUT));
        assertEquals(AppleStyle.TEAL, AdvancedAlloyFurnaceScreen.faceAccent(FurnaceFaceMode.MATERIAL_INPUT_OUTPUT));
        assertEquals(AppleStyle.ORANGE, AdvancedAlloyFurnaceScreen.faceAccent(FurnaceFaceMode.CATALYST_INPUT));
        assertEquals(AppleStyle.PURPLE, AdvancedAlloyFurnaceScreen.faceAccent(FurnaceFaceMode.MOLD_INPUT));
        assertEquals(AppleStyle.TERTIARY, AdvancedAlloyFurnaceScreen.faceAccent(FurnaceFaceMode.DISABLED));
        assertEquals(AppleStyle.TERTIARY, AdvancedAlloyFurnaceScreen.faceAccent(null));

        Set<Integer> distinct = new HashSet<>();
        for (FurnaceFaceMode mode : FurnaceFaceMode.values()) {
            assertTrue(distinct.add(AdvancedAlloyFurnaceScreen.faceAccent(mode)), "每个面模式要能一眼区分：" + mode);
        }
        assertEquals(FurnaceFaceMode.values().length, distinct.size());
    }

    @Test
    void aeTaskStatusMapsToProcessingQueuedAndWaitingColours() {
        String prefix = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.";
        assertEquals(AppleStyle.GREEN, AdvancedAlloyFurnaceScreen.statusAccent(prefix + "processing"));
        assertEquals(AppleStyle.TERTIARY, AdvancedAlloyFurnaceScreen.statusAccent(prefix + "queued"));
        assertEquals(AppleStyle.RED, AdvancedAlloyFurnaceScreen.statusAccent(prefix + "waiting_energy"));
        assertEquals(AppleStyle.RED, AdvancedAlloyFurnaceScreen.statusAccent(prefix + "waiting_structure"));
        assertEquals(AppleStyle.RED, AdvancedAlloyFurnaceScreen.statusAccent(prefix + "waiting_recipe"));
        assertEquals(AppleStyle.TERTIARY, AdvancedAlloyFurnaceScreen.statusAccent(null));
    }

    /**
     * 每个菜单槽位必须且只能落在一个 Apple 槽位底格网格里：
     * 漏掉一个就会出现「槽位没底格」，重复则会出现「底格被画两次、悬停双重高亮」。
     */
    @Test
    void everyMenuSlotBelongsToExactlyOneSlotGrid() {
        int total = AdvancedAlloyFurnaceLayout.TOTAL_SLOTS + 36;
        for (int index = 0; index < total; index++) {
            Slot slot = mock(Slot.class);
            slot.index = index;
            int groups = 0;
            if (AdvancedAlloyFurnaceScreen.isInputSlot(slot)) groups++;
            if (AdvancedAlloyFurnaceScreen.isOutputSlot(slot)) groups++;
            if (AdvancedAlloyFurnaceScreen.isConfigSlot(slot)) groups++;
            if (AdvancedAlloyFurnaceScreen.isPatternSlot(slot)) groups++;
            if (AdvancedAlloyFurnaceScreen.isPlayerSlot(slot)) groups++;
            assertEquals(1, groups, "slot " + index + " must belong to exactly one grid");
        }
        // 9 输入 + 9 输出 + 催化剂 + 模具 + 108 样板 + 36 玩家
        assertEquals(164, total);
    }

    @Test
    void switchLabelsKeepPositiveWidthRegardlessOfWindowPosition() {
        int width = AdvancedAlloyFurnaceScreen.switchLabelWidth();
        assertEquals(AdvancedAlloyFurnaceViewLayout.SWITCH_X
                - AdvancedAlloyFurnaceViewLayout.SWITCH_LABEL_X - 4, width);
        assertTrue(width >= 70, "all three Chinese switch labels must fit before the toggle");
        for (int left : new int[]{0, 40, 118, 400}) {
            int labelScreenX = left + AdvancedAlloyFurnaceViewLayout.SWITCH_LABEL_X;
            int toggleScreenX = left + AdvancedAlloyFurnaceViewLayout.SWITCH_X;
            assertEquals(toggleScreenX - labelScreenX - 4, width,
                    "centering the window must not clip its labels to an empty string");
        }
    }

    @Test
    void autoIoTooltipsAreBoundedToTheirTwoRows() {
        assertEquals(0, AdvancedAlloyFurnaceViewLayout.autoIoTooltipRowAt(
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_X, AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_Y));
        assertEquals(0, AdvancedAlloyFurnaceViewLayout.autoIoTooltipRowAt(
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_X + AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_W - 1,
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_Y + AdvancedAlloyFurnaceViewLayout.SWITCH_ROW_H - 0.1));
        assertEquals(1, AdvancedAlloyFurnaceViewLayout.autoIoTooltipRowAt(
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_X + 40,
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_Y + AdvancedAlloyFurnaceViewLayout.SWITCH_ROW_H));
        assertEquals(-1, AdvancedAlloyFurnaceViewLayout.autoIoTooltipRowAt(
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_X - 1, AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_Y));
        assertEquals(-1, AdvancedAlloyFurnaceViewLayout.autoIoTooltipRowAt(
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_X + AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_W,
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_Y));
        assertEquals(-1, AdvancedAlloyFurnaceViewLayout.autoIoTooltipRowAt(
                AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_X, AdvancedAlloyFurnaceViewLayout.SWITCH_LIST_Y
                        + 2 * AdvancedAlloyFurnaceViewLayout.SWITCH_ROW_H));
    }

    @Test
    void parallelInfoTooltipTakesPriorityOverTheAlloyingTooltip() {
        int infoX = AdvancedAlloyFurnaceViewLayout.PROC_PROGRESS_X
                + AdvancedAlloyFurnaceViewLayout.PROC_PROGRESS_W - 12;
        int infoY = AdvancedAlloyFurnaceViewLayout.PROC_PROGRESS_Y - 1;
        assertTrue(AdvancedAlloyFurnaceViewLayout.parallelInfoTooltipAt(infoX + 1, infoY + 1));
        assertFalse(AdvancedAlloyFurnaceViewLayout.progressTooltipAt(infoX + 1, infoY + 1));
        assertFalse(AdvancedAlloyFurnaceViewLayout.parallelInfoTooltipAt(infoX - 1, infoY + 1));
        assertTrue(AdvancedAlloyFurnaceViewLayout.progressTooltipAt(infoX - 1, infoY + 1));
        assertTrue(AdvancedAlloyFurnaceViewLayout.progressTooltipAt(
                AdvancedAlloyFurnaceViewLayout.PROC_PROGRESS_X,
                AdvancedAlloyFurnaceViewLayout.PROC_PROGRESS_Y + 12));
    }

    @Test
    void energyHeadlineUsesCompactUnitsWithoutTrailingZeroes() {
        assertEquals("0", AdvancedAlloyFurnaceScreen.compact(0));
        assertEquals("999", AdvancedAlloyFurnaceScreen.compact(999));
        assertEquals("1K", AdvancedAlloyFurnaceScreen.compact(1_000));
        assertEquals("7.2M", AdvancedAlloyFurnaceScreen.compact(7_200_000L));
        assertEquals("10M", AdvancedAlloyFurnaceScreen.compact(10_000_000L));
        assertEquals("1.5G", AdvancedAlloyFurnaceScreen.compact(1_500_000_000L));
        assertEquals("2.5T", AdvancedAlloyFurnaceScreen.compact(2_500_000_000_000L));
    }
}
