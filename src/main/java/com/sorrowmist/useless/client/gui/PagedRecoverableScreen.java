package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import com.sorrowmist.useless.client.gui.ldl.api.SlotsNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/**
 * 带分页、只读恢复页与搜索框的容器界面基类。
 *
 * <p>搜索在客户端完成：屏幕遍历当前页同步过来的槽位内容，按子类给出的匹配规则筛出命中项，
 * 并在这些槽位上叠加高亮标记。槽位位置与交互始终由原版按真实坐标处理，高亮只增加视觉标注，
 * 不改变槽位排布，因此未命中项仍在原位可见，拾取与快速移动也无需额外换算。</p>
 *
 * <p>半尺寸布局（456×304）：标题栏 28 + 内边距 12，左右两栏各 212、栏距 8。
 * 左栏是 9×10 存储网格卡（节距 22 = 20px 底格 + 2px 间隙，物品 16px 居中于底格 +2，卡内边距 8），
 * 右栏是搜索卡与 9×3 + 快捷栏物品栏卡；两栏底部对齐于 y=292。
 * 卡片几何直接对应 {@link PagedRecoverableMenu} 里的槽位原点，界面只按同一套坐标画底格，
 * 槽位顺序 / 序号 / 网络逻辑完全不变。</p>
 */
public class PagedRecoverableScreen<T extends PagedRecoverableMenu> extends AppleContainerScreen<T> {
    /** 命中槽位的高亮边框颜色，带透明度以免遮挡槽位内容。 */
    protected static final int HIGHLIGHT_COLOR = 0xCC55FF55;

    // ---- 半尺寸布局：所有坐标都相对面板左上角，与菜单槽位坐标使用同一坐标系 ----
    private static final int PANEL_W = 456;
    private static final int PANEL_H = 309;
    private static final int PAD = 12;
    /** 标题栏 28 + 内边距 12。 */
    private static final int CONTENT_Y = 40;
    private static final int COL_W = 212;
    private static final int LEFT_X = PAD;
    private static final int RIGHT_X = PAD + COL_W + 8;
    /** 存储网格卡：8 + 说明行 14 + 4 + 网格 218 + 8。 */
    private static final int CARD_H = 256;
    private static final int PAGER_W = 74, PAGER_H = 17;
    private static final int PAGER_X = LEFT_X + COL_W - 8 - PAGER_W;
    private static final int PAGER_Y = CONTENT_Y + 8;
    private int searchCardHeight() { return supportsSearchMode() ? 69 : 47; }
    private int inventoryY() { return CONTENT_Y + searchCardHeight() + 8; }
    private static final int INV_H = 117;
    private static final int FIELD_X = RIGHT_X + 8;
    private static final int FIELD_Y = CONTENT_Y + 8;
    private static final int FIELD_W = COL_W - 16;
    private static final int FIELD_H = 16;
    private static final int SEG_Y = FIELD_Y + FIELD_H + 6;
    private static final int SEG_H = 16;

    private static final PatternSearchMode[] SEG_MODES = {
            PatternSearchMode.ALL, PatternSearchMode.IN, PatternSearchMode.OUT, PatternSearchMode.MOLD};

    @Nullable
    private EditBox searchField;

    private String searchQuery = "";
    private PatternSearchMode searchMode = PatternSearchMode.ALL;
    /** 参与高亮的槽位上限：只读恢复页不进入搜索结果。 */
    private int filterableSlots = PagedRecoverableMenu.SLOTS_PER_PAGE;

    public PagedRecoverableScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_W;
        imageHeight = PANEL_H;
    }

    private boolean layoutRecovery;

    @Override
    protected void preInit() { imageHeight = PANEL_H + (menu.isRecoveryPage() ? 24 : 0); }

    private void moveReferenceSlots() {
        int extra = menu.isRecoveryPage() ? 24 : 0;
        for (int i = 0; i < Math.min(90, menu.slots.size()); i++)
            SlotsNode.moveSlot(menu.slots.get(i), 23 + i % 9 * 22, 72 + extra + i / 9 * 22);
        for (int i = 0; i < 36 && 90 + i < menu.slots.size(); i++)
            SlotsNode.moveSlot(menu.slots.get(90 + i), 243 + i % 9 * 22,
                    inventoryY() + (i < 27 ? 23 + i / 9 * 22 : 91));
    }

    @Override
    protected void initControls() {
        // Display coordinates only; keep Menu slot ownership/indexes and quick-move intact.
        moveReferenceSlots();
        searchField = new AppleEditBox(font,
                leftPos + FIELD_X + 14, topPos + FIELD_Y + 3,
                FIELD_W - 28, FIELD_H - 4,
                Component.translatable("gui.useless_mod.paged.search"));
        searchField.setMaxLength(64);
        searchField.setHint(searchHint());
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            searchQuery = value;
            applyFilter();
        });
        addRenderableWidget(searchField);

        filterableSlots = computeFilterableSlots();
        applyFilter();
    }

    /**
     * 布局（456×304）：左栏 存储网格卡（说明行 + 翻页胶囊 + 9×10 网格）；
     * 右栏 搜索卡（搜索框 / 范围分段 / 提示 / 命中徽标 / 统计行）+ 物品栏卡。
     * 所有卡片与槽位底格共用菜单的真实槽位坐标。
     */
    @Override
    protected void buildApple(AppleHost h) {
        AppleWindow window = new AppleWindow(imageWidth, imageHeight, () -> title);
        window.icon(windowIcon(), windowAccent());
        h.add(window);

        AppleBadge headerBadge = new AppleBadge(imageWidth - PAD - 96, 8, 96, this::headerBadge, this::headerBadgeColor);
        headerBadge.right();
        headerBadge.id("count");
        h.add(headerBadge);

        // 左栏：存储网格卡
        layoutRecovery = menu.isRecoveryPage();
        h.add(new AppleCard(LEFT_X, CONTENT_Y, COL_W, CARD_H + (layoutRecovery ? 24 : 0)));
        if (layoutRecovery) h.add(new AppleCanvas(LEFT_X + 8, CONTENT_Y + 28, COL_W - 16, 16,
                (g, mx, my, x, y, w, hh) -> {
                    AppleUi.fillRound(g, x, y, w, hh, 4, AppleStyle.ORANGE_TINT);
                    AppleUi.icon(g, AppleUi.Icon.WARNING, x + 4, y + 4, 8, AppleStyle.ORANGE_TEXT);
                    AppleTextLayout.draw(g, font, Component.translatable("gui.useless_mod.apple.recovery_page"),
                            x + 17, y + 4, w - 21, .8f, AppleStyle.ORANGE_TEXT, false);
                }));
        h.add(new AppleLabel(LEFT_X + 8, CONTENT_Y + 8, 130, AppleLabel.Kind.CAPTION, this::storageCaption));
        h.add(new ApplePageTabs(PAGER_X, PAGER_Y, PAGER_W, PAGER_H,
                menu::getPage, menu::getPageCount, menu::getActivePageCount, this::selectPage).id("pager"));
        AppleSlotGrid grid = slotGrid();
        grid.cellSize(20);
        grid.disabledWhen(sl -> menu.isRecoverySlot(menu.slots.indexOf(sl)));
        h.add(grid);

        // 右栏：搜索卡
        h.add(new AppleCard(RIGHT_X, CONTENT_Y, COL_W, searchCardHeight()));
        h.add(new AppleTextField(FIELD_X, FIELD_Y, FIELD_W, FIELD_H, searchField, 14)
                .icon(AppleUi.Icon.SEARCH).id("search"));
        if (supportsSearchMode()) {
            h.add(new AppleSegmented(FIELD_X, SEG_Y, FIELD_W, SEG_H,
                    () -> Arrays.stream(SEG_MODES).map(PagedRecoverableScreen::searchModeLabel).toList(),
                    () -> Arrays.asList(SEG_MODES).indexOf(searchMode),
                    i -> setSearchMode(SEG_MODES[i])).id("seg"));
        }
        h.add(new AppleLabel(FIELD_X, hintY(), FIELD_W, AppleLabel.Kind.FOOTNOTE,
                () -> menu.isFiltered() ? matchBadge() : headerBadge()));

        // The reference inventory follows the compact search card, not the storage bottom.
        h.add(new AppleCard(RIGHT_X, inventoryY(), COL_W, INV_H));
        h.add(new AppleLabel(RIGHT_X + 10, inventoryY() + 8, COL_W - 18, AppleLabel.Kind.CAPTION, () -> playerInventoryTitle));
    }

    /** 标题栏窗口图标：样板类界面默认用样板图标，子类可换成更贴切的语义图标。 */
    protected AppleUi.Icon windowIcon() {
        return AppleUi.Icon.PATTERN;
    }

    protected int windowAccent() { return AppleStyle.BLUE; }

    /** 存储卡左上角的说明文字（存储 / 恢复页）。 */
    protected Component storageCaption() {
        return Component.translatable(menu.isRecoveryPage()
                ? "gui.useless_mod.apple.recovery" : "gui.useless_mod.apple.storage");
    }

    private Component headerBadge() {
        return menu.isRecoveryPage()
                ? Component.translatable("gui.useless_mod.apple.recovery")
                : Component.translatable("gui.useless_mod.apple.stored_count", menu.getActiveSlotCount());
    }

    private int headerBadgeColor() {
        return menu.isRecoveryPage() ? AppleStyle.RED : AppleStyle.SECONDARY;
    }

    /** 提示行位置：带范围分段时下移一行，让分段控件与提示互不重叠。 */
    private int hintY() {
        return supportsSearchMode() ? SEG_Y + SEG_H + 6 : SEG_Y;
    }

    private Component matchBadge() {
        return Component.translatable("gui.useless_mod.apple.matches", Math.max(0, countMatches()));
    }

    private int matchBadgeColor() {
        return countMatches() > 0 ? AppleStyle.BLUE : AppleStyle.SECONDARY;
    }

    @Override
    protected void tickControls() {
        int f = computeFilterableSlots();
        if (f != filterableSlots) filterableSlots = f;
        if (layoutRecovery != menu.isRecoveryPage()) {
            preInit();
            topPos = (height - imageHeight) / 2;
            moveReferenceSlots();
            host = new AppleHost(imageWidth, imageHeight);
            buildApple(host);
            host.layout(width, height, leftPos, topPos);
        }
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

    private void setSearchMode(PatternSearchMode mode) {
        searchMode = mode;
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
    private static Component searchModeLabel(PatternSearchMode mode) {
        String key = switch (mode) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 搜索框聚焦期间不把按键转交父类：AbstractContainerScreen 在子控件未消费按键时
        // 会比对容器快捷键（默认 E 打开物品栏），命中即调用 onClose 关闭本界面，
        // 导致该字母无法输入。Esc 仍然放行，保持「Esc 关闭界面」的既有行为。
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && searchField != null
                && (searchField.keyPressed(keyCode, scanCode, modifiers) || searchField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击落在搜索框之外时清除其焦点，避免输入框一直保持聚焦底纹。
        if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) {
            setFocused(null);
            searchField.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void page(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void selectPage(int target) {
        int delta = target - menu.getPage();
        for (int i = 0; i < Math.abs(delta); i++)
            page(delta > 0 ? PagedRecoverableMenu.NEXT_PAGE : PagedRecoverableMenu.PREVIOUS_PAGE);
    }

    private int countMatches() {
        if (!menu.isFiltered() || menu.isRecoveryPage()) return -1;
        int limit = Math.min(filterableSlots, menu.slots.size()), n = 0;
        for (int i = 0; i < limit; i++)
            if (!menu.getSlot(i).getItem().isEmpty() && matchesSearch(i)) n++;
        return n;
    }

    /**
     * 绘制鼠标悬停高亮。
     *
     * <p>只读恢复槽位不绘制悬停高亮：该区域只允许取出，高亮会给出「可以放入」的错误暗示，
     * 与未启用样式的底板相互矛盾。悬停本身仍被原版记录，因此恢复区中已有条目的物品提示与
     * 取出操作不受影响。</p>
     *
     * <p>槽位序号经 {@code menu.slots} 反查而非取自 {@code Slot.index}，后者是页视图内的相对
     * 序号，对玩家背包槽位同样落在存储区区间内。反查失败时返回 {@code -1}，判定结果一律为
     * {@code false}，不会误伤非存储区槽位。</p>
     */
    @Override
    public int getSlotColor(int index) {
        return menu.isRecoverySlot(index) ? 0 : super.getSlotColor(index);
    }

    @Override
    protected void renderAboveSlots(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 物品由原版在 super.render 中绘制，先 flush 再画高亮，并用 SEARCH_HIGHLIGHT_Z 抬 z。
        graphics.flush();
        renderSearchHighlights(graphics, partialTick);
    }

    /**
     * 命中槽位高亮的绘制 z。
     *
     * <p>GUI 的深度测试是开着的（{@code RenderType.GUI} 走 {@code LEQUAL_DEPTH_TEST}），
     * 而槽位物品画在 z=150 附近（{@code GuiGraphics#renderItem} 的 +150，再叠模型自身
     * 经 {@code display.gui} 变换后的几个像素）。高亮边框留在 z=0 会比物品<b>远</b>，
     * 压在物品贴图上的那几条边会被深度测试直接丢掉 —— 贴图铺满整格的方块类物品上
     * 高亮基本看不见，且光 {@code flush()} 修不好（它只管先后，不管远近）。</p>
     *
     * <p>取 170：压过物品并留出余量，又刻意<b>低于</b>堆叠数字的 z=200 ——
     * 数量是搜索时最要紧的信息，不该被半透明高亮染色；同时也低于原版 tooltip 的 400。</p>
     */
    private static final float SEARCH_HIGHLIGHT_Z = 170.0F;

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
        // 抬 z 放在这里而不是 highlightSlot 内部，是为了让子类覆写的标记样式一并生效。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, SEARCH_HIGHLIGHT_Z);
        int order = 0;
        for (int menuSlot = 0; menuSlot < limit; menuSlot++) {
            if (!matchesSearch(menuSlot)) continue;
            Slot slot = menu.getSlot(menuSlot);
            if (slot.getItem().isEmpty()) continue;
            highlightSlot(graphics, slot, order++, partialTick);
        }
        graphics.pose().popPose();
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
        DesignAssets.draw(graphics, "slot_search_hit_40", leftPos + slot.x - 2, topPos + slot.y - 2, 20, 20);
    }
}
