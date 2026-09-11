package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.core.component.UComponents;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModeWheelScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 480;
    private static final int PANEL_MARGIN = 6;
    private static final int HEADER_HEIGHT = 42;
    private static final int NORMAL_FOOTER_HEIGHT = 20;
    private static final int EDITOR_FOOTER_HEIGHT = 38;
    private static final int CONTENT_PADDING = 4;
    private static final int CARD_GAP = 5;
    private static final int CARD_HEADER_HEIGHT = 18;
    private static final int CARD_BOTTOM_PADDING = 6;
    private static final int MODULE_HEIGHT = 18;
    private static final int MODULE_GAP = 2;
    private static final int PAGE_TAB_HEIGHT = 15;
    private static final int PAGE_TAB_WIDTH = 82;
    private static final int DRAG_THRESHOLD = 4;
    private static final int DESIGN_HEIGHT = 320;

    private final List<PressableAE2Button> toolbarButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<GroupNameField> groupNameFields = new ArrayList<>();
    private final List<CardLayout> cardLayouts = new ArrayList<>();
    private final List<ModuleLayout> moduleLayouts = new ArrayList<>();
    private final List<Rect> pageTabRects = new ArrayList<>();

    private ItemStack targetItem;
    private BeefToolLayout layout = BeefToolModuleRegistry.defaultLayout();
    private EditBox pageNameField;
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
        pageNameField = null;

        calculatePanelBounds();
        calculateGeometry();
        createToolbarWidgets();
        if (!awaitingLayout && editing) {
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
        int desiredWidth = desiredContentWidth() + PANEL_MARGIN * 2;
        int desiredHeight = HEADER_HEIGHT + desiredContentHeight()
                + (editing ? EDITOR_FOOTER_HEIGHT : NORMAL_FOOTER_HEIGHT);
        panelWidth = Math.max(1, Math.min(PANEL_MAX_WIDTH, Math.min(availableWidth, desiredWidth)));
        panelHeight = Math.max(1, Math.min(DESIGN_HEIGHT, Math.min(availableHeight, desiredHeight)));
        panelLeft = (uiWidth - panelWidth) / 2;
        panelTop = Math.max(PANEL_MARGIN, (uiHeight - panelHeight) / 2);

        contentLeft = panelLeft + PANEL_MARGIN;
        contentRight = panelLeft + panelWidth - PANEL_MARGIN;
        contentTop = panelTop + HEADER_HEIGHT;
        int footerHeight = editing ? EDITOR_FOOTER_HEIGHT : NORMAL_FOOTER_HEIGHT;
        contentBottom = Math.max(contentTop + 1, panelTop + panelHeight - footerHeight);
    }

    private void calculateUiScale() {
        float widthScale = Math.max(0.1F, (width - PANEL_MARGIN * 2.0F) / PANEL_MAX_WIDTH);
        float heightScale = Math.max(0.1F, (height - PANEL_MARGIN * 2.0F) / DESIGN_HEIGHT);
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
        return 54 + 4 + 54 + 4 + 58 + 4 + 58 + 4 + 58;
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
            List<String> modules = visibleModules(group.modules());
            int column = bottoms[0] <= bottoms[1] ? 0 : 1;
            bottoms[column] += cardHeight(1, modules.size(), moduleColumns(modules.size())) + CARD_GAP;
        }
        return Math.max(rawY, Math.max(bottoms[0], bottoms[1])) + CONTENT_PADDING;
    }

    private int[] sumGroupColumnWidths(BeefToolLayout.Page page) {
        int left = 0;
        int right = 0;
        for (int index = 0; index < page.groups().size(); index++) {
            int width = naturalGroupWidth(page.groups().get(index));
            if ((index & 1) == 0) left = Math.max(left, width);
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

    private int commonModuleWidth(BeefToolLayout.Page page) {
        int width = 50;
        for (BeefToolLayout.Group group : page.groups()) {
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
        int y = panelTop + 4;
        int actionWidth = editing ? 50 : 70;
        int actionX = right - actionWidth;
        int nextX = actionX - 22;
        int previousX = nextX - 22;

        PressableAE2Button previous = addToolbarButton(new PressableAE2Button(
                previousX, y, 18, 14, Component.literal("<"), ignored -> changePage(-1)));
        PressableAE2Button next = addToolbarButton(new PressableAE2Button(
                nextX, y, 18, 14, Component.literal(">"), ignored -> changePage(1)));
        PressableAE2Button action = addToolbarButton(new PressableAE2Button(
                actionX, y, actionWidth, 14,
                Component.translatable(editing
                        ? "gui.useless_mod.mode_config.done"
                        : "gui.useless_mod.mode_config.edit"),
                ignored -> {
                    if (editing) finishEditing();
                    else startEditing();
                }));

        boolean ready = !awaitingLayout;
        previous.visible = ready && layout.pages().size() > 1;
        next.visible = ready && layout.pages().size() > 1;
        previous.active = ready && currentPage() > 0;
        next.active = ready && currentPage() + 1 < layout.pages().size();
        action.active = ready;

        if (!editing || !ready) return;

        int footerY = panelTop + panelHeight - 18;
        int footerX = panelLeft + PANEL_MARGIN;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 54, 14,
                Component.translatable("gui.useless_mod.mode_config.add_page"), ignored -> addPage()));
        footerX += 58;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 54, 14,
                Component.translatable("gui.useless_mod.mode_config.delete_page"), ignored -> deletePage()));
        footerX += 58;
        addToolbarButton(new PressableAE2Button(
                footerX, footerY, 58, 14,
                Component.translatable("gui.useless_mod.mode_config.add_group"), ignored -> addGroup()));
        footerX += 62;
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
            modeButtons.add(new ModeButton(module.id(), button));
        }
        positionModeButtons();
    }

    private void createEditorNameFields() {
        BeefToolLayout.Page page = currentPageObject();
        int pageFieldX = panelLeft + PANEL_MARGIN + font.width(title) + 10;
        int pageFieldWidth = Math.min(150, Math.max(64, pageNavigationLeft() - pageFieldX - 8));
        pageNameField = new EditBox(font, pageFieldX, panelTop + 4, pageFieldWidth, 14,
                Component.translatable("gui.useless_mod.mode_config.page_name"));
        configureNameField(pageNameField);
        pageNameField.setValue(page.name());
        addRenderableWidget(pageNameField);

        for (CardLayout card : cardLayouts) {
            if (card.unassigned() || card.groupIndex() < 0) continue;
            BeefToolLayout.Group group = groupAt(card.pageIndex(), card.groupIndex());
            if (group == null) continue;
            EditBox field = new EditBox(font, card.left() + 10, card.top() + 2,
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
        int rawY = contentTop + CONTENT_PADDING;
        if (editing) {
            List<String> unassigned = visibleModules(layout.unassignedModules());
            int height = cardHeight(contentRight - contentLeft, unassigned.size(), moduleColumns(unassigned.size()));
            rawCards.add(new RawCard(-1, -1, contentLeft, rawY,
                    contentRight - contentLeft, height, true, unassigned));
            rawY += height + CARD_GAP;
        }

        BeefToolLayout.Page page = currentPageObject();
        int[] columnWidths = fitColumnWidths(sumGroupColumnWidths(page), contentRight - contentLeft);
        int[] columnBottoms = {rawY, rawY};
        for (int groupIndex = 0; groupIndex < page.groups().size(); groupIndex++) {
            BeefToolLayout.Group group = page.groups().get(groupIndex);
            List<String> visibleModules = visibleModules(group.modules());
            int column = columnBottoms[0] <= columnBottoms[1] ? 0 : 1;
            int left = contentLeft + (column == 0 ? 0 : columnWidths[0] + CARD_GAP);
            int cardWidth = Math.min(columnWidths[column], naturalGroupWidth(group));
            int height = cardHeight(cardWidth, visibleModules.size(), moduleColumns(visibleModules.size()));
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

    private Rect moduleRect(CardLayout card, int index) {
        int columns = moduleColumns(card.modules().size());
        List<String> sourceModules = card.unassigned()
                ? layout.unassignedModules()
                : groupAt(card.pageIndex(), card.groupIndex()).modules();
        int buttonWidth = card.modules().size() == 1
                ? moduleButtonWidth(sourceModules)
                : commonModuleWidth(currentPageObject());
        int column = index % columns;
        int row = index / columns;
        return new Rect(card.left() + 5 + column * (buttonWidth + MODULE_GAP),
                card.top() + CARD_HEADER_HEIGHT + row * (MODULE_HEIGHT + MODULE_GAP),
                buttonWidth, MODULE_HEIGHT);
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
            pageNameField.visible = editing && !awaitingLayout;
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
        return Component.translatable("gui.useless_mod.mode_config.state", name,
                Component.translatable(isActive(id)
                        ? "tooltip.useless_mod.enable"
                        : "tooltip.useless_mod.disable"));
    }

    private boolean isActive(String id) {
        if (targetItem == null || targetItem.isEmpty()) return false;
        return switch (id) {
            case BeefToolModuleRegistry.ENCHANT_SILK_TOUCH ->
                    targetItem.get(UComponents.EnchantModeComponent) == EnchantMode.SILK_TOUCH;
            case BeefToolModuleRegistry.ENCHANT_FORTUNE ->
                    targetItem.get(UComponents.EnchantModeComponent) == EnchantMode.FORTUNE;
            case BeefToolModuleRegistry.TOOL_NONE -> currentTool() == ToolTypeMode.NONE_MODE;
            case BeefToolModuleRegistry.TOOL_WRENCH -> currentTool() == ToolTypeMode.WRENCH_MODE;
            case BeefToolModuleRegistry.TOOL_SCREWDRIVER -> currentTool() == ToolTypeMode.SCREWDRIVER_MODE;
            case BeefToolModuleRegistry.TOOL_MALLET -> currentTool() == ToolTypeMode.MALLET_MODE;
            case BeefToolModuleRegistry.TOOL_CROWBAR -> currentTool() == ToolTypeMode.CROWBAR_MODE;
            case BeefToolModuleRegistry.TOOL_HAMMER -> currentTool() == ToolTypeMode.HAMMER_MODE;
            case BeefToolModuleRegistry.TOOL_OMNITOOL -> currentTool() == ToolTypeMode.OMNITOOL_MODE;
            case BeefToolModuleRegistry.CONSTRUCTION_WAND -> bool(UComponents.ConstructionWandEnabledComponent, false);
            case BeefToolModuleRegistry.CONSTRUCTION_WAND_ANGEL ->
                    targetItem.getOrDefault(UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT)
                            == ConstructionWandCoreMode.ANGEL;
            case BeefToolModuleRegistry.CONSTRUCTION_WAND_DESTRUCTION ->
                    targetItem.getOrDefault(UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT)
                            == ConstructionWandCoreMode.DESTRUCTION;
            case BeefToolModuleRegistry.ENHANCED_CHAIN_MINING -> bool(UComponents.EnhancedChainMiningComponent, false);
            case BeefToolModuleRegistry.FORCE_MINING -> bool(UComponents.ForceMiningComponent, false);
            case BeefToolModuleRegistry.AE_STORAGE_PRIORITY -> bool(UComponents.AEStoragePriorityComponent, false);
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
                    targetItem.getOrDefault(UComponents.BeefInvulnerabilityEnabledComponent,
                            targetItem.getItem() instanceof com.sorrowmist.useless.content.items.EndlessBeafItem);
            case BeefToolModuleRegistry.BEEF_ADVANCED_STEALTH ->
                    bool(UComponents.BeefAdvancedStealthEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_CAPTURE -> bool(UComponents.BeefCaptureEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_TELEPORT -> bool(UComponents.BeefTeleportEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_AOE_DAMAGE -> bool(UComponents.BeefAoeDamageEnabledComponent, false);
            case BeefToolModuleRegistry.BEEF_MAGNET -> bool(UComponents.BeefMagnetEnabledComponent, false);
            default -> false;
        };
    }

    private ToolTypeMode currentTool() {
        return targetItem.getOrDefault(UComponents.CurrentToolTypeComponent, ToolTypeMode.NONE_MODE);
    }

    private boolean bool(net.minecraft.core.component.DataComponentType<Boolean> type, boolean fallback) {
        return targetItem.getOrDefault(type, fallback);
    }

    private boolean bool(
            net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
                    net.minecraft.core.component.DataComponentType<Boolean>> holder,
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
        boolean changed = editing && commitNameFields();
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
        if (editing && drag == null) commitNameFields();
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
            case BeefToolModuleRegistry.AE_STORAGE_PRIORITY -> toggle(ModeTogglePacket.ModeType.AE_STORAGE_PRIORITY,
                    UComponents.AEStoragePriorityComponent, false);
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
                    UComponents.BeefMagnetEnabledComponent, false);
            default -> {
            }
        }
    }

    private void sendToolType(ToolTypeMode mode) {
        PacketDistributor.sendToServer(new ToolTypeModeSwitchPacket(mode));
    }

    private void toggle(ModeTogglePacket.ModeType type,
                        net.minecraft.core.component.DataComponentType<Boolean> component,
                        boolean fallback) {
        PacketDistributor.sendToServer(new ModeTogglePacket(type, !bool(component, fallback)));
    }

    private void toggle(
            ModeTogglePacket.ModeType type,
            net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
                    net.minecraft.core.component.DataComponentType<Boolean>> component,
            boolean fallback) {
        toggle(type, component.get(), fallback);
    }

    private void toggleCore(ConstructionWandCoreMode requested) {
        ConstructionWandCoreMode current = targetItem.getOrDefault(
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
        MachineScreenStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.drawString(font, title, panelLeft + PANEL_MARGIN, panelTop + 7,
                MachineScreenStyle.TEXT_COLOR, false);
        drawPageTabs(graphics);

        if (awaitingLayout) {
            graphics.drawString(font, Component.translatable("gui.useless_mod.mode_config.loading"),
                    panelLeft + PANEL_MARGIN, contentTop + 8, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        } else {
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
                        if (modeButton.button().visible) modeButton.button().render(graphics, mouseX, mouseY, partialTick);
                    }
                }
            } finally {
                graphics.flush();
                RenderSystem.disableScissor();
            }
        }

        if (pageNameField != null && pageNameField.visible) {
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
            MachineScreenStyle.drawInset(graphics, card.left(), card.top(),
                    card.left() + card.width(), card.top() + card.height());
            if (card.unassigned()) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.mode_config.unassigned"),
                        card.left() + 6, card.top() + 4, MachineScreenStyle.TEXT_COLOR, false);
            } else if (!editing) {
                graphics.drawString(font, groupAt(card.pageIndex(), card.groupIndex()).name(),
                        card.left() + 6, card.top() + 4, MachineScreenStyle.TEXT_COLOR, false);
            } else {
                graphics.drawString(font, Component.literal("::"),
                        card.left() + 2, card.top() + 4, MachineScreenStyle.MUTED_TEXT_COLOR, false);
                graphics.drawString(font, Component.literal("X"),
                        card.left() + card.width() - 14, card.top() + 4,
                        MachineScreenStyle.ERROR_TEXT_COLOR, false);
            }
            if (card.modules().isEmpty()) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.mode_config.empty"),
                        card.left() + 6, card.top() + CARD_HEADER_HEIGHT + 4,
                        MachineScreenStyle.MUTED_TEXT_COLOR, false);
            }
        }
    }

    private void drawEditorModules(GuiGraphics graphics, int mouseX, int mouseY) {
        for (ModuleLayout module : moduleLayouts) {
            if (!intersectsContent(module.rect())) continue;
            boolean hovered = module.rect().contains(mouseX, mouseY);
            int color = hovered ? MachineScreenStyle.HIGHLIGHT_COLOR : MachineScreenStyle.PANEL_COLOR;
            graphics.fill(module.rect().left(), module.rect().top(),
                    module.rect().right(), module.rect().bottom(), color);
            graphics.fill(module.rect().left(), module.rect().top(),
                    module.rect().right(), module.rect().top() + 1, MachineScreenStyle.SLOT_SHADOW_COLOR);
            Component message = buttonMessage(module.id());
            String text = font.plainSubstrByWidth(message.getString(), Math.max(1, module.rect().width() - 6));
            int textColor = hovered ? MachineScreenStyle.TEXT_COLOR : MachineScreenStyle.SUBTLE_TEXT_COLOR;
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
            graphics.fill(left, top, left + ghostWidth, top + 18, 0xEE20242C);
            graphics.drawString(font, font.plainSubstrByWidth(text, ghostWidth - 6), left + 3, top + 5,
                    0xFFFFFFFF, false);
        }
    }

    private void drawPageTabs(GuiGraphics graphics) {
        pageTabRects.clear();
        if (layout.pages().isEmpty()) return;
        ensurePageTabVisible();
        int left = panelLeft + PANEL_MARGIN;
        int right = panelLeft + panelWidth - PANEL_MARGIN;
        int y = panelTop + 24;
        int visibleCount = Math.max(1, (right - left) / PAGE_TAB_WIDTH);
        for (int index = pageTabOffset;
             index < Math.min(layout.pages().size(), pageTabOffset + visibleCount); index++) {
            int x = left + (index - pageTabOffset) * PAGE_TAB_WIDTH;
            Rect rect = new Rect(x, y, PAGE_TAB_WIDTH - 2, PAGE_TAB_HEIGHT);
            pageTabRects.add(rect);
            boolean selected = index == currentPage();
            MachineScreenStyle.drawInset(graphics, rect.left(), rect.top(), rect.right(), rect.bottom());
            if (selected) {
                graphics.fill(rect.left() + 2, rect.top() + 2, rect.right() - 2, rect.bottom() - 2,
                        MachineScreenStyle.HIGHLIGHT_COLOR);
            }
            String name = font.plainSubstrByWidth(layout.pages().get(index).name(), rect.width() - 6);
            graphics.drawString(font, name, rect.left() + 3, rect.top() + 4,
                    selected ? MachineScreenStyle.TEXT_COLOR : MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
    }

    private void ensurePageTabVisible() {
        int visibleCount = Math.max(1, (panelWidth - PANEL_MARGIN * 2) / PAGE_TAB_WIDTH);
        int selected = currentPage();
        if (selected < pageTabOffset) pageTabOffset = selected;
        if (selected >= pageTabOffset + visibleCount) pageTabOffset = selected - visibleCount + 1;
        pageTabOffset = Mth.clamp(pageTabOffset, 0, Math.max(0, layout.pages().size() - visibleCount));
    }

    private void drawStatus(GuiGraphics graphics) {
        if (statusKey == null) return;
        int y = editing ? panelTop + panelHeight - 34 : panelTop + panelHeight - 15;
        Component status = Component.translatable(statusKey);
        String text = font.plainSubstrByWidth(status.getString(), Math.max(1, panelWidth - 12));
        graphics.drawString(font, text, panelLeft + PANEL_MARGIN, y,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private void drawImportConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalWidth = Math.min(320, uiWidth - 12);
        int modalHeight = 82;
        int left = (uiWidth - modalWidth) / 2;
        int top = (uiHeight - modalHeight) / 2;
        graphics.fill(0, 0, uiWidth, uiHeight, 0x99000000);
        MachineScreenStyle.drawPanel(graphics, left, top, modalWidth, modalHeight);
        graphics.drawString(font, Component.translatable("gui.useless_mod.mode_config.import_confirm"),
                left + 8, top + 10, MachineScreenStyle.TEXT_COLOR, false);
        drawManualButton(graphics, left + 10, top + 48, 110, 18,
                Component.translatable("gui.useless_mod.mode_config.confirm"),
                new Rect(left + 10, top + 48, 110, 18), mouseX, mouseY);
        drawManualButton(graphics, left + modalWidth - 120, top + 48, 110, 18,
                Component.translatable("gui.useless_mod.mode_config.cancel"),
                new Rect(left + modalWidth - 120, top + 48, 110, 18), mouseX, mouseY);
    }

    private void drawManualButton(GuiGraphics graphics, int x, int y, int width, int height,
                                  Component message, Rect rect, int mouseX, int mouseY) {
        int color = rect.contains(mouseX, mouseY)
                ? MachineScreenStyle.HIGHLIGHT_COLOR : MachineScreenStyle.PANEL_COLOR;
        graphics.fill(x, y, x + width, y + height, color);
        graphics.drawString(font, font.plainSubstrByWidth(message.getString(), width - 6),
                x + 3, y + 5, MachineScreenStyle.TEXT_COLOR, false);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
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
        if (mouseY >= panelTop + 22 && mouseY <= panelTop + 22 + PAGE_TAB_HEIGHT) {
            pageTabOffset = Mth.clamp(pageTabOffset - (int) Math.signum(deltaY),
                    0, Math.max(0, layout.pages().size() - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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
        if (mouseY < panelTop + 22 || mouseY > panelTop + 22 + PAGE_TAB_HEIGHT) return -1;
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
        if (keyCode == GLFW.GLFW_KEY_ENTER && editing
                && (pageNameField != null && pageNameField.isFocused()
                || groupNameFields.stream().anyMatch(field -> field.field().isFocused()))) {
            if (commitNameFields()) sendLayout();
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (editing && commitNameFields()) sendLayout();
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
