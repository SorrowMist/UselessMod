package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.client.render.StaffLinkHighlightRenderer;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.LinkFlow;import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.LinkTrigger;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.content.stafflink.StaffLinkTargets;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * 布局分两块：上面是「网络 + 容器列表」（可搜索、可改名、双击在世界里高亮），
 * 下面是选中线路的搬运规则，底部是玩家背包。</p>
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

    // ---- 线路配置
    private static final int CONFIG_INSET_TOP = 130;
    private static final int ROUTE_ROW_Y = 134;
    private static final int ROW_SECOND_Y = 152;
    private static final int ROW_THIRD_Y = 170;
    private static final int ROW_FOURTH_Y = 188;
    private static final int ROW_HEIGHT = 14;

    private static final int FLOW_WIDTH = 74;
    private static final int FLOW_MEDIUM_GAP = 5;
    private static final int SIDE_WIDTH = 113;
    private static final int SIDE_TRIGGER_GAP = 6;

    /** 数值输入框一行的三格：起点与宽度，最后一格的右边缘正好落在 {@link #CONTENT_RIGHT}。 */
    private static final int[] NUMERIC_CELL_X = {8, 85, 162};
    private static final int[] NUMERIC_CELL_WIDTH = {77, 77, 78};

    /** 输入框停手多久后自动提交（tick）。玩家填了值却没失焦时靠它兜底。 */
    private static final int AUTO_COMMIT_TICKS = 10;

    /**
     * 超过 int 上限就开始提示。
     *
     * <p>原版容器接口（{@code extractItem} / {@code drain} / {@code extractEnergy}）一次只收
     * {@code int}，超过的部分只能靠外层循环反复搬。数值越大，一次运行里要跑的「源格 × 目标格」
     * 组合越多，单 tick 的耗时会明显上升——尤其当「周期」也调得很小时。</p>
     */
    private static final long INT_LIMIT = Integer.MAX_VALUE;
    private static final int AMOUNT_TEXT_COLOR = 0xE0E0E0;
    private static final int AMOUNT_WARN_COLOR = 0xFFB06000;

    // ---- 过滤器 + 解散
    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 204;
    private static final int FILTER_COLUMNS = 3;
    private static final int FILTER_SLOT_SIZE = 16;
    private static final int FILTER_SLOT_STEP = 17;
    private static final int DISSOLVE_X = 128;
    private static final int DISSOLVE_WIDTH = CONTENT_RIGHT - DISSOLVE_X;

    private static final Direction[] SIDE_ORDER = {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** 双击判定窗口。 */
    private static final long DOUBLE_CLICK_MILLIS = 350L;

    private final PressableAE2Button[] routeButtons = new PressableAE2Button[StaffLinkNetwork.ROUTE_COUNT];
    private PressableAE2Button flowButton;
    private PressableAE2Button mediumButton;
    private PressableAE2Button enabledButton;
    private PressableAE2Button sideButton;
    private PressableAE2Button triggerButton;
    private PressableAE2Button prevNetworkButton;
    private PressableAE2Button nextNetworkButton;
    private PressableAE2Button newNetworkButton;
    private PressableAE2Button dissolveButton;

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

    private GlobalPos lastClickedAnchor;
    private long lastClickMillis;

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
                leftPos + CONTENT_LEFT + FLOW_WIDTH + FLOW_MEDIUM_GAP, topPos + ROW_SECOND_Y,
                FLOW_WIDTH, ROW_HEIGHT, Component.empty(),
                button -> edit(config -> withMedium(config, config.medium().nextSupported()))));
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
        searchField = addTextField(CONTENT_LEFT, SEARCH_ROW_Y, CONTENT_WIDTH,
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

        // 开界面与下发快照是两个包；万一快照先到，这里把它捞回来，界面就不会空着。
        StaffLinkNetwork pending = ClientPacketHandlers.consumePendingStaffLinkSync(menu.getNetworkId());
        if (pending != null) {
            menu.receiveSync(pending);
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

        updateControls();
    }

    /** 界面收到服务端快照。 */
    public void receiveSync(StaffLinkNetwork network) {
        menu.receiveSync(network);
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
            menu.applyRoute(menu.defaultRouteFor(anchor, menu.getSelectedRoute()));
        }
    }

    private void newNetwork() {
        // 先把当前网络名提交掉，再让输入框失焦——否则新网络的空名字会被旧名字覆盖回去。
        applyEdits();
        setFocused(null);
        menu.createNetwork();
        scrollOffset = 0;
        StaffLinkHighlightRenderer.clear();
    }

    /** 切到相邻的一张网络；先把当前编辑提交掉，免得刚改的值丢了。 */
    private void cycleNetwork(int delta) {
        applyEdits();
        setFocused(null);
        menu.cycleNetwork(delta);
        scrollOffset = 0;
        StaffLinkHighlightRenderer.clear();
    }

    private void dissolveNetwork() {
        applyEdits();
        setFocused(null);
        menu.dissolveNetwork();
        scrollOffset = 0;
        StaffLinkHighlightRenderer.clear();
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

    /** 超过 int 上限时给输入框上色提醒（数值越大，单次运行的搬运趟数越多）。 */
    private void updateAmountWarning() {
        amountField.setTextColor(currentAmount() > INT_LIMIT ? AMOUNT_WARN_COLOR : AMOUNT_TEXT_COLOR);
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
        int[] position = staffNetworkPosition();
        boolean multipleNetworks = position[1] > 1;
        prevNetworkButton.active = multipleNetworks;
        nextNetworkButton.active = multipleNetworks;
        newNetworkButton.active = true;
        dissolveButton.active = true;
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
        updateAmountWarning();

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
            if (anchor.equals(menu.getSelectedAnchor())) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_RIGHT, y + 9,
                        MachineScreenStyle.HIGHLIGHT_COLOR);
            }
            if (StaffLinkHighlightRenderer.isHighlighted(anchor)) {
                graphics.fill(CONTENT_LEFT - 2, y - 2, CONTENT_LEFT, y + 9, 0xFF4DF28C);
            }
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
            graphics.drawString(font, glyph, CONTENT_LEFT + 2, y, glyphColor, false);

            // 名字后面缀上坐标：多个同名容器（都叫「箱子」）光看名字根本分不出来。
            String coord = anchor.pos().toShortString();
            int nameBudget = Math.max(24,
                    CONTENT_WIDTH - LIST_UNBIND_WIDTH - 16 - font.width(coord));
            String name = font.plainSubstrByWidth(anchorDisplayName(anchor).getString(), nameBudget);
            graphics.drawString(font, name, CONTENT_LEFT + 10, y, MachineScreenStyle.TEXT_COLOR, false);
            graphics.drawString(font, coord, CONTENT_LEFT + 10 + font.width(name) + 4, y,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            graphics.drawString(font, "x", CONTENT_RIGHT - LIST_UNBIND_WIDTH + 3, y,
                    MachineScreenStyle.ERROR_TEXT_COLOR, false);
        }
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
        renderAnchorTooltip(graphics, mouseX, mouseY);
        renderFilterTooltip(graphics, mouseX, mouseY);
        renderNumericTooltip(graphics, mouseX, mouseY);
        renderStatsTooltip(graphics, mouseX, mouseY);
        renderNetworkTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderFilterItems(GuiGraphics graphics) {
        if (!menu.isFilterActive()) {
            return;
        }
        List<ItemStack> mirror = menu.getFilterMirror();
        for (int index = 0; index < mirror.size() && index < StaffLinkRoute.FILTER_LIMIT; index++) {
            ItemStack stack = mirror.get(index);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, leftPos + filterSlotX(index), topPos + filterSlotY(index));
            }
        }
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
        graphics.renderTooltip(font,
                List.of(anchorDisplayName(anchor),
                        Component.literal(anchor.dimension().location() + " " + anchor.pos().toShortString()),
                        Component.translatable("gui.useless_mod.wireless_logistics.highlight_hint")),
                Optional.empty(), mouseX, mouseY);
    }

    /** 过滤器槽的说明：槽里放的是「容器标记」而不是要搬运的东西本身，得讲清楚。 */
    private void renderFilterTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = filterSlotAt(mouseX - leftPos, mouseY - topPos);
        if (index < 0 || !menu.isFilterActive()) {
            return;
        }
        ItemStack marker = menu.getFilterMirror().get(index);
        List<Component> lines = new ArrayList<>(2);
        if (!marker.isEmpty()) {
            lines.add(marker.getHoverName());
        }
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.filter_hint"));
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** 悬停切换按钮时说明它做什么（顺便告诉玩家 Shift+滚轮也能切）。 */
    private void renderNetworkTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY < NETWORK_ROW_Y || localY > NETWORK_ROW_Y + SMALL_FIELD_HEIGHT) {
            return;
        }
        boolean overPrev = localX >= PREV_BUTTON_X && localX < PREV_BUTTON_X + NETWORK_SWITCH_WIDTH;
        boolean overNext = localX >= NEXT_BUTTON_X && localX < NEXT_BUTTON_X + NETWORK_SWITCH_WIDTH;
        if (!overPrev && !overNext) {
            return;
        }
        graphics.renderTooltip(font,
                List.of(Component.translatable(
                        "gui.useless_mod.wireless_logistics.network_switch_hint")),
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
            if (spec.scaled() && currentAmount() > INT_LIMIT) {
                lines.add(Component.translatable(
                                "gui.useless_mod.wireless_logistics.amount_int_warning",
                                ScaledEnergyAmount.format(INT_LIMIT))
                        .withStyle(ChatFormatting.GOLD));
            }
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
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && anyFieldFocused()) {
            applyEdits();
            setFocused(null);
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            for (EditBox field : fields()) {
                if (field.isFocused()
                        && (field.keyPressed(keyCode, scanCode, modifiers) || field.canConsumeInput())) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        int row = anchorRowAt(localX, localY);
        if (row >= 0) {
            List<GlobalPos> anchors = visibleAnchors();
            int index = scrollOffset + row;
            if (index < anchors.size()) {
                GlobalPos anchor = anchors.get(index);
                if (localX >= CONTENT_RIGHT - LIST_UNBIND_WIDTH) {
                    menu.detach(anchor);
                    StaffLinkHighlightRenderer.clear();
                    clampScroll();
                } else {
                    // 双击：在世界里高亮 / 取消高亮；单击只做选中。
                    if (isDoubleClick(anchor)) {
                        StaffLinkHighlightRenderer.toggle(anchor);
                    }
                    menu.setSelection(anchor, menu.getSelectedRoute());
                    ensureRouteConfig();
                }
                updateControls();
            }
            return true;
        }

        int filterIndex = filterSlotAt(localX, localY);
        if (filterIndex >= 0) {
            if (menu.isFilterActive()) {
                ItemStack carried = menu.getCarried();
                menu.setFilterSlot(filterIndex, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
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

    private boolean isDoubleClick(GlobalPos anchor) {
        long now = System.currentTimeMillis();
        boolean doubled = anchor.equals(lastClickedAnchor) && now - lastClickMillis <= DOUBLE_CLICK_MILLIS;
        lastClickedAnchor = anchor;
        lastClickMillis = now;
        return doubled;
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
     * 当前杖上「第几张 / 共几张」网络。
     *
     * <p>直接读客户端手上那把杖的组件：服务端切换网络时会 {@code broadcastChanges}，
     * 组件跟着同步过来，所以这里不需要额外的包。</p>
     */
    private int[] staffNetworkPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return new int[]{0, 1};
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (!(stack.getItem() instanceof EndlessBeafItem)) {
                continue;
            }
            List<UUID> ids = StaffLinkManager.networkIds(stack);
            int index = ids.indexOf(menu.getNetworkId());
            return new int[]{index < 0 ? 0 : index, Math.max(1, ids.size())};
        }
        return new int[]{0, 1};
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
                config.weight(), config.filter());
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
