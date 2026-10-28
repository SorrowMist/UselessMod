package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.client.BeefAutoClicker;
import com.sorrowmist.useless.content.menus.ChainGroupMenu;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.BeefToolProtectionManager;
import com.sorrowmist.useless.core.config.ChainGroupManager;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.data.BeefToolModuleRegistry;
import com.sorrowmist.useless.network.BeefToolLayoutRequestPacket;
import com.sorrowmist.useless.network.BeefToolLayoutUpdatePacket;
import com.sorrowmist.useless.network.ConstructionWandCorePacket;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.ToolTypeModeSwitchPacket;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import com.sorrowmist.useless.client.gui.apple.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModeWheelScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 526;
    /** v3：左侧圆形轮盘预览栏宽度。 */
    private static final int WHEEL_W = 228;
    private static final int PANEL_MARGIN = 12;
    private static final int HEADER_HEIGHT = 92;
    private static final int NORMAL_FOOTER_HEIGHT = 24;
    private static final int EDITOR_FOOTER_HEIGHT = 24;
    private static final int CONTENT_PADDING = 8;
    private static final int CARD_GAP = 8;
    private static final int CARD_HEADER_HEIGHT = 18;
    private static final int CARD_BOTTOM_PADDING = 6;
    private static final int MODULE_HEIGHT = 14;
    private static final int MODULE_GAP = 3;
    /** 连锁等价组齿轮图标的边长（画在连锁挖掘模块按钮右端）。 */
    private static final int GEAR_SIZE = 12;
    private static final int PAGE_TAB_HEIGHT = 14;
    private static final int PAGE_TAB_WIDTH = 48;
    private static final int DRAG_THRESHOLD = 4;
    private static final int DESIGN_HEIGHT = 298;
    private static final int EDITOR_DESIGN_HEIGHT = 335;

    private final List<PressableAE2Button> toolbarButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<GroupNameField> groupNameFields = new ArrayList<>();
    private final List<CardLayout> cardLayouts = new ArrayList<>();
    private final List<ModuleLayout> moduleLayouts = new ArrayList<>();
    private final List<Rect> pageTabRects = new ArrayList<>();

    private ItemStack targetItem;
    private BeefToolLayout layout = BeefToolModuleRegistry.defaultLayout();
    private EditBox pageNameField;
    private boolean pageNameWasFocused;
    private record WheelChoice(String id, Rect rect) {}
    private final List<WheelChoice> wheelChoices = new ArrayList<>();
    private DragState drag;
    private String lastAvailabilitySignature = "";
    private String statusKey;
    private int statusTicks;
    private String pendingImportText;
    private boolean confirmImport;
    private boolean editing;
    private boolean awaitingLayout = true;
    private boolean layoutRequested;
    private int pageTabOffset;
    private int scrollOffset;
    private int maxScroll;
    private int contentLeft;
    private int contentTop;
    private int contentRight;
    private int contentBottom;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    /** LDLib Apple 窗口外壳（AppleWindow）；在 UI 坐标系内渲染，随面板尺寸重建。 */
    private AppleHost chrome;
    private int chromeW = -1, chromeH = -1;
    public AppleHost appleHost() { return chrome; }
    private int panelHeight;
    private int uiWidth;
    private int uiHeight;
    private float uiScale = 1.0F;
    private float uiOffsetX;
    private float uiOffsetY;

    public ModeWheelScreen(ItemStack targetItem) {
        super(Component.translatable("gui.useless_mod.mode_config.title"));
        this.targetItem = targetItem == null ? ItemStack.EMPTY : targetItem;
    }

    @Override
    protected void init() {
        super.init();
        toolbarButtons.clear();
        modeButtons.clear();
        groupNameFields.clear();
        wheelChoices.clear();
        pageNameWasFocused = false;
        pageNameField = null;

        calculatePanelBounds();
        calculateGeometry();
        createToolbarWidgets();
        if (!awaitingLayout) {
            createEditorNameFields();
        }
        if (!awaitingLayout && !editing) {
            createModeButtons();
        }

        if (!layoutRequested && minecraft != null && minecraft.player != null) {
            layoutRequested = true;
            PacketDistributor.sendToServer(new BeefToolLayoutRequestPacket());
        }
    }

    private void calculatePanelBounds() {
        calculateUiScale();
        int availableWidth = Math.max(1, uiWidth - PANEL_MARGIN * 2);
        int availableHeight = Math.max(1, uiHeight - PANEL_MARGIN * 2);
        int desiredWidth = PANEL_MAX_WIDTH;
        int desiredHeight = editing ? EDITOR_DESIGN_HEIGHT : DESIGN_HEIGHT;
        panelWidth = Math.max(1, Math.min(PANEL_MAX_WIDTH, Math.min(availableWidth, desiredWidth)));
        panelHeight = Math.max(1, Math.min(availableHeight, desiredHeight));
        panelLeft = (uiWidth - panelWidth) / 2;
        panelTop = Math.max(PANEL_MARGIN, (uiHeight - panelHeight) / 2);

        contentLeft = panelLeft + PANEL_MARGIN + WHEEL_W;
        contentRight = panelLeft + panelWidth - 22;
        contentTop = panelTop + (editing ? HEADER_HEIGHT : 66);
        int footerHeight = editing ? EDITOR_FOOTER_HEIGHT : NORMAL_FOOTER_HEIGHT;
        contentBottom = Math.max(contentTop + 1, panelTop + panelHeight - footerHeight);
    }

    private void calculateUiScale() {
        float widthScale = Math.max(0.1F, (width - PANEL_MARGIN * 2.0F) / PANEL_MAX_WIDTH);
        float heightScale = Math.max(0.1F, (height - PANEL_MARGIN * 2.0F) / (editing ? EDITOR_DESIGN_HEIGHT : DESIGN_HEIGHT));
        uiScale = Math.min(1.0F, Math.min(widthScale, heightScale));
        uiWidth = Math.max(1, Math.round(width / uiScale));
        uiHeight = Math.max(1, Math.round(height / uiScale));
        uiOffsetX = (width - uiWidth * uiScale) / 2.0F;
        uiOffsetY = (height - uiHeight * uiScale) / 2.0F;
    }

    private int desiredContentWidth() {
        BeefToolLayout.Page page = currentPageObject();
        int[] groupWidths = sumGroupColumnWidths(page);
        int groupWidth = groupWidths[0] + groupWidths[1];
        if (groupWidth > 0 && groupWidths[1] > 0) groupWidth += CARD_GAP;
        if (editing) {
            groupWidth = Math.max(groupWidth, naturalModuleRowWidth(layout.unassignedModules()));
            groupWidth = Math.max(groupWidth, editorFooterWidth());
        }
        return Math.max(Math.max(140, groupWidth), minimumContentWidth());
    }

    private int editorFooterWidth() {
        return 54 + 4 + 54 + 12 + 58 + 12 + 58 + 4 + 58;
    }

    private int minimumContentWidth() {
        int titleWidth = font == null ? 100 : font.width(title);
        int actionWidth = editing ? 50 : 70;
        int minimumPanelWidth = titleWidth + actionWidth + (editing ? 138 : 64);
        return Math.max(0, minimumPanelWidth - PANEL_MARGIN * 2);
    }

    private int desiredContentHeight() {
        BeefToolLayout.Page page = currentPageObject();
        int rawY = CONTENT_PADDING;
        if (editing) {
            rawY += cardHeight(1, visibleModules(layout.unassignedModules()).size(),
                    moduleColumns(visibleModules(layout.unassignedModules()).size())) + CARD_GAP;
        }
        int[] bottoms = {rawY, rawY};
        for (BeefToolLayout.Group group : page.groups()) {
            if (BeefToolModuleRegistry.isGroupHidden(group)) continue;
            List<String> modules = visibleModules(group.modules());
            int column = 0;
            bottoms[column] += cardHeight(1, modules.size(), moduleColumns(modules.size())) + CARD_GAP;
        }
        return Math.max(rawY, Math.max(bottoms[0], bottoms[1])) + CONTENT_PADDING;
    }

    private int[] sumGroupColumnWidths(BeefToolLayout.Page page) {
        int left = 0;
        int right = 0;
        int visibleIndex = 0;
        for (BeefToolLayout.Group group : page.groups()) {
            if (BeefToolModuleRegistry.isGroupHidden(group)) continue;
            int width = naturalGroupWidth(group);
            if ((visibleIndex++ & 1) == 0) left = Math.max(left, width);
            else right = Math.max(right, width);
        }
        return new int[]{left, right};
    }

    private int naturalGroupWidth(BeefToolLayout.Group group) {
        List<String> modules = visibleModules(group.modules());
        int titleWidth = font == null ? 80 : font.width(group.name()) + 30;
        if (modules.isEmpty()) return Math.max(100, titleWidth);
        int buttonWidth = modules.size() == 1
                ? moduleButtonWidth(group.modules())
                : commonModuleWidth(currentPageObject());
        int columns = moduleColumns(modules.size());
        int contentWidth = columns == 1
                ? buttonWidth
                : buttonWidth * 2 + MODULE_GAP;
        return Math.max(titleWidth, contentWidth + 10);
    }

    private int naturalModuleRowWidth(List<String> moduleIds) {
        List<String> modules = visibleModules(moduleIds);
        if (modules.isEmpty()) return 140;
        int buttonWidth = modules.size() == 1
                ? moduleButtonWidth(moduleIds)
                : commonModuleWidth(currentPageObject());
        int columns = moduleColumns(modules.size());
        return (columns == 1 ? buttonWidth : buttonWidth * 2 + MODULE_GAP) + 10;
    }

    private int moduleButtonWidth(List<String> moduleIds) {
        int width = 50;
        for (String id : visibleModules(moduleIds)) {
            width = Math.max(width, font == null ? 80 : font.width(buttonMessage(id)) + 8);
        }
        return width;
    }



    /** 整页统一的按钮宽度：所有含两个以上按钮的分组取齐，保证各分组外观一致。 */
    private int commonModuleWidth(BeefToolLayout.Page page) {
        int width = 50;
        for (BeefToolLayout.Group group : page.groups()) {
            if (BeefToolModuleRegistry.isGroupHidden(group)) continue;
            List<String> modules = visibleModules(group.modules());
            if (modules.size() < 2) continue;
            for (String id : modules) {
                width = Math.max(width, font == null ? 80 : font.width(buttonMessage(id)) + 8);
            }
        }
        if (editing) {
            List<String> modules = visibleModules(layout.unassignedModules());
            if (modules.size() >= 2) {
                for (String id : modules) {
                    width = Math.max(width, font == null ? 80 : font.width(buttonMessage(id)) + 8);
                }
            }
        }
        return width;
    }

    private int moduleColumns(int moduleCount) {
        return moduleCount <= 1 ? 1 : 2;
    }

    private int pageNavigationLeft() {
        int right = panelLeft + panelWidth - PANEL_MARGIN;
        int actionWidth = editing ? 50 : 70;
        return right - actionWidth - 44;
    }

    private void createToolbarWidgets() {
        int right = panelLeft + panelWidth - PANEL_MARGIN;
        int y = panelTop + 26;
        int actionWidth = 64;
        int actionX = right - actionWidth;
        int nextX = actionX - 22;
        int previousX = nextX - 22;
        int navY = panelTop + panelHeight - 19;

        PressableAE2Button previous = addToolbarButton(new PressableAE2Button(
                previousX, navY, 18, 14, Component.literal("<"), ignored -> changePage(-1)));
        PressableAE2Button next = addToolbarButton(new PressableAE2Button(
                nextX, navY, 18, 14, Component.literal(">"), ignored -> changePage(1)));
        PressableAE2Button action = addToolbarButton(new PressableAE2Button(
                actionX, panelTop + 7, actionWidth, 16,
                Component.translatable(editing
                        ? "gui.useless_mod.mode_config.done"
                        : "gui.useless_mod.mode_config.edit"),
                ignored -> {
                    if (editing) finishEditing();
                    else startEditing();
                }));

        boolean ready = !awaitingLayout;
        previous.visible = ready && !editing && layout.pages().size() > 1;
        next.visible = ready && !editing && layout.pages().size() > 1;
        previous.active = ready && currentPage() > 0;
        next.active = ready && currentPage() + 1 < layout.pages().size();
        action.active = ready;

        if (!editing || !ready) return;

        int footerY = panelTop + 66;
        int footerX = panelLeft + PANEL_MARGIN;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 54, 14,
                Component.translatable("gui.useless_mod.mode_config.add_page"), ignored -> addPage()));
        footerX += 58;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 54, 14,
                Component.translatable("gui.useless_mod.mode_config.delete_page"), ignored -> deletePage()));
        footerX += 66; // v3：分组之间留 12px 间隔（页 | 分组 | 导入导出）
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 58, 14,
                Component.translatable("gui.useless_mod.mode_config.add_group"), ignored -> addGroup()));
        footerX = right - 58 - 4 - 58;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 58, 14,
                Component.translatable("gui.useless_mod.mode_config.export"), ignored -> exportLayout()));
        footerX += 62;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 58, 14,
                Component.translatable("gui.useless_mod.mode_config.import"), ignored -> importLayout()));
    }

    private PressableAE2Button addToolbarButton(PressableAE2Button button) {
        toolbarButtons.add(addRenderableWidget(button));
        return button;
    }

    private void createModeButtons() {
        for (ModuleLayout module : moduleLayouts) {
            PressableAE2Button button = addRenderableWidget(new PressableAE2Button(
                    module.rect().left(), module.rect().top(), module.rect().width(), module.rect().height(),
                    buttonMessage(module.id()), ignored -> onModeSelected(module.id())));
            if (BeefToolModuleRegistry.ENHANCED_CHAIN_MINING.equals(module.id())) button.setReservedRight(GEAR_SIZE + 2);
            modeButtons.add(new ModeButton(module.id(), button));
        }
        positionModeButtons();
    }

    private void createEditorNameFields() {
        BeefToolLayout.Page page = currentPageObject();
        int pageFieldWidth = 80;
        int pageFieldX = panelLeft + panelWidth - PANEL_MARGIN - pageFieldWidth;
        pageNameField = new AppleEditBox(font, pageFieldX, panelTop + 40, pageFieldWidth, 16,
                Component.translatable("gui.useless_mod.mode_config.page_name"));
        configureNameField(pageNameField);
        pageNameField.setValue(page.name());
        addRenderableWidget(pageNameField);
        if (!editing) return;

        for (CardLayout card : cardLayouts) {
            if (card.unassigned() || card.groupIndex() < 0) continue;
            BeefToolLayout.Group group = groupAt(card.pageIndex(), card.groupIndex());
            if (group == null) continue;
            EditBox field = new AppleEditBox(font, card.left() + 10, card.top() + 2,
                    Math.max(20, card.width() - 31), 14,
                    Component.translatable("gui.useless_mod.mode_config.group_name"));
            configureNameField(field);
            field.setValue(group.name());
            addRenderableWidget(field);
            groupNameFields.add(new GroupNameField(card.pageIndex(), card.groupIndex(), field));
        }
        updateEditorFieldPositions();
    }

    private static void configureNameField(EditBox field) {
        field.setMaxLength(BeefToolLayout.MAX_NAME_LENGTH);
        field.setFilter(value -> value.codePointCount(0, value.length()) <= BeefToolLayout.MAX_NAME_LENGTH
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0);
    }

    private void calculateGeometry() {
        cardLayouts.clear();
        moduleLayouts.clear();
        pageTabRects.clear();
        if (layout.pages().isEmpty()) {
            layout.pages().add(new BeefToolLayout.Page("Page 1"));
            layout.setSelectedPage(0);
        }

        List<RawCard> rawCards = new ArrayList<>();
        int rawY = contentTop;
        if (editing) {
            List<String> unassigned = visibleModules(layout.unassignedModules());
            int height = chipCardHeight(contentRight - contentLeft, unassigned);
            rawCards.add(new RawCard(-1, -1, contentLeft, rawY,
                    contentRight - contentLeft, height, true, unassigned));
            rawY += height + CARD_GAP;
        }

        BeefToolLayout.Page page = currentPageObject();
        int[] columnWidths = {contentRight - contentLeft, 0};
        int[] columnBottoms = {rawY, rawY};
        for (int groupIndex = 0; groupIndex < page.groups().size(); groupIndex++) {
            BeefToolLayout.Group group = page.groups().get(groupIndex);
            if (BeefToolModuleRegistry.isGroupHidden(group)) continue;
            List<String> visibleModules = visibleModules(group.modules());
            int column = 0;
            int left = contentLeft + (column == 0 ? 0 : columnWidths[0] + CARD_GAP);
            int cardWidth = columnWidths[column];
            int height = chipCardHeight(cardWidth, visibleModules);
            int top = columnBottoms[column];
            rawCards.add(new RawCard(currentPage(), groupIndex, left, top,
                    cardWidth, height, false, visibleModules));
            columnBottoms[column] = top + height + CARD_GAP;
        }

        int rawBottom = rawY;
        for (int bottom : columnBottoms) rawBottom = Math.max(rawBottom, bottom);
        if (rawCards.isEmpty()) rawBottom = rawY;
        maxScroll = Math.max(0, rawBottom - contentBottom + CONTENT_PADDING);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (RawCard raw : rawCards) {
            CardLayout card = new CardLayout(raw.pageIndex(), raw.groupIndex(), raw.left(),
                    raw.top() - scrollOffset, raw.width(), raw.height(), raw.unassigned(), raw.modules());
            cardLayouts.add(card);
            for (int moduleIndex = 0; moduleIndex < card.modules().size(); moduleIndex++) {
                Rect rect = moduleRect(card, moduleIndex);
                moduleLayouts.add(new ModuleLayout(card.pageIndex(), card.groupIndex(),
                        card.unassigned(), card.modules().get(moduleIndex), moduleIndex, rect));
            }
        }
    }

    private int[] fitColumnWidths(int[] naturalWidths, int availableWidth) {
        int left = naturalWidths[0];
        int right = naturalWidths[1];
        if (right == 0) return new int[]{Math.max(1, Math.min(left, availableWidth)), 0};
        int usable = Math.max(2, availableWidth - CARD_GAP);
        int naturalTotal = Math.max(1, left + right);
        if (naturalTotal <= usable) return new int[]{left, right};
        int fittedLeft = Math.max(1, usable * left / naturalTotal);
        int fittedRight = Math.max(1, usable - fittedLeft);
        return new int[]{fittedLeft, fittedRight};
    }

    private int cardHeight(int width, int moduleCount, int columns) {
        int rows = Math.max(1, (moduleCount + columns - 1) / columns);
        return CARD_HEADER_HEIGHT + rows * MODULE_HEIGHT
                + Math.max(0, rows - 1) * MODULE_GAP + CARD_BOTTOM_PADDING;
    }

    private List<Rect> chipRects(int width, List<String> modules) {
        List<Rect> result = new ArrayList<>();
        int x = 7, y = CARD_HEADER_HEIGHT;
        for (String id : modules) {
            int reserve = BeefToolModuleRegistry.ENHANCED_CHAIN_MINING.equals(id) ? GEAR_SIZE + 2 : 0;
            int w = Math.min(width - 14, Math.max(30, Math.round(font.width(buttonMessage(id)) * .8f) + 12 + reserve));
            if (x > 7 && x + w > width - 7) { x = 7; y += MODULE_HEIGHT + MODULE_GAP; }
            result.add(new Rect(x, y, w, MODULE_HEIGHT));
            x += w + MODULE_GAP;
        }
        return result;
    }

    private int chipCardHeight(int width, List<String> modules) {
        List<Rect> chips = chipRects(width, modules);
        return chips.isEmpty() ? CARD_HEADER_HEIGHT + MODULE_HEIGHT + CARD_BOTTOM_PADDING
                : chips.get(chips.size() - 1).bottom() + CARD_BOTTOM_PADDING;
    }

    private Rect moduleRect(CardLayout card, int index) {
        Rect chip = chipRects(card.width(), card.modules()).get(index);
        return new Rect(card.left() + chip.left(), card.top() + chip.top(), chip.width(), chip.height());
    }

    private List<String> visibleModules(List<String> moduleIds) {
        List<String> visible = new ArrayList<>();
        for (String id : moduleIds) {
            if (BeefToolModuleRegistry.isAvailable(id, targetItem)) visible.add(id);
        }
        return List.copyOf(visible);
    }

    private void positionModeButtons() {
        for (int i = 0; i < modeButtons.size() && i < moduleLayouts.size(); i++) {
            ModeButton modeButton = modeButtons.get(i);
            ModuleLayout module = moduleLayouts.get(i);
            modeButton.button().setX(module.rect().left());
            modeButton.button().setY(module.rect().top());
            modeButton.button().setWidth(module.rect().width());
            modeButton.button().setHeight(module.rect().height());
            modeButton.button().visible = intersectsContent(module.rect());
            modeButton.button().active = !awaitingLayout;
            modeButton.button().setMessage(buttonMessage(modeButton.id()));
        }
    }

    private void updateEditorFieldPositions() {
        if (pageNameField != null) {
            pageNameField.visible = !awaitingLayout;
            if (!pageNameField.isFocused() && !pageNameField.getValue().equals(currentPageObject().name())) {
                pageNameField.setValue(currentPageObject().name());
            }
        }
        for (GroupNameField field : groupNameFields) {
            CardLayout card = findCard(field.pageIndex(), field.groupIndex());
            if (card == null) {
                field.field().visible = false;
                continue;
            }
            field.field().setX(card.left() + 10);
            field.field().setY(card.top() + 2);
            field.field().setWidth(Math.max(20, card.width() - 31));
            field.field().visible = intersectsContent(card.rect());
        }
    }

    private boolean intersectsContent(Rect rect) {
        return rect.right() > contentLeft && rect.left() < contentRight
                && rect.bottom() > contentTop && rect.top() < contentBottom;
    }

    private CardLayout findCard(int pageIndex, int groupIndex) {
        for (CardLayout card : cardLayouts) {
            if (!card.unassigned() && card.pageIndex() == pageIndex && card.groupIndex() == groupIndex) {
                return card;
            }
        }
        return null;
    }

    private BeefToolLayout.Page currentPageObject() {
        if (layout.pages().isEmpty()) {
            layout.pages().add(new BeefToolLayout.Page("Page 1"));
            layout.setSelectedPage(0);
        }
        int page = currentPage();
        return layout.pages().get(page);
    }

    private int currentPage() {
        if (layout.pages().isEmpty()) return 0;
        return Mth.clamp(layout.selectedPage(), 0, layout.pages().size() - 1);
    }

    private BeefToolLayout.Group groupAt(int pageIndex, int groupIndex) {
        if (pageIndex < 0 || pageIndex >= layout.pages().size()) return null;
        List<BeefToolLayout.Group> groups = layout.pages().get(pageIndex).groups();
        return groupIndex >= 0 && groupIndex < groups.size() ? groups.get(groupIndex) : null;
    }

    private Component buttonMessage(String id) {
        Component name = BeefToolModuleRegistry.name(id);
        if (BeefToolModuleRegistry.isExclusive(id)) {
            return isActive(id)
                    ? Component.translatable("gui.useless_mod.mode_config.current", name)
                    : name;
        }
        return Component.translatable("gui.useless_mod.mode_config.state", name, stateMessage(id));
    }

    /**
     * 模块的状态文案。绝大多数模块是「开启 / 关闭」，
     * 但「右键泥土」这类模块是在两种行为之间切换，会用工具优先级的说法（锄头优先 / 铲子优先）。
     */
    private Component stateMessage(String id) {
        if (BeefToolModuleRegistry.BEEF_FARMLAND_MODE.equals(id)) {
            return EndlessBeafItem.farmlandStateText(targetItem);
        }
        return Component.translatable(isActive(id)
                ? "tooltip.useless_mod.enable"
                : "tooltip.useless_mod.disable");
    }

    /** v3：左侧轮盘预览——中心为当前（悬停或首个已启用）模块，四周为本页各模块的系统色图标块。 */
    private void drawWheelPreview(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = panelLeft + PANEL_MARGIN, y0 = contentTop;
        int size = WHEEL_W - 8;
        wheelChoices.clear();
        AppleUi.softShadow(g, x0, y0, size, size, 8, 4, size / 2f, 0x22000000);
        AppleUi.fillRound(g, x0, y0, size, size, size / 2f, 0xD9FFFFFF);
        List<String> ids = new ArrayList<>();
        for (BeefToolLayout.Group group : currentPageObject().groups()) {
            if (BeefToolModuleRegistry.isGroupHidden(group)) continue;
            ids.addAll(visibleModules(group.modules()));
        }
        int cx = x0 + size / 2, cy = y0 + size / 2, outer = size / 2 - 6;
        AppleUi.fillRound(g, cx - outer, cy - outer, outer * 2, outer * 2, outer, 0x12DCE6F2);
        int inner = 38;
        AppleUi.fillRound(g, cx - inner, cy - inner, inner * 2, inner * 2, inner, AppleStyle.CARD);
        String current = null;
        int active = 0;
        for (ModuleLayout m : moduleLayouts) {
            if (m.rect().contains(mouseX, mouseY)) current = m.id();
        }
        for (String id : ids) {
            if (isActive(id)) { active++; if (current == null) current = id; }
        }
        int n = ids.size();
        boolean two = n > 16;
        int outerCount = two ? (n + 1) / 2 : n;
        for (int i = 0; i < n; i++) {
            String id = ids.get(i);
            boolean outerRing = !two || i < outerCount;
            int k = outerRing ? i : i - outerCount, cnt = outerRing ? outerCount : n - outerCount;
            int ring = outerRing ? outer - 14 : outer - 40;
            double a = -Math.PI / 2 + 2 * Math.PI * (k + (outerRing ? 0 : 0.5)) / Math.max(1, cnt);
            int tx = cx + (int) Math.round(Math.cos(a) * ring) - 7;
            int ty = cy + (int) Math.round(Math.sin(a) * ring) - 7;
            Rect hit = new Rect(tx - 3, ty - 3, 20, 20);
            wheelChoices.add(new WheelChoice(id, hit));
            if (hit.contains(mouseX, mouseY)) current = id;
            boolean on = isActive(id);
            int col = on ? moduleAccent(id) : AppleStyle.TRACK;
            if (id.equals(current)) AppleUi.fillRound(g, tx - 1, ty - 1, 16, 16, 8, AppleStyle.BLUE_TINT);
            AppleUi.fillRound(g, tx, ty, 14, 14, 7, col);
            if (on) AppleUi.icon(g, AppleUi.Icon.CHECK, tx + 3, ty + 3, 8, AppleStyle.ON_ACCENT);
            else AppleUi.icon(g, AppleUi.Icon.MINUS, tx + 3, ty + 3, 8, AppleStyle.TERTIARY);
            if (!two || outerRing) {
                String label = font.plainSubstrByWidth(BeefToolModuleRegistry.name(id).getString(), 64);
                g.pose().pushPose();
                g.pose().translate(tx + 7, ty + 16, 0);
                g.pose().scale(0.55f, 0.55f, 1);
                AppleUi.centered(g, font, label, 0, 0, AppleStyle.SECONDARY);
                g.pose().popPose();
            }
        }
        if (current != null) {
            String name = font.plainSubstrByWidth(BeefToolModuleRegistry.name(current).getString(), inner * 2 - 4);
            AppleUi.centered(g, font, name, cx, cy - 2, moduleAccent(current));
        }
        AppleUi.centered(g, font, Component.translatable("gui.useless_mod.mode_config.wheel_current").getString(), cx, cy - 16, AppleStyle.TERTIARY);
        AppleUi.centered(g, font, active + "/" + n, cx, cy + 12, AppleStyle.TERTIARY);
    }

    private int moduleAccent(String id) {
        int[] colors = {AppleStyle.BLUE, AppleStyle.ORANGE, AppleStyle.RED, AppleStyle.TEAL, AppleStyle.PURPLE};
        var groups = currentPageObject().groups();
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).modules().contains(id)) return colors[i % colors.length];
        return AppleStyle.BLUE;
    }

    private boolean isActive(String id) {
        if (targetItem == null || targetItem.isEmpty()) return false;
        return switch (id) {
            case BeefToolModuleRegistry.ENCHANT_SILK_TOUCH ->
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(targetItem, UComponents.EnchantModeComponent) == EnchantMode.SILK_TOUCH;
            case BeefToolModuleRegistry.ENCHANT_FORTUNE ->
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(targetItem, UComponents.EnchantModeComponent) == EnchantMode.FORTUNE;
            case BeefToolModuleRegistry.TOOL_NONE -> currentTool() == ToolTypeMode.NONE_MODE;
            case BeefToolModuleRegistry.TOOL_WRENCH -> currentTool() == ToolTypeMode.WRENCH_MODE;
            case BeefToolModuleRegistry.TOOL_SCREWDRIVER -> currentTool() == ToolTypeMode.SCREWDRIVER_MODE;
            case BeefToolModuleRegistry.TOOL_MALLET -> currentTool() == ToolTypeMode.MALLET_MODE;
            case BeefToolModuleRegistry.TOOL_CROWBAR -> currentTool() == ToolTypeMode.CROWBAR_MODE;
            case BeefToolModuleRegistry.TOOL_HAMMER -> currentTool() == ToolTypeMode.HAMMER_MODE;
            case BeefToolModuleRegistry.TOOL_OMNITOOL -> currentTool() == ToolTypeMode.OMNITOOL_MODE;
            case BeefToolModuleRegistry.CONSTRUCTION_WAND -> bool(UComponents.ConstructionWandEnabledComponent, false);
            case BeefToolModuleRegistry.CONSTRUCTION_WAND_ANGEL ->
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(targetItem, UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT)
                            == ConstructionWandCoreMode.ANGEL;
            case BeefToolModuleRegistry.CONSTRUCTION_WAND_DESTRUCTION ->
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(targetItem, UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT)
                            == ConstructionWandCoreMode.DESTRUCTION;
            case BeefToolModuleRegistry.ENHANCED_CHAIN_MINING -> bool(UComponents.EnhancedChainMiningComponent, false);
            case BeefToolModuleRegistry.FORCE_MINING -> bool(UComponents.ForceMiningComponent, false);
            case BeefToolModuleRegistry.AUTO_SMELT -> bool(UComponents.AutoSmeltComponent, false);
            case BeefToolModuleRegistry.EXDEORUM_CROOK -> bool(UComponents.ExDeorumCrookComponent, false);
            case BeefToolModuleRegistry.EXDEORUM_HAMMER -> bool(UComponents.ExDeorumHammerComponent, false);
            case BeefToolModuleRegistry.EXDEORUM_COMPRESSED_HAMMER ->
                    bool(UComponents.ExDeorumCompressedHammerComponent, false);
            case BeefToolModuleRegistry.AE_STORAGE_PRIORITY -> bool(UComponents.AEStoragePriorityComponent, false);
            case BeefToolModuleRegistry.AE_NETWORK_CONNECT -> bool(UComponents.AeNetworkConnectComponent, false);
            case BeefToolModuleRegistry.WRENCH_TAG -> bool(UComponents.WrenchTagEnabledComponent, true);
            case BeefToolModuleRegistry.FORCE_KILL -> bool(UComponents.ForceKillEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MALUM_SPIRIT ->
                    bool(UComponents.BeefMalumSpiritEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MYSTICAL_AGRICULTURE ->
                    bool(UComponents.BeefMysticalAgricultureEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_BEHEADING ->
                    bool(UComponents.BeefBeheadingEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_TIME_ACCELERATION ->
                    bool(UComponents.BeefTimeAccelerationEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_INVULNERABILITY ->
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(targetItem, UComponents.BeefInvulnerabilityEnabledComponent,
                            targetItem.getItem() instanceof com.sorrowmist.useless.content.items.EndlessBeafItem);
            case BeefToolModuleRegistry.BEEF_ADVANCED_STEALTH ->
                    bool(UComponents.BeefAdvancedStealthEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_CAPTURE -> bool(UComponents.BeefCaptureEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_TELEPORT -> bool(UComponents.BeefTeleportEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_AOE_DAMAGE -> bool(UComponents.BeefAoeDamageEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MAGNET -> bool(UComponents.BeefMagnetEnabledComponent, true);
            case BeefToolModuleRegistry.BEEF_FARMLAND_MODE -> bool(UComponents.BeefFarmlandModeComponent, false);
            case BeefToolModuleRegistry.BEEF_CROP_HARVEST -> bool(UComponents.BeefCropHarvestComponent, true);
            case BeefToolModuleRegistry.BEEF_SHEARS -> bool(UComponents.BeefShearsComponent, true);
            case BeefToolModuleRegistry.BEEF_FLINT_AND_STEEL ->
                    bool(UComponents.BeefFlintAndSteelComponent, true);
            case BeefToolModuleRegistry.BEEF_RITUAL_SATCHEL ->
                    bool(UComponents.BeefRitualSatchelComponent, false);
            case BeefToolModuleRegistry.BEEF_RIPEN -> bool(UComponents.BeefRipenComponent, false);
            case BeefToolModuleRegistry.BEEF_FORCE_GROW -> bool(UComponents.BeefForceGrowComponent, false);
            case BeefToolModuleRegistry.BEEF_AUTO_CLICK -> BeefAutoClicker.isEnabled();
            case BeefToolModuleRegistry.BEEF_WIRELESS_LOGISTICS ->
                    bool(UComponents.StaffLinkEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_KILL_AURA -> bool(UComponents.BeefKillAuraComponent, false);
            case BeefToolModuleRegistry.BEEF_PROTECT_MODE -> bool(UComponents.BeefProtectModeComponent, false);
            case BeefToolModuleRegistry.BEEF_ENTITY_TIME_ACCELERATION ->
                    bool(UComponents.BeefEntityTimeAccelerationComponent, false);
            default -> false;
        };
    }

    private ToolTypeMode currentTool() {
        return com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(targetItem, UComponents.CurrentToolTypeComponent, ToolTypeMode.NONE_MODE);
    }

    private boolean bool(com.sorrowmist.useless.compat.forge1201.component.DataComponentType<Boolean> type, boolean fallback) {
        return com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(targetItem, type, fallback);
    }

    private boolean bool(
            com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder<com.sorrowmist.useless.compat.forge1201.component.DataComponentType<?>,
                    com.sorrowmist.useless.compat.forge1201.component.DataComponentType<Boolean>> holder,
            boolean fallback) {
        return bool(holder.get(), fallback);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) statusKey = null;
        }
        boolean focused = pageNameField != null && pageNameField.isFocused();
        if (pageNameWasFocused && !focused && commitNameFields()) sendLayout();
        pageNameWasFocused = focused;
        if (chrome != null) chrome.tick();
        refreshTargetAndModes();
    }

    private void refreshTargetAndModes() {
        if (minecraft == null || minecraft.player == null) return;
        var target = UselessItemUtils.findTargetToolInHands(minecraft.player);
        if (target.isEmpty()) {
            onClose();
            return;
        }

        targetItem = target.get().getKey();
        String signature = availabilitySignature(targetItem);
        if (!signature.equals(lastAvailabilitySignature)) {
            lastAvailabilitySignature = signature;
            if (!awaitingLayout) rebuildWidgets();
        } else {
            for (ModeButton button : modeButtons) button.button().setMessage(buttonMessage(button.id()));
        }
    }

    private String availabilitySignature(ItemStack target) {
        StringBuilder signature = new StringBuilder();
        for (BeefToolModuleRegistry.Definition definition : BeefToolModuleRegistry.definitions()) {
            if (BeefToolModuleRegistry.isAvailable(definition.id(), target)) {
                signature.append('|').append(definition.id()).append(':').append(buttonMessage(definition.id()));
            }
        }
        return signature.toString();
    }

    public void receiveLayout(BeefToolLayout received) {
        this.layout = received.copy();
        this.layout.setSelectedPage(currentPage());
        this.awaitingLayout = false;
        this.lastAvailabilitySignature = availabilitySignature(targetItem);
        if (minecraft != null && minecraft.screen == this) rebuildWidgets();
    }

    public void receiveLayoutError(BeefToolLayout.Error error) {
        setStatus(errorKey(error));
        if (awaitingLayout) {
            awaitingLayout = false;
            layout = BeefToolModuleRegistry.defaultLayout();
            if (minecraft != null && minecraft.screen == this) rebuildWidgets();
        }
    }

    private void startEditing() {
        if (awaitingLayout) return;
        editing = true;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void finishEditing() {
        boolean changed = commitNameFields();
        editing = false;
        scrollOffset = 0;
        if (changed) sendLayout();
        rebuildWidgets();
    }

    private void changePage(int delta) {
        if (awaitingLayout || drag != null) return;
        boolean changed = commitNameFields();
        int oldPage = currentPage();
        int next = Mth.clamp(oldPage + delta, 0, layout.pages().size() - 1);
        if (next == oldPage) {
            if (changed) sendLayout();
            return;
        }
        layout.setSelectedPage(next);
        scrollOffset = 0;
        sendLayout();
        rebuildWidgets();
    }

    private void selectPage(int page, boolean persist, boolean rebuild) {
        if (page < 0 || page >= layout.pages().size()) return;
        if (drag == null) commitNameFields();
        layout.setSelectedPage(page);
        scrollOffset = 0;
        ensurePageTabVisible();
        if (persist) sendLayout();
        if (rebuild) rebuildWidgets();
    }

    private void addPage() {
        if (layout.pages().size() >= BeefToolLayout.MAX_PAGES) {
            setStatus("gui.useless_mod.mode_config.error.limit");
            return;
        }
        commitNameFields();
        layout.pages().add(new BeefToolLayout.Page("Page " + (layout.pages().size() + 1)));
        layout.setSelectedPage(layout.pages().size() - 1);
        scrollOffset = 0;
        sendLayout();
        rebuildWidgets();
    }

    private void deletePage() {
        commitNameFields();
        BeefToolLayout.Page page = currentPageObject();
        movePageModulesToUnassigned(page);
        if (layout.pages().size() == 1) {
            page.groups().clear();
            page.setName("Page 1");
            layout.setSelectedPage(0);
        } else {
            layout.pages().remove(page);
            layout.setSelectedPage(Math.min(currentPage(), layout.pages().size() - 1));
        }
        scrollOffset = 0;
        sendLayout();
        rebuildWidgets();
    }

    private void movePageModulesToUnassigned(BeefToolLayout.Page page) {
        for (BeefToolLayout.Group group : page.groups()) {
            layout.unassignedModules().addAll(group.modules());
        }
    }

    private void addGroup() {
        commitNameFields();
        BeefToolLayout.Page page = currentPageObject();
        if (page.groups().size() >= BeefToolLayout.MAX_GROUPS_PER_PAGE) {
            setStatus("gui.useless_mod.mode_config.error.limit");
            return;
        }
        page.groups().add(new BeefToolLayout.Group("Group " + (page.groups().size() + 1)));
        sendLayout();
        rebuildWidgets();
    }

    private void deleteGroup(CardLayout card) {
        if (card == null || card.groupIndex() < 0) return;
        commitNameFields();
        BeefToolLayout.Group group = groupAt(card.pageIndex(), card.groupIndex());
        if (group == null) return;
        layout.unassignedModules().addAll(group.modules());
        layout.pages().get(card.pageIndex()).groups().remove(group);
        sendLayout();
        rebuildWidgets();
    }

    private boolean commitNameFields() {
        boolean changed = false;
        if (pageNameField != null && !layout.pages().isEmpty()) {
            BeefToolLayout.Page page = currentPageObject();
            String name = acceptedName(pageNameField.getValue(), page.name());
            if (!name.equals(page.name())) {
                page.setName(name);
                changed = true;
            }
        }
        for (GroupNameField field : groupNameFields) {
            BeefToolLayout.Group group = groupAt(field.pageIndex(), field.groupIndex());
            if (group == null) continue;
            String name = acceptedName(field.field().getValue(), group.name());
            if (!name.equals(group.name())) {
                group.setName(name);
                changed = true;
            }
        }
        return changed;
    }

    private static String acceptedName(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > BeefToolLayout.MAX_NAME_LENGTH
                || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return fallback;
        }
        return trimmed;
    }

    private void exportLayout() {
        if (commitNameFields()) sendLayout();
        String text = layout.toJson();
        if (text.getBytes(StandardCharsets.UTF_8).length > BeefToolLayout.MAX_TEXT_LENGTH) {
            setStatus("gui.useless_mod.mode_config.error.limit");
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        setStatus("gui.useless_mod.mode_config.exported");
    }

    private void importLayout() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        try {
            BeefToolLayout candidate = BeefToolLayout.fromJson(text);
            validateKnownModules(candidate);
            pendingImportText = text;
            confirmImport = true;
        } catch (BeefToolLayout.LayoutException exception) {
            setStatus(errorKey(exception.error()));
        }
    }

    private void confirmImport(boolean accepted) {
        if (accepted && pendingImportText != null) {
            PacketDistributor.sendToServer(new BeefToolLayoutUpdatePacket(pendingImportText));
            setStatus("gui.useless_mod.mode_config.importing");
        }
        pendingImportText = null;
        confirmImport = false;
    }

    private void sendLayout() {
        if (awaitingLayout) return;
        try {
            validateKnownModules(layout);
            layout.setSelectedPage(currentPage());
            PacketDistributor.sendToServer(new BeefToolLayoutUpdatePacket(layout.toJson()));
        } catch (BeefToolLayout.LayoutException exception) {
            setStatus(errorKey(exception.error()));
        }
    }

    private static void validateKnownModules(BeefToolLayout candidate) throws BeefToolLayout.LayoutException {
        candidate.validate();
        // 等价组与布局同进同出，导入时也要在本地先拦下非法内容，
        // 免得等服务器回一个笼统的 INVALID_STRUCTURE。
        ChainGroupManager.validateEntries(candidate.chainGroups());
        // 保护名单同样随布局导入导出，本地先拦下非法内容。
        BeefToolProtectionManager.validateEntries(candidate.protectedTypes(), candidate.protectedEntities());
        for (BeefToolLayout.Page page : candidate.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                for (String id : group.modules()) validateKnown(id);
            }
        }
        for (String id : candidate.unassignedModules()) validateKnown(id);
    }

    private static void validateKnown(String id) throws BeefToolLayout.LayoutException {
        if (!BeefToolModuleRegistry.isKnown(id)) {
            throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.UNKNOWN_MODULE);
        }
    }

    private static String errorKey(BeefToolLayout.Error error) {
        return switch (error) {
            case INVALID_TEXT -> "gui.useless_mod.mode_config.error.invalid_text";
            case UNSUPPORTED_VERSION -> "gui.useless_mod.mode_config.error.unsupported_version";
            case INVALID_STRUCTURE -> "gui.useless_mod.mode_config.error.invalid_structure";
            case INVALID_NAME -> "gui.useless_mod.mode_config.error.invalid_name";
            case DUPLICATE_MODULE -> "gui.useless_mod.mode_config.error.duplicate_module";
            case UNKNOWN_MODULE -> "gui.useless_mod.mode_config.error.unknown_module";
            case LIMIT -> "gui.useless_mod.mode_config.error.limit";
        };
    }

    private void setStatus(String key) {
        statusKey = key;
        statusTicks = 100;
    }

    private void onModeSelected(String id) {
        if (targetItem == null || targetItem.isEmpty()) return;
        switch (id) {
            case BeefToolModuleRegistry.ENCHANT_SILK_TOUCH ->
                    PacketDistributor.sendToServer(new EnchantmentSwitchPacket(EnchantMode.SILK_TOUCH));
            case BeefToolModuleRegistry.ENCHANT_FORTUNE ->
                    PacketDistributor.sendToServer(new EnchantmentSwitchPacket(EnchantMode.FORTUNE));
            case BeefToolModuleRegistry.TOOL_NONE -> sendToolType(ToolTypeMode.NONE_MODE);
            case BeefToolModuleRegistry.TOOL_WRENCH -> sendToolType(ToolTypeMode.WRENCH_MODE);
            case BeefToolModuleRegistry.TOOL_SCREWDRIVER -> sendToolType(ToolTypeMode.SCREWDRIVER_MODE);
            case BeefToolModuleRegistry.TOOL_MALLET -> sendToolType(ToolTypeMode.MALLET_MODE);
            case BeefToolModuleRegistry.TOOL_CROWBAR -> sendToolType(ToolTypeMode.CROWBAR_MODE);
            case BeefToolModuleRegistry.TOOL_HAMMER -> sendToolType(ToolTypeMode.HAMMER_MODE);
            case BeefToolModuleRegistry.TOOL_OMNITOOL -> sendToolType(
                    currentTool() == ToolTypeMode.OMNITOOL_MODE
                            ? ToolTypeMode.NONE_MODE
                            : ToolTypeMode.OMNITOOL_MODE);
            case BeefToolModuleRegistry.CONSTRUCTION_WAND -> toggle(ModeTogglePacket.ModeType.CONSTRUCTION_WAND,
                    UComponents.ConstructionWandEnabledComponent, false);
            case BeefToolModuleRegistry.CONSTRUCTION_WAND_ANGEL -> toggleCore(ConstructionWandCoreMode.ANGEL);
            case BeefToolModuleRegistry.CONSTRUCTION_WAND_DESTRUCTION -> toggleCore(ConstructionWandCoreMode.DESTRUCTION);
            case BeefToolModuleRegistry.ENHANCED_CHAIN_MINING -> toggle(ModeTogglePacket.ModeType.CHAIN_MINING,
                    UComponents.EnhancedChainMiningComponent, false);
            case BeefToolModuleRegistry.FORCE_MINING -> toggle(ModeTogglePacket.ModeType.FORCE_MINING,
                    UComponents.ForceMiningComponent, false);
            case BeefToolModuleRegistry.AUTO_SMELT -> toggle(ModeTogglePacket.ModeType.AUTO_SMELT,
                    UComponents.AutoSmeltComponent, false);
            case BeefToolModuleRegistry.EXDEORUM_CROOK -> toggle(ModeTogglePacket.ModeType.EXDEORUM_CROOK,
                    UComponents.ExDeorumCrookComponent, false);
            case BeefToolModuleRegistry.EXDEORUM_HAMMER -> toggle(ModeTogglePacket.ModeType.EXDEORUM_HAMMER,
                    UComponents.ExDeorumHammerComponent, false);
            case BeefToolModuleRegistry.EXDEORUM_COMPRESSED_HAMMER ->
                    toggle(ModeTogglePacket.ModeType.EXDEORUM_COMPRESSED_HAMMER,
                            UComponents.ExDeorumCompressedHammerComponent, false);
            case BeefToolModuleRegistry.AE_STORAGE_PRIORITY -> toggle(ModeTogglePacket.ModeType.AE_STORAGE_PRIORITY,
                    UComponents.AEStoragePriorityComponent, false);
            case BeefToolModuleRegistry.AE_NETWORK_CONNECT -> toggle(ModeTogglePacket.ModeType.AE_NETWORK_CONNECT,
                    UComponents.AeNetworkConnectComponent, false);
            case BeefToolModuleRegistry.WRENCH_TAG -> toggle(ModeTogglePacket.ModeType.WRENCH_TAG,
                    UComponents.WrenchTagEnabledComponent, true);
            case BeefToolModuleRegistry.FORCE_KILL -> toggle(ModeTogglePacket.ModeType.FORCE_KILL,
                    UComponents.ForceKillEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MALUM_SPIRIT ->
                    toggle(ModeTogglePacket.ModeType.BEEF_MALUM_SPIRIT,
                            UComponents.BeefMalumSpiritEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MYSTICAL_AGRICULTURE ->
                    toggle(ModeTogglePacket.ModeType.BEEF_MYSTICAL_AGRICULTURE,
                            UComponents.BeefMysticalAgricultureEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_BEHEADING ->
                    toggle(ModeTogglePacket.ModeType.BEEF_BEHEADING,
                            UComponents.BeefBeheadingEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_TIME_ACCELERATION -> toggle(ModeTogglePacket.ModeType.BEEF_TIME_ACCELERATION,
                    UComponents.BeefTimeAccelerationEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_INVULNERABILITY -> toggle(ModeTogglePacket.ModeType.BEEF_INVULNERABILITY,
                    UComponents.BeefInvulnerabilityEnabledComponent,
                    targetItem.getItem() instanceof com.sorrowmist.useless.content.items.EndlessBeafItem);
            case BeefToolModuleRegistry.BEEF_ADVANCED_STEALTH -> toggle(ModeTogglePacket.ModeType.BEEF_ADVANCED_STEALTH,
                    UComponents.BeefAdvancedStealthEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_CAPTURE -> toggle(ModeTogglePacket.ModeType.BEEF_CAPTURE,
                    UComponents.BeefCaptureEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_TELEPORT -> toggle(ModeTogglePacket.ModeType.BEEF_TELEPORT,
                    UComponents.BeefTeleportEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_AOE_DAMAGE -> toggle(ModeTogglePacket.ModeType.BEEF_AOE_DAMAGE,
                    UComponents.BeefAoeDamageEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MAGNET -> toggle(ModeTogglePacket.ModeType.BEEF_MAGNET,
                    UComponents.BeefMagnetEnabledComponent, true);
            case BeefToolModuleRegistry.BEEF_FARMLAND_MODE -> toggle(ModeTogglePacket.ModeType.BEEF_FARMLAND_MODE,
                    UComponents.BeefFarmlandModeComponent, false);
            case BeefToolModuleRegistry.BEEF_CROP_HARVEST -> toggle(ModeTogglePacket.ModeType.BEEF_CROP_HARVEST,
                    UComponents.BeefCropHarvestComponent, true);
            case BeefToolModuleRegistry.BEEF_SHEARS -> toggle(ModeTogglePacket.ModeType.BEEF_SHEARS,
                    UComponents.BeefShearsComponent, true);
            case BeefToolModuleRegistry.BEEF_FLINT_AND_STEEL ->
                    toggle(ModeTogglePacket.ModeType.BEEF_FLINT_AND_STEEL,
                            UComponents.BeefFlintAndSteelComponent, true);
            case BeefToolModuleRegistry.BEEF_RITUAL_SATCHEL ->
                    toggle(ModeTogglePacket.ModeType.BEEF_RITUAL_SATCHEL,
                            UComponents.BeefRitualSatchelComponent, false);
            case BeefToolModuleRegistry.BEEF_RIPEN -> toggle(ModeTogglePacket.ModeType.BEEF_RIPEN,
                    UComponents.BeefRipenComponent, false);
            case BeefToolModuleRegistry.BEEF_FORCE_GROW -> toggle(ModeTogglePacket.ModeType.BEEF_FORCE_GROW,
                    UComponents.BeefForceGrowComponent, false);
            case BeefToolModuleRegistry.BEEF_AUTO_CLICK -> BeefAutoClicker.toggle();
            case BeefToolModuleRegistry.BEEF_WIRELESS_LOGISTICS ->
                    toggle(ModeTogglePacket.ModeType.BEEF_WIRELESS_LOGISTICS,
                            UComponents.StaffLinkEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_KILL_AURA ->
                    toggle(ModeTogglePacket.ModeType.BEEF_KILL_AURA,
                            UComponents.BeefKillAuraComponent, false);
            case BeefToolModuleRegistry.BEEF_PROTECT_MODE ->
                    toggle(ModeTogglePacket.ModeType.BEEF_PROTECT_MODE,
                            UComponents.BeefProtectModeComponent, false);
            case BeefToolModuleRegistry.BEEF_ENTITY_TIME_ACCELERATION ->
                    toggle(ModeTogglePacket.ModeType.BEEF_ENTITY_TIME_ACCELERATION,
                            UComponents.BeefEntityTimeAccelerationComponent, false);
            default -> {
            }
        }
    }

    private void sendToolType(ToolTypeMode mode) {
        PacketDistributor.sendToServer(new ToolTypeModeSwitchPacket(mode));
    }

    private void toggle(ModeTogglePacket.ModeType type,
                        com.sorrowmist.useless.compat.forge1201.component.DataComponentType<Boolean> component,
                        boolean fallback) {
        PacketDistributor.sendToServer(new ModeTogglePacket(type, !bool(component, fallback)));
    }

    private void toggle(
            ModeTogglePacket.ModeType type,
            com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder<com.sorrowmist.useless.compat.forge1201.component.DataComponentType<?>,
                    com.sorrowmist.useless.compat.forge1201.component.DataComponentType<Boolean>> component,
            boolean fallback) {
        toggle(type, component.get(), fallback);
    }

    private void toggleCore(ConstructionWandCoreMode requested) {
        ConstructionWandCoreMode current = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(targetItem,
                UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT);
        PacketDistributor.sendToServer(new ConstructionWandCorePacket(
                current == requested ? ConstructionWandCoreMode.DEFAULT : requested));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.pose().pushPose();
        graphics.pose().translate(uiOffsetX, uiOffsetY, 0.0F);
        graphics.pose().scale(uiScale, uiScale, 1.0F);
        mouseX = (int) toUiX(mouseX);
        mouseY = (int) toUiY(mouseY);
        if (chrome == null || chromeW != panelWidth || chromeH != panelHeight) {
            chromeW = panelWidth; chromeH = panelHeight;
            chrome = new AppleHost(panelWidth, panelHeight);
            chrome.add(new AppleWindow(panelWidth, panelHeight, () -> title).referenceIcon("endless_beaf_item"));
        }
        chrome.layout(uiWidth, uiHeight, panelLeft, panelTop);
        chrome.render(graphics, mouseX, mouseY, partialTick);
        drawPageTabs(graphics);
        int footerTop = contentBottom;
        graphics.fill(panelLeft + 10, footerTop, panelLeft + panelWidth - 10, footerTop + 1, AppleStyle.SEPARATOR);

        if (awaitingLayout) {
            graphics.drawString(font, Component.translatable("gui.useless_mod.mode_config.loading"),
                    panelLeft + PANEL_MARGIN, contentTop + 8, AppleStyle.TERTIARY, false);
        } else {
            drawWheelPreview(graphics, mouseX, mouseY);
            enableContentScissor(graphics);
            try {
                drawCards(graphics, mouseX, mouseY);
                if (editing) {
                    drawEditorModules(graphics, mouseX, mouseY);
                    for (GroupNameField field : groupNameFields) {
                        if (field.field().visible) field.field().render(graphics, mouseX, mouseY, partialTick);
                    }
                } else {
                    for (ModeButton modeButton : modeButtons) {
                        if (modeButton.button().visible) drawModeChip(graphics, modeButton, mouseX, mouseY);
                    }
                    drawChainGroupGear(graphics, mouseX, mouseY);
                }
            } finally {
                graphics.flush();
                RenderSystem.disableScissor();
            }
        }

        drawChainGroupGearTooltip(graphics, mouseX, mouseY);
        drawModeButtonTooltips(graphics, mouseX, mouseY);

        if (pageNameField != null && pageNameField.visible) {
            String label = Component.translatable("gui.useless_mod.mode_config.page_name").getString();
            graphics.drawString(font, label, pageNameField.getX() - font.width(label) - 6,
                    panelTop + 44, AppleStyle.TERTIARY, false);
            pageNameField.render(graphics, mouseX, mouseY, partialTick);
        }
        for (Renderable button : toolbarButtons) {
            if (button instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        drawStatus(graphics);
        if (confirmImport) drawImportConfirmation(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void enableContentScissor(GuiGraphics graphics) {
        graphics.flush();
        var window = Minecraft.getInstance().getWindow();
        double guiScale = window.getGuiScale();
        double screenLeft = uiOffsetX + contentLeft * uiScale;
        double screenTop = uiOffsetY + contentTop * uiScale;
        double screenRight = uiOffsetX + contentRight * uiScale;
        double screenBottom = uiOffsetY + contentBottom * uiScale;
        RenderSystem.enableScissor(
                (int) (screenLeft * guiScale),
                (int) (window.getHeight() - screenBottom * guiScale),
                Math.max(0, (int) ((screenRight - screenLeft) * guiScale)),
                Math.max(0, (int) ((screenBottom - screenTop) * guiScale)));
    }

    private void drawCards(GuiGraphics graphics, int mouseX, int mouseY) {
        for (CardLayout card : cardLayouts) {
            if (!intersectsContent(card.rect())) continue;
            AppleTex.card(AppleStyle.R_CARD).draw(graphics, mouseX, mouseY, card.left(), card.top(), card.width(), card.height());
            // 卡片标题区：只用分隔线 + 粗体/灰阶区分层级（不再有彩色强调条）。
            graphics.fill(card.left() + 6, card.top() + CARD_HEADER_HEIGHT - 2,
                    card.left() + card.width() - 6, card.top() + CARD_HEADER_HEIGHT - 1, AppleStyle.SEPARATOR);
            if (card.unassigned()) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.mode_config.unassigned").withStyle(net.minecraft.ChatFormatting.BOLD),
                        card.left() + 6, card.top() + 4, AppleStyle.SECONDARY, false);
            } else if (!editing) {
                Component gname = Component.literal(groupAt(card.pageIndex(), card.groupIndex()).name())
                        .withStyle(net.minecraft.ChatFormatting.BOLD);
                graphics.drawString(font, gname, card.left() + 6, card.top() + 4, AppleStyle.LABEL, false);
                graphics.drawString(font, Component.translatable("gui.useless_mod.mode_config.module_count", card.modules().size()),
                        card.left() + 12 + font.width(gname), card.top() + 4, AppleStyle.TERTIARY, false);
            } else {
                AppleUi.icon(graphics, AppleUi.Icon.GRIP, card.left() + 2, card.top() + 5, 7, AppleStyle.TERTIARY);
                AppleUi.icon(graphics, AppleUi.Icon.XMARK, card.left() + card.width() - 14, card.top() + 5, 7, AppleStyle.TERTIARY);
            }
            if (card.modules().isEmpty()) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.mode_config.empty"),
                        card.left() + 6, card.top() + CARD_HEADER_HEIGHT + 4,
                        AppleStyle.TERTIARY, false);
            }
        }
    }

    private void drawModeChip(GuiGraphics g, ModeButton mode, int mx, int my) {
        var b = mode.button();
        boolean on = isActive(mode.id());
        int accent = moduleAccent(mode.id());
        AppleUi.fillRound(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 7,
                on ? AppleStyle.tintOf(accent) : b.isMouseOver(mx, my) ? AppleStyle.FILL_HOVER : AppleStyle.FILL);
        int reserve = BeefToolModuleRegistry.ENHANCED_CHAIN_MINING.equals(mode.id()) ? GEAR_SIZE + 2 : 0;
        AppleTextLayout.drawCentered(g, font, AppleButton.appleTint(buttonMessage(mode.id())),
                b.getX(), b.getY(), b.getWidth() - reserve, b.getHeight(), 5, 3, null,
                on ? 0xFF000000 | AppleUi.readable(accent) : AppleStyle.SECONDARY);
    }

    private void drawEditorModules(GuiGraphics graphics, int mouseX, int mouseY) {
        for (ModuleLayout module : moduleLayouts) {
            if (!intersectsContent(module.rect())) continue;
            boolean hovered = module.rect().contains(mouseX, mouseY);
            AppleUi2.round(graphics, module.rect().left(), module.rect().top(), module.rect().width(), module.rect().height(),
                    AppleStyle.R_CONTROL, hovered ? AppleStyle.FILL_HOVER : AppleStyle.FILL);
            Component message = buttonMessage(module.id());
            String text = font.plainSubstrByWidth(message.getString(), Math.max(1, module.rect().width() - 6));
            int textColor = hovered ? AppleStyle.LABEL : AppleStyle.SECONDARY;
            graphics.drawString(font, text, module.rect().left() + 3,
                    module.rect().top() + 5, textColor, false);
        }
        if (drag != null && drag.moved) {
            String text = drag.kind == DragKind.MODULE
                    ? buttonMessage(drag.moduleId).getString()
                    : drag.kind == DragKind.GROUP ? drag.group.name() : drag.page.name();
            int ghostWidth = Math.min(150, font.width(text) + 8);
            int left = Mth.clamp((int) (drag.mouseX - ghostWidth / 2), 0, Math.max(0, uiWidth - ghostWidth));
            int top = Mth.clamp((int) (drag.mouseY - 10), 0, Math.max(0, uiHeight - 20));
            AppleTex.card(AppleStyle.R_CONTROL).draw(graphics, 0, 0, left, top, ghostWidth, 18);
            AppleUi2.round(graphics, left, top, ghostWidth, 18, AppleStyle.R_CONTROL, AppleStyle.SELECTED_ROW);
            graphics.drawString(font, font.plainSubstrByWidth(text, ghostWidth - 6), left + 3, top + 5,
                    AppleStyle.LABEL, false);
        }
    }

    private void drawPageTabs(GuiGraphics graphics) {
        pageTabRects.clear();
        if (layout.pages().isEmpty()) return;
        ensurePageTabVisible();
        int visibleCount = tabVisibleCount();
        int shown = Math.min(layout.pages().size() - pageTabOffset, visibleCount);
        int left = panelLeft + PANEL_MARGIN;
        int y = panelTop + 40;
        // 分段控件轨道
        AppleUi2.round(graphics, left - 1, y - 1, shown * PAGE_TAB_WIDTH, PAGE_TAB_HEIGHT + 2, AppleStyle.R_CONTROL, AppleStyle.TRACK);
        for (int index = pageTabOffset;
             index < Math.min(layout.pages().size(), pageTabOffset + visibleCount); index++) {
            int x = left + (index - pageTabOffset) * PAGE_TAB_WIDTH;
            Rect rect = new Rect(x, y, PAGE_TAB_WIDTH - 2, PAGE_TAB_HEIGHT);
            pageTabRects.add(rect);
            boolean selected = index == currentPage();
            if (selected) {
                AppleTex.card(AppleStyle.R_CONTROL - 1).draw(graphics, 0, 0, rect.left(), rect.top(), rect.width(), rect.height());
            } else if (index > pageTabOffset && index - 1 != currentPage()) {
                graphics.fill(rect.left() - 1, rect.top() + 3, rect.left(), rect.bottom() - 3, AppleStyle.SEPARATOR);
            }
            String name = font.plainSubstrByWidth(layout.pages().get(index).name(), rect.width() - 6);
            graphics.drawString(font, name, rect.left() + (rect.width() - font.width(name)) / 2, rect.top() + 3,
                    selected ? AppleStyle.LABEL : AppleStyle.SECONDARY, false);
        }
    }

    private void ensurePageTabVisible() {
        int visibleCount = tabVisibleCount();
        int selected = currentPage();
        if (selected < pageTabOffset) pageTabOffset = selected;
        if (selected >= pageTabOffset + visibleCount) pageTabOffset = selected - visibleCount + 1;
        pageTabOffset = Mth.clamp(pageTabOffset, 0, Math.max(0, layout.pages().size() - visibleCount));
    }

    private int tabVisibleCount() {
        return Mth.clamp((WHEEL_W - 8) / PAGE_TAB_WIDTH, 1, 4);
    }

    private void drawStatus(GuiGraphics graphics) {
        if (statusKey == null) {
            if (editing && !awaitingLayout) graphics.drawString(font,
                    Component.translatable("gui.useless_mod.mode_config.hint"),
                    panelLeft + PANEL_MARGIN, panelTop + panelHeight - 16, AppleStyle.TERTIARY, false);
            return;
        }
        int y = panelTop + panelHeight - 16;
        Component status = Component.translatable(statusKey);
        String text = font.plainSubstrByWidth(status.getString(), Math.max(1, panelWidth - 90));
        graphics.drawString(font, text, panelLeft + PANEL_MARGIN, y, AppleStyle.SECONDARY, false);
    }

    private void drawImportConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalWidth = Math.min(320, uiWidth - 12);
        int modalHeight = 82;
        int left = (uiWidth - modalWidth) / 2;
        int top = (uiHeight - modalHeight) / 2;
        graphics.fill(0, 0, uiWidth, uiHeight, AppleStyle.SCRIM);
        AppleStyle.shadow(AppleStyle.SH_CARD).draw(graphics, mouseX, mouseY, left, top, modalWidth, modalHeight);
        AppleTex.card(AppleStyle.R_WINDOW).draw(graphics, mouseX, mouseY, left, top, modalWidth, modalHeight);
        graphics.drawString(font,
                font.plainSubstrByWidth(
                        Component.translatable("gui.useless_mod.mode_config.import_confirm").getString(),
                        modalWidth - 16),
                left + 10, top + 12, AppleStyle.LABEL, false);
        drawManualButton(graphics, left + 10, top + 48, 110, 18,
                Component.translatable("gui.useless_mod.mode_config.confirm").withStyle(s0 -> s0.withInsertion("primary")),
                new Rect(left + 10, top + 48, 110, 18), mouseX, mouseY);
        drawManualButton(graphics, left + modalWidth - 120, top + 48, 110, 18,
                Component.translatable("gui.useless_mod.mode_config.cancel"),
                new Rect(left + modalWidth - 120, top + 48, 110, 18), mouseX, mouseY);
    }

    private void drawManualButton(GuiGraphics graphics, int x, int y, int width, int height,
                                  Component message, Rect rect, int mouseX, int mouseY) {
        boolean primary = "primary".equals(message.getStyle().getInsertion());
        AppleUi.ButtonStyle st = primary ? AppleUi.ButtonStyle.PRIMARY : AppleUi.ButtonStyle.GRAY;
        AppleUi.buttonSurface(graphics, x, y, width, height, st, rect.contains(mouseX, mouseY), false, true);
        String t = font.plainSubstrByWidth(message.getString(), width - 6);
        graphics.drawString(font, t, x + (width - font.width(t)) / 2, y + 5, AppleUi.buttonTextColor(st, true), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = toUiX(mouseX);
        mouseY = toUiY(mouseY);
        if (confirmImport) {
            int modalWidth = Math.min(320, uiWidth - 12);
            int modalHeight = 82;
            int left = (uiWidth - modalWidth) / 2;
            int top = (uiHeight - modalHeight) / 2;
            if (new Rect(left + 10, top + 48, 110, 18).contains(mouseX, mouseY)) {
                confirmImport(true);
                return true;
            }
            if (new Rect(left + modalWidth - 120, top + 48, 110, 18).contains(mouseX, mouseY)) {
                confirmImport(false);
                return true;
            }
            return true;
        }
        if (awaitingLayout) return true;
        if (pageNameField != null && pageNameField.isFocused() && !pageNameField.isMouseOver(mouseX, mouseY)) {
            if (commitNameFields()) sendLayout();
            pageNameField.setFocused(false);
            setFocused(null);
        }
        if (!editing && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (WheelChoice choice : wheelChoices) {
                if (choice.rect().contains(mouseX, mouseY)) {
                    onModeSelected(choice.id());
                    return true;
                }
            }
        }

        // 连锁挖掘模块右端的齿轮：进入「连锁等价组」编辑子界面。
        // 必须在 super.mouseClicked 之前拦截，否则点击会被模块按钮先吃掉。
        if (!editing && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ModuleLayout chainModule = findModule(BeefToolModuleRegistry.ENHANCED_CHAIN_MINING);
            if (chainModule != null && intersectsContent(chainModule.rect())
                    && chainGroupGearRect(chainModule).contains(mouseX, mouseY)) {
                // 菜单是空的、只在客户端本地建，不下发；界面继承容器屏是为了让 JEI/EMI 的侧栏出现。
                Inventory playerInventory = Minecraft.getInstance().player.getInventory();
                Minecraft.getInstance().setScreen(new ChainGroupScreen(
                        new ChainGroupMenu(0, playerInventory), playerInventory, targetItem, layout.copy()));
                return true;
            }
        }

        int pageTab = pageAt(mouseX, mouseY);
        if (pageTab >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (editing) {
                drag = DragState.page(layout.pages().get(pageTab), mouseX, mouseY);
            } else {
                selectPage(pageTab, true, true);
            }
            return true;
        }

        if (editing && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CardLayout deleteCard = cardDeleteAt(mouseX, mouseY);
            if (deleteCard != null) {
                deleteGroup(deleteCard);
                return true;
            }
            CardLayout handleCard = groupHandleAt(mouseX, mouseY);
            if (handleCard != null) {
                BeefToolLayout.Group group = groupAt(handleCard.pageIndex(), handleCard.groupIndex());
                if (group != null) {
                    drag = DragState.group(layout.pages().get(handleCard.pageIndex()), group,
                            mouseX, mouseY);
                    return true;
                }
            }
            ModuleLayout module = moduleAt(mouseX, mouseY);
            if (module != null) {
                drag = DragState.module(module.id(), mouseX, mouseY);
                return true;
            }
        }
        if (!editing && (mouseX < contentLeft || mouseX >= contentRight
                || mouseY < contentTop || mouseY >= contentBottom)) {
            for (var toolbar : toolbarButtons) {
                if (toolbar.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(toolbar);
                    return true;
                }
            }
            if (pageNameField != null && pageNameField.mouseClicked(mouseX, mouseY, button)) {
                setFocused(pageNameField);
                return true;
            }
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = toUiX(mouseX);
        mouseY = toUiY(mouseY);
        dragX /= uiScale;
        dragY /= uiScale;
        if (drag == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        drag.mouseX = mouseX;
        drag.mouseY = mouseY;
        if (!drag.moved && Math.hypot(mouseX - drag.startX, mouseY - drag.startY) >= DRAG_THRESHOLD) {
            drag.moved = true;
        }
        if (!drag.moved) return true;

        if (drag.kind != DragKind.PAGE) {
            int page = pageAt(mouseX, mouseY);
            if (page >= 0 && page != currentPage()) {
                DragState saved = drag;
                selectPage(page, false, true);
                drag = saved;
            }
            if (mouseY < contentTop + 12 && scrollOffset > 0) {
                scrollOffset = Math.max(0, scrollOffset - 6);
                updateGeometryAfterScroll();
            } else if (mouseY > contentBottom - 12 && scrollOffset < maxScroll) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 6);
                updateGeometryAfterScroll();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = toUiX(mouseX);
        mouseY = toUiY(mouseY);
        for (PressableAE2Button toolbarButton : toolbarButtons) toolbarButton.releaseVisualState();
        for (ModeButton modeButton : modeButtons) modeButton.button().releaseVisualState();
        if (drag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            drag.mouseX = mouseX;
            drag.mouseY = mouseY;
            DragState released = drag;
            drag = null;
            if (!released.moved) {
                if (released.kind == DragKind.PAGE) {
                    selectPage(layout.pages().indexOf(released.page), true, true);
                }
                return true;
            }
            if (drop(released, mouseX, mouseY)) {
                sendLayout();
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        mouseX = toUiX(mouseX);
        mouseY = toUiY(mouseY);
        if (confirmImport || awaitingLayout) return true;
        if (mouseY >= contentTop && mouseY <= contentBottom) {
            int next = Mth.clamp(scrollOffset - (int) Math.round(deltaY * 18), 0, maxScroll);
            if (next != scrollOffset) {
                scrollOffset = next;
                updateGeometryAfterScroll();
            }
            return true;
        }
        if (mouseY >= panelTop + 40 && mouseY <= panelTop + 40 + PAGE_TAB_HEIGHT) {
            pageTabOffset = Mth.clamp(pageTabOffset - (int) Math.signum(deltaY),
                    0, Math.max(0, layout.pages().size() - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaY);
    }

    private double toUiX(double mouseX) {
        return (mouseX - uiOffsetX) / uiScale;
    }

    private double toUiY(double mouseY) {
        return (mouseY - uiOffsetY) / uiScale;
    }

    private void updateGeometryAfterScroll() {
        calculateGeometry();
        positionModeButtons();
        updateEditorFieldPositions();
    }

    private boolean drop(DragState released, double mouseX, double mouseY) {
        if (released.kind == DragKind.MODULE) return dropModule(released.moduleId, mouseX, mouseY);
        if (released.kind == DragKind.GROUP) return dropGroup(released.group, mouseX, mouseY);
        return dropPage(released.page, mouseX, mouseY);
    }

    private boolean dropModule(String moduleId, double mouseX, double mouseY) {
        CardLayout targetCard = groupAt(mouseX, mouseY);
        if (targetCard != null) {
            BeefToolLayout.Group targetGroup = groupAt(targetCard.pageIndex(), targetCard.groupIndex());
            if (targetGroup == null) return false;
            int insertion = insertionIndex(targetCard, mouseX, mouseY);
            removeModule(moduleId);
            insertion = Mth.clamp(insertion, 0, targetGroup.modules().size());
            targetGroup.modules().add(insertion, moduleId);
            return true;
        }
        if (unassignedCardAt(mouseX, mouseY) != null || pageAt(mouseX, mouseY) >= 0) {
            removeModule(moduleId);
            layout.unassignedModules().add(moduleId);
            return true;
        }
        return false;
    }

    private int insertionIndex(CardLayout targetCard, double mouseX, double mouseY) {
        int insertion = targetCard.modules().size();
        for (ModuleLayout module : moduleLayouts) {
            if (module.pageIndex() != targetCard.pageIndex()
                    || module.groupIndex() != targetCard.groupIndex()
                    || module.unassigned() != targetCard.unassigned()) continue;
            if (module.rect().contains(mouseX, mouseY)) {
                insertion = module.index() + (mouseY > module.rect().top() + module.rect().height() / 2 ? 1 : 0);
                break;
            }
        }
        return insertion;
    }

    private void removeModule(String moduleId) {
        layout.unassignedModules().removeIf(moduleId::equals);
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                group.modules().removeIf(moduleId::equals);
            }
        }
    }

    private boolean dropGroup(BeefToolLayout.Group group, double mouseX, double mouseY) {
        if (group == null) return false;
        CardLayout targetCard = groupAt(mouseX, mouseY);
        BeefToolLayout.Page targetPage = layout.pages().get(currentPage());
        BeefToolLayout.Group targetGroup = targetCard == null
                ? null : groupAt(targetCard.pageIndex(), targetCard.groupIndex());
        if (targetGroup == group) return false;

        if (targetCard == null && pageAt(mouseX, mouseY) < 0) return false;
        int insertion = targetGroup == null ? targetPage.groups().size()
                : targetPage.groups().indexOf(targetGroup);
        BeefToolLayout.Page sourcePage = null;
        for (BeefToolLayout.Page page : layout.pages()) {
            if (page.groups().remove(group)) {
                sourcePage = page;
                break;
            }
        }
        if (sourcePage == null) return false;
        if (sourcePage == targetPage && targetGroup != null
                && sourcePage.groups().indexOf(targetGroup) < insertion) insertion--;
        insertion = Mth.clamp(insertion, 0, targetPage.groups().size());
        targetPage.groups().add(insertion, group);
        layout.setSelectedPage(layout.pages().indexOf(targetPage));
        return true;
    }

    private boolean dropPage(BeefToolLayout.Page page, double mouseX, double mouseY) {
        int targetIndex = pageAt(mouseX, mouseY);
        if (targetIndex < 0) return false;
        BeefToolLayout.Page target = layout.pages().get(targetIndex);
        if (target == page) return false;
        int sourceIndex = layout.pages().indexOf(page);
        layout.pages().remove(page);
        int insertion = layout.pages().indexOf(target);
        if (sourceIndex < insertion) insertion--;
        layout.pages().add(insertion, page);
        layout.setSelectedPage(layout.pages().indexOf(page));
        return true;
    }

    private ModuleLayout moduleAt(double mouseX, double mouseY) {
        for (ModuleLayout module : moduleLayouts) {
            if (module.rect().contains(mouseX, mouseY) && intersectsContent(module.rect())) return module;
        }
        return null;
    }

    /** 按模块 ID 找当前页的模块布局，找不到返回 null。 */
    private ModuleLayout findModule(String id) {
        for (ModuleLayout module : moduleLayouts) {
            if (module.id().equals(id)) {
                return module;
            }
        }
        return null;
    }

    /** 连锁等价组齿轮的命中区域：贴在模块按钮右端内侧。 */
    private Rect chainGroupGearRect(ModuleLayout module) {
        Rect rect = module.rect();
        return new Rect(rect.right() - GEAR_SIZE - 1, rect.top() + (rect.height() - GEAR_SIZE) / 2,
                GEAR_SIZE, GEAR_SIZE);
    }

    /**
     * 在连锁挖掘模块按钮右端画一个齿轮，提示这里可以点开连锁等价组配置。
     *
     * <p>用 {@code fill} 拼出轮廓，不新增贴图。</p>
     */
    private void drawChainGroupGear(GuiGraphics graphics, int mouseX, int mouseY) {
        ModuleLayout module = findModule(BeefToolModuleRegistry.ENHANCED_CHAIN_MINING);
        if (module == null || !intersectsContent(module.rect())) {
            return;
        }
        Rect gear = chainGroupGearRect(module);
        int color = gear.contains(mouseX, mouseY)
                ? AppleStyle.LABEL
                : AppleStyle.SECONDARY;

        // Apple 风：浅蓝圆底 + 自绘齿轮图标；按钮标签已为其预留右侧空间，不再与文字重叠
        boolean hot = gear.contains(mouseX, mouseY);
        AppleUi.fillRound(graphics, gear.left(), gear.top(), GEAR_SIZE, GEAR_SIZE, GEAR_SIZE / 2f,
                hot ? 0x33007AFF : 0x1A007AFF);
        AppleUi.icon(graphics, AppleUi.Icon.GEAR, gear.left() + 2, gear.top() + 2, GEAR_SIZE - 4,
                hot ? AppleUi.BLUE_TEXT : AppleUi.BLUE);
    }

    /** 齿轮的悬停提示画在裁剪区之外，免得提示框被内容区裁掉。 */
    private void drawChainGroupGearTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (awaitingLayout || editing) {
            return;
        }
        ModuleLayout module = findModule(BeefToolModuleRegistry.ENHANCED_CHAIN_MINING);
        if (module == null || !intersectsContent(module.rect())
                || !chainGroupGearRect(module).contains(mouseX, mouseY)) {
            return;
        }
        graphics.renderTooltip(font,
                Component.translatable("gui.useless_mod.chain_group.gear_tooltip"), mouseX, mouseY);
    }

    /** 少数「光看名字不知道该怎么用」的模式按钮，悬停时给出说明。 */
    private void drawModeButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (awaitingLayout || editing) {
            return;
        }
        for (ModeButton modeButton : modeButtons) {
            PressableAE2Button button = modeButton.button();
            if (!button.visible || !button.isMouseOver(mouseX, mouseY)) continue;
            if (!intersectsContent(new Rect(button.getX(), button.getY(), button.getWidth(), button.getHeight()))) {
                return;
            }
            List<Component> tooltip = modeButtonTooltip(modeButton.id());
            if (tooltip != null && !tooltip.isEmpty()) {
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            }
            return;
        }
    }

    /**
     * 模式按钮的悬停说明，返回 {@code null} 表示不显示。
     *
     * <p>只有需要额外说明「怎么操作」的模块才登记，其余一律不显示，免得把轮盘糊满。</p>
     *
     * <p><b>必须「一行一个 Component」</b>：{@code GuiGraphics#renderTooltip(Font, Component, int, int)}
     * 走的是 {@code Component#getVisualOrderText()}，它把整串文字当成<b>一行</b>做 bidi 重排，
     * 翻译文本里的 {@code \n} 不会变成换行（只会渲染成一个缺字形）。
     * 只有传多行列表（{@code renderComponentTooltip}）才会逐行渲染。</p>
     */
    private List<Component> modeButtonTooltip(String id) {
        return switch (id) {
            case BeefToolModuleRegistry.BEEF_PROTECT_MODE -> List.of(
                    Component.translatable("gui.useless_mod.mode_config.beef_protect_mode.title"),
                    Component.translatable("gui.useless_mod.mode_config.beef_protect_mode.ctrl"),
                    Component.translatable("gui.useless_mod.mode_config.beef_protect_mode.shift"),
                    Component.translatable("gui.useless_mod.mode_config.beef_protect_mode.auto"));
            case BeefToolModuleRegistry.BEEF_WIRELESS_LOGISTICS -> List.of(
                    Component.translatable("gui.useless_mod.mode_config.wireless_logistics.title"),
                    Component.translatable("gui.useless_mod.mode_config.wireless_logistics.key",
                            KeyBindings.OPEN_WIRELESS_LOGISTICS_KEY.get().getTranslatedKeyMessage()));
            default -> null;
        };
    }

    private CardLayout groupAt(double mouseX, double mouseY) {
        for (CardLayout card : cardLayouts) {
            if (!card.unassigned() && card.rect().contains(mouseX, mouseY)
                    && intersectsContent(card.rect())) return card;
        }
        return null;
    }

    private CardLayout unassignedCardAt(double mouseX, double mouseY) {
        for (CardLayout card : cardLayouts) {
            if (card.unassigned() && card.rect().contains(mouseX, mouseY)
                    && intersectsContent(card.rect())) return card;
        }
        return null;
    }

    private CardLayout cardDeleteAt(double mouseX, double mouseY) {
        for (CardLayout card : cardLayouts) {
            if (!card.unassigned() && card.rect().contains(mouseX, mouseY)
                    && mouseX >= card.right() - 20 && mouseY <= card.top() + CARD_HEADER_HEIGHT) return card;
        }
        return null;
    }

    private CardLayout groupHandleAt(double mouseX, double mouseY) {
        for (CardLayout card : cardLayouts) {
            if (!card.unassigned() && card.rect().contains(mouseX, mouseY)
                    && mouseX <= card.left() + 10 && mouseY <= card.top() + CARD_HEADER_HEIGHT) return card;
        }
        return null;
    }

    private int pageAt(double mouseX, double mouseY) {
        if (mouseY < panelTop + 40 || mouseY > panelTop + 40 + PAGE_TAB_HEIGHT) return -1;
        for (int index = 0; index < pageTabRects.size(); index++) {
            if (pageTabRects.get(index).contains(mouseX, mouseY)) {
                return pageTabOffset + index;
            }
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (confirmImport) {
                confirmImport(false);
                return true;
            }
            onClose();
            return true;
        }
        if (confirmImport) return true;
        if (keyCode == GLFW.GLFW_KEY_ENTER
                && (pageNameField != null && pageNameField.isFocused()
                || groupNameFields.stream().anyMatch(field -> field.field().isFocused()))) {
            if (commitNameFields()) sendLayout();
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return confirmImport || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (commitNameFields()) sendLayout();
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RawCard(int pageIndex, int groupIndex, int left, int top,
                           int width, int height, boolean unassigned, List<String> modules) {
    }

    private record CardLayout(int pageIndex, int groupIndex, int left, int top,
                              int width, int height, boolean unassigned, List<String> modules) {
        Rect rect() {
            return new Rect(left, top, width, height);
        }

        int right() {
            return left + width;
        }
    }

    private record ModuleLayout(int pageIndex, int groupIndex, boolean unassigned,
                                String id, int index, Rect rect) {
    }

    private record ModeButton(String id, PressableAE2Button button) {
    }

    private record GroupNameField(int pageIndex, int groupIndex, EditBox field) {
    }

    private enum DragKind {
        MODULE,
        GROUP,
        PAGE
    }

    private static final class DragState {
        private final DragKind kind;
        private final String moduleId;
        private final BeefToolLayout.Page page;
        private final BeefToolLayout.Group group;
        private final double startX;
        private final double startY;
        private boolean moved;
        private double mouseX;
        private double mouseY;

        private DragState(DragKind kind, String moduleId, BeefToolLayout.Page page,
                          BeefToolLayout.Group group, double startX, double startY) {
            this.kind = kind;
            this.moduleId = moduleId;
            this.page = page;
            this.group = group;
            this.startX = startX;
            this.startY = startY;
            this.mouseX = startX;
            this.mouseY = startY;
        }

        static DragState module(String moduleId, double x, double y) {
            return new DragState(DragKind.MODULE, moduleId, null, null, x, y);
        }

        static DragState group(BeefToolLayout.Page page, BeefToolLayout.Group group, double x, double y) {
            return new DragState(DragKind.GROUP, null, page, group, x, y);
        }

        static DragState page(BeefToolLayout.Page page, double x, double y) {
            return new DragState(DragKind.PAGE, null, page, null, x, y);
        }
    }

    private record Rect(int left, int top, int width, int height) {
        int right() {
            return left + width;
        }

        int bottom() {
            return top + height;
        }

        boolean contains(double x, double y) {
            return x >= left && x < right() && y >= top && y < bottom();
        }
    }
}
