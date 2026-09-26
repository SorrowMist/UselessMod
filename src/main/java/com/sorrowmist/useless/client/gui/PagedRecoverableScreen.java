package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * 带分页、只读恢复页与搜索框的容器界面基类。
 *
 * <p>搜索在客户端完成：屏幕遍历当前页同步过来的槽位内容，按子类给出的匹配规则筛出命中项，
 * 并在这些槽位上叠加高亮标记。槽位位置与交互始终由原版按真实坐标处理，高亮只增加视觉标注，
 * 不改变槽位排布，因此未命中项仍在原位可见，拾取与快速移动也无需额外换算。</p>
 */
public class PagedRecoverableScreen<T extends PagedRecoverableMenu> extends AbstractContainerScreen<T> {
    /** 命中槽位的高亮边框颜色，带透明度以免遮挡槽位内容。 */
    protected static final int HIGHLIGHT_COLOR = 0xCC55FF55;

    /**
     * 搜索框左缘，与下方样板槽位对齐。
     *
     * <p>槽位由菜单以 {@code storageX = 8} 为起点排布，槽位实体左缘即 {@code leftPos + 8}。
     * 搜索框与该左缘取齐，使输入区与槽位内容收于同一条左侧竖线；槽位底板另有 1 像素内缩边框，
     * 属底板自身装饰，不作为对齐基准。</p>
     */
    private static final int SEARCH_FIELD_X = 8;
    /** 搜索框占据标题下方的独立一行，避免与标题和页码指示重叠。 */
    private static final int SEARCH_FIELD_Y = 17;
    /**
     * 搜索区右缘，与下一页按钮的右缘对齐。
     *
     * <p>下一页按钮位于 {@code leftPos + 152}、宽 18，右缘为 170。搜索区整体以此为右边界，
     * 使翻页按钮与搜索范围切换按钮在视觉上收束于同一条竖线。</p>
     */
    private static final int SEARCH_AREA_RIGHT = 152 + 18;
    /** 搜索框与范围切换按钮之间的间隔。 */
    private static final int SEARCH_MODE_GAP = 2;
    /** 搜索范围切换按钮宽度，与输入框高度一致以保持等宽比例。 */
    private static final int SEARCH_MODE_WIDTH = 26;
    /**
     * 搜索框宽度。
     *
     * <p>由搜索区右缘反推：右缘减去按钮宽度与间隔，即为输入框的右边界；再减去左起点得到宽度。
     * 该推导保证按钮右缘始终与下一页按钮对齐，调整按钮宽度或右缘时无需另行手算。</p>
     */
    private static final int SEARCH_FIELD_WIDTH =
            SEARCH_AREA_RIGHT - SEARCH_MODE_WIDTH - SEARCH_MODE_GAP - SEARCH_FIELD_X;
    /** 搜索框高度，与范围切换按钮及 AE 文本框贴图的行高保持协调。 */
    private static final int SEARCH_FIELD_HEIGHT = 14;
    /** 搜索范围切换按钮紧随搜索框右侧。 */
    private static final int SEARCH_MODE_X = SEARCH_FIELD_X + SEARCH_FIELD_WIDTH + SEARCH_MODE_GAP;
    /** 槽位网格在搜索框之下的纵向起点，与菜单槽位坐标保持一致。 */
    private static final int SLOT_GRID_Y = 32;

    @Nullable
    private PressableAE2Button previousPageButton;
    @Nullable
    private PressableAE2Button nextPageButton;
    @Nullable
    private AE2StyleTextField searchField;
    @Nullable
    private PressableAE2Button searchModeButton;

    private String searchQuery = "";
    private PatternSearchMode searchMode = PatternSearchMode.ALL;
    /** 参与高亮的槽位上限：只读恢复页不进入搜索结果。 */
    private int filterableSlots = PagedRecoverableMenu.SLOTS_PER_PAGE;

    public PagedRecoverableScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 306;
        inventoryLabelY = 213;
    }

    @Override
    protected void init() {
        super.init();
        previousPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 132, topPos + 4, 18, 12,
                Component.literal("<"), button -> page(PagedRecoverableMenu.PREVIOUS_PAGE)));
        nextPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 152, topPos + 4, 18, 12,
                Component.literal(">"), button -> page(PagedRecoverableMenu.NEXT_PAGE)));

        searchField = new AE2StyleTextField(font,
                leftPos + SEARCH_FIELD_X, topPos + SEARCH_FIELD_Y,
                SEARCH_FIELD_WIDTH, SEARCH_FIELD_HEIGHT,
                Component.translatable("gui.useless_mod.paged.search"));
        searchField.setMaxLength(64);
        searchField.setHint(searchHint());
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            searchQuery = value;
            applyFilter();
        });
        addRenderableWidget(searchField);

        if (supportsSearchMode()) {
            searchModeButton = addRenderableWidget(new PressableAE2Button(
                    leftPos + SEARCH_MODE_X, topPos + SEARCH_FIELD_Y, SEARCH_MODE_WIDTH, SEARCH_FIELD_HEIGHT,
                    searchModeLabel(), button -> cycleSearchMode()));
        }

        filterableSlots = computeFilterableSlots();
        applyFilter();
    }

    /**
     * 当前界面是否需要搜索范围选择。
     *
     * <p>只有样板类界面同时具有输入与输出两侧，需要区分范围；模具等单一语义界面返回
     * {@code false}，避免出现无意义的切换按钮。</p>
     */
    protected boolean supportsSearchMode() {
        return false;
    }

    /** 当前生效的样板搜索范围。 */
    protected final PatternSearchMode searchMode() {
        return searchMode;
    }

    private void cycleSearchMode() {
        searchMode = searchMode.next();
        if (searchModeButton != null) {
            searchModeButton.setMessage(searchModeLabel());
        }
        if (searchField != null) {
            searchField.setHint(searchHint());
        }
        applyFilter();
    }

    /**
     * 搜索框的空值提示文本，随当前搜索范围变化。
     *
     * <p>提示需要反映当前范围匹配的对象，否则玩家无从判断输入的名称会落在输入侧、输出侧
     * 还是模具上，范围切换也会缺少可见反馈。</p>
     */
    private Component searchHint() {
        if (!supportsSearchMode()) {
            return Component.translatable("gui.useless_mod.paged.search.hint");
        }
        String key = switch (searchMode) {
            case IN -> "gui.useless_mod.paged.search.hint.in";
            case OUT -> "gui.useless_mod.paged.search.hint.out";
            case MOLD -> "gui.useless_mod.paged.search.hint.mold";
            case ALL -> "gui.useless_mod.paged.search.hint.all";
        };
        return Component.translatable(key);
    }

    /** 搜索范围按钮的短标签，用于在有限宽度内指示当前范围。 */
    private Component searchModeLabel() {
        String key = switch (searchMode) {
            case OUT -> "gui.useless_mod.paged.search_mode.out";
            case IN -> "gui.useless_mod.paged.search_mode.in";
            case MOLD -> "gui.useless_mod.paged.search_mode.mold";
            case ALL -> "gui.useless_mod.paged.search_mode.all";
        };
        return Component.translatable(key);
    }

    /**
     * 当前页内可搜索的槽位数量。
     *
     * <p>默认把后备库存的活跃槽位总数换算成本页可搜索数量：溢出到只读恢复页的条目只能取出、
     * 无法回填，纳入搜索结果只会让玩家看到不可用项，因此恢复页整体落在范围之外。
     * {@code filterableSlots} 以当前页内的相对槽位号计数，换算后与其坐标系一致。</p>
     */
    protected int computeFilterableSlots() {
        int remaining = menu.getActiveSlotCount() - menu.getPage() * PagedRecoverableMenu.SLOTS_PER_PAGE;
        return Math.max(0, Math.min(PagedRecoverableMenu.SLOTS_PER_PAGE, remaining));
    }

    /**
     * 判断指定菜单槽位是否命中当前查询。
     *
     * <p>默认实现命中全部非空项。子类按样板或模具语义覆写后即可获得对应搜索能力。</p>
     */
    protected boolean matchesSearch(int menuSlot) {
        return !menu.getSlot(menuSlot).getItem().isEmpty();
    }

    /**
     * 按当前查询刷新搜索高亮状态。
     *
     * <p>高亮不改变槽位排布，因此这里只需切换状态标记；命中项在渲染阶段按当前页内容即时判定，
     * 页面切换或内容变化都会自然反映。查询为空时退出高亮状态。</p>
     */
    protected final void applyFilter() {
        menu.setSearchActive(!PatternSearchMatcher.normalize(searchQuery).isEmpty());
    }

    /** 当前生效的规范化查询串。 */
    protected final String normalizedQuery() {
        return PatternSearchMatcher.normalize(searchQuery);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击落在搜索框之外时清除其焦点。原版 AbstractContainerScreen 覆写的 mouseClicked
        // 不处理组件焦点，缺少该判定时输入框会保持聚焦底纹，视觉上等同于一直处于按下状态。
        if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) {
            setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 逐个复位所有可按下按钮。若只复位部分按钮，未复位者会停留在按下态；
        // 该复位无法由原版完成，因为 pressed 是本项目按钮自行维护的显示状态。
        releaseButtonVisuals(previousPageButton, nextPageButton, searchModeButton);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 复位给定按钮的按下显示状态，忽略空引用。 */
    private static void releaseButtonVisuals(PressableAE2Button... buttons) {
        for (PressableAE2Button button : buttons) {
            if (button != null) {
                button.releaseVisualState();
            }
        }
    }

    private void page(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, SLOT_GRID_Y, 9, 10);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 225, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 283, 9, 1);
        for (var slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 88) {
            titleText = font.plainSubstrByWidth(titleText, 85) + "...";
        }
        graphics.drawString(font, titleText, 8, 6, MachineScreenStyle.TEXT_COLOR, false);
        String page = (menu.getPage() + 1) + "/" + menu.getPageCount();
        graphics.drawString(font, page, 128 - font.width(page), 6,
                menu.isRecoveryPage()
                        ? MachineScreenStyle.ERROR_TEXT_COLOR
                        : MachineScreenStyle.MUTED_TEXT_COLOR,
                false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 物品与提示框均由原版在 super.render 中绘制，先提交其批次再绘制高亮，
        // 否则同批次内的绘制顺序无法保证高亮位于物品之上。
        graphics.flush();
        renderSearchHighlights(graphics, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 高亮命中当前查询的槽位。
     *
     * <p>槽位位置与交互均由原版按真实坐标处理，搜索结果只在其上叠加高亮边框，
     * 因此未命中项仍在原位可见，玩家背包等非搜索区域不受影响，悬停与拾取也无需额外换算。</p>
     *
     * <p>搜索范围限于当前页内的可回填槽位：只读恢复页的条目无法回填，不参与高亮。</p>
     */
    private void renderSearchHighlights(GuiGraphics graphics, float partialTick) {
        if (!menu.isFiltered() || menu.isRecoveryPage()) return;
        int limit = Math.min(filterableSlots, menu.slots.size());
        int order = 0;
        for (int menuSlot = 0; menuSlot < limit; menuSlot++) {
            if (!matchesSearch(menuSlot)) continue;
            Slot slot = menu.getSlot(menuSlot);
            if (slot.getItem().isEmpty()) continue;
            highlightSlot(graphics, slot, order++, partialTick);
        }
    }

    /**
     * 绘制单个命中槽位的高亮标记。
     *
     * <p>默认绘制覆盖槽位区域的半透明边框，颜色沿命中项顺序与时间双重推进，形成彩虹流转效果；
     * 子类可覆写以改变标记样式。</p>
     *
     * @param order 命中项在当前页内的顺序，用于制造相邻槽位之间的色相梯度
     */
    protected void highlightSlot(GuiGraphics graphics, Slot slot, int order, float partialTick) {
        int color = rainbowColor(order, partialTick);
        int x1 = leftPos + slot.x;
        int y1 = topPos + slot.y;
        int x2 = x1 + 16;
        int y2 = y1 + 16;
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);
    }

    /**
     * 计算彩虹流转颜色。
     *
     * <p>色相由命中项顺序与游戏运行时间共同决定：顺序项产生固定的相邻梯度，时间项使整体色相持续
     * 推移，两者叠加即得到沿搜索命中序列流动的彩虹。返回值按 ARGB 组装，透明度沿用
     * {@link #HIGHLIGHT_COLOR} 的高位，避免高亮遮挡槽位内容。</p>
     */
    private static int rainbowColor(int order, float partialTick) {
        long millis = Util.getMillis();
        // 每 16 毫秒一帧的基准下，相位每约 3 秒循环一周。
        float phase = (millis % 3000L) / 3000.0F;
        float hue = (phase + order * 0.07F) % 1.0F;
        int rgb = Mth.hsvToRgb(hue, 0.85F, 1.0F);
        return (HIGHLIGHT_COLOR & 0xFF000000) | (rgb & 0x00FFFFFF);
    }
}
