package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.ChainGroupMenu;
import com.sorrowmist.useless.core.config.ChainGroupManager;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.network.BeefToolLayoutUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.sorrowmist.useless.client.gui.apple.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 连锁挖掘「等价组」编辑界面。
 *
 * <p>从模式轮盘（G）→「连锁挖掘」模块右端的齿轮进入。等价组是玩家个人设置，
 * 与造化杖布局存在同一份 {@link BeefToolLayout} 里，所以编辑直接走
 * {@link BeefToolLayoutUpdatePacket}——与轮盘的导入导出是同一条校验/落库路径。</p>
 *
 * <p>本界面是 {@link AbstractContainerScreen} 而非普通 {@code Screen}：JEI 与 EMI 的原料侧栏
 * 默认只画在容器界面旁边（JEI 内置的 handler 只覆盖 {@code AbstractContainerScreen}），
 * 普通 {@code Screen} 拿不到侧栏也就没法拖拽。菜单是空的、也不下发，
 * 数据仍然走服务端权威的布局更新包。</p>
 */
public final class ChainGroupScreen extends AppleContainerScreen<ChainGroupMenu> {

    /**
     * 面板宽度：JEI 的原料侧栏只会画在 {@code guiRight} 右侧，而且至少要放得下 2 列
     * （每列 18px，两侧各留 6px 边距，共需 48px）才会显示，所以面板不能太宽。
     */
    private static final int IMAGE_WIDTH = 396;
    private static final int IMAGE_HEIGHT = 269;

    /**
     * 给 JEI / EMI 原料侧栏预留的右侧宽度。
     *
     * <p>面板默认居中；只有当居中会把右侧挤到不足这个宽度时，才自动往左挪。
     * 取 120 是让侧栏至少能出 6 列（12px 边距 + 6×18px），而不是勉强压着 48px 的下限。</p>
     */
    private static final int SIDEBAR_RESERVE = 120;

    /** 内容区左边缘。 */
    private static final int CONTENT_LEFT = 20;
    /** 右边缘对齐线 R1：所有底部按钮、输入框、拖拽槽、条目行尾的删除 x 都落在这一条线上。 */
    private static final int CONTENT_RIGHT = 368;
    /** 左列（组列表）右边缘 R2，也是组行拖拽槽的右边缘。 */
    private static final int GROUP_COLUMN_RIGHT = 160;
    /** 右列（条目列表）左边缘。 */
    private static final int ENTRY_COLUMN_LEFT = 168;

    private static final int TITLE_Y = 6;
    private static final int COLUMN_HEADER_Y = 40;
    private static final int LIST_TOP = 53;
    private static final int LIST_ROW_HEIGHT = 22;
    private static final int LIST_VISIBLE_ROWS = 8;

    private static final int FOOTER_HEIGHT = 16;
    private static final int ADD_GROUP_X = 12;
    private static final int REMOVE_GROUP_X = 114;
    private static final int FOOTER_BUTTON_WIDTH = 46;
    private static final int ENTRY_FIELD_WIDTH = 157;
    private static final int ADD_ENTRY_WIDTH = 39;
    private static final int ADD_ENTRY_X = CONTENT_RIGHT - ADD_ENTRY_WIDTH;

    private static final int DROP_ZONE_SIZE = 16;
    private static final int DELETE_HIT_WIDTH = 16;

    private final ItemStack targetItem;

    private BeefToolLayout layout;
    private int selectedGroup = -1;
    private int groupScroll;
    private int entryScroll;

    /** AppleTextField owns the background; this input owns only native text/cursor/selection. */
    private AppleEditBox entryField;

    private String statusKey;
    private int statusTicks;
    private Geometry placedGeometry;

    public record Geometry(int groupHeight, int entryHeight, int groupFooterY, int fieldY, int dropY, int height) {}

    /** Reference content-driven columns; bounded lists retain scrolling for arbitrarily long data. */
    public static Geometry referenceGeometry(int groups, int entries, boolean error) {
        int gh = groups == 0 ? 180 : Math.min(LIST_VISIBLE_ROWS, Math.max(0, groups)) * LIST_ROW_HEIGHT;
        int eh = entries == 0 ? 50 : Math.min(LIST_VISIBLE_ROWS, Math.max(0, entries)) * LIST_ROW_HEIGHT;
        int gf = LIST_TOP + gh + 8, fy = LIST_TOP + eh + 8;
        int dy = fy + FOOTER_HEIGHT + 8 + (error ? 33 : 0);
        return new Geometry(gh, eh, gf, fy, dy, Math.max(269, Math.max(gf + FOOTER_HEIGHT, dy + 28) + 12));
    }

    private Geometry geometry() {
        return referenceGeometry(layout.chainGroups().size(), selectedEntries().size(), statusKey != null);
    }

    @Override
    protected void preInit() { imageHeight = geometry().height(); }

    private void refreshReferenceGeometry() {
        Geometry next = geometry();
        if (host == null || next.equals(placedGeometry)) return;
        imageHeight = next.height();
        topPos = Math.max(0, (height - imageHeight) / 2);
        host = new AppleHost(imageWidth, imageHeight);
        buildApple(host);
        host.layout(width, height, leftPos, topPos);
    }


    public ChainGroupScreen(ChainGroupMenu menu, Inventory inventory, ItemStack targetItem,
                            BeefToolLayout layout) {
        super(menu, inventory, Component.translatable("gui.useless_mod.chain_group.title"));
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
        this.targetItem = targetItem;
        this.layout = layout;
        if (!layout.chainGroups().isEmpty()) {
            this.selectedGroup = 0;
        }
    }

    @Override
    protected void initControls() {
        // 默认居中；只有当居中会让右侧放不下 JEI / EMI 的原料侧栏时才自动左移，
        // 否则侧栏会因为挤不出 2 列而整个不显示（GUI 越窄越容易触发）。
        int centered = (width - imageWidth) / 2;
        leftPos = Math.max(0, Math.min(centered, width - imageWidth - SIDEBAR_RESERVE));
        // 面板比 GUI 还高时（极端 GUI 缩放）顶部对齐，免得底部按钮跑出屏幕。
        topPos = Math.max(0, (height - imageHeight) / 2);

        Component hint = Component.translatable("gui.useless_mod.chain_group.entry_hint");
        if (entryField == null) {
            entryField = new AppleEditBox(font, leftPos + ENTRY_COLUMN_LEFT, topPos + geometry().fieldY(),
                    ENTRY_FIELD_WIDTH, FOOTER_HEIGHT, hint);
            entryField.setMaxLength(BeefToolLayout.MAX_CHAIN_ENTRY_LENGTH);
            entryField.setHint(hint);
        } else {
            entryField.setX(leftPos + ENTRY_COLUMN_LEFT);
            entryField.setY(topPos + geometry().fieldY());
        }
        addRenderableWidget(entryField);

        clampSelection();
        clampScroll();
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        boolean focused = entryField != null && entryField.isFocused();
        int cursor = focused ? entryField.getCursorPosition() : 0;
        int selection = focused ? entryField.selectionAnchor() : 0;
        super.resize(client, width, height);
        // A content-height change may also change fractional GUI scale. Keep the same
        // native input, including failed text, focus, caret and selection, across rebuilds.
        setFocused(focused ? entryField : null);
        entryField.setCursorPosition(cursor);
        entryField.setHighlightPos(selection);
        entryField.setFocused(focused);
    }

    /**
     * 布局（288×246）：标题栏（右侧错误提示胶囊）；「分组」「条目」两张分栏卡；底部工具栏卡
     * （新建组 / 删除组 / 输入框 / 添加）。列表行、拖拽槽、删除命中区坐标与原版一致。
     */
    @Override
    protected void buildApple(AppleHost h) {
        h.add(new AppleWindow(imageWidth, imageHeight, () -> title).icon(AppleUi.Icon.CHAIN, AppleStyle.BLUE));
        Geometry layout = geometry();
        placedGeometry = layout;
        h.add(new AppleBadge(IMAGE_WIDTH - 48, 7, 40,
                () -> Component.translatable("gui.useless_mod.chain_group.group_count", chainGroupCount()),
                () -> AppleStyle.BLUE).right().id("count"));
        h.add(new AppleCard(12, LIST_TOP, 148, layout.groupHeight()));
        h.add(new AppleCard(ENTRY_COLUMN_LEFT, LIST_TOP, 200, layout.entryHeight()));
        h.add(new AppleTextField(ENTRY_COLUMN_LEFT, layout.fieldY(), ENTRY_FIELD_WIDTH, FOOTER_HEIGHT, entryField).id("entry_field"));
        if (statusKey != null) h.add(new AppleLabel(ENTRY_COLUMN_LEFT + 2, layout.fieldY() + 20, 196,
                AppleLabel.Kind.FOOTNOTE, () -> Component.translatable(statusKey)).color(AppleStyle.RED_TEXT).wrap());
        h.add(new AppleCanvas(ENTRY_COLUMN_LEFT, layout.dropY(), CONTENT_RIGHT - ENTRY_COLUMN_LEFT, 28,
                (g, mx, my, x, y, w, hh) -> {
                    AppleUi.fillRound(g, x, y, w, hh, 5, AppleStyle.BLUE_TINT);
                    for (int dx = 0; dx < w; dx += 5) {
                        g.fill(x + dx, y, x + Math.min(w, dx + 3), y + 1, AppleStyle.BLUE);
                        g.fill(x + dx, y + hh - 1, x + Math.min(w, dx + 3), y + hh, AppleStyle.BLUE);
                    }
                    String hint = Component.translatable("gui.useless_mod.chain_group.drop_hint").getString();
                    AppleTextLayout.drawCentered(g, font, Component.literal(hint), x + 8, y, w - 16, hh, 2, 4,
                            AppleUi.Icon.IMPORT, AppleStyle.BLUE);
                }));
        h.add(new AppleLabel(14, COLUMN_HEADER_Y, 146, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.chain_group.group_label")));
        h.add(new AppleLabel(ENTRY_COLUMN_LEFT + 1, COLUMN_HEADER_Y, 140, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.chain_group.entry_label")));
        h.add(new AppleCanvas(12, LIST_TOP, 148, layout.groupHeight(),
                (g, mx, my, x, y, w, hh) -> renderGroupList(g, mx, my)).id("groups"));
        h.add(new AppleCanvas(ENTRY_COLUMN_LEFT, LIST_TOP, CONTENT_RIGHT - ENTRY_COLUMN_LEFT, layout.entryHeight(),
                (g, mx, my, x, y, w, hh) -> renderEntryList(g, mx, my)).id("entries"));
        h.add(new ApplePushButton(ADD_GROUP_X, layout.groupFooterY(), FOOTER_BUTTON_WIDTH, FOOTER_HEIGHT, ApplePushButton.Kind.TINTED,
                () -> Component.translatable("gui.useless_mod.chain_group.add_group"), this::addGroup).id("add_group"));
        h.add(new ApplePushButton(REMOVE_GROUP_X, layout.groupFooterY(), FOOTER_BUTTON_WIDTH, FOOTER_HEIGHT, ApplePushButton.Kind.DANGER,
                () -> Component.translatable("gui.useless_mod.chain_group.remove_group"), this::removeSelectedGroup)
                .<ApplePushButton>enabledWhen(() -> selectedGroup >= 0 && selectedGroup < this.layout.chainGroups().size())
                .id("remove_group"));
        h.add(new ApplePushButton(ADD_ENTRY_X, layout.fieldY(), ADD_ENTRY_WIDTH, FOOTER_HEIGHT, ApplePushButton.Kind.PRIMARY,
                () -> Component.translatable("gui.useless_mod.chain_group.add_entry"), this::commitField).id("add_entry"));
    }

    // ------------------------------------------------------------------ 服务端交互

    /**
     * 界面收到服务端校验后的布局快照。
     *
     * <p>输入框里还有没提交的内容时整份跳过：这些内容只会在玩家按回车或点「添加」时
     * 才变成条目，被快照覆盖掉就等于把玩家打的字吞了。</p>
     */
    public void receiveSync(BeefToolLayout synced) {
        if (entryField != null && !entryField.getValue().isBlank()) {
            return;
        }
        this.layout = synced;
        clampSelection();
        clampScroll();
    }

    /** 服务端拒绝了这次改动。 */
    public void receiveError(BeefToolLayout.Error error) {
        setStatus(errorKey(error));
    }

    private void sendLayout() {
        try {
            ChainGroupManager.validateEntries(layout.chainGroups());
        } catch (BeefToolLayout.LayoutException exception) {
            setStatus(errorKey(exception.error()));
            return;
        }
        PacketDistributor.sendToServer(new BeefToolLayoutUpdatePacket(layout.toJson()));
        clampScroll();
    }

    // ------------------------------------------------------------------ 编辑动作

    private void addGroup() {
        if (layout.chainGroups().size() >= BeefToolLayout.MAX_CHAIN_GROUPS) {
            setStatus("gui.useless_mod.mode_config.error.limit");
            return;
        }
        layout.chainGroups().add(new ArrayList<>());
        selectedGroup = layout.chainGroups().size() - 1;
        entryScroll = 0;
        sendLayout();
    }

    private void removeSelectedGroup() {
        if (selectedGroup < 0 || selectedGroup >= layout.chainGroups().size()) {
            return;
        }
        layout.chainGroups().remove(selectedGroup);
        clampSelection();
        entryScroll = 0;
        sendLayout();
    }

    /** 把输入框里的内容作为一个新条目加进当前选中的组。 */
    private void commitField() {
        if (entryField == null) {
            return;
        }
        String raw = entryField.getValue();
        String entry = raw == null ? "" : raw.trim();
        if (entry.isEmpty()) {
            return;
        }
        if (!addEntry(selectedGroup, entry)) {
            return;
        }
        entryField.setValue("");
    }

    /** JEI 拖拽入口：把一个方块加进指定组。 */
    public void addEntryFromBlock(int groupIndex, ResourceLocation blockId) {
        if (blockId != null) {
            addEntry(groupIndex, blockId.toString());
        }
    }

    private boolean addEntry(int groupIndex, String entry) {
        if (groupIndex < 0 || groupIndex >= layout.chainGroups().size()) {
            setStatus("gui.useless_mod.chain_group.error.no_group");
            return false;
        }
        if (!ChainGroupManager.isValidEntrySyntax(entry)) {
            setStatus("gui.useless_mod.chain_group.error.invalid");
            return false;
        }
        List<String> group = layout.chainGroups().get(groupIndex);
        if (group.contains(entry)) {
            setStatus("gui.useless_mod.chain_group.error.duplicate");
            return false;
        }
        if (group.size() >= BeefToolLayout.MAX_CHAIN_ENTRIES_PER_GROUP
                || totalEntries() >= BeefToolLayout.MAX_TOTAL_CHAIN_ENTRIES) {
            setStatus("gui.useless_mod.mode_config.error.limit");
            return false;
        }
        group.add(entry);
        selectedGroup = groupIndex;
        sendLayout();
        return true;
    }

    private void removeEntry(int groupIndex, int entryIndex) {
        if (groupIndex < 0 || groupIndex >= layout.chainGroups().size()) {
            return;
        }
        List<String> group = layout.chainGroups().get(groupIndex);
        if (entryIndex < 0 || entryIndex >= group.size()) {
            return;
        }
        group.remove(entryIndex);
        clampScroll();
        sendLayout();
    }

    private int totalEntries() {
        int total = 0;
        for (List<String> group : layout.chainGroups()) {
            total += group.size();
        }
        return total;
    }

    // ------------------------------------------------------------------ JEI 拖拽接口

    public int chainGroupCount() {
        return layout.chainGroups().size();
    }

    public int dropZoneSize() {
        return DROP_ZONE_SIZE;
    }

    /** Capture group identity as well as visibility; sync/removal must not redirect a retained JEI target. */
    public java.util.function.BooleanSupplier jeiDropGuard(int groupIndex, boolean selectedArea) {
        var group = layout.chainGroups().get(groupIndex);
        AppleHost originalHost = host;
        return () -> host == originalHost && !host.isPopoverOpen()
                && groupIndex < layout.chainGroups().size() && layout.chainGroups().get(groupIndex) == group
                && (selectedArea ? selectedGroup == groupIndex : isGroupRowVisible(groupIndex));
    }

    public int selectedGroupIndex() { return selectedGroup; }

    public net.minecraft.client.renderer.Rect2i selectedGroupDropArea() {
        return new net.minecraft.client.renderer.Rect2i(leftPos + ENTRY_COLUMN_LEFT, topPos + geometry().dropY(),
                CONTENT_RIGHT - ENTRY_COLUMN_LEFT, 28);
    }

    /** 该组当前是否可见（被滚动出可视区时不该给出拖拽目标）。 */
    public boolean isGroupRowVisible(int groupIndex) {
        int row = groupIndex - groupScroll;
        return row >= 0 && row < LIST_VISIBLE_ROWS;
    }

    public int groupDropZoneScreenX(int groupIndex) {
        return leftPos + GROUP_COLUMN_RIGHT - DROP_ZONE_SIZE;
    }

    public int groupDropZoneScreenY(int groupIndex) {
        return topPos + LIST_TOP + (groupIndex - groupScroll) * LIST_ROW_HEIGHT;
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车提交输入框内容，提交后不关闭界面。
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && entryField != null && entryField.isFocused()) {
            commitField();
            return true;
        }
        // 输入框聚焦期间不把按键转交父类：AbstractContainerScreen 在子控件未消费按键时
        // 会比对容器快捷键（默认 E 打开物品栏），命中即调用 onClose 关闭本界面，
        // 导致该字母无法输入。Esc 仍然放行，保持「Esc 关闭界面」的既有行为。
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && entryField != null
                && (entryField.keyPressed(keyCode, scanCode, modifiers) || entryField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int groupRow = groupRowAt(localX, localY);
            // 命中区域是整列，点在列表下方的空白行时不能把选中索引顶到越界。
            if (groupRow >= 0 && groupScroll + groupRow < layout.chainGroups().size()) {
                selectedGroup = groupScroll + groupRow;
                entryScroll = 0;
                refreshReferenceGeometry();
                return true;
            }
            int entryRow = entryRowAt(localX, localY);
            if (entryRow >= 0 && localX >= CONTENT_RIGHT - DELETE_HIT_WIDTH) {
                removeEntry(selectedGroup, entryScroll + entryRow);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY >= LIST_TOP && localY < LIST_TOP + Math.max(geometry().groupHeight(), geometry().entryHeight())) {
            int step = scrollY > 0 ? -1 : 1;
            if (localX >= CONTENT_LEFT && localX <= GROUP_COLUMN_RIGHT) {
                groupScroll = Mth.clamp(groupScroll + step, 0, maxGroupScroll());
                return true;
            }
            if (localX >= ENTRY_COLUMN_LEFT && localX <= CONTENT_RIGHT) {
                entryScroll = Mth.clamp(entryScroll + step, 0, maxEntryScroll());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    protected void tickControls() {
        // 这里刻意不做任何自动提交：输入框里的是「待新增的条目」，
        // 只有玩家按回车或点「添加」才该产生记录。停手一会儿就自动加一条，
        // 或者点一下别处就自动加一条，都是替玩家做了不可逆的决定。
        if (statusTicks > 0 && --statusTicks == 0) {
            statusKey = null;
        }
        refreshReferenceGeometry();
    }

    @Override
    public void onClose() {
        // 输入框里没提交的内容直接丢弃——按回车或点「添加」才算数。
        // 回到轮盘，玩家可以接着调其它模式；轮盘 init 会重新请求一份最新布局。
        Minecraft.getInstance().setScreen(new ModeWheelScreen(targetItem));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    protected void renderExtraTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderGroupList(GuiGraphics graphics, int mx, int my) {
        List<List<String>> groups = layout.chainGroups();
        if (groups.isEmpty()) {
            // 与正常条目行同基线（LIST_TOP + 4），否则会比列表内衬顶边高出 4px 而压在那条边框线上。
            // 提示按列宽折行：该文案长于列宽，截断会丢失后半句语义。
            drawCenteredEmpty(graphics, Component.translatable("gui.useless_mod.chain_group.empty"),
                    leftPos + 20, topPos + LIST_TOP, 132, geometry().groupHeight());
            return;
        }

        for (int row = 0; row < LIST_VISIBLE_ROWS; row++) {
            int index = groupScroll + row;
            if (index >= groups.size()) {
                break;
            }
            List<String> group = groups.get(index);
            int y = topPos + LIST_TOP + row * LIST_ROW_HEIGHT;
            int left = leftPos + CONTENT_LEFT - 2;
            int right = leftPos + GROUP_COLUMN_RIGHT;

            // 选中态用「底色 + 左侧竖条」：只靠一层底色太淡。
            if (index == selectedGroup) {
                AppleUi2.round(graphics, left, y - 1, right - left, LIST_ROW_HEIGHT, 4f, AppleStyle.SELECTED_ROW);
            } else if (mx >= left && mx < right && my >= y - 1 && my < y + LIST_ROW_HEIGHT - 1) {
                AppleUi2.round(graphics, left, y - 1, right - left, LIST_ROW_HEIGHT, 4f, AppleStyle.HOVER_ROW);
            }

            // 行尾拖拽槽：JEI 拖进来的方块会追加到这一组。
            int zoneX = leftPos + GROUP_COLUMN_RIGHT - DROP_ZONE_SIZE;
            AppleUi.slot(graphics, zoneX, y - 1, false);
            AppleUi.icon(graphics, AppleUi.Icon.PLUS, zoneX + 4, y + 3, 8, AppleStyle.QUATERNARY);

            String count = "(" + group.size() + ")";
            int countX = zoneX - font.width(count) - 3;
            String preview = group.isEmpty() ? "-" : entryDisplayName(group.get(0));
            int iconX = leftPos + CONTENT_LEFT;
            Block iconBlock = group.isEmpty() ? null : resolveExactBlock(group.get(0));
            if (iconBlock != null && iconBlock.asItem() != Items.AIR) graphics.renderItem(new ItemStack(iconBlock), iconX, y + 2);
            else AppleUi.fillRound(graphics, iconX, y + 3, 14, 14, 4, AppleStyle.ORANGE_TINT);
            int textBudget = Math.max(16, countX - 4 - (leftPos + CONTENT_LEFT + 20));
            graphics.drawString(font,
                    font.plainSubstrByWidth((index + 1) + ". " + preview, textBudget),
                    leftPos + CONTENT_LEFT + 20, y + 6,
                    index == selectedGroup ? AppleStyle.LABEL : AppleStyle.SECONDARY, false);
            graphics.drawString(font, count, countX, y + 4, AppleStyle.TERTIARY, false);
        }
    }

    private void renderEntryList(GuiGraphics graphics, int mx, int my) {
        List<String> entries = selectedEntries();
        if (entries.isEmpty()) {
            // 与正常条目行同基线（LIST_TOP + 4），否则会比列表内衬顶边高出 4px 而压在那条边框线上。
            // 提示按列宽折行：该文案在窄列下可能放不下，截断会丢失后半句语义。
            drawCenteredEmpty(graphics, Component.translatable("gui.useless_mod.chain_group.entry_empty"),
                    leftPos + ENTRY_COLUMN_LEFT + 8, topPos + LIST_TOP, 184, geometry().entryHeight());
            return;
        }

        int deleteX = leftPos + CONTENT_RIGHT - 6;
        for (int row = 0; row < LIST_VISIBLE_ROWS; row++) {
            int index = entryScroll + row;
            if (index >= entries.size()) {
                break;
            }
            String entry = entries.get(index);
            int rowY = topPos + LIST_TOP + row * LIST_ROW_HEIGHT;
            if (mx >= leftPos + ENTRY_COLUMN_LEFT - 2 && mx < leftPos + CONTENT_RIGHT && my >= rowY - 1 && my < rowY + LIST_ROW_HEIGHT - 1)
                AppleUi2.round(graphics, leftPos + ENTRY_COLUMN_LEFT - 2, rowY - 1, CONTENT_RIGHT - ENTRY_COLUMN_LEFT + 2, LIST_ROW_HEIGHT, 4f, AppleStyle.HOVER_ROW);
            Block block = resolveExactBlock(entry);
            int y = topPos + LIST_TOP + row * LIST_ROW_HEIGHT;
            int textX = leftPos + ENTRY_COLUMN_LEFT;

            if (block != null && block.asItem() != Items.AIR) {
                graphics.renderItem(new ItemStack(block), textX, y);
                textX += 18;
            }

            int budget = deleteX - 6 - textX;
            if (isResolvable(entry)) {
                String display = entryDisplayName(entry);
                String name = font.plainSubstrByWidth(display, budget);
                graphics.drawString(font, name, textX, y + 4, AppleStyle.LABEL, false);
                // 名字后面缀上原始 ID：不同模组的方块重名很常见，光看名字分不出来。
                // 但标签 / 通配符没有方块名，显示名就是条目原文，这时只画一次。
                if (!display.equals(entry)) {
                    int idX = textX + font.width(name) + 4;
                    int idBudget = deleteX - 6 - idX;
                    if (idBudget > 12) {
                        graphics.drawString(font, font.plainSubstrByWidth(entry, idBudget), idX, y + 4,
                                AppleStyle.TERTIARY, false);
                    }
                }
            } else {
                // 语法合法但当前解析不到（未知方块 ID / 尚未加载的标签）标红提示。
                graphics.drawString(font, font.plainSubstrByWidth(entry, budget), textX, y + 4,
                        AppleStyle.RED_TEXT, false);
            }
            boolean hd = mx >= leftPos + CONTENT_RIGHT - DELETE_HIT_WIDTH && mx <= leftPos + CONTENT_RIGHT && my >= y - 1 && my < y + LIST_ROW_HEIGHT - 1;
            AppleUi.fillRound(graphics, deleteX - 8, y + 3, 14, 14, 4, AppleStyle.RED_TINT);
            AppleUi.icon(graphics, AppleUi.Icon.TRASH, deleteX - 5, y + 6, 8, hd ? AppleStyle.RED_TEXT : AppleStyle.RED);
        }
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;

        int groupRow = groupRowAt(localX, localY);
        if (groupRow >= 0) {
            int index = groupScroll + groupRow;
            if (index < layout.chainGroups().size()
                    && localX >= GROUP_COLUMN_RIGHT - DROP_ZONE_SIZE) {
                graphics.renderTooltip(font,
                        Component.translatable("gui.useless_mod.chain_group.drop_hint"),
                        (int) mouseX, (int) mouseY);
                return;
            }
        }

        int entryRow = entryRowAt(localX, localY);
        if (entryRow >= 0) {
            List<String> entries = selectedEntries();
            int index = entryScroll + entryRow;
            if (index < entries.size()) {
                graphics.renderTooltip(font, Component.literal(entries.get(index)),
                        (int) mouseX, (int) mouseY);
            }
        }
    }

    // ------------------------------------------------------------------ 工具方法

    /**
     * 按给定宽度折行绘制提示文本。
     *
     * <p>列表空态提示长于列宽，直接截断会丢失后半句语义，因此折行完整呈现。
     * 行距取字体行高加 1 像素，避免相邻两行贴连；不使用列表行高，否则行距过疏。</p>
     */
    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 1),
                    AppleStyle.TERTIARY, false);
        }
    }

    private void drawCenteredEmpty(GuiGraphics g, Component text, int x, int y, int w, int h) {
        var lines = font.split(text, Math.round(w / .8f));
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + (h - lines.size() * 8f) / 2f, 0);
        g.pose().scale(.8f, .8f, 1);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, i * 10, AppleStyle.SECONDARY, false);
        g.pose().popPose();
    }

    private List<String> selectedEntries() {
        if (selectedGroup < 0 || selectedGroup >= layout.chainGroups().size()) {
            return List.of();
        }
        return layout.chainGroups().get(selectedGroup);
    }

    /** 条目对应的方块显示名；解析不到时退回条目原文。 */
    private static String entryDisplayName(String entry) {
        Block block = resolveExactBlock(entry);
        return block == null ? entry : block.getName().getString();
    }

    /** 只解析「精确方块 ID」形式的条目；标签与通配符没有单一对应方块。 */
    @Nullable
    private static Block resolveExactBlock(String entry) {
        if (entry.startsWith("#") || entry.indexOf('*') >= 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? null : block;
    }

    /** 条目当前能否解析到任何方块（精确 ID 查注册表，标签查标签集，通配符不判）。 */
    private static boolean isResolvable(String entry) {
        if (entry.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
            if (id == null) {
                return false;
            }
            return BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id))
                    .map(set -> set.size() > 0)
                    .orElse(false);
        }
        if (entry.indexOf('*') >= 0) {
            return true;
        }
        return resolveExactBlock(entry) != null;
    }

    private int groupRowAt(double localX, double localY) {
        if (localX < CONTENT_LEFT || localX > GROUP_COLUMN_RIGHT) {
            return -1;
        }
        if (localY < LIST_TOP || localY >= LIST_TOP + geometry().groupHeight()) {
            return -1;
        }
        int row = (int) ((localY - LIST_TOP) / LIST_ROW_HEIGHT);
        return row >= 0 && row < LIST_VISIBLE_ROWS ? row : -1;
    }

    private int entryRowAt(double localX, double localY) {
        if (localX < ENTRY_COLUMN_LEFT || localX >= CONTENT_RIGHT
                || localY < LIST_TOP || localY >= LIST_TOP + geometry().entryHeight()) return -1;
        int row = (int) ((localY - LIST_TOP) / LIST_ROW_HEIGHT);
        return row >= 0 && row < Math.min(LIST_VISIBLE_ROWS, selectedEntries().size()) ? row : -1;
    }

    private int maxGroupScroll() {
        return Math.max(0, layout.chainGroups().size() - LIST_VISIBLE_ROWS);
    }

    private int maxEntryScroll() {
        return Math.max(0, selectedEntries().size() - LIST_VISIBLE_ROWS);
    }

    private void clampSelection() {
        if (layout.chainGroups().isEmpty()) {
            selectedGroup = -1;
        } else {
            selectedGroup = Mth.clamp(selectedGroup, 0, layout.chainGroups().size() - 1);
        }
    }

    private void clampScroll() {
        groupScroll = Mth.clamp(groupScroll, 0, maxGroupScroll());
        entryScroll = Mth.clamp(entryScroll, 0, maxEntryScroll());
    }

    private void setStatus(String key) {
        statusKey = key;
        statusTicks = 100;
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
}
