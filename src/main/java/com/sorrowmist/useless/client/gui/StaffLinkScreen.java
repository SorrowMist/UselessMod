package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.LinkFilterSlot;
import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.LinkTrigger;
import com.sorrowmist.useless.content.stafflink.ResourceFamily;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
import com.sorrowmist.useless.content.stafflink.StaffLinkFilters;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.content.stafflink.StaffLinkTargets;
import com.sorrowmist.useless.network.StaffLinkSyncPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 无线物流配置界面。
 *
 * <p>沿用本模组机器界面的统一风格（{@link MachineScreenStyle} + {@link PressableAE2Button}）。
 * 布局分两块：上面是「网络 + 容器列表」（可搜索、可改名、Ctrl/Shift 多选后批量编辑），
 * 下面是选中线路的搬运规则，底部是玩家背包。</p>
 *
 * <p>容器列表本身不在这里高亮：手持杖且开着无线物流模式时，世界里的自动高亮由
 * {@code StaffLinkHighlightRenderer} 独立负责，界面关着也生效。</p>
 *
 * <p>所有控件的右边缘统一落在 {@link #CONTENT_RIGHT}，视觉上对齐成一列。</p>
 */
public final class StaffLinkScreen extends AbstractContainerScreen<StaffLinkMenu> {
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 336;

    /** 内容区左右边界；内衬比内容各外扩 2px，形成对称留白。 */
    private static final int CONTENT_LEFT = 8;
    private static final int CONTENT_RIGHT = 240;
    private static final int INSET_LEFT = 6;
    private static final int INSET_RIGHT = 242;
    private static final int CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_LEFT;

    // ---- 锚点列表
    private static final int NETWORK_ROW_Y = 19;
    private static final int SEARCH_ROW_Y = 33;
    private static final int NAME_ROW_Y = 47;
    private static final int SMALL_FIELD_HEIGHT = 12;

    /** 搜索框为批量按钮让出右侧空间；三个控件的右边缘仍落在 {@link #CONTENT_RIGHT}。 */
    private static final int SEARCH_WIDTH = 142;
    private static final int SELECT_ALL_X = 154;
    private static final int SELECT_ALL_WIDTH = 42;
    private static final int CLEAR_SELECTION_X = 200;
    private static final int CLEAR_SELECTION_WIDTH = 40;

    /** 网络切换按钮的宽度（`<` / `>`）。 */
    private static final int NETWORK_SWITCH_WIDTH = 14;
    private static final int PREV_BUTTON_X = 8;
    private static final int NETWORK_FIELD_X = 24;
    private static final int NETWORK_FIELD_WIDTH = 110;
    private static final int NEXT_BUTTON_X = 136;
    private static final int NEW_BUTTON_X = 154;
    private static final int NEW_BUTTON_WIDTH = 30;

    private static final int LIST_FIRST_ROW_Y = 61;
    private static final int LIST_ROW_HEIGHT = 13;
    private static final int LIST_VISIBLE_ROWS = 5;
    private static final int LIST_TOP = 59;
    private static final int LIST_BOTTOM = 124;
    private static final int LIST_UNBIND_WIDTH = 12;
    private static final int LIST_INSET_BOTTOM = 128;
    /** 行首 ▲ / ▼ 两个按钮：宽度各 9px，从 CONTENT_LEFT 起。 */
    private static final int LIST_MOVE_WIDTH = 9;
    private static final int LIST_MOVE_COUNT = 2;
    /** 行首按钮占掉的横向空间；流向字形与名字整体右移这么多，名字预算也要相应扣掉。 */
    private static final int LIST_TEXT_SHIFT = LIST_MOVE_WIDTH * LIST_MOVE_COUNT + 2;
    private static final int LIST_GLYPH_X = CONTENT_LEFT + 2 + LIST_TEXT_SHIFT;
    private static final int LIST_NAME_X = CONTENT_LEFT + 10 + LIST_TEXT_SHIFT;

    // ---- 线路配置
    private static final int CONFIG_INSET_TOP = 130;
    private static final int ROUTE_ROW_Y = 134;
    private static final int ROW_SECOND_Y = 152;
    private static final int ROW_THIRD_Y = 170;
    private static final int ROW_FOURTH_Y = 188;
    private static final int ROW_HEIGHT = 14;

    private static final int FLOW_WIDTH = 74;
    private static final int FLOW_MEDIUM_GAP = 5;
    /** 资源类型按钮的左边界；下拉列表贴着它向下展开。 */
    private static final int MEDIUM_BUTTON_X = CONTENT_LEFT + FLOW_WIDTH + FLOW_MEDIUM_GAP;
    private static final int MEDIUM_MENU_Y = ROW_SECOND_Y + ROW_HEIGHT + 1;
    private static final int MEDIUM_MENU_WIDTH = FLOW_WIDTH;
    private static final int MEDIUM_MENU_ROW_HEIGHT = ROW_HEIGHT;
    private static final int SIDE_WIDTH = 113;
    private static final int SIDE_TRIGGER_GAP = 6;

    /** 数值输入框一行的三格：起点与宽度，最后一格的右边缘正好落在 {@link #CONTENT_RIGHT}。 */
    private static final int[] NUMERIC_CELL_X = {8, 85, 162};
    private static final int[] NUMERIC_CELL_WIDTH = {77, 77, 78};

    /** 输入框停手多久后自动提交（tick）。玩家填了值却没失焦时靠它兜底。 */
    private static final int AUTO_COMMIT_TICKS = 10;

    /** 复制/粘贴的一次性提示在屏幕上停留多久（tick）。 */
    private static final int NOTICE_TICKS = 50;
    private static final int NOTICE_COLOR = 0xFF2E7D32;

    /**
     * 批量编辑选中行：实色青底 + 左侧深青竖条 + 行尾 ✓，三重叠加保证一眼看出。
     *
     * <p>底色刻意用不透明的青，和单选那层近白（{@code HIGHLIGHT_COLOR}）以及面板底色
     * （{@code PANEL_COLOR}）都能明显区分。</p>
     */
    private static final int MULTI_SELECT_COLOR = 0xFFB6E4E4;
    private static final int MULTI_SELECT_TEXT_COLOR = 0xFF149E9E;
    /** 单选（配置区正在编辑的那台）行的左侧竖条。 */
    private static final int SELECTED_BAR_COLOR = 0xFF413F54;

    // ---- 过滤器 + 批量 / 解散
    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 204;
    private static final int FILTER_COLUMNS = 3;
    private static final int FILTER_SLOT_SIZE = 16;
    private static final int FILTER_SLOT_STEP = 17;
    /** 「应用到全部」与「解散网络」并排，两条右边缘分别落在 182 / {@link #CONTENT_RIGHT}。 */
    private static final int APPLY_ALL_X = 128;
    private static final int APPLY_ALL_WIDTH = 54;
    private static final int DISSOLVE_X = 186;
    private static final int DISSOLVE_WIDTH = CONTENT_RIGHT - DISSOLVE_X;

    private static final Direction[] SIDE_ORDER = {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private final PressableAE2Button[] routeButtons = new PressableAE2Button[StaffLinkNetwork.ROUTE_COUNT];
    private PressableAE2Button flowButton;
    private PressableAE2Button mediumButton;
    /**
     * 资源类型下拉是否展开。
     *
     * <p>做成下拉而不是「点一下换一个」：装了化学品 / 魔源 / 通量之后类型能到十种，
     * 一路轮换过去太费手。</p>
     */
    private boolean mediumMenuOpen;
    /** 下拉里的候选类型；环境不变，{@link #init()} 里算一次就够。 */
    private List<LinkMedium> mediumOptions = List.of();
    private PressableAE2Button enabledButton;
    private PressableAE2Button sideButton;
    private PressableAE2Button triggerButton;
    private PressableAE2Button prevNetworkButton;
    private PressableAE2Button nextNetworkButton;
    private PressableAE2Button newNetworkButton;
    private PressableAE2Button dissolveButton;
    private PressableAE2Button selectAllButton;
    private PressableAE2Button clearSelectionButton;
    private PressableAE2Button applyAllButton;

    private EditBox networkNameField;
    private EditBox searchField;
    private EditBox nameField;
    private EditBox weightField;
    private EditBox amountField;
    private EditBox intervalField;
    /** 数值框 + 标签 + 取值范围；标签宽度决定框的起点，范围用来做悬停提示。 */
    private final List<NumericSpec> numericFields = new ArrayList<>();

    private record NumericSpec(EditBox field, Component label, long min, long max, Component hint,
                               boolean scaled) {
    }

    private int scrollOffset;
    private String controlSignature = "";
    /** 上一 tick 有焦点的输入框，用来捕捉「焦点离开」这一刻。 */
    private EditBox focusedField;
    /** 输入框停手了多少 tick，到 {@link #AUTO_COMMIT_TICKS} 就自动提交。 */
    private int editIdleTicks;

    /** 上一次点击的锚点；Shift+点击用它当范围选择的一端。 */
    private GlobalPos lastClickedAnchor;

    /**
     * 会话内的配置剪贴板。
     *
     * <p>Ctrl+C 存的是<b>整条线路配置</b>；Ctrl+V 只取它的字段，锚点与线路号一律用当前选中的
     * ——所以既能把 A 机器的配置贴到 B 机器，也能贴到同一台机器的另一条线路上。</p>
     *
     * <p>刻意留在内存里、不走系统剪贴板：这里只想要「复制一份配置」这一件事，
     * 读系统剪贴板就得额外处理一堆解析失败的脏数据，收益不成正比。</p>
     */
    @Nullable
    private static StaffLinkRoute copiedConfig;
    /** 复制/粘贴的一次性提示；{@link #noticeTicks} 归零后消失。 */
    @Nullable
    private Component notice;
    private int noticeTicks;

    public StaffLinkScreen(StaffLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelX = 44;
        inventoryLabelY = 242;
    }

    @Override
    protected void init() {
        super.init();

        mediumOptions = LinkMedium.supported();
        mediumMenuOpen = false;

        for (int route = 0; route < routeButtons.length; route++) {
            final int index = route;
            routeButtons[route] = addRenderableWidget(new PressableAE2Button(
                    leftPos + CONTENT_LEFT + route * 26, topPos + ROUTE_ROW_Y, 24, ROW_HEIGHT,
                    Component.literal(String.valueOf(route)), button -> selectRoute(index)));
        }

        flowButton = addRenderableWidget(new PressableAE2Button(
                leftPos + CONTENT_LEFT, topPos + ROW_SECOND_Y, FLOW_WIDTH, ROW_HEIGHT, Component.empty(),
                button -> edit(config -> withFlow(config, config.flow().next()))));
        mediumButton = addRenderableWidget(new PressableAE2Button(
                leftPos + MEDIUM_BUTTON_X, topPos + ROW_SECOND_Y,
                FLOW_WIDTH, ROW_HEIGHT, Component.empty(),
                button -> mediumMenuOpen = !mediumMenuOpen));
        enabledButton = addRenderableWidget(new PressableAE2Button(
                leftPos + CONTENT_RIGHT - FLOW_WIDTH, topPos + ROW_SECOND_Y, FLOW_WIDTH, ROW_HEIGHT,
                Component.empty(), button -> edit(config -> withEnabled(config, !config.enabled()))));

        sideButton = addRenderableWidget(new PressableAE2Button(
                leftPos + CONTENT_LEFT, topPos + ROW_THIRD_Y, SIDE_WIDTH, ROW_HEIGHT, Component.empty(),
                button -> edit(config -> withSide(config, nextSide(config.side())))));
        triggerButton = addRenderableWidget(new PressableAE2Button(
                leftPos + CONTENT_RIGHT - SIDE_WIDTH, topPos + ROW_THIRD_Y, SIDE_WIDTH, ROW_HEIGHT,
                Component.empty(), button -> edit(config -> withTrigger(config, config.trigger().next()))));

        networkNameField = addTextField(NETWORK_FIELD_X, NETWORK_ROW_Y, NETWORK_FIELD_WIDTH,
                Component.translatable("gui.useless_mod.wireless_logistics.network_name_hint"),
                StaffLinkNetwork.MAX_NAME);
        searchField = addTextField(CONTENT_LEFT, SEARCH_ROW_Y, SEARCH_WIDTH,
                Component.translatable("gui.useless_mod.wireless_logistics.search_hint"),
                StaffLinkNetwork.MAX_ANCHOR_NAME);
        nameField = addTextField(CONTENT_LEFT, NAME_ROW_Y, CONTENT_WIDTH,
                Component.translatable("gui.useless_mod.wireless_logistics.rename"),
                StaffLinkNetwork.MAX_ANCHOR_NAME);

        weightField = addNumericField(0, "weight_label",
                StaffLinkRoute.MIN_WEIGHT, StaffLinkRoute.MAX_WEIGHT, true, false);
        amountField = addNumericField(1, "amount_label",
                StaffLinkRoute.MIN_AMOUNT, Long.MAX_VALUE, false, true);
        intervalField = addNumericField(2, "interval_label",
                StaffLinkRoute.MIN_INTERVAL, StaffLinkRoute.MAX_INTERVAL, false, false);

        newNetworkButton = addRenderableWidget(new PressableAE2Button(
                leftPos + NEW_BUTTON_X, topPos + NETWORK_ROW_Y, NEW_BUTTON_WIDTH, SMALL_FIELD_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.network_new"),
                button -> newNetwork()));
        prevNetworkButton = addRenderableWidget(new PressableAE2Button(
                leftPos + PREV_BUTTON_X, topPos + NETWORK_ROW_Y, NETWORK_SWITCH_WIDTH, SMALL_FIELD_HEIGHT,
                Component.literal("<"), button -> cycleNetwork(-1)));
        nextNetworkButton = addRenderableWidget(new PressableAE2Button(
                leftPos + NEXT_BUTTON_X, topPos + NETWORK_ROW_Y, NETWORK_SWITCH_WIDTH, SMALL_FIELD_HEIGHT,
                Component.literal(">"), button -> cycleNetwork(1)));
        dissolveButton = addRenderableWidget(new PressableAE2Button(
                leftPos + DISSOLVE_X, topPos + FILTER_Y + 4, DISSOLVE_WIDTH, 16,
                Component.translatable("gui.useless_mod.wireless_logistics.dissolve"),
                button -> dissolveNetwork()));

        selectAllButton = addRenderableWidget(new PressableAE2Button(
                leftPos + SELECT_ALL_X, topPos + SEARCH_ROW_Y, SELECT_ALL_WIDTH, SMALL_FIELD_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.select_all"),
                button -> selectAllVisible()));
        clearSelectionButton = addRenderableWidget(new PressableAE2Button(
                leftPos + CLEAR_SELECTION_X, topPos + SEARCH_ROW_Y, CLEAR_SELECTION_WIDTH, SMALL_FIELD_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.clear_selection"),
                button -> clearSelection()));
        applyAllButton = addRenderableWidget(new PressableAE2Button(
                leftPos + APPLY_ALL_X, topPos + FILTER_Y + 4, APPLY_ALL_WIDTH, 16,
                Component.translatable("gui.useless_mod.wireless_logistics.apply_all"),
                button -> applyAllVisible()));

        // 开界面与下发快照是两个包；万一快照先到，这里把它捞回来，界面就不会空着。
        StaffLinkSyncPacket pending = ClientPacketHandlers.consumePendingStaffLinkSync(menu.getNetworkId());
        if (pending != null) {
            menu.receiveSync(pending.network(), pending.index(), pending.count());
        }

        updateControls();
    }

    private EditBox addTextField(int x, int y, int width, Component hint, int maxLength) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, SMALL_FIELD_HEIGHT, hint);
        field.setMaxLength(maxLength);
        field.setHint(hint);
        addRenderableWidget(field);
        trackEdits(field);
        return field;
    }

    /**
     * 记录「玩家正在打字」。
     *
     * <p>只在输入框有焦点时重置空闲计数——服务端同步也会触发 responder，若不加这个判断，
     * 自动提交会被同步一直推迟。</p>
     */
    private void trackEdits(EditBox field) {
        field.setResponder(value -> {
            if (field.isFocused()) {
                editIdleTicks = 0;
            }
        });
    }

    /**
     * 一行放三个数值输入框：每个占一格，标签画在框左边，框右对齐到本格右边缘。
     *
     * <p>框的起点按当前语言下标签的实际宽度算，所以中英文都不会把标签压在框上；
     * 英文标签用的是短名，完整含义与取值范围看悬停提示。</p>
     */
    private EditBox addNumericField(int cell, String labelKey, long min, long max,
                                    boolean allowNegative, boolean scaled) {
        Component label = Component.translatable("gui.useless_mod.wireless_logistics." + labelKey);
        int cellWidth = NUMERIC_CELL_WIDTH[cell];
        int fieldWidth = Math.max(34, cellWidth - font.width(label) - 4);
        int fieldX = NUMERIC_CELL_X[cell] + cellWidth - fieldWidth;

        EditBox field = new EditBox(font, leftPos + fieldX, topPos + ROW_FOURTH_Y, fieldWidth,
                ROW_HEIGHT, label);
        if (scaled) {
            // 「数量」没有上限，允许 K / M / G / T / P / E 输入——和矿石生成器的能量输入同一套。
            field.setMaxLength(24);
            field.setFilter(ScaledEnergyAmount::isValidInput);
        } else {
            field.setMaxLength(6);
            field.setFilter(value -> value.isEmpty()
                    || (allowNegative ? value.matches("-?\\d{0,3}") : value.matches("\\d{0,5}")));
        }
        addRenderableWidget(field);
        trackEdits(field);
        numericFields.add(new NumericSpec(field, label, min, max,
                Component.translatable("gui.useless_mod.wireless_logistics." + labelKey + "_hint"),
                scaled));
        return field;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 焦点离开输入框时把这次编辑提交掉，否则下一 tick 的同步会把它覆盖回去。
        EditBox focused = focusedField();
        if (focusedField != null && focused != focusedField) {
            applyEdits();
        }
        focusedField = focused;

        // 兜底：玩家填完值没失焦（比如直接看效果）时，停手一会儿就自动提交，
        // 否则设置根本没送到服务端，表现就是「明明填了 2 却还是按旧值搬」。
        if (focused == null) {
            editIdleTicks = 0;
        } else if (++editIdleTicks >= AUTO_COMMIT_TICKS) {
            applyEdits();
            editIdleTicks = 0;
        }

        if (noticeTicks > 0 && --noticeTicks == 0) {
            notice = null;
        }

        updateControls();
    }

    /** 界面收到服务端快照。 */
    public void receiveSync(StaffLinkSyncPacket packet) {
        menu.receiveSync(packet.network(), packet.index(), packet.count());
        clampScroll();
        updateControls();
    }

    /** 界面收到服务端的「上次搬运」读数。 */
    public void receiveStatus(UUID networkId, long requested, long moved, int targets, long tick,
                              StaffLinkTargets.TransferBlocker blocker) {
        if (!networkId.equals(menu.getNetworkId())) {
            return;
        }
        menu.receiveStatus(tick, requested, moved, targets, blocker);
    }

    @Override
    public void onClose() {
        applyEdits();
        super.onClose();
    }

    // ------------------------------------------------------------------ 状态刷新

    private void selectRoute(int route) {
        menu.setSelection(menu.getSelectedAnchor(), route);
        ensureRouteConfig();
        updateControls();
    }

    /** 选中的「锚点 × 线路」还没有配置时补一条默认的，让配置区永远有东西可调。 */
    private void ensureRouteConfig() {
        GlobalPos anchor = menu.getSelectedAnchor();
        if (anchor != null && menu.isAnchorBound(anchor) && menu.getSelectedConfig() == null) {
            // 只给这一台补默认值，不能走批量：多选期间点一下别的机器不该把默认值糊到全体上。
            menu.applyRouteSingle(anchor, menu.defaultRouteFor(anchor, menu.getSelectedRoute()));
        }
    }

    private void newNetwork() {
        // 先把当前网络名提交掉，再让输入框失焦——否则新网络的空名字会被旧名字覆盖回去。
        applyEdits();
        setFocused(null);
        menu.createNetwork();
        scrollOffset = 0;
    }

    /** 切到相邻的一张网络；先把当前编辑提交掉，免得刚改的值丢了。 */
    private void cycleNetwork(int delta) {
        applyEdits();
        setFocused(null);
        menu.cycleNetwork(delta);
        scrollOffset = 0;
    }

    private void dissolveNetwork() {
        applyEdits();
        setFocused(null);
        menu.dissolveNetwork();
        scrollOffset = 0;
    }

    private interface ConfigEdit {
        StaffLinkRoute apply(StaffLinkRoute config);
    }

    private void edit(ConfigEdit editor) {
        StaffLinkRoute config = menu.getSelectedConfig();
        if (config == null) {
            return;
        }
        menu.applyRoute(editor.apply(config));
        updateControls();
    }

    /** 把改名框、网络名框与三个数值输入框的内容提交上去。 */
    private void applyEdits() {
        String requestedNetwork = networkNameField.getValue().trim();
        if (!requestedNetwork.equals(menu.getNetworkName())) {
            menu.renameNetwork(requestedNetwork);
        }

        GlobalPos anchor = menu.getSelectedAnchor();
        if (anchor != null && menu.isAnchorBound(anchor)) {
            String requested = nameField.getValue().trim();
            String current = menu.getAnchorName(anchor);
            if (!requested.equals(current == null ? "" : current)) {
                menu.renameAnchor(anchor, requested);
            }
        }

        StaffLinkRoute config = menu.getSelectedConfig();
        if (config == null) {
            return;
        }
        long weight = parsePlain(weightField.getValue(), config.weight(),
                StaffLinkRoute.MIN_WEIGHT, StaffLinkRoute.MAX_WEIGHT);
        long amount = parseScaled(amountField.getValue(), config.amount(), StaffLinkRoute.MIN_AMOUNT);
        long interval = parsePlain(intervalField.getValue(), config.interval(),
                StaffLinkRoute.MIN_INTERVAL, StaffLinkRoute.MAX_INTERVAL);
        if (weight != config.weight() || amount != config.amount() || interval != config.interval()) {
            menu.applyRoute(withNumbers(config, (int) weight, amount, (int) interval));
        }
    }

    private static long parsePlain(String text, long fallback, long min, long max) {
        try {
            return Mth.clamp(Long.parseLong(text.trim()), min, max);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** 「数量」允许 {@code K / M / G / T / P / E} 后缀；解析不了就回落到原值。 */
    private static long parseScaled(String text, long fallback, long min) {
        OptionalLong parsed = ScaledEnergyAmount.parse(text, Long.MAX_VALUE);
        return parsed.isPresent() ? Math.max(min, parsed.getAsLong()) : fallback;
    }

    /** 输入框里当前的「数量」（解析不出来按 0 算），用来做实时提示。 */
    private long currentAmount() {
        return parseScaled(amountField.getValue(), 0L, 0L);
    }

    /**
     * 「数量」的显示形式。
     *
     * <p>只在<b>缩写的能原样解析回来</b>时才用缩写（1000 → {@code 1K}，1e12 → {@code 1T}）；
     * 像 1024 这种缩写会丢精度的（{@code 1.02K} 只能解析回 1020）就老老实实显示原数字——
     * 否则玩家点一下别的按钮触发提交，数量就被悄悄改小了。</p>
     */
    private static String formatAmount(long amount) {
        String scaled = ScaledEnergyAmount.format(amount);
        OptionalLong roundTrip = ScaledEnergyAmount.parse(scaled, Long.MAX_VALUE);
        return roundTrip.isPresent() && roundTrip.getAsLong() == amount
                ? scaled : String.valueOf(amount);
    }

    private void updateControls() {
        StaffLinkRoute config = menu.getSelectedConfig();
        boolean hasConfig = config != null;
        GlobalPos anchor = menu.getSelectedAnchor();
        boolean hasAnchor = anchor != null && menu.isAnchorBound(anchor);

        flowButton.active = hasConfig;
        mediumButton.active = hasConfig;
        enabledButton.active = hasConfig;
        sideButton.active = hasConfig;
        triggerButton.active = hasConfig;
        if (!hasConfig) {
            // 没选中线路时下拉没有意义，顺手收起来。
            mediumMenuOpen = false;
        }
        int[] position = staffNetworkPosition();
        boolean multipleNetworks = position[1] > 1;
        prevNetworkButton.active = multipleNetworks;
        nextNetworkButton.active = multipleNetworks;
        newNetworkButton.active = true;
        dissolveButton.active = true;
        // 这三个按钮的状态依赖「可见锚点」与「多选集合」，两者每 tick 都可能变，
        // 所以必须放在下面的 signature 提前 return 之前。
        List<GlobalPos> visible = visibleAnchors();
        selectAllButton.active = !visible.isEmpty();
        clearSelectionButton.active = !menu.getMultiSelection().isEmpty();
        applyAllButton.active = hasConfig && !visible.isEmpty();
        networkNameField.setEditable(true);
        nameField.setEditable(hasAnchor);
        // 推模型里只有「释放」端发起搬运：吸收端的「数量 / 周期」不参与，禁掉以免误解。
        // （「权重」对两端都有意义：输入端之间排序、输出端之间排序都看它。）
        boolean initiates = hasConfig && config.flow() == LinkFlow.RELEASE;
        weightField.active = hasConfig;
        amountField.active = initiates;
        intervalField.active = initiates;
        weightField.setEditable(hasConfig);
        amountField.setEditable(initiates);
        intervalField.setEditable(initiates);

        // 正在输入的框不要被同步覆盖，否则打字会被打断。
        if (!networkNameField.isFocused()) {
            setFieldValue(networkNameField, menu.getNetworkName());
        }
        if (!nameField.isFocused()) {
            String name = hasAnchor ? menu.getAnchorName(anchor) : null;
            setFieldValue(nameField, name == null ? "" : name);
        }
        if (config == null) {
            if (!weightField.isFocused()) setFieldValue(weightField, "");
            if (!amountField.isFocused()) setFieldValue(amountField, "");
            if (!intervalField.isFocused()) setFieldValue(intervalField, "");
        } else {
            if (!weightField.isFocused()) setFieldValue(weightField, String.valueOf(config.weight()));
            if (!amountField.isFocused()) setFieldValue(amountField, formatAmount(config.amount()));
            if (!intervalField.isFocused()) setFieldValue(intervalField, String.valueOf(config.interval()));
        }

        String signature = hasConfig
                ? config.flow() + "|" + config.medium() + "|" + config.enabled() + "|" + config.side()
                  + "|" + config.trigger() + "|" + menu.getSelectedRoute()
                : "none|" + menu.getSelectedRoute();
        if (signature.equals(controlSignature)) {
            return;
        }
        controlSignature = signature;

        if (!hasConfig) {
            Component empty = Component.literal("-");
            flowButton.setMessage(empty);
            mediumButton.setMessage(empty);
            enabledButton.setMessage(empty);
            sideButton.setMessage(empty);
            triggerButton.setMessage(empty);
            return;
        }

        flowButton.setMessage(label("flow_label", config.flow().displayName()));
        mediumButton.setMessage(label("medium_label", config.medium().displayName()));
        enabledButton.setMessage(label("enabled_label",
                Component.translatable(config.enabled()
                        ? "gui.useless_mod.wireless_logistics.state_on"
                        : "gui.useless_mod.wireless_logistics.state_off")));
        sideButton.setMessage(label("side_label", config.side() == null
                ? Component.translatable("gui.useless_mod.wireless_logistics.side.null")
                : Component.translatable("gui.useless_mod.wireless_logistics.side."
                        + config.side().getName())));
        triggerButton.setMessage(label("trigger_label", config.trigger().displayName()));
    }

    private static void setFieldValue(EditBox field, String value) {
        if (!value.equals(field.getValue())) {
            field.setValue(value);
        }
    }

    private static Component label(String key, Component value) {
        return Component.translatable("gui.useless_mod.wireless_logistics." + key, value);
    }

    // ------------------------------------------------------------------ 搜索过滤

    /** 经过搜索过滤后的锚点列表。 */
    private List<GlobalPos> visibleAnchors() {
        List<GlobalPos> anchors = menu.getAnchors();
        String query = searchField == null ? "" : searchField.getValue().trim();
        if (query.isEmpty()) {
            return anchors;
        }
        List<GlobalPos> filtered = new ArrayList<>();
        for (GlobalPos anchor : anchors) {
            if (matchesSearch(anchor, query)) {
                filtered.add(anchor);
            }
        }
        return filtered;
    }

    /** 自定义名、方块本名、坐标都参与匹配；方块名走 {@link PinyinSearch} 所以支持拼音。 */
    private boolean matchesSearch(GlobalPos anchor, String query) {
        String custom = menu.getAnchorName(anchor);
        if (custom != null && PinyinSearch.matches(custom, query)) {
            return true;
        }
        String blockName = blockNameOf(anchor);
        if (blockName != null && PinyinSearch.matches(blockName, query)) {
            return true;
        }
        return anchor.pos().toShortString().contains(query);
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawInset(graphics, leftPos + INSET_LEFT, topPos + 18,
                leftPos + INSET_RIGHT, topPos + LIST_INSET_BOTTOM);
        MachineScreenStyle.drawInset(graphics, leftPos + INSET_LEFT, topPos + CONFIG_INSET_TOP,
                leftPos + INSET_RIGHT, topPos + 238);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 44, 254, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 44, 312, 9, 1);
        renderFilterSlotBackgrounds(graphics);
        for (Slot slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    private void renderFilterSlotBackgrounds(GuiGraphics graphics) {
        boolean active = menu.isFilterActive();
        int fill = active ? MachineScreenStyle.SLOT_COLOR : 0xFF777B8D;
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            int x = leftPos + filterSlotX(index);
            int y = topPos + filterSlotY(index);
            graphics.fill(x, y, x + FILTER_SLOT_SIZE, y + FILTER_SLOT_SIZE, fill);
            if (active) {
                graphics.fill(x, y, x + FILTER_SLOT_SIZE, y + 1, MachineScreenStyle.SLOT_SHADOW_COLOR);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        renderNetworkLabel(graphics);
        renderAnchorList(graphics);
        renderNumericLabels(graphics);
        graphics.drawString(font, Component.translatable("gui.useless_mod.wireless_logistics.filter"),
                FILTER_X + FILTER_COLUMNS * FILTER_SLOT_STEP + 2, FILTER_Y + 4,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
        renderTransferStats(graphics);
        renderSelectionCount(graphics);
        renderNotice(graphics);
    }

    /** 标题行右侧的一次性提示（复制/粘贴等）；{@link #NOTICE_TICKS} tick 后自己消失。 */
    private void renderNotice(GuiGraphics graphics) {
        if (notice == null) {
            return;
        }
        graphics.drawString(font, notice, CONTENT_RIGHT - font.width(notice), titleLabelY,
                NOTICE_COLOR, false);
    }

    /** 批量编辑计数：多选非空时在配置区右下角提示选了几台（空着时那一片正好没人用）。 */
    private void renderSelectionCount(GuiGraphics graphics) {
        int count = menu.getMultiSelection().size();
        if (count <= 0) {
            return;
        }
        Component text = Component.translatable(
                "gui.useless_mod.wireless_logistics.multi_select_count", count);
        graphics.drawString(font, text, CONTENT_RIGHT - font.width(text), FILTER_Y + 22,
                MULTI_SELECT_TEXT_COLOR, false);
    }

    /**
     * 「上次搬运」读数：实际搬走 / 请求，搬不动时补一句卡在哪。
     *
     * <p>有这行数字就能一眼分清「设置没送到服务端」（请求量还是旧值）和
     * 「送过去了但搬不动」（请求量对、搬走 0，并给出原因）。</p>
     */
    private void renderTransferStats(GuiGraphics graphics) {
        StaffLinkEngine.TransferStats stats = menu.getLastStats();
        Component text;
        int color = MachineScreenStyle.MUTED_TEXT_COLOR;
        if (stats.tick() < 0) {
            text = Component.translatable("gui.useless_mod.wireless_logistics.stats_none");
        } else if (stats.blocker() != StaffLinkTargets.TransferBlocker.NONE) {
            text = Component.translatable("gui.useless_mod.wireless_logistics.stats_blocked",
                    stats.moved(), stats.requested(),
                    Component.translatable(blockerKey(stats.blocker())));
            color = MachineScreenStyle.ERROR_TEXT_COLOR;
        } else {
            text = Component.translatable("gui.useless_mod.wireless_logistics.stats",
                    stats.moved(), stats.requested());
            if (stats.moved() > 0) {
                color = 0xFF2E7D32;
            } else if (stats.requested() > 0) {
                color = MachineScreenStyle.ERROR_TEXT_COLOR;
            }
        }
        graphics.drawString(font, text, FILTER_X + FILTER_COLUMNS * FILTER_SLOT_STEP + 2, FILTER_Y + 20,
                color, false);
    }

    private static String blockerKey(StaffLinkTargets.TransferBlocker blocker) {
        return switch (blocker) {
            case SOURCE_UNREACHABLE -> "gui.useless_mod.wireless_logistics.blocker.source_unreachable";
            case TARGET_UNREACHABLE -> "gui.useless_mod.wireless_logistics.blocker.target_unreachable";
            case FILTERED -> "gui.useless_mod.wireless_logistics.blocker.filtered";
            case SOURCE_EMPTY -> "gui.useless_mod.wireless_logistics.blocker.source_empty";
            case SOURCE_REJECTED -> "gui.useless_mod.wireless_logistics.blocker.source_rejected";
            case TARGET_REJECTED -> "gui.useless_mod.wireless_logistics.blocker.target_rejected";
            case NONE -> "gui.useless_mod.wireless_logistics.stats_none";
        };
    }

    /** 悬停读数时给出完整含义（含分给了几个输出）。 */
    private void renderStatsTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        int statsX = FILTER_X + FILTER_COLUMNS * FILTER_SLOT_STEP + 2;
        if (localX < statsX || localX > CONTENT_RIGHT || localY < FILTER_Y + 18 || localY > FILTER_Y + 32) {
            return;
        }
        StaffLinkEngine.TransferStats stats = menu.getLastStats();
        graphics.renderTooltip(font,
                List.of(Component.translatable("gui.useless_mod.wireless_logistics.stats_hint",
                        stats.moved(), stats.requested(), stats.targets())),
                Optional.empty(), mouseX, mouseY);
    }

    /** 第一行右侧：「网络 2/3」，右对齐到面板右边。 */
    private void renderNetworkLabel(GuiGraphics graphics) {
        int[] position = staffNetworkPosition();
        Component text = Component.translatable("gui.useless_mod.wireless_logistics.network_position",
                position[0] + 1, position[1]);
        graphics.drawString(font, text, CONTENT_RIGHT - font.width(text), NETWORK_ROW_Y + 3,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private void renderAnchorList(GuiGraphics graphics) {
        List<GlobalPos> anchors = visibleAnchors();
        if (anchors.isEmpty()) {
            Component hint = menu.getAnchors().isEmpty()
                    ? Component.translatable("gui.useless_mod.wireless_logistics.empty")
                    : Component.translatable("gui.useless_mod.wireless_logistics.search_no_match");
            graphics.drawString(font, hint, CONTENT_LEFT + 2, LIST_FIRST_ROW_Y,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            return;
        }

        for (int row = 0; row < LIST_VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            if (index >= anchors.size()) {
                break;
            }
            GlobalPos anchor = anchors.get(index);
            int y = LIST_FIRST_ROW_Y + row * LIST_ROW_HEIGHT;

            // 选中态用「底色 + 左侧竖条 + 行尾 ✓」三重标记：只靠一层底色太淡，
            // 一堆同名容器时根本看不出哪些进了批量编辑。
            boolean isSelected = anchor.equals(menu.getSelectedAnchor());
            boolean isMulti = menu.isMultiSelected(anchor);
            if (isMulti) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT, y + 9, MULTI_SELECT_COLOR);
            } else if (isSelected) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT, y + 9,
                        MachineScreenStyle.HIGHLIGHT_COLOR);
            }
            if (isMulti) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_LEFT, y + 9, MULTI_SELECT_TEXT_COLOR);
            } else if (isSelected) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_LEFT, y + 9, SELECTED_BAR_COLOR);
            }
            if (isSelected && isMulti) {
                // 既是批量目标、又是配置区正在编辑的那台：加一圈描边区分出来。
                outline(graphics, CONTENT_LEFT - 2, y - 2, CONTENT_WIDTH + 2, 11, SELECTED_BAR_COLOR);
            }
            // 行首 ▲ / ▼：调整这个容器在列表里的次序（权重相同时，靠前的先拿）。
            // 能不能点看的是「可见列表里有没有上一行 / 下一行」——搜索过滤开着时，玩家期望的
            // 就是「跟上面那一行换位」，而不是跟列表里某个看不见的邻居换。
            drawMoveButton(graphics, CONTENT_LEFT + 1, y, true, index > 0);
            drawMoveButton(graphics, CONTENT_LEFT + 1 + LIST_MOVE_WIDTH, y, false,
                    index < anchors.size() - 1);

            // 名字前面标出「这条线路上它是发还是收」——一堆同名容器时，光看名字分不出谁在发。
            StaffLinkRoute routeConfig = menu.getConfig(anchor, menu.getSelectedRoute());
            String glyph = routeConfig == null ? "-" : routeConfig.flow() == LinkFlow.RELEASE ? ">" : "<";
            int glyphColor;
            if (routeConfig == null || !routeConfig.enabled()) {
                glyphColor = MachineScreenStyle.MUTED_TEXT_COLOR;
            } else if (routeConfig.flow() == LinkFlow.RELEASE) {
                glyphColor = 0xFF2E7D32;
            } else {
                glyphColor = 0xFF3B6EA5;
            }
            graphics.drawString(font, glyph, LIST_GLYPH_X, y, glyphColor, false);

            // 名字后面缀上坐标：多个同名容器（都叫「箱子」）光看名字根本分不出来。
            // 额外预留 8px 给行尾的批量 ✓，无条件预留，免得勾选/取消时名字左右跳。
            String coord = anchor.pos().toShortString();
            int nameBudget = Math.max(24,
                    CONTENT_WIDTH - LIST_TEXT_SHIFT - LIST_UNBIND_WIDTH - 16 - 8 - font.width(coord));
            String name = font.plainSubstrByWidth(anchorDisplayName(anchor).getString(), nameBudget);
            graphics.drawString(font, name, LIST_NAME_X, y, MachineScreenStyle.TEXT_COLOR, false);
            graphics.drawString(font, coord, LIST_NAME_X + font.width(name) + 4, y,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            if (isMulti) {
                graphics.drawString(font, "✓", CONTENT_RIGHT - LIST_UNBIND_WIDTH - 8, y,
                        MULTI_SELECT_TEXT_COLOR, false);
            }
            graphics.drawString(font, "x", CONTENT_RIGHT - LIST_UNBIND_WIDTH + 3, y,
                    MachineScreenStyle.ERROR_TEXT_COLOR, false);
        }
    }

    /**
     * 画一个行首的「上移 / 下移」小按钮。
     *
     * <p>用 ▲ / ▼ 文字字形而不是自绘三角：默认字体带了 unicode 回退，行尾的批量 ✓ 就是这么画的。</p>
     */
    private void drawMoveButton(GuiGraphics graphics, int x, int y, boolean up, boolean enabled) {
        graphics.drawString(font, up ? "▲" : "▼", x, y,
                enabled ? MachineScreenStyle.TEXT_COLOR : MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    /**
     * 判断点击落在行首的哪个移动按钮上。
     *
     * @return -1 上移按钮、+1 下移按钮、0 没点到按钮
     */
    private int moveButtonAt(double localX) {
        int left = CONTENT_LEFT + 1;
        if (localX >= left && localX < left + LIST_MOVE_WIDTH) {
            return -1;
        }
        if (localX >= left + LIST_MOVE_WIDTH && localX < left + LIST_MOVE_WIDTH * LIST_MOVE_COUNT) {
            return 1;
        }
        return 0;
    }

    /**
     * 「往上 / 往下越过一个<b>可见</b>行」对应的目标下标（在完整锚点列表里）。
     *
     * <p>搜索过滤开着时，可见的上一行未必是列表里的上一个，所以要用可见邻居去换算目标下标，
     * 服务端只管挪到那个位置。</p>
     *
     * @return 目标下标；已经到可见列表的头 / 尾时返回 -1
     */
    private int moveTargetIndex(List<GlobalPos> visible, GlobalPos anchor, int direction) {
        int visibleIndex = visible.indexOf(anchor);
        if (visibleIndex < 0) {
            return -1;
        }
        int neighbour = visibleIndex + direction;
        if (neighbour < 0 || neighbour >= visible.size()) {
            return -1;
        }
        return menu.getAnchors().indexOf(visible.get(neighbour));
    }

    /** 锚点显示名：玩家改过的名字优先，否则用方块本名，区块没加载时退回坐标。 */
    private Component anchorDisplayName(GlobalPos anchor) {
        String custom = menu.getAnchorName(anchor);
        if (custom != null) {
            return Component.literal(custom);
        }
        String blockName = blockNameOf(anchor);
        return blockName != null ? Component.literal(blockName) : Component.literal(anchor.pos().toShortString());
    }

    /** 该坐标上方块的本名；维度不匹配或区块未加载时返回 {@code null}。 */
    @Nullable
    private String blockNameOf(GlobalPos anchor) {
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null || !anchor.dimension().equals(level.dimension()) || !level.isLoaded(anchor.pos())) {
            return null;
        }
        return level.getBlockState(anchor.pos()).getBlock().getName().getString();
    }

    private void renderNumericLabels(GuiGraphics graphics) {
        for (NumericSpec spec : numericFields) {
            int labelX = spec.field().getX() - leftPos - font.width(spec.label()) - 4;
            graphics.drawString(font, spec.label(), labelX, ROW_FOURTH_Y + 4,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            if (!spec.field().active) {
                // 不生效的输入框压一层暗色，一眼能看出来它不可编辑。
                graphics.fill(spec.field().getX(), spec.field().getY(),
                        spec.field().getX() + spec.field().getWidth(),
                        spec.field().getY() + spec.field().getHeight(), 0x66000000);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFilterItems(graphics);
        PressableAE2Button selected = routeButtons[menu.getSelectedRoute()];
        if (menu.getSelectedAnchor() != null) {
            outline(graphics, selected.getX() - 1, selected.getY() - 1,
                    selected.getWidth() + 2, selected.getHeight() + 2, MachineScreenStyle.TEXT_COLOR);
        }
        if (mediumMenuOpen) {
            // 下拉盖住了下面的控件：这时候再弹它们的悬停提示只会互相打架。
            // 连容器槽的 tooltip 也一起跳过——鼠标其实停在下拉上，不该弹出底下那个槽的说明。
            renderMediumMenu(graphics, mouseX, mouseY);
            return;
        }
        renderAnchorTooltip(graphics, mouseX, mouseY);
        renderFilterTooltip(graphics, mouseX, mouseY);
        renderNumericTooltip(graphics, mouseX, mouseY);
        renderStatsTooltip(graphics, mouseX, mouseY);
        renderNetworkTooltip(graphics, mouseX, mouseY);
        renderRouteTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 画资源类型下拉。
     *
     * <p>铺在按钮正下方，选项来自 {@link LinkMedium#supported()}：装了化学品 / 魔源 / 通量之后
     * 类型能到十种，一路轮换过去太费手，直接列出来点。</p>
     */
    private void renderMediumMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mediumOptions.isEmpty()) {
            return;
        }
        int x = leftPos + MEDIUM_BUTTON_X;
        int y = topPos + MEDIUM_MENU_Y;

        // 先把此前攒下的批处理内容落地。
        //
        // GuiGraphics 是「按图层批处理、最后统一提交」的，不是按调用顺序即时绘制：
        // 背包槽位、标签、输入框都在 super.render() 里进了同一批，即便我们在它之后才画，
        // 提交时仍会按图层顺序排到弹出层前面（表现就是物品和输入框盖在下拉菜单上）。
        // 先 flush 一次把它们定型，弹出层随后单独成一批。
        graphics.flush();
        MachineScreenStyle.drawPanel(graphics, x, y, MEDIUM_MENU_WIDTH,
                mediumOptions.size() * MEDIUM_MENU_ROW_HEIGHT);

        LinkMedium current = menu.getSelectedMedium();
        int hovered = mediumMenuIndexAt(mouseX - leftPos, mouseY - topPos);
        for (int index = 0; index < mediumOptions.size(); index++) {
            LinkMedium option = mediumOptions.get(index);
            int rowY = y + index * MEDIUM_MENU_ROW_HEIGHT;
            if (option == current) {
                // 当前这一项给个底色，玩家一眼看到自己在哪一档。
                graphics.fill(x + 2, rowY, x + MEDIUM_MENU_WIDTH - 2,
                        rowY + MEDIUM_MENU_ROW_HEIGHT, MachineScreenStyle.HIGHLIGHT_COLOR);
            } else if (index == hovered) {
                graphics.fill(x + 2, rowY, x + MEDIUM_MENU_WIDTH - 2,
                        rowY + MEDIUM_MENU_ROW_HEIGHT, MachineScreenStyle.SLOT_COLOR);
            }
            Component text = option.displayName();
            graphics.drawString(font, text, x + (MEDIUM_MENU_WIDTH - font.width(text)) / 2,
                    rowY + 3,
                    option == current ? MachineScreenStyle.TEXT_COLOR : MachineScreenStyle.SUBTLE_TEXT_COLOR,
                    false);
        }
        // 再落一次地，让弹出层立刻定型——否则本帧后面还有内容入批时会重新排到它前面。
        graphics.flush();
    }

    /** 命中的下拉项下标；没命中返回 -1。 */
    private int mediumMenuIndexAt(double localX, double localY) {
        if (localX < MEDIUM_BUTTON_X || localX >= MEDIUM_BUTTON_X + MEDIUM_MENU_WIDTH
                || localY < MEDIUM_MENU_Y) {
            return -1;
        }
        int index = (int) ((localY - MEDIUM_MENU_Y) / MEDIUM_MENU_ROW_HEIGHT);
        return index >= 0 && index < mediumOptions.size() ? index : -1;
    }

    /** 悬停线路按钮行时说明 Ctrl+C / Ctrl+V 能整条复制粘贴配置——否则这个快捷键没人发现得了。 */
    private void renderRouteTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY < ROUTE_ROW_Y || localY > ROUTE_ROW_Y + ROW_HEIGHT
                || localX < CONTENT_LEFT || localX > CONTENT_RIGHT) {
            return;
        }
        graphics.renderTooltip(font,
                List.of(Component.translatable(
                        "gui.useless_mod.wireless_logistics.config_copy_hint",
                        menu.getSelectedRoute())),
                Optional.empty(), mouseX, mouseY);
    }

    private void renderFilterItems(GuiGraphics graphics) {
        if (!menu.isFilterActive()) {
            return;
        }
        List<LinkFilterSlot> mirror = menu.getFilterMirror();
        for (int index = 0; index < mirror.size() && index < StaffLinkRoute.FILTER_LIMIT; index++) {
            LinkFilterSlot slot = mirror.get(index);
            if (slot.isEmpty()) {
                continue;
            }
            int x = leftPos + filterSlotX(index);
            int y = topPos + filterSlotY(index);
            if (slot.isFluid()) {
                renderFluidMarker(graphics, x, y, slot.fluid());
            } else {
                graphics.renderItem(slot.item(), x, y);
            }
        }
    }

    /**
     * 把<b>流体本身</b>画进过滤槽：取它的静止贴图、按流体的染色画满一格。
     *
     * <p>刻意不画桶。槽里标记的就是这种流体，画个桶会让玩家以为过滤的是「水桶这个物品」——
     * 那正是这次要修掉的混淆。</p>
     */
    private void renderFluidMarker(GuiGraphics graphics, int x, int y, FluidStack fluid) {
        if (fluid.isEmpty() || fluid.getFluid() == Fluids.EMPTY || minecraft == null) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extensions.getStillTexture(fluid));

        int tint = extensions.getTintColor(fluid);
        float alpha = ((tint >> 24) & 0xFF) / 255.0F;
        graphics.setColor(
                ((tint >> 16) & 0xFF) / 255.0F,
                ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F,
                alpha == 0.0F ? 1.0F : alpha);
        graphics.blit(x, y, 0, FILTER_SLOT_SIZE, FILTER_SLOT_SIZE, sprite);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 悬停锚点行时给出「维度 + 坐标」的完整信息，方便确认改名的到底是哪一个。 */
    private void renderAnchorTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int row = anchorRowAt(mouseX - leftPos, mouseY - topPos);
        if (row < 0) {
            return;
        }
        List<GlobalPos> anchors = visibleAnchors();
        int index = scrollOffset + row;
        if (index >= anchors.size()) {
            return;
        }
        GlobalPos anchor = anchors.get(index);
        List<Component> lines = new ArrayList<>(4);
        lines.add(anchorDisplayName(anchor));
        lines.add(Component.literal(
                anchor.dimension().location() + " " + anchor.pos().toShortString()));
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.highlight_hint"));
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.multi_select_hint"));
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.anchor_order_hint"));
        if (menu.isMultiSelected(anchor)) {
            lines.add(Component.translatable(
                    "gui.useless_mod.wireless_logistics.multi_select_active"));
        }
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** 过滤器槽的说明：标记的是资源本身（流体就是流体、物品就是物品），得讲清楚。 */
    private void renderFilterTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = filterSlotAt(mouseX - leftPos, mouseY - topPos);
        if (index < 0 || !menu.isFilterActive()) {
            return;
        }
        LinkFilterSlot marker = menu.getFilterMirror().get(index);
        List<Component> lines = new ArrayList<>(2);
        if (marker.isFluid()) {
            lines.add(marker.fluid().getHoverName());
        } else if (marker.isItem()) {
            lines.add(marker.item().getHoverName());
        }
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.filter_hint"));
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /**
     * 悬停网络名 / 切换按钮时说明这一行是干什么的。
     *
     * <p>顺带讲清归属：网络挂在玩家（组队后是队伍）名下，和手上这把杖无关。玩家最容易
     * 误解的就是「换把杖、把杖放进箱子，网络还在不在」。</p>
     */
    private void renderNetworkTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY < NETWORK_ROW_Y || localY > NETWORK_ROW_Y + SMALL_FIELD_HEIGHT) {
            return;
        }
        boolean overField = localX >= NETWORK_FIELD_X && localX < NETWORK_FIELD_X + NETWORK_FIELD_WIDTH;
        boolean overPrev = localX >= PREV_BUTTON_X && localX < PREV_BUTTON_X + NETWORK_SWITCH_WIDTH;
        boolean overNext = localX >= NEXT_BUTTON_X && localX < NEXT_BUTTON_X + NETWORK_SWITCH_WIDTH;
        if (!overField && !overPrev && !overNext) {
            return;
        }
        graphics.renderTooltip(font, List.of(
                        Component.translatable(overField
                                ? "gui.useless_mod.wireless_logistics.network_name_hint"
                                : "gui.useless_mod.wireless_logistics.network_switch_hint"),
                        Component.translatable("gui.useless_mod.wireless_logistics.network_owned_hint")),
                Optional.empty(), mouseX, mouseY);
    }

    /** 悬停数值框时给出完整含义与可填范围（英文标签是短名，靠这里补全）。 */
    private void renderNumericTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (NumericSpec spec : numericFields) {
            if (!spec.field().isMouseOver(mouseX, mouseY)) {
                continue;
            }
            List<Component> lines = new ArrayList<>(4);
            lines.add(spec.label());
            lines.add(spec.hint());
            if (!spec.field().active) {
                lines.add(Component.translatable(
                        "gui.useless_mod.wireless_logistics.release_only_hint"));
            }
            // 「数量」没有上限，只提示下限。
            lines.add(spec.scaled()
                    ? Component.translatable("gui.useless_mod.wireless_logistics.range_min", spec.min())
                    : Component.translatable("gui.useless_mod.wireless_logistics.range",
                            spec.min(), spec.max()));
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && mediumMenuOpen) {
            // 先收下拉，别顺手把整个界面关了。
            mediumMenuOpen = false;
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && anyFieldFocused()) {
            applyEdits();
            setFocused(null);
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            for (EditBox field : fields()) {
                if (field.isFocused()
                        && (field.keyPressed(keyCode, scanCode, modifiers) || field.canConsumeInput())) {
                    // 有输入框聚焦时 Ctrl+C/V 是「复制/粘贴文本」，轮不到配置剪贴板。
                    return true;
                }
            }
        }
        // 到这儿说明没有输入框在抢按键，Ctrl+C / Ctrl+V 才归配置剪贴板。
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                copyConfig();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                pasteConfig();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Ctrl+C：把当前线路的配置存进会话剪贴板。
     *
     * <p>连过滤器一起复制——只复制数值、把过滤器落下，玩家还得再拖一遍标记，那就不叫复制了。</p>
     */
    private void copyConfig() {
        StaffLinkRoute config = menu.getSelectedConfig();
        if (config == null) {
            return;
        }
        copiedConfig = config;
        showNotice(Component.translatable("gui.useless_mod.wireless_logistics.config_copied"));
    }

    /**
     * Ctrl+V：把剪贴板里的配置贴到当前选中线路。
     *
     * <p>走 {@link StaffLinkMenu#applyRoute}，所以多选非空时会一次贴给所有被选锚点——
     * 「配好一台 → 圈选其余 → Ctrl+V」就是一条批量套用路径。</p>
     */
    private void pasteConfig() {
        StaffLinkRoute source = copiedConfig;
        GlobalPos anchor = menu.getSelectedAnchor();
        if (source == null || anchor == null || !menu.isAnchorBound(anchor)) {
            return;
        }
        // 先把输入框里没提交的值落下去，否则它会在随后回来的快照里覆盖掉刚贴上的配置。
        applyEdits();
        menu.applyRoute(new StaffLinkRoute(
                anchor, menu.getSelectedRoute(),
                source.enabled(), source.flow(), source.medium(), source.amount(), source.interval(),
                source.side(), source.trigger(), source.weight(), source.filter()));
        updateControls();
        showNotice(Component.translatable("gui.useless_mod.wireless_logistics.config_pasted"));
    }

    /** 显示一条一次性提示，{@link #NOTICE_TICKS} tick 后自己消失。 */
    private void showNotice(Component message) {
        notice = message;
        noticeTicks = NOTICE_TICKS;
    }

    private List<EditBox> fields() {
        List<EditBox> all = new ArrayList<>(numericFields.size() + 3);
        all.add(networkNameField);
        all.add(searchField);
        all.add(nameField);
        for (NumericSpec spec : numericFields) {
            all.add(spec.field());
        }
        return all;
    }

    /** 当前有焦点的输入框；都没有时返回 {@code null}。 */
    @Nullable
    private EditBox focusedField() {
        for (EditBox field : fields()) {
            if (field.isFocused()) {
                return field;
            }
        }
        return null;
    }

    private boolean anyFieldFocused() {
        return focusedField() != null;
    }

    private boolean isOverAnyField(double mouseX, double mouseY) {
        for (EditBox field : fields()) {
            if (field.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;

        // 下拉展开时先吃掉这一次点击：点在选项上就选中，点在别处就收起。
        // 这也是下拉的常规行为——第一次点只负责关掉它，不会顺手点到下面的控件。
        if (mediumMenuOpen) {
            int picked = mediumMenuIndexAt(localX, localY);
            mediumMenuOpen = false;
            if (picked >= 0 && picked < mediumOptions.size()) {
                LinkMedium medium = mediumOptions.get(picked);
                edit(config -> withMedium(config, medium));
            }
            return true;
        }

        int row = anchorRowAt(localX, localY);
        if (row >= 0) {
            List<GlobalPos> anchors = visibleAnchors();
            int index = scrollOffset + row;
            if (index < anchors.size()) {
                GlobalPos anchor = anchors.get(index);
                // 行首 ▲ / ▼：把这一台挪到「上一个 / 下一个可见行」的位置。
                int moveDirection = moveButtonAt(localX);
                if (moveDirection != 0) {
                    int target = moveTargetIndex(anchors, anchor, moveDirection);
                    if (target >= 0) {
                        menu.moveAnchorTo(anchor, target);
                    }
                    return true;
                }
                // 优先级：解绑(x) > Shift 范围 > Ctrl 加减 > 普通单选。
                if (localX >= CONTENT_RIGHT - LIST_UNBIND_WIDTH) {
                    menu.detach(anchor);
                    clampScroll();
                } else if (Screen.hasShiftDown() && lastClickedAnchor != null) {
                    selectRange(anchor);
                } else if (Screen.hasControlDown()) {
                    // Ctrl + 点击：把这一台加进 / 移出批量编辑的集合。
                    menu.toggleMultiSelection(anchor);
                    menu.setSelection(anchor, menu.getSelectedRoute());
                    ensureRouteConfig();
                } else {
                    // 普通点击是「重新单选」：顺手清掉上一次的批量选择，语义才不粘。
                    menu.clearMultiSelection();
                    menu.setSelection(anchor, menu.getSelectedRoute());
                    ensureRouteConfig();
                }
                lastClickedAnchor = anchor;
                updateControls();
            }
            return true;
        }

        int filterIndex = filterSlotAt(localX, localY);
        if (filterIndex >= 0) {
            if (menu.isFilterActive()) {
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) {
                    // 空手点一下 = 清掉这一格。
                    menu.setFilterSlot(filterIndex, LinkFilterSlot.EMPTY);
                } else {
                    // 按线路资源类型解释手上的东西：流体线路从容器里取出流体本身，
                    // 解释不了就不动这一格——绝不把不匹配的东西塞进去。
                    LinkFilterSlot slot = StaffLinkFilters.fromItem(menu.getSelectedMedium(), carried);
                    if (slot != null) {
                        menu.setFilterSlot(filterIndex, slot);
                    }
                }
                updateControls();
            }
            return true;
        }

        // 点到按钮等控件之前，先把输入框里已填但未提交的值提交掉。
        if (!isOverAnyField(mouseX, mouseY) && anyFieldFocused()) {
            applyEdits();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Shift+点击：从上次点击的锚点到当前锚点，按可见列表整段选中。
     *
     * <p>用锚点而不是行下标当端点：搜索过滤与滚动都会改变可见集合，记死下标会错位；
     * 上次那台已经被过滤掉或解绑时（{@code indexOf} 返回 -1）退化成只选当前这一台。</p>
     */
    private void selectRange(GlobalPos anchor) {
        List<GlobalPos> anchors = visibleAnchors();
        int to = anchors.indexOf(anchor);
        if (to < 0) {
            return;
        }
        int from = lastClickedAnchor == null ? -1 : anchors.indexOf(lastClickedAnchor);
        if (from < 0) {
            menu.setMultiSelection(List.of(anchor));
        } else {
            menu.setMultiSelection(anchors.subList(Math.min(from, to), Math.max(from, to) + 1));
        }
        menu.setSelection(anchor, menu.getSelectedRoute());
        ensureRouteConfig();
    }

    /** 「全选」：把当前列表（受搜索过滤）里的锚点整批加入批量编辑。 */
    private void selectAllVisible() {
        applyEdits();
        menu.setMultiSelection(visibleAnchors());
        ensureRouteConfig();
        updateControls();
    }

    /** 「清空」：退出批量编辑，只留单选。 */
    private void clearSelection() {
        menu.clearMultiSelection();
        updateControls();
    }

    /**
     * 「应用到全部」：把当前线路配置刷给列表里的全部锚点。
     *
     * <p>搜索框有内容时只作用于匹配到的锚点；走 {@link StaffLinkMenu#applyRouteTo}，
     * <b>不改变</b>玩家当前的多选状态。</p>
     */
    private void applyAllVisible() {
        applyEdits();
        StaffLinkRoute config = menu.getSelectedConfig();
        if (config == null) {
            return;
        }
        List<GlobalPos> targets = visibleAnchors();
        if (targets.isEmpty()) {
            return;
        }
        menu.applyRouteTo(targets, config);
        updateControls();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        flowButton.releaseVisualState();
        mediumButton.releaseVisualState();
        enabledButton.releaseVisualState();
        sideButton.releaseVisualState();
        triggerButton.releaseVisualState();
        prevNetworkButton.releaseVisualState();
        nextNetworkButton.releaseVisualState();
        newNetworkButton.releaseVisualState();
        dissolveButton.releaseVisualState();
        selectAllButton.releaseVisualState();
        clearSelectionButton.releaseVisualState();
        applyAllButton.releaseVisualState();
        for (PressableAE2Button routeButton : routeButtons) {
            routeButton.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY >= LIST_TOP && localY <= LIST_BOTTOM && localX >= CONTENT_LEFT && localX <= CONTENT_RIGHT) {
            int maxScroll = Math.max(0, visibleAnchors().size() - LIST_VISIBLE_ROWS);
            scrollOffset = Mth.clamp(scrollOffset + (scrollY > 0 ? -1 : 1), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int anchorRowAt(double localX, double localY) {
        if (localX < CONTENT_LEFT || localX > CONTENT_RIGHT) {
            return -1;
        }
        if (localY < LIST_TOP || localY > LIST_BOTTOM) {
            return -1;
        }
        int row = (int) ((localY - LIST_TOP) / LIST_ROW_HEIGHT);
        return row >= 0 && row < LIST_VISIBLE_ROWS ? row : -1;
    }

    private int filterSlotAt(double localX, double localY) {
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            int x = filterSlotX(index);
            int y = filterSlotY(index);
            if (localX >= x && localX < x + FILTER_SLOT_SIZE && localY >= y && localY < y + FILTER_SLOT_SIZE) {
                return index;
            }
        }
        return -1;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, visibleAnchors().size() - LIST_VISIBLE_ROWS);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private static int filterSlotX(int index) {
        return FILTER_X + (index % FILTER_COLUMNS) * FILTER_SLOT_STEP;
    }

    private static int filterSlotY(int index) {
        return FILTER_Y + (index / FILTER_COLUMNS) * FILTER_SLOT_STEP;
    }

    /** 过滤器槽的屏幕坐标（JEI 拖拽也要用）。 */
    public int filterSlotScreenX(int index) {
        return leftPos + filterSlotX(index);
    }

    public int filterSlotScreenY(int index) {
        return topPos + filterSlotY(index);
    }

    public static int filterSlotSize() {
        return FILTER_SLOT_SIZE;
    }

    public static int filterSlotCount() {
        return StaffLinkRoute.FILTER_LIMIT;
    }

    /**
     * 当前是「第几张 / 共几张」网络。
     *
     * <p>网络列表挂在<b>归属者</b>（玩家或队伍）名下、存在服务端存档里，客户端手里没有这份
     * 列表，所以位置信息由同步包一起带下来（见 {@code StaffLinkSyncPacket}）。</p>
     */
    private int[] staffNetworkPosition() {
        return new int[]{menu.getNetworkIndex(), menu.getNetworkCount()};
    }

    // ------------------------------------------------------------------ 小工具

    private static Direction nextSide(Direction current) {
        for (int i = 0; i < SIDE_ORDER.length; i++) {
            if (SIDE_ORDER[i] == current) {
                return SIDE_ORDER[(i + 1) % SIDE_ORDER.length];
            }
        }
        return SIDE_ORDER[0];
    }

    private static StaffLinkRoute withFlow(StaffLinkRoute config, LinkFlow flow) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), flow,
                config.medium(), config.amount(), config.interval(), config.side(), config.trigger(),
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withMedium(StaffLinkRoute config, LinkMedium medium) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                medium, config.amount(), config.interval(), config.side(), config.trigger(),
                config.weight(), pruneFilter(config.filter(), medium));
    }

    /**
     * 换资源类型时丢掉不适用的过滤标记。
     *
     * <p>不丢的话，物品线路上留下的物品标记会跟着走到流体线路上——它们一个流体也匹配不上，
     * 过滤器就变成「什么都不搬」，而玩家完全看不出原因。丢掉之后过滤器为空 = 不限制，
     * 行为安全；真要限制再重新拖一个就行。</p>
     *
     * <p>丢的时候<b>保留格位</b>（换成空格而不是把后面的往前挤），否则换个资源类型整排标记
     * 就跟着挪位置，玩家会以为配置被改乱了。</p>
     */
    private static List<LinkFilterSlot> pruneFilter(List<LinkFilterSlot> filter, LinkMedium medium) {
        boolean fluidRoute = medium.family() == ResourceFamily.FLUID;
        List<LinkFilterSlot> kept = new ArrayList<>(filter.size());
        for (LinkFilterSlot slot : filter) {
            boolean keep = slot.isEmpty() || (fluidRoute ? slot.isFluid() : slot.isItem());
            kept.add(keep ? slot : LinkFilterSlot.EMPTY);
        }
        return kept;
    }

    private static StaffLinkRoute withEnabled(StaffLinkRoute config, boolean enabled) {
        return new StaffLinkRoute(config.anchor(), config.route(), enabled, config.flow(),
                config.medium(), config.amount(), config.interval(), config.side(), config.trigger(),
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withSide(StaffLinkRoute config, Direction side) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                config.medium(), config.amount(), config.interval(), side, config.trigger(),
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withTrigger(StaffLinkRoute config, LinkTrigger trigger) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                config.medium(), config.amount(), config.interval(), config.side(), trigger,
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withNumbers(StaffLinkRoute config, int weight, long amount, int interval) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                config.medium(), amount, interval, config.side(), config.trigger(),
                weight, config.filter());
    }
}
