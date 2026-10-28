package com.sorrowmist.useless.client.gui;

import appeng.api.stacks.GenericStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.network.AECancelPacket;
import com.sorrowmist.useless.network.AEReturnOutputTogglePacket;
import com.sorrowmist.useless.network.AutoIOChangePacket;
import com.sorrowmist.useless.network.FaceModeChangePacket;
import com.sorrowmist.useless.network.PatternPageChangePacket;
import com.sorrowmist.useless.network.RedstoneControlPacket;
import com.sorrowmist.useless.network.TankClearPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.sorrowmist.useless.client.gui.AdvancedAlloyFurnaceViewLayout.*;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_END;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.TOTAL_SLOTS;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CHEMICAL_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CHEMICAL_TANK_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.aePopoverHeight;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.aeRowAt;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.aeTaskProgressText;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.percent;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.ratio;

/**
 * 万象合金炉界面（设计稿 01 的半尺寸复刻）。
 *
 * <p>三栏结构：左 212 = 样板卡（9×3）+ 物品栏；中 160 = 输入（9 槽 + 9 储罐）/ 冶炼 / 输出 / 并行数；
 * 右 152 = 六面配置 + 四个开关行 + 能量 + 取消 AE。所有槽位坐标、储罐与按钮热区都取自
 * {@link com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout}，
 * 与 {@code AdvancedAlloyFurnaceMenu} 共用同一套几何。</p>
 *
 * <p>交互全部沿用原有逻辑与网络包：面配置下拉 → 逐个 next/previous 包；AE 任务弹窗读同步数据；
 * 进度区点击 → JEI 配方；储罐点击 → 清空；能量 / 储罐 / 面 / 开关均有原 tooltip。</p>
 */
public class AdvancedAlloyFurnaceScreen extends AppleContainerScreen<AdvancedAlloyFurnaceMenu> {

    private static final int W = WINDOW_WIDTH;
    private static final int H = WINDOW_HEIGHT;

    /** 打开下拉菜单的逻辑面序号，-1 表示未打开。 */
    private int openFaceMenuIndex = -1;

    public AdvancedAlloyFurnaceScreen(AdvancedAlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        // 标题由 AppleWindow 绘制，原版标签全部关闭
        this.titleLabelX = 0;
        this.titleLabelY = 0;
        this.inventoryLabelX = 0;
        this.inventoryLabelY = 0;
    }

    @Override
    protected void initControls() {
        for (Slot slot : menu.slots) {
            int i = slot.index;
            int x, y;
            if (isInputSlot(slot)) { x = inputSlotX(i); y = inputSlotY(i); }
            else if (isOutputSlot(slot)) { x = outputSlotX(i - OUTPUT_SLOTS_START); y = outputSlotY(i - OUTPUT_SLOTS_START); }
            else if (isPatternSlot(slot)) {
                int cell = (i - PATTERN_SLOTS_START) % PATTERN_SLOTS_PER_PAGE;
                x = patternSlotX(cell); y = patternSlotY(cell);
            } else if (i == CATALYST_SLOT || i == MOLD_SLOT) {
                x = (i == CATALYST_SLOT ? CONTENT_B_X : PROC_MOLD_X) + 2; y = PROC_SLOT_Y + 2;
            } else {
                int cell = i - TOTAL_SLOTS;
                x = playerInventorySlotX(cell % 9);
                y = cell < 27 ? playerInventorySlotY(cell / 9) : PLAYER_HOTBAR_Y;
            }
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(slot, x, y);
        }
        updatePatternSlotVisibility();
    }

    // ==================== Apple 控件树 ====================

    @Override
    protected void buildApple(AppleHost h) {
        AppleWindow window = new AppleWindow(W, H, () -> this.title);
        window.icon(AppleUi.Icon.FLAME, AppleStyle.ORANGE);
        h.add(window);

        // 标题栏右侧：AE 任务徽标 / 运行状态 / 红石模式（含各自 tooltip）
        h.add(new AppleCanvas(HEADER_CANVAS_X, HEADER_ROW_Y - 1, HEADER_CANVAS_W, HEADER_BADGE_H + 2,
                (g, mx, my, x, y, cw, ch) -> paintHeader(g, mx, my))
                .onClick((mx, my, b) -> handleHeaderClick(mx, my, b)).id("header"));

        buildPatternColumn(h);
        buildProcessorColumn(h);
        buildConfigColumn(h);
    }

    /** 左栏：样板 9×3 + 玩家物品栏 9×3 + 快捷栏。 */
    private void buildPatternColumn(AppleHost h) {
        h.add(new AppleCard(PATTERN_CARD_X, PATTERN_CARD_Y, PATTERN_CARD_W, PATTERN_CARD_H));
        h.add(new AppleLabel(CONTENT_A_X, PATTERN_CAPTION_Y, 100, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.patterns")));
        h.add(new ApplePager(PATTERN_PAGER_X, PATTERN_PAGER_Y, PATTERN_PAGER_W, PATTERN_PAGER_H,
                () -> Component.literal((this.menu.getPatternPage() + 1) + "/" + (this.menu.getMaxPatternPage() + 1)),
                () -> this.menu.getPatternPage() > 0, () -> changePatternPage(-1),
                () -> this.menu.getPatternPage() < this.menu.getMaxPatternPage(), () -> changePatternPage(1))
                .id("pattern_pager"));
        h.add(slotGrid(AdvancedAlloyFurnaceScreen::isPatternSlot).id("pattern_slots"));

        h.add(new AppleCard(INVENTORY_CARD_X, INVENTORY_CARD_Y, INVENTORY_CARD_W, INVENTORY_CARD_H));
        h.add(new AppleLabel(CONTENT_A_X, INVENTORY_CAPTION_Y, 160, AppleLabel.Kind.CAPTION,
                () -> this.playerInventoryTitle));
        h.add(slotGrid(AdvancedAlloyFurnaceScreen::isPlayerSlot).id("player_slots"));
    }

    /** 中栏：一张长卡，输入 → 冶炼 → 输出 → 并行数。 */
    private void buildProcessorColumn(AppleHost h) {
        h.add(new AppleCard(MID_CARD_X, MID_CARD_Y, MID_CARD_W, MID_CARD_H));

        // 输入
        h.add(new AppleLabel(CONTENT_B_X, IN_CAPTION_Y, 60, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.in")));
        h.add(new AppleLabel(CONTENT_B_X, IN_CAPTION_Y, CONTENT_B_RIGHT - CONTENT_B_X, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable("gui.useless_mod.apple.slots_tanks", 9, TANK_COUNT_PER_SIDE)).right());
        h.add(slotGrid(AdvancedAlloyFurnaceScreen::isInputSlot).id("input_slots"));
        h.add(tankCanvas(false).id("tanks_in"));

        h.add(separatorCanvas(MID_SEPARATOR_1_Y));

        // 冶炼：催化剂 / 模具 / 进度 + 查看配方
        h.add(new AppleLabel(CONTENT_B_X, PROC_CAPTION_Y, 28, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.catalyst")));
        h.add(new AppleLabel(PROC_MOLD_X, PROC_CAPTION_Y, 28, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.mold")));
        h.add(slotGrid(AdvancedAlloyFurnaceScreen::isConfigSlot).id("config_slots"));
        h.add(new AppleCanvas(PROC_PROGRESS_X, PROC_PROGRESS_Y, PROC_PROGRESS_W, PROC_PROGRESS_H,
                (g, mx, my, x, y, cw, ch) -> paintProgressBlock(g, mx, my, x, y, cw, ch))
                .onClick((mx, my, b) -> openRecipeCategory()).id("progress"));

        h.add(separatorCanvas(MID_SEPARATOR_2_Y));

        // 输出
        h.add(new AppleLabel(CONTENT_B_X, OUT_CAPTION_Y, 60, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.layout.output")));
        h.add(new AppleLabel(CONTENT_B_X, OUT_CAPTION_Y, CONTENT_B_RIGHT - CONTENT_B_X, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable("gui.useless_mod.apple.slots_tanks", 9, TANK_COUNT_PER_SIDE)).right());
        h.add(slotGrid(AdvancedAlloyFurnaceScreen::isOutputSlot).id("output_slots"));
        h.add(tankCanvas(true).id("tanks_out"));

        h.add(separatorCanvas(MID_SEPARATOR_3_Y));

        // 并行数三行
        h.add(new AppleCanvas(CONTENT_B_X, STATS_Y, CONTENT_B_RIGHT - CONTENT_B_X, STAT_ROWS * STAT_ROW_H,
                (g, mx, my, x, y, cw, ch) -> paintStatRules(g, x, y, cw, ch)).id("stat_rules"));
        h.add(new AppleLabel(CONTENT_B_X, STATS_Y + 4, CONTENT_B_RIGHT - CONTENT_B_X, AppleLabel.Kind.BODY,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.stat.current_parallel")));
        h.add(new AppleLabel(CONTENT_B_X, STATS_Y + 4, CONTENT_B_RIGHT - CONTENT_B_X, AppleLabel.Kind.HEADLINE,
                () -> Component.literal(String.valueOf(this.menu.getCurrentParallel())))
                .color(AppleStyle.ORANGE_TEXT).right());
        h.add(new AppleLabel(CONTENT_B_X, STATS_Y + STAT_ROW_H + 4, CONTENT_B_RIGHT - CONTENT_B_X,
                AppleLabel.Kind.BODY,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.stat.max_parallel")));
        h.add(new AppleLabel(CONTENT_B_X, STATS_Y + STAT_ROW_H + 4, CONTENT_B_RIGHT - CONTENT_B_X,
                AppleLabel.Kind.HEADLINE,
                () -> Component.literal(String.valueOf(this.menu.getCatalystMaxParallel())))
                .color(AppleStyle.ORANGE_TEXT).right());
        h.add(new AppleLabel(CONTENT_B_X, STATS_Y + 2 * STAT_ROW_H + 4, CONTENT_B_RIGHT - CONTENT_B_X,
                AppleLabel.Kind.BODY,
                () -> Component.translatable("gui.useless_mod.apple.catalyst")));
        h.add(new AppleLabel(CONTENT_B_X, STATS_Y + 2 * STAT_ROW_H + 4, CONTENT_B_RIGHT - CONTENT_B_X,
                AppleLabel.Kind.HEADLINE, this::catalystSummary).color(AppleStyle.PURPLE).right());
    }

    /** 右栏：六面配置 → 四个开关行 → 能量 → 取消 AE。 */
    private void buildConfigColumn(AppleHost h) {
        h.add(new AppleCard(FACE_CARD_X, FACE_CARD_Y, FACE_CARD_W, FACE_CARD_H));
        h.add(new AppleLabel(CONTENT_C_X, FACE_CAPTION_Y, 100, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.sides")));
        h.add(new AppleCanvas(CONTENT_C_X, FACE_GRID_Y, CONTENT_C_RIGHT - CONTENT_C_X,
                (FACE_COUNT / 2 - 1) * FACE_CELL_STEP_Y + FACE_CELL_H,
                (g, mx, my, x, y, cw, ch) -> paintFaces(g, mx, my, x, y, cw, ch))
                .onClick((mx, my, b) -> handleFaceClick(mx, my, b)).id("faces"));

        h.add(new AppleCanvas(SWITCH_LIST_X, SWITCH_LIST_Y, SWITCH_LIST_W, SWITCH_LIST_H,
                (g, mx, my, x, y, cw, ch) -> paintSwitches(g, mx, my, x, y, cw, ch)).id("switch_list"));
        h.add(new AppleSwitch(SWITCH_X, SWITCH_LIST_Y + 4, SWITCH_W, SWITCH_H,
                this::isAutoOutput, this::toggleAutoOutput).id("switch_auto_output"));
        h.add(new AppleSwitch(SWITCH_X, SWITCH_LIST_Y + SWITCH_ROW_H + 4, SWITCH_W, SWITCH_H,
                this::isAutoInput, this::toggleAutoInput).id("switch_auto_input"));
        h.add(new AppleSwitch(SWITCH_X, SWITCH_LIST_Y + 2 * SWITCH_ROW_H + 4, SWITCH_W, SWITCH_H,
                this::isReturnToAe, this::toggleReturnToAe).id("switch_ae_return"));
        h.add(new AppleCanvas(SWITCH_LABEL_X, SWITCH_LIST_Y + 3 * SWITCH_ROW_H,
                CONTENT_C_RIGHT - SWITCH_LABEL_X, SWITCH_ROW_H,
                (g, mx, my, x, y, cw, ch) -> { })
                .onClick((mx, my, b) -> cycleRedstoneControl()).id("redstone_row"));

        h.add(new AppleCard(ENERGY_CARD_X, ENERGY_CARD_Y, ENERGY_CARD_W, ENERGY_CARD_H));
        h.add(new AppleCanvas(CONTENT_C_X, ENERGY_ROW_Y, CONTENT_C_RIGHT - CONTENT_C_X, ENERGY_ROW_H,
                (g, mx, my, x, y, cw, ch) -> paintEnergy(g, x, y, cw)).id("energy"));

        h.add(new ApplePushButton(CANCEL_BUTTON_X, CANCEL_BUTTON_Y, CANCEL_BUTTON_W, CANCEL_BUTTON_H,
                ApplePushButton.Kind.DANGER,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks"),
                this::cancelAeTasks).icon(AppleUi.Icon.XMARK).id("cancel_ae"));
        h.add(new AppleLabel(CANCEL_BUTTON_X, CANCEL_FOOT_Y, CANCEL_BUTTON_W, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks.desc"))
                .center());
    }

    // ==================== 槽位底格 ====================

    private AppleSlotGrid slotGrid(Predicate<Slot> filter) {
        return new AppleSlotGrid(this.menu, () -> this.leftPos, () -> this.topPos, filter);
    }

    static boolean isInputSlot(Slot slot) {
        return slot.index >= INPUT_SLOTS_START && slot.index < INPUT_SLOTS_START + INPUT_SLOTS_COUNT;
    }

    static boolean isOutputSlot(Slot slot) {
        return slot.index >= OUTPUT_SLOTS_START && slot.index < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT;
    }

    static boolean isConfigSlot(Slot slot) {
        return slot.index == CATALYST_SLOT || slot.index == MOLD_SLOT;
    }

    static boolean isPatternSlot(Slot slot) {
        return slot.index >= PATTERN_SLOTS_START && slot.index <= PATTERN_SLOTS_END;
    }

    static boolean isPlayerSlot(Slot slot) {
        return slot.index >= TOTAL_SLOTS;
    }

    private AppleCanvas tankCanvas(boolean output) {
        return new AppleCanvas(TANK_GRID_X, output ? OUT_GRID_Y : IN_GRID_Y, gridExtent(TANK_ROWS), gridExtent(TANK_ROWS),
                (g, mx, my, x, y, cw, ch) -> paintTanks(g, mx, my, x, y, output))
                .onClick((mx, my, b) -> handleTankClick(mx, my, output));
    }

    private AppleCanvas separatorCanvas(int y) {
        return new AppleCanvas(CONTENT_B_X, y, CONTENT_B_RIGHT - CONTENT_B_X, 1,
                (g, mx, my, x, yy, cw, ch) -> AppleUi.separator(g, x, yy, cw)).id("rule_" + y);
    }

    // ==================== 绘制：标题栏 ====================

    private void paintHeader(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        int buttonX = x + REDSTONE_BUTTON_X, buttonY = y + HEADER_ROW_Y;
        boolean hoverButton = inside(mouseX, mouseY, buttonX, buttonY, REDSTONE_BUTTON_SIZE, REDSTONE_BUTTON_SIZE);
        AppleUi2.round(g, buttonX, buttonY, REDSTONE_BUTTON_SIZE, REDSTONE_BUTTON_SIZE, 5f,
                hoverButton ? AppleStyle.FILL_HOVER : AppleStyle.FILL);
        drawRedstoneGlyph(g, buttonX + REDSTONE_BUTTON_SIZE / 2f, buttonY + REDSTONE_BUTTON_SIZE / 2f, redstoneAccent());

        Component status = statusText();
        int statusWidth = headerBadgeWidth(status);
        int statusRight = buttonX - 4;
        int statusTop = y + HEADER_ROW_Y;
        badge(g, status, statusRight - statusWidth, statusTop, statusWidth, isRunning() ? AppleStyle.GREEN : AppleStyle.TERTIARY);

        if (activeAeTaskCount() > 0) {
            Component ae = aeTasksText();
            int aeWidth = headerBadgeWidth(ae);
            int aeRight = statusRight - statusWidth - 4;
            badge(g, ae, aeRight - aeWidth, statusTop, aeWidth, AppleStyle.TEAL);
        }
    }

    private void badge(GuiGraphics g, Component text, int x, int y, int width, int accent) {
        AppleUi2.round(g, x, y, width, HEADER_BADGE_H, HEADER_BADGE_H / 2f, AppleStyle.tintOf(accent));
        AppleUi.fillRound(g, x + 6, y + (HEADER_BADGE_H - 5) / 2f, 5, 5, 2.5f, accent);
        g.drawString(this.font, text, x + 14, y + 4, accent, false);
    }

    private int headerBadgeWidth(Component text) {
        return this.font.width(text) + 22;
    }

    /** 红石圆点图形（16 设计像素的 4 向刻度 + 圆心）。 */
    private static void drawRedstoneGlyph(GuiGraphics g, float cx, float cy, int color) {
        AppleUi.fillRound(g, cx - 2.5f, cy - 2.5f, 5, 5, 2.5f, color);
        g.fill((int) cx, (int) cy - 5, (int) cx + 1, (int) cy - 3, color);
        g.fill((int) cx, (int) cy + 3, (int) cx + 1, (int) cy + 5, color);
        g.fill((int) cx - 5, (int) cy, (int) cx - 3, (int) cy + 1, color);
        g.fill((int) cx + 3, (int) cy, (int) cx + 5, (int) cy + 1, color);
    }

    // ==================== 绘制：六面配置 ====================

    private void paintFaces(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        FurnaceFaceMode[] modes = faceModes();
        for (int face = 0; face < FACE_COUNT; face++) {
            int cx = x + (face % 2) * FACE_CELL_STEP_X;
            int cy = y + (face / 2) * FACE_CELL_STEP_Y;
            boolean hover = inside(mouseX, mouseY, cx, cy, FACE_CELL_W, FACE_CELL_H);
            boolean expanded = face == this.openFaceMenuIndex;
            if (expanded) {
                AppleUi2.round(g, cx, cy, FACE_CELL_W, FACE_CELL_H, 6f, AppleStyle.BLUE);
                AppleUi2.round(g, cx + 1.5f, cy + 1.5f, FACE_CELL_W - 3, FACE_CELL_H - 3, 5f, 0xFFFFFFFF);
            } else {
                AppleUi2.round(g, cx, cy, FACE_CELL_W, FACE_CELL_H, 6f, hover ? 0xFFFFFFFF : 0xE6FFFFFF);
            }
            Component name = Component.translatable("gui.useless_mod.advanced_alloy_furnace.face." + faceNameKey(face));
            AppleTextLayout.draw(g, font, name, cx + 6, cy + 4, FACE_CELL_W - 12, .75f, AppleStyle.TERTIARY, false);

            FurnaceFaceMode mode = modes == null ? FurnaceFaceMode.DISABLED : modes[face];
            int accent = faceAccent(mode);
            Component label = modeLabel(mode);
            int capsuleX = cx + 3, capsuleW = FACE_CELL_W - 6;
            AppleUi2.round(g, capsuleX, cy + 17, capsuleW, 13, 6f, AppleStyle.tintOf(accent));
            AppleTextLayout.drawCentered(g, font, label, capsuleX, cy + 17, capsuleW, 13, 3, 2, null, accent);
        }
    }

    // ==================== 绘制：开关行 ====================

    private void paintSwitches(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        AppleUi2.round(g, x, y, w, h, 6f, 0xF2FFFFFF);
        for (int row = 1; row < SWITCH_ROWS; row++) {
            int ry = y + row * SWITCH_ROW_H;
            g.fill(x + 28, ry - 1, x + w, ry, AppleStyle.SEPARATOR);
        }
        drawSwitchRow(g, x, y, 0, AppleUi.Icon.ARROW_UP, AppleStyle.GREEN,
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.auto_output"));
        drawSwitchRow(g, x, y, 1, AppleUi.Icon.ARROW_DOWN, AppleStyle.BLUE,
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.auto_input"));
        drawSwitchRow(g, x, y, 2, AppleUi.Icon.AE, AppleStyle.TEAL,
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_return_output"));

        // 红石控制：右侧显示当前模式 + ⌄（点击循环）
        int ry = y + 3 * SWITCH_ROW_H;
        boolean hover = inside(mouseX, mouseY, x, ry, w, SWITCH_ROW_H);
        AppleIconTile.draw(g, x + 8, ry + 5, 12, AppleUi.Icon.BOLT, AppleStyle.RED, true);
        AppleTextLayout.draw(g, font, Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                x + 28, ry + 7, 48, .75f, AppleStyle.LABEL, false);
        Component value = Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control."
                + redstoneMode().name().toLowerCase());
        AppleTextLayout.draw(g, font, value, x + 78, ry + 7, w - 86, .7f,
                hover ? AppleStyle.BLUE : AppleStyle.SECONDARY, true);
        AppleUi.icon(g, AppleUi.Icon.ARROW_DOWN, x + w - 8, ry + 8, 6, AppleStyle.QUATERNARY);
    }

    private void drawSwitchRow(GuiGraphics g, int x, int y, int row, AppleUi.Icon icon, int accent, Component label) {
        int ry = y + row * SWITCH_ROW_H;
        AppleIconTile.draw(g, x + 8, ry + 5, 12, icon, accent, true);
        AppleTextLayout.draw(g, font, label, x + 28, ry + 7, switchLabelWidth(), .85f, AppleStyle.LABEL, false);
    }

    /** Width between the local label anchor and switch, independent of window position. */
    static int switchLabelWidth() {
        return Math.max(0, SWITCH_X - SWITCH_LABEL_X - 4);
    }

    // ==================== 绘制：能量 ====================

    private void paintEnergy(GuiGraphics g, int x, int y, int w) {
        long energy = this.menu.getEnergy();
        long max = this.menu.getMaxEnergy();
        int pct = percent(energy, max);

        AppleUi.icon(g, AppleUi.Icon.BOLT, x, y + 2, 8, AppleStyle.RED);
        g.drawString(this.font, Component.translatable("gui.useless_mod.apple.energy"), x + 11, y + 1,
                AppleStyle.SECONDARY, false);
        String pctText = pct + "%";
        g.drawString(this.font, pctText, x + w - this.font.width(pctText), y + 1, AppleStyle.TERTIARY, false);

        String big = compact(energy);
        bigText(g, big, x, y + 11, AppleStyle.LABEL);
        String unit = "/ " + compact(max) + " FE";
        int unitX = x + Math.round(this.font.width(big) * 1.8f) + 4;
        AppleTextLayout.draw(g, font, Component.literal(unit), unitX, y + 18,
                x + w - unitX, .85f, AppleStyle.SECONDARY, false);

        AppleUi.progress(g, x, y + 33, w, ENERGY_BAR_H, (float) ratio(energy, max), AppleStyle.RED);
        AppleTextLayout.draw(g, font, Component.literal(formatLong(energy) + " / " + formatLong(max) + " FE"),
                x, y + 45, w, .7f, AppleStyle.TERTIARY, false);
    }

    private void bigText(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.8f, 1.8f, 1f);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    // ==================== 绘制：冶炼进度 / 并行数分隔线 ====================

    private void paintProgressBlock(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int infoX = x + w - 12, infoY = y - 1;
        boolean hoverInfo = inside(mouseX, mouseY, infoX, infoY, 12, 12);
        AppleTextLayout.draw(g, font, boldText(Component.translatable("gui.useless_mod.apple.alloying")),
                x, y, w - 14, .85f, AppleStyle.LABEL, false);
        AppleUi2.round(g, infoX, infoY, 12, 12, 3.5f,
                hoverInfo ? AppleStyle.BTN_TINTED_HOVER : AppleStyle.BLUE_TINT);
        AppleUi.icon(g, AppleUi.Icon.INFO, infoX + 2, infoY + 2, 8, AppleStyle.BLUE);

        int max = this.menu.getMaxProgress();
        int progress = this.menu.getProgress();
        AppleUi.progress(g, x, y + 13, w, 6, (float) ratio(progress, max), AppleStyle.ORANGE);
        String detail = max > 0 ? aeTaskProgressText(progress, max) : "0/0 (0%)";
        AppleTextLayout.draw(g, font, Component.literal(detail), x, y + 23, w, .75f, AppleStyle.TERTIARY, false);

        Component hint = Component.translatable("gui.useless_mod.advanced_alloy_furnace.view_recipes");
        AppleTextLayout.draw(g, font, hint, x, y + 33, w - 8, .75f, AppleStyle.BLUE, false);
        AppleUi.icon(g, AppleUi.Icon.CHEVRON_RIGHT, x + w - 7, y + 32, 6, AppleStyle.BLUE);
    }

    private void paintStatRules(GuiGraphics g, int x, int y, int w, int h) {
        for (int row = 1; row < STAT_ROWS; row++) {
            int ry = y + row * STAT_ROW_H;
            g.fill(x, ry - 1, x + w, ry, AppleStyle.SEPARATOR);
        }
    }

    // ==================== 绘制：储罐 ====================

    private void paintTanks(GuiGraphics g, int mouseX, int mouseY, int x, int y, boolean output) {
        for (int i = 0; i < TANK_COUNT_PER_SIDE; i++) {
            int cx = x + (i % TANK_COLUMNS) * SLOT_PITCH;
            int cy = y + (i / TANK_COLUMNS) * SLOT_PITCH;
            boolean hover = inside(mouseX, mouseY, cx, cy, SLOT_CELL, SLOT_CELL);
            AppleUi.strokedRound(g, cx + 0.5f, cy + 0.5f, SLOT_CELL - 1, SLOT_CELL - 1, 5f,
                    hover ? AppleStyle.SLOT_HOVER : AppleStyle.SLOT, AppleStyle.SLOT_EDGE);
        }
        this.renderFluidTanks(g, x + SLOT_ITEM_INSET, y + SLOT_ITEM_INSET, output);
        this.renderChemicalTanks(g, x + SLOT_ITEM_INSET, y + SLOT_ITEM_INSET, output);
    }

    // ==================== 弹层：面模式下拉 ====================

    private boolean handleFaceClick(double mouseX, double mouseY, int button) {
        if (this.menu.getBlockEntity() == null) {
            return false;
        }
        int face = faceCellAt(mouseX - this.leftPos, mouseY - this.topPos);
        if (face < 0) {
            return false;
        }
        if (button == 1) {
            this.cycleFaceMode(face, true);
            return true;
        }
        openFaceMenu(face);
        return true;
    }

    private void openFaceMenu(int face) {
        AppleHost host = appleHost();
        if (host == null) {
            return;
        }
        this.openFaceMenuIndex = face;
        ApplePopover pop = new ApplePopover(faceMenuX(face), faceMenuY(face), FACE_MENU_W, FACE_MENU_H);
        pop.onClose(() -> this.openFaceMenuIndex = -1);
        pop.addWidget(new AppleCanvas(0, 0, FACE_MENU_W, FACE_MENU_H,
                (g, mx, my, x, y, cw, ch) -> paintFaceMenu(g, mx, my, x, y, cw, ch))
                .onClick((mx, my, b) -> {
                    int row = faceMenuRowAt(my - pop.getPositionY());
                    if (row >= 0) {
                        applyFaceMode(face, FurnaceFaceMode.values()[row]);
                        host.closePopover();
                    }
                    return true;
                }).id("face_menu"));
        host.openPopover(pop, true, this);
    }

    private void paintFaceMenu(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int face = this.openFaceMenuIndex;
        Component header = face < 0 ? Component.empty()
                : Component.translatable("gui.useless_mod.advanced_alloy_furnace.face." + faceNameKey(face));
        g.drawString(this.font, header, x + 8, y + FACE_MENU_PAD + 3, AppleStyle.TERTIARY, false);
        g.fill(x + FACE_MENU_PAD, y + FACE_MENU_PAD + FACE_MENU_HEADER_H - 3,
                x + w - FACE_MENU_PAD, y + FACE_MENU_PAD + FACE_MENU_HEADER_H - 2, AppleStyle.SEPARATOR);

        FurnaceFaceMode current = face < 0 ? null : currentFaceMode(face);
        FurnaceFaceMode[] all = FurnaceFaceMode.values();
        for (int row = 0; row < FACE_MENU_ROWS; row++) {
            int ry = y + FACE_MENU_PAD + FACE_MENU_HEADER_H + row * FACE_MENU_ROW_H;
            boolean selected = current != null && current.ordinal() == row;
            boolean hover = inside(mouseX, mouseY, x + 2, ry, w - 4, FACE_MENU_ROW_H);
            if (selected) {
                AppleUi2.round(g, x + 2, ry, w - 4, FACE_MENU_ROW_H, 4f, AppleStyle.BLUE);
            } else if (hover) {
                AppleUi2.round(g, x + 2, ry, w - 4, FACE_MENU_ROW_H, 4f, AppleStyle.SELECTED_ROW);
            }
            int accent = selected ? AppleStyle.ON_ACCENT : faceAccent(all[row]);
            AppleUi.fillRound(g, x + 8, ry + (FACE_MENU_ROW_H - 5) / 2f, 5, 5, 2.5f, accent);
            Component label = modeLabel(all[row]);
            g.drawString(this.font, label, x + 18, ry + 4,
                    selected ? AppleStyle.ON_ACCENT : AppleStyle.LABEL, false);
            if (selected) {
                AppleUi.icon(g, AppleUi.Icon.CHECK, x + w - 18, ry + 4, 8, AppleStyle.ON_ACCENT);
            }
        }
    }

    // ==================== 弹层：AE 任务 ====================

    private void openAeTasks() {
        AppleHost host = appleHost();
        if (host == null) {
            return;
        }
        int rows = Math.max(1, Math.min(this.menu.getAETaskProgressList().size(), AE_MAX_ROWS));
        int height = aePopoverHeight(rows);
        ApplePopover pop = new ApplePopover(AE_POP_X, AE_POP_Y, AE_POP_W, height).dimBackground();
        pop.addWidget(new AppleCanvas(0, 0, AE_POP_W, height,
                (g, mx, my, x, y, cw, ch) -> paintAeTasks(g, mx, my, x, y, cw, ch)).id("ae_task_list"));
        int footerY = AE_ROWS_Y + rows * AE_ROW_H + 6;
        pop.addWidget(new ApplePushButton(AE_POP_PAD, footerY, AE_CANCEL_W, AE_FOOTER_H,
                ApplePushButton.Kind.DANGER,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks"),
                this::cancelAeTasks).icon(AppleUi.Icon.XMARK).id("ae_cancel"));
        pop.addWidget(new ApplePushButton(AE_POP_W - AE_POP_PAD - AE_DONE_W, footerY, AE_DONE_W, AE_FOOTER_H,
                ApplePushButton.Kind.PRIMARY, () -> Component.translatable("gui.done"), host::closePopover)
                .id("ae_done"));
        pop.addWidget(new ApplePushButton(AE_POP_W - AE_POP_PAD - AE_CLOSE_SIZE, 6, AE_CLOSE_SIZE, AE_CLOSE_SIZE,
                ApplePushButton.Kind.SECONDARY, Component::empty, host::closePopover)
                .icon(AppleUi.Icon.XMARK).id("ae_close"));
        host.openPopover(pop, true, this);
    }

    private void paintAeTasks(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<AdvancedAlloyFurnaceAeManager.AETaskProgress> tasks = new ArrayList<>(this.menu.getAETaskProgressList());
        int visible = Math.min(tasks.size(), AE_MAX_ROWS);

        AppleIconTile.draw(g, x + AE_POP_PAD, y + AE_POP_PAD, AE_HEADER_ICON, AppleUi.Icon.GEAR, AppleStyle.TEAL, true);
        g.drawString(this.font, boldText(aeTasksText()), x + AE_POP_PAD + AE_HEADER_ICON + 6,
                y + AE_POP_PAD - 1, AppleStyle.LABEL, false);
        Component progressText = Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_progress",
                this.menu.getTotalAEProgress(), this.menu.getTotalAEMaxProgress(),
                percent(this.menu.getTotalAEProgress(), this.menu.getTotalAEMaxProgress()));
        String progressLine = this.font.plainSubstrByWidth(progressText.getString(), w - AE_POP_PAD * 2 - 30);
        g.drawString(this.font, progressLine, x + AE_POP_PAD + AE_HEADER_ICON + 6,
                y + AE_POP_PAD + 11, AppleStyle.SECONDARY, false);

        AppleUi.progress(g, x + AE_POP_PAD, y + AE_PROGRESS_Y, w - AE_POP_PAD * 2, 6,
                (float) ratio(this.menu.getTotalAEProgress(), this.menu.getTotalAEMaxProgress()), AppleStyle.TEAL);

        if (visible == 0) {
            Component empty = Component.translatable("gui.useless_mod.apple.no_tasks");
            g.drawString(this.font, empty, x + (w - this.font.width(empty)) / 2,
                    y + AE_ROWS_Y + AE_ROW_H / 2, AppleStyle.TERTIARY, false);
            return;
        }

        for (int i = 0; i < visible; i++) {
            AdvancedAlloyFurnaceAeManager.AETaskProgress task = tasks.get(i);
            int ry = y + AE_ROWS_Y + i * AE_ROW_H;
            AppleUi2.round(g, x + AE_POP_PAD, ry + 4, 16, 16, 3.5f, AppleStyle.BLUE_TINT);
            AppleUi.patternGlyph(g, x + AE_POP_PAD, ry + 4);

            String statusKey = task.getStatusKey() == null
                    ? "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing" : task.getStatusKey();
            Component status = Component.translatable(statusKey);
            int accent = statusAccent(statusKey);
            int badgeWidth = this.font.width(status) + 18;
            int badgeX = x + w - AE_POP_PAD - badgeWidth;
            AppleUi2.round(g, badgeX, ry + 3, badgeWidth, 12, 6f, AppleStyle.tintOf(accent));
            AppleUi.fillRound(g, badgeX + 6, ry + (12 - 5) / 2f + 3, 5, 5, 2.5f, accent);
            g.drawString(this.font, status, badgeX + 13, ry + 5, accent, false);

            String name = task.getProductName() + " (×" + task.getTotalOutputCount() + ")";
            String clipped = this.font.plainSubstrByWidth(name, badgeX - (x + AE_POP_PAD + 22) - 6);
            g.drawString(this.font, boldText(Component.literal(clipped)), x + AE_POP_PAD + 22, ry + 2,
                    AppleStyle.LABEL, false);

            Component detail = task.getMaxProgress() > 1
                    ? Component.literal(aeTaskProgressText(task.getProgress(), task.getMaxProgress()))
                    : taskDetail(task.getStatusDetail());
            String detailText = this.font.plainSubstrByWidth(detail.getString(), badgeX - (x + AE_POP_PAD + 22) - 6);
            g.drawString(this.font, detailText, x + AE_POP_PAD + 22, ry + 13, AppleStyle.TERTIARY, false);

            g.fill(x + AE_POP_PAD, ry + AE_ROW_H - 1, x + w - AE_POP_PAD, ry + AE_ROW_H, AppleStyle.SEPARATOR);
        }

        if (tasks.size() > visible) {
            String more = "+" + (tasks.size() - visible);
            g.drawString(this.font, more, x + w - AE_POP_PAD - this.font.width(more),
                    y + AE_ROWS_Y + visible * AE_ROW_H - 2, AppleStyle.TERTIARY, false);
        }
    }

    // ==================== 交互 ====================

    private boolean handleHeaderClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int x = this.leftPos, y = this.topPos;
        if (inside(mouseX, mouseY, x + REDSTONE_BUTTON_X, y + HEADER_ROW_Y,
                REDSTONE_BUTTON_SIZE, REDSTONE_BUTTON_SIZE)) {
            return cycleRedstoneControl();
        }
        if (activeAeTaskCount() > 0) {
            Component status = statusText();
            int statusWidth = headerBadgeWidth(status);
            int statusRight = x + REDSTONE_BUTTON_X - 4;
            Component ae = aeTasksText();
            int aeWidth = headerBadgeWidth(ae);
            int aeRight = statusRight - statusWidth - 4;
            if (inside(mouseX, mouseY, aeRight - aeWidth, y + HEADER_ROW_Y, aeWidth, HEADER_BADGE_H)) {
                openAeTasks();
                return true;
            }
        }
        return false;
    }

    private void changePatternPage(int delta) {
        int newPage = Math.max(0, Math.min(this.menu.getMaxPatternPage(), this.menu.getPatternPage() + delta));
        if (newPage == this.menu.getPatternPage()) {
            return;
        }
        this.menu.setPatternPage(newPage);
        updatePatternSlotVisibility();
        PacketDistributor.sendToServer(new PatternPageChangePacket(newPage));
    }

    private boolean openRecipeCategory() {
        if (ModList.get().isLoaded("jei")) {
            com.sorrowmist.useless.compat.jei.JEIPlugin.showAdvancedAlloyFurnaceRecipes();
        }
        return true;
    }

    private void cancelAeTasks() {
        if (this.menu.getBlockEntity() != null) {
            PacketDistributor.sendToServer(new AECancelPacket(this.menu.getBlockEntity().getBlockPos()));
        }
    }

    private boolean isAutoOutput() {
        return this.menu.getBlockEntity() != null && this.menu.getBlockEntity().isAutoOutputEnabled();
    }

    private boolean isAutoInput() {
        return this.menu.getBlockEntity() != null && this.menu.getBlockEntity().isAutoInputEnabled();
    }

    private boolean isReturnToAe() {
        return this.menu.isReturnOutputToAe();
    }

    private void toggleAutoOutput() {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        if (entity == null) {
            return;
        }
        boolean next = !entity.isAutoOutputEnabled();
        entity.setAutoOutputEnabled(next);
        PacketDistributor.sendToServer(new AutoIOChangePacket(entity.getBlockPos(), true));
    }

    private void toggleAutoInput() {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        if (entity == null) {
            return;
        }
        boolean next = !entity.isAutoInputEnabled();
        entity.setAutoInputEnabled(next);
        PacketDistributor.sendToServer(new AutoIOChangePacket(entity.getBlockPos(), false));
    }

    private void toggleReturnToAe() {
        if (this.menu.getBlockEntity() != null) {
            PacketDistributor.sendToServer(new AEReturnOutputTogglePacket(this.menu.getBlockEntity().getBlockPos()));
        }
    }

    private boolean cycleRedstoneControl() {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        if (entity == null) {
            return false;
        }
        entity.setRedstoneControlMode(entity.getRedstoneControlMode().next());
        PacketDistributor.sendToServer(new RedstoneControlPacket(entity.getBlockPos()));
        return true;
    }

    /**
     * 面模式：按最短方向发送若干次「循环」包，最终落到目标模式。
     * <p>网络包只支持 next / previous，所以直接选中的语义由客户端折算成多次循环；本地先改，
     * 保证下拉关闭时立即看到结果。</p>
     */
    private void applyFaceMode(int faceIndex, FurnaceFaceMode target) {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        if (entity == null || target == null) {
            return;
        }
        FurnaceFace face = FurnaceFace.values()[faceIndex];
        FurnaceFaceMode current = entity.getFaceMode(face);
        int count = FurnaceFaceMode.values().length;
        int forward = (target.ordinal() - current.ordinal() + count) % count;
        int backward = (current.ordinal() - target.ordinal() + count) % count;
        if (forward == 0) {
            return;
        }
        boolean reverse = backward < forward;
        int steps = reverse ? backward : forward;
        entity.setFaceMode(face, target);
        for (int i = 0; i < steps; i++) {
            PacketDistributor.sendToServer(new FaceModeChangePacket(entity.getBlockPos(), faceIndex, reverse));
        }
    }

    /** 右键（或标题栏红石按钮）沿一个方向循环面模式。 */
    private void cycleFaceMode(int faceIndex, boolean reverse) {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        if (entity == null) {
            return;
        }
        FurnaceFace face = FurnaceFace.values()[faceIndex];
        FurnaceFaceMode current = entity.getFaceMode(face);
        entity.setFaceMode(face, reverse ? current.previous() : current.next());
        PacketDistributor.sendToServer(new FaceModeChangePacket(entity.getBlockPos(), faceIndex, reverse));
    }

    private boolean handleTankClick(double mouseX, double mouseY, boolean output) {
        if (this.menu.getBlockEntity() == null) {
            return false;
        }
        int index = tankIndexAt(mouseX - this.leftPos, mouseY - this.topPos, output);
        if (index < 0) {
            return false;
        }
        if (index < FLUID_TANK_COUNT) {
            FluidStack fluid = output
                    ? this.menu.getOutputFluidTank(index).getFluid()
                    : this.menu.getInputFluidTank(index).getFluid();
            if (!fluid.isEmpty()) {
                PacketDistributor.sendToServer(new TankClearPacket(
                        this.menu.getBlockEntity().getBlockPos(), index, !output));
            }
            return true;
        }
        if (!this.menu.hasChemicalSupport()) {
            return true;
        }
        int chemical = index - CHEMICAL_TANK_START;
        ChemicalStackView stack = output
                ? this.menu.getOutputChemical(chemical) : this.menu.getInputChemical(chemical);
        if (stack != null && !stack.isEmpty()) {
            PacketDistributor.sendToServer(new TankClearPacket(
                    this.menu.getBlockEntity().getBlockPos(), chemical, !output, true));
        }
        return true;
    }

    private void updatePatternSlotVisibility() {
        int currentPage = this.menu.getPatternPage();
        int slotsPerPage = this.menu.getPatternSlotsPerPage();
        int base = currentPage * slotsPerPage;
        int end = Math.min(base + slotsPerPage, PATTERN_SLOTS_COUNT);

        for (Slot slot : this.menu.slots) {
            if (slot instanceof PatternSlotItemHandler patternSlot) {
                int slotIndex = slot.getSlotIndex();
                int relativeIndex = slotIndex - PATTERN_SLOTS_START;
                patternSlot.setActive(relativeIndex >= base && relativeIndex < end);
            }
        }
    }

    // ==================== 数据读取（全部来自同步数据）====================

    private boolean isRunning() {
        return this.menu.getProgress() > 0 && this.menu.getMaxProgress() > 0;
    }

    private int activeAeTaskCount() {
        return this.menu.getActiveAETaskCount();
    }

    private Component statusText() {
        return Component.translatable(isRunning()
                ? "gui.useless_mod.apple.running" : "gui.useless_mod.apple.idle");
    }

    private Component aeTasksText() {
        return Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_tasks", activeAeTaskCount());
    }

    /** 第三行「催化剂」：真实催化剂名称 + 当前倍率，宽度不够时截断。 */
    private Component catalystSummary() {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        if (entity == null) {
            return Component.literal("—");
        }
        ItemStack catalyst = entity.getItemHandler().getStackInSlot(CATALYST_SLOT);
        if (catalyst.isEmpty()) {
            return Component.literal("—");
        }
        int parallel = this.menu.getCatalystMaxParallel();
        String full = catalyst.getHoverName().getString() + " (" + parallel + "×)";
        return Component.literal(this.font.plainSubstrByWidth(full, CONTENT_B_RIGHT - CONTENT_B_X - 54));
    }

    @Nullable
    private FurnaceFaceMode[] faceModes() {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        return entity == null ? null : entity.getFaceModes();
    }

    private FurnaceFaceMode currentFaceMode(int face) {
        FurnaceFaceMode[] modes = faceModes();
        return modes == null ? FurnaceFaceMode.DISABLED : modes[face];
    }

    private RedstoneControlMode redstoneMode() {
        AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
        return entity == null ? RedstoneControlMode.DISABLED : entity.getRedstoneControlMode();
    }

    private int redstoneAccent() {
        return switch (redstoneMode()) {
            case DISABLED -> AppleStyle.QUATERNARY;
            case HIGH_ACTIVE -> AppleStyle.RED;
            case LOW_ACTIVE -> AppleStyle.ORANGE;
        };
    }

    // ==================== 文案 / 颜色映射 ====================

    private static String faceNameKey(int face) {
        FurnaceFace value = FurnaceFace.values()[Math.floorMod(face, FurnaceFace.COUNT)];
        return value.name().toLowerCase();
    }

    private static Component modeLabel(FurnaceFaceMode mode) {
        return Component.translatable("gui.useless_mod.advanced_alloy_furnace.face_mode." + mode.name().toLowerCase());
    }

    /** 面模式配色（对齐设计稿：输入蓝 / 输出绿 / 双向青 / 催化剂橙 / 模具紫 / 禁止灰）。 */
    static int faceAccent(FurnaceFaceMode mode) {
        if (mode == null) {
            return AppleStyle.TERTIARY;
        }
        return switch (mode) {
            case MATERIAL_INPUT -> AppleStyle.BLUE;
            case MATERIAL_OUTPUT -> AppleStyle.GREEN;
            case MATERIAL_INPUT_OUTPUT -> AppleStyle.TEAL;
            case CATALYST_INPUT -> AppleStyle.ORANGE;
            case MOLD_INPUT -> AppleStyle.PURPLE;
            case DISABLED -> AppleStyle.TERTIARY;
        };
    }

    /** AE 任务状态配色：处理中绿 / 排队灰 / 其余（等待…）红。 */
    static int statusAccent(String statusKey) {
        if (statusKey == null) {
            return AppleStyle.TERTIARY;
        }
        if (statusKey.endsWith(".processing")) {
            return AppleStyle.GREEN;
        }
        if (statusKey.endsWith(".queued")) {
            return AppleStyle.TERTIARY;
        }
        return AppleStyle.RED;
    }

    private Component taskDetail(String statusDetail) {
        if (statusDetail == null || statusDetail.isBlank()) {
            return Component.empty();
        }
        return statusDetail.startsWith("gui.useless_mod.")
                ? Component.translatable(statusDetail)
                : Component.literal(statusDetail);
    }

    private static Component boldText(Component text) {
        return text.copy().withStyle(style -> style.withBold(true));
    }

    static String compact(long value) {
        if (value >= 1_000_000_000_000L) {
            return trimUnit(value / 1_000_000_000_000D) + "T";
        }
        if (value >= 1_000_000_000L) {
            return trimUnit(value / 1_000_000_000D) + "G";
        }
        if (value >= 1_000_000L) {
            return trimUnit(value / 1_000_000D) + "M";
        }
        if (value >= 1_000L) {
            return trimUnit(value / 1_000D) + "K";
        }
        return Long.toString(value);
    }

    private static String trimUnit(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private static String formatLong(long amount) {
        return String.format("%,d", amount);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ==================== 槽位物品渲染（沿用原逻辑）====================

    /**
     * 重写槽位渲染，支持自定义槽位的背景图标
     */
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, @NotNull Slot slot) {
        int x = slot.x;
        int y = slot.y;
        ItemStack stack = slot.getItem();

        boolean isPatternSlot = slot instanceof PatternSlotItemHandler;
        // 如果是自定义的PatternSlotItemHandler槽位，检查是否激活
        if (isPatternSlot) {
            PatternSlotItemHandler patternSlot = (PatternSlotItemHandler) slot;
            if (!patternSlot.isActive()) {
                return;
            }
            var icon = patternSlot.getIcon();
            if ((patternSlot.renderIconWithItem() || stack.isEmpty()) && patternSlot.isSlotEnabled() && icon != null) {
                // Original supplied pattern icon, rather than the dark AE2 placeholder.
                AppleUi.patternGlyph(guiGraphics, x, y);
            }
        }

        GenericStack patternOutput = isPatternSlot ? getPatternOutput(stack) : null;
        if (PatternSlotRenderer.renderPattern(
                guiGraphics, this.font, patternOutput, x, y, slot.x + slot.y * this.imageWidth)) {
            return;
        }

        guiGraphics.renderItem(stack, x, y, slot.x + slot.y * this.imageWidth);

        if (slot.index < TOTAL_SLOTS && !stack.isEmpty() && stack.getCount() > 1) {
            this.renderCustomItemCount(guiGraphics, stack, x, y);
        } else {
            guiGraphics.renderItemDecorations(this.font, stack, x, y, null);
        }
    }

    /** AE 风数量缩写，正常字号（与原版物品数量一致），右下角对齐。 */
    private void renderCustomItemCount(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if (stack.getCount() <= 1) {
            return;
        }
        String text = formatAeCount(stack.getCount());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        guiGraphics.drawString(this.font, text, x + 17 - this.font.width(text), y + 9, 0xFFFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    private String formatAeCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            int k = count / 1000;
            return k + "K";
        } else if (count < 1000000000) {
            int m = count / 1000000;
            return m + "M";
        } else {
            int g = count / 1000000000;
            return g + "G";
        }
    }

    /**
     * 解析编码样板，返回主产物（用于在样板槽上直接显示产物图标）
     */
    @Nullable
    private GenericStack getPatternOutput(ItemStack stack) {
        return stack.isEmpty()
                ? null
                : PatternSlotRenderer.getPrimaryOutput(stack, Minecraft.getInstance().level);
    }

    // ==================== 流体 / 化学储罐渲染（沿用原逻辑，尺寸改为 16×16 格内）====================

    /**
     * 渲染流体槽
     */
    private void renderFluidTanks(GuiGraphics guiGraphics, int originX, int originY, boolean isInput) {
        if (this.menu.getBlockEntity() == null) {
            return;
        }
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            int tankX = originX + (i % TANK_COLUMNS) * SLOT_PITCH;
            int tankY = originY + (i / TANK_COLUMNS) * SLOT_PITCH;

            FluidStack fluid = isInput
                    ? this.menu.getInputFluidTank(i).getFluid()
                    : this.menu.getOutputFluidTank(i).getFluid();
            int capacity = isInput
                    ? this.menu.getInputFluidTank(i).getCapacity()
                    : this.menu.getOutputFluidTank(i).getCapacity();

            this.renderFluidTank(guiGraphics, tankX, tankY, fluid, capacity);
        }
    }

    /**
     * 渲染单个流体槽
     */
    private void renderFluidTank(GuiGraphics guiGraphics, int x, int y, FluidStack fluidStack, int capacity) {
        if (fluidStack.isEmpty() || capacity <= 0) {
            return;
        }

        Fluid fluid = fluidStack.getFluid();
        if (fluid == Fluids.EMPTY) {
            return;
        }

        IClientFluidTypeExtensions fluidTypeExtensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = fluidTypeExtensions.getStillTexture(fluidStack);

        TextureAtlasSprite sprite = null;
        if (this.minecraft != null) {
            sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        }
        if (sprite == null) {
            return;
        }

        int fluidHeight = (int) ((float) fluidStack.getAmount() / capacity * TANK_INNER_SIZE);
        if (fluidHeight <= 0) {
            return;
        }

        int fluidColor = fluidTypeExtensions.getTintColor(fluidStack);

        guiGraphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float r = ((fluidColor >> 16) & 0xFF) / 255.0F;
        float g = ((fluidColor >> 8) & 0xFF) / 255.0F;
        float b = (fluidColor & 0xFF) / 255.0F;
        float a = ((fluidColor >> 24) & 0xFF) / 255.0F;
        if (a == 0) a = 1.0F;

        RenderSystem.setShaderColor(r, g, b, a);

        int remainingHeight = fluidHeight;
        int currentY = y + TANK_INNER_SIZE - fluidHeight; // 从底部开始填充
        int tileSize = 16;

        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        while (remainingHeight > 0) {
            int drawHeight = Math.min(tileSize, remainingHeight);

            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV(drawHeight);

            float x1 = x;
            float x2 = x + TANK_INNER_SIZE;
            float y1 = currentY;
            float y2 = currentY + drawHeight;

            bufferBuilder.vertex(x1, y2, 0).uv(u0, v1).endVertex();
            bufferBuilder.vertex(x2, y2, 0).uv(u1, v1).endVertex();
            bufferBuilder.vertex(x2, y1, 0).uv(u1, v0).endVertex();
            bufferBuilder.vertex(x1, y1, 0).uv(u0, v0).endVertex();

            remainingHeight -= drawHeight;
            currentY += drawHeight;
        }

        BufferUploader.drawWithShader(bufferBuilder.end());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

    private void renderChemicalTanks(GuiGraphics guiGraphics, int originX, int originY, boolean isInput) {
        if (!this.menu.hasChemicalSupport()) {
            return;
        }
        for (int i = 0; i < CHEMICAL_TANK_COUNT; i++) {
            int index = CHEMICAL_TANK_START + i;
            int tankX = originX + (index % TANK_COLUMNS) * SLOT_PITCH;
            int tankY = originY + (index / TANK_COLUMNS) * SLOT_PITCH;
            ChemicalStackView chemical = isInput
                    ? this.menu.getInputChemical(i) : this.menu.getOutputChemical(i);
            this.renderChemicalTank(guiGraphics, tankX, tankY, chemical, this.menu.getChemicalTankCapacity());
        }
    }

    private void renderChemicalTank(GuiGraphics guiGraphics, int x, int y,
                                    ChemicalStackView chemical, long capacity) {
        if (chemical == null || chemical.isEmpty() || capacity <= 0L) {
            return;
        }

        int fillHeight = (int) Math.min(TANK_INNER_SIZE,
                Math.max(1L, Math.min((long) TANK_INNER_SIZE,
                        (long) ((double) chemical.amount() / (double) capacity * TANK_INNER_SIZE))));
        ResourceLocation icon = chemical.icon();
        TextureAtlasSprite sprite = icon == null || this.minecraft == null ? null
                : this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        if (sprite == null) {
            return;
        }

        int color = chemical.tintColor();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        guiGraphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, 1.0F);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        int remainingHeight = fillHeight;
        int currentY = y + TANK_INNER_SIZE - fillHeight;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        while (remainingHeight > 0) {
            int drawHeight = Math.min(16, remainingHeight);
            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV(drawHeight);
            bufferBuilder.vertex(x, currentY + drawHeight, 0).uv(u0, v1).endVertex();
            bufferBuilder.vertex(x + TANK_INNER_SIZE, currentY + drawHeight, 0).uv(u1, v1).endVertex();
            bufferBuilder.vertex(x + TANK_INNER_SIZE, currentY, 0).uv(u1, v0).endVertex();
            bufferBuilder.vertex(x, currentY, 0).uv(u0, v0).endVertex();
            remainingHeight -= drawHeight;
            currentY += drawHeight;
        }
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

    // ==================== Tooltip（沿用原文案，热区改为新几何）====================

    @Override
    protected void renderExtraTooltips(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        this.renderTankTooltip(guiGraphics, mouseX, mouseY, true);
        this.renderTankTooltip(guiGraphics, mouseX, mouseY, false);
        this.renderEnergyTooltip(guiGraphics, mouseX, mouseY);
        this.renderProgressTooltip(guiGraphics, mouseX, mouseY);
        this.renderTipsTooltip(guiGraphics, mouseX, mouseY);
        this.renderFaceModeTooltip(guiGraphics, mouseX, mouseY);
        this.renderAutoIOTooltip(guiGraphics, mouseX, mouseY);
        this.renderRedstoneControlTooltip(guiGraphics, mouseX, mouseY);
        this.renderCancelAeTooltip(guiGraphics, mouseX, mouseY);
        this.renderAeReturnTooltip(guiGraphics, mouseX, mouseY);
        this.renderHeaderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTankTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isInput) {
        int index = tankIndexAt(mouseX - this.leftPos, mouseY - this.topPos, !isInput);
        if (index < 0) {
            return;
        }
        if (index < FLUID_TANK_COUNT) {
            FluidStack fluid = isInput
                    ? this.menu.getInputFluidTank(index).getFluid()
                    : this.menu.getOutputFluidTank(index).getFluid();
            int capacity = isInput
                    ? this.menu.getInputFluidTank(index).getCapacity()
                    : this.menu.getOutputFluidTank(index).getCapacity();
            if (!fluid.isEmpty()) {
                guiGraphics.renderTooltip(this.font, List.of(fluid.getDisplayName(),
                                Component.translatable("gui.useless_mod.advanced_alloy_furnace.fluid_amount",
                                        formatLong(fluid.getAmount()), formatLong(capacity))),
                        Optional.empty(), mouseX, mouseY);
            }
            return;
        }
        if (!this.menu.hasChemicalSupport()) {
            return;
        }
        int chemical = index - CHEMICAL_TANK_START;
        ChemicalStackView stack = isInput
                ? this.menu.getInputChemical(chemical) : this.menu.getOutputChemical(chemical);
        if (stack != null && !stack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, List.of(stack.displayName(),
                            Component.translatable("gui.useless_mod.advanced_alloy_furnace.chemical_amount",
                                    formatLong(stack.amount()), formatLong(this.menu.getChemicalTankCapacity()))),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderEnergyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, this.leftPos + CONTENT_C_X, this.topPos + ENERGY_ROW_Y,
                CONTENT_C_RIGHT - CONTENT_C_X, ENERGY_ROW_H)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.energy",
                this.menu.getEnergy(), this.menu.getMaxEnergy()));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.useless_mod.furnace_tier", this.menu.getFurnaceTier())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_mod.furnace_upgrade_hint"));
        tooltip.add(Component.translatable("tooltip.useless_mod.furnace_upgrade_effect"));
        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderProgressTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!progressTooltipAt(mouseX - this.leftPos, mouseY - this.topPos)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        if (maxProgress > 0) {
            float progressPercent = (float) progress / maxProgress * 100;
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.progress",
                    progress, maxProgress, String.format("%.1f", progressPercent)));

            boolean isActive = progress > 0 && progress < maxProgress;
            String statusKey = isActive
                    ? "gui.useless_mod.advanced_alloy_furnace.status.active"
                    : "gui.useless_mod.advanced_alloy_furnace.status.idle";
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.status",
                            Component.translatable(statusKey))
                    .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.GRAY));

            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.current_parallel",
                            this.menu.getCurrentParallel())
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.max_parallel",
                            this.menu.getCatalystMaxParallel())
                    .withStyle(ChatFormatting.BLUE));
        } else {
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.no_process"));
        }

        int aeTotalProgress = this.menu.getTotalAEProgress();
        int aeTotalMaxProgress = this.menu.getTotalAEMaxProgress();
        var taskProgressList = this.menu.getAETaskProgressList();

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_tasks",
                        taskProgressList.size())
                .withStyle(ChatFormatting.DARK_PURPLE));

        if (!taskProgressList.isEmpty()) {
            for (var taskProgress : taskProgressList) {
                String productName = taskProgress.getProductName();
                int taskProgressVal = taskProgress.getProgress();
                int taskMaxProgress = taskProgress.getMaxProgress();
                long totalOutputCount = taskProgress.getTotalOutputCount();
                String statusKey = taskProgress.getStatusKey();
                String statusDetail = taskProgress.getStatusDetail();

                Component nameComponent = Component.literal(productName);
                Component statusComponent = Component.translatable(statusKey);

                if (taskMaxProgress > 1) {
                    tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_progress_ticks",
                                    nameComponent, totalOutputCount, taskProgressVal, taskMaxProgress, statusComponent)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else if (statusDetail != null && !statusDetail.isBlank()) {
                    tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_waiting_detail",
                                    nameComponent, totalOutputCount, statusComponent, taskDetail(statusDetail))
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_task_waiting",
                                    nameComponent, totalOutputCount, statusComponent)
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        } else if (aeTotalMaxProgress > 0) {
            float aeProgressPercent = (float) aeTotalProgress / aeTotalMaxProgress * 100;
            tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_progress",
                            aeTotalProgress, aeTotalMaxProgress, String.format("%.1f", aeProgressPercent))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.view_recipes")
                .withStyle(ChatFormatting.AQUA));

        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderTipsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!parallelInfoTooltipAt(mouseX - this.leftPos, mouseY - this.topPos)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();

        int currentParallel = this.menu.getCurrentParallel();
        int catalystMaxParallel = this.menu.getCatalystMaxParallel();

        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_info")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.current_parallel", currentParallel)
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.max_parallel", catalystMaxParallel)
                .withStyle(ChatFormatting.BLUE));

        if (this.menu.getBlockEntity() != null) {
            AdvancedAlloyFurnaceBlockEntity entity = this.menu.getBlockEntity();
            ItemStack catalyst = entity.getItemHandler().getStackInSlot(CATALYST_SLOT);
            if (!catalyst.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.catalyst",
                                catalyst.getDisplayName().getString(), catalystMaxParallel)
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.title")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.3")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.parallel_description.4")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.useless_mod.advanced_alloy_furnace.catalyst_warning")
                .withStyle(ChatFormatting.RED));

        guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderFaceModeTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getBlockEntity() == null) {
            return;
        }
        int face = faceCellAt(mouseX - this.leftPos, mouseY - this.topPos);
        if (face < 0) {
            return;
        }
        FurnaceFace value = FurnaceFace.values()[face];
        FurnaceFaceMode mode = currentFaceMode(face);
        Component faceName = Component.translatable("gui.useless_mod.advanced_alloy_furnace.face."
                + value.name().toLowerCase());
        guiGraphics.renderTooltip(this.font, List.of(faceName, modeLabel(mode)), Optional.empty(), mouseX, mouseY);
    }

    private void renderAutoIOTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getBlockEntity() == null) {
            return;
        }
        int row = autoIoTooltipRowAt(mouseX - this.leftPos, mouseY - this.topPos);
        if (row < 0) {
            return;
        }
        boolean output = row == 0;
        Component title = Component.translatable(output
                ? "gui.useless_mod.advanced_alloy_furnace.auto_output"
                : "gui.useless_mod.advanced_alloy_furnace.auto_input");
        boolean enabled = output ? isAutoOutput() : isAutoInput();
        Component status = Component.translatable(enabled
                ? "gui.useless_mod.enabled" : "gui.useless_mod.disabled");
        guiGraphics.renderTooltip(this.font, List.of(title, status), Optional.empty(), mouseX, mouseY);
    }

    private void renderRedstoneControlTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getBlockEntity() == null) {
            return;
        }
        int rowY = this.topPos + SWITCH_LIST_Y + 3 * SWITCH_ROW_H;
        if (!inside(mouseX, mouseY, this.leftPos + SWITCH_LIST_X, rowY, SWITCH_LIST_W, SWITCH_ROW_H)) {
            return;
        }
        guiGraphics.renderTooltip(this.font, List.of(
                        Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                        Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control."
                                + redstoneMode().name().toLowerCase())),
                Optional.empty(), mouseX, mouseY);
    }

    private void renderCancelAeTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, this.leftPos + CANCEL_BUTTON_X, this.topPos + CANCEL_BUTTON_Y,
                CANCEL_BUTTON_W, CANCEL_BUTTON_H)) {
            return;
        }
        guiGraphics.renderTooltip(this.font, List.of(
                        Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks"),
                        Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks.desc")),
                Optional.empty(), mouseX, mouseY);
    }

    private void renderAeReturnTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getBlockEntity() == null) {
            return;
        }
        int rowY = this.topPos + SWITCH_LIST_Y + 2 * SWITCH_ROW_H;
        if (!inside(mouseX, mouseY, this.leftPos + SWITCH_LIST_X, rowY, SWITCH_LIST_W, SWITCH_ROW_H)) {
            return;
        }
        guiGraphics.renderTooltip(this.font, List.of(
                        Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_return_output"),
                        Component.translatable(isReturnToAe()
                                ? "gui.useless_mod.enabled" : "gui.useless_mod.disabled")),
                Optional.empty(), mouseX, mouseY);
    }

    private void renderHeaderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        if (inside(mouseX, mouseY, x + REDSTONE_BUTTON_X, y + HEADER_ROW_Y,
                REDSTONE_BUTTON_SIZE, REDSTONE_BUTTON_SIZE)) {
            guiGraphics.renderTooltip(this.font, List.of(
                            Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                            Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control."
                                    + redstoneMode().name().toLowerCase())),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (activeAeTaskCount() <= 0) {
            return;
        }
        Component status = statusText();
        int statusRight = x + REDSTONE_BUTTON_X - 4;
        int aeRight = statusRight - headerBadgeWidth(status) - 4;
        int aeWidth = headerBadgeWidth(aeTasksText());
        if (inside(mouseX, mouseY, aeRight - aeWidth, y + HEADER_ROW_Y, aeWidth, HEADER_BADGE_H)) {
            guiGraphics.renderTooltip(this.font, List.of(
                            aeTasksText(),
                            Component.translatable("gui.useless_mod.advanced_alloy_furnace.ae_progress",
                                    this.menu.getTotalAEProgress(), this.menu.getTotalAEMaxProgress(),
                                    percent(this.menu.getTotalAEProgress(), this.menu.getTotalAEMaxProgress()))),
                    Optional.empty(), mouseX, mouseY);
        }
    }
}
