package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.jei.JEIPlugin;
import com.sorrowmist.useless.content.menus.ChainGroupMenu;
import com.sorrowmist.useless.core.config.ChainGroupManager;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.network.BeefToolLayoutUpdatePacket;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
public final class ChainGroupScreen extends AbstractContainerScreen<ChainGroupMenu> {

    /**
     * 面板宽度：JEI 的原料侧栏只会画在 {@code guiRight} 右侧，而且至少要放得下 2 列
     * （每列 18px，两侧各留 6px 边距，共需 48px）才会显示，所以面板不能太宽。
     */
    private static final int IMAGE_WIDTH = 288;
    private static final int IMAGE_HEIGHT = 222;

    /**
     * 给 JEI / EMI 原料侧栏预留的右侧宽度。
     *
     * <p>面板默认居中；只有当居中会把右侧挤到不足这个宽度时，才自动往左挪。
     * 取 120 是让侧栏至少能出 6 列（12px 边距 + 6×18px），而不是勉强压着 48px 的下限。</p>
     */
    private static final int SIDEBAR_RESERVE = 120;

    /** 内容区左边缘。 */
    private static final int CONTENT_LEFT = 8;
    /** 右边缘对齐线 R1：所有底部按钮、输入框、拖拽槽、条目行尾的删除 x 都落在这一条线上。 */
    private static final int CONTENT_RIGHT = 280;
    /** 左列（组列表）右边缘 R2，也是组行拖拽槽的右边缘。 */
    private static final int GROUP_COLUMN_RIGHT = 126;
    /** 右列（条目列表）左边缘。 */
    private static final int ENTRY_COLUMN_LEFT = 134;

    private static final int TITLE_Y = 6;
    private static final int COLUMN_HEADER_Y = 18;
    private static final int LIST_TOP = 28;
    private static final int LIST_ROW_HEIGHT = 16;
    private static final int LIST_VISIBLE_ROWS = 10;
    private static final int LIST_BOTTOM = LIST_TOP + LIST_VISIBLE_ROWS * LIST_ROW_HEIGHT;

    private static final int FOOTER_Y = 196;
    private static final int FOOTER_HEIGHT = 14;
    private static final int STATUS_Y = 210;

    private static final int ADD_GROUP_X = 8;
    private static final int REMOVE_GROUP_X = 64;
    private static final int FOOTER_BUTTON_WIDTH = 52;
    private static final int ENTRY_FIELD_WIDTH = 78;
    private static final int ADD_ENTRY_WIDTH = 64;
    private static final int ADD_ENTRY_X = CONTENT_RIGHT - ADD_ENTRY_WIDTH;

    private static final int DROP_ZONE_SIZE = 16;
    private static final int DELETE_HIT_WIDTH = 8;

    private static final int SELECTED_BAR_COLOR = 0xFF3E8E8E;

    private final ItemStack targetItem;
    private final List<PressableAE2Button> buttons = new ArrayList<>();

    private BeefToolLayout layout;
    private int selectedGroup = -1;
    private int groupScroll;
    private int entryScroll;

    /**
     * 用 {@link AE2StyleTextField} 而不是原版 {@code EditBox}：原版画提示文本时既不截断也不裁剪，
     * 提示一长就会溢出到右边的「添加」按钮上；这个子类自己按框宽截断。
     */
    private AE2StyleTextField entryField;

    private String statusKey;
    private int statusTicks;

    /** 一次性诊断只打一次，定位完就删。 */
    private boolean diagnosticsLogged;

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
    protected void init() {
        super.init();
        // 默认居中；只有当居中会让右侧放不下 JEI / EMI 的原料侧栏时才自动左移，
        // 否则侧栏会因为挤不出 2 列而整个不显示（GUI 越窄越容易触发）。
        int centered = (width - imageWidth) / 2;
        leftPos = Math.max(0, Math.min(centered, width - imageWidth - SIDEBAR_RESERVE));
        // 面板比 GUI 还高时（极端 GUI 缩放）顶部对齐，免得底部按钮跑出屏幕。
        topPos = Math.max(0, (height - imageHeight) / 2);

        buttons.clear();
        addButton(ADD_GROUP_X, FOOTER_BUTTON_WIDTH,
                Component.translatable("gui.useless_mod.chain_group.add_group"), this::addGroup);
        addButton(REMOVE_GROUP_X, FOOTER_BUTTON_WIDTH,
                Component.translatable("gui.useless_mod.chain_group.remove_group"), this::removeSelectedGroup);
        addButton(ADD_ENTRY_X, ADD_ENTRY_WIDTH,
                Component.translatable("gui.useless_mod.chain_group.add_entry"), this::commitField);

        Component hint = Component.translatable("gui.useless_mod.chain_group.entry_hint");
        entryField = new AE2StyleTextField(font, leftPos + ENTRY_COLUMN_LEFT, topPos + FOOTER_Y + 1,
                ENTRY_FIELD_WIDTH, FOOTER_HEIGHT - 2, hint);
        entryField.setMaxLength(BeefToolLayout.MAX_CHAIN_ENTRY_LENGTH);
        entryField.setHint(hint);
        addRenderableWidget(entryField);

        clampSelection();
        clampScroll();
    }

    private void addButton(int x, int width, Component message, Runnable action) {
        PressableAE2Button button = addRenderableWidget(new PressableAE2Button(
                leftPos + x, topPos + FOOTER_Y, width, FOOTER_HEIGHT, message, ignored -> action.run()));
        buttons.add(button);
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
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && entryField != null && entryField.isFocused()) {
            commitField();
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PressableAE2Button widget : buttons) {
            widget.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY >= LIST_TOP && localY <= LIST_BOTTOM) {
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
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 这里刻意不做任何自动提交：输入框里的是「待新增的条目」，
        // 只有玩家按回车或点「添加」才该产生记录。停手一会儿就自动加一条，
        // 或者点一下别处就自动加一条，都是替玩家做了不可逆的决定。
        if (statusTicks > 0 && --statusTicks == 0) {
            statusKey = null;
        }
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

    /**
     * 面板与列表画在这里。
     *
     * <p>父类 {@code renderBackground} 先铺半透明底、再调本方法；之后才是
     * JEI 的侧栏背景、控件（renderables）、槽位与标签，最后是 JEI 的物品格子。</p>
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        logJeiDiagnosticsOnce();
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);

        graphics.drawString(font, title, leftPos + CONTENT_LEFT, topPos + TITLE_Y,
                MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.chain_group.group_label"),
                leftPos + CONTENT_LEFT, topPos + COLUMN_HEADER_Y,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.chain_group.entry_label"),
                leftPos + ENTRY_COLUMN_LEFT, topPos + COLUMN_HEADER_Y,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);

        // 两列各垫一层内衬，把「组列表 / 条目列表」分开，不然一片平底看不出是两个区域。
        MachineScreenStyle.drawInset(graphics, leftPos + CONTENT_LEFT - 2, topPos + LIST_TOP - 2,
                leftPos + GROUP_COLUMN_RIGHT, topPos + LIST_BOTTOM);
        MachineScreenStyle.drawInset(graphics, leftPos + ENTRY_COLUMN_LEFT - 2, topPos + LIST_TOP - 2,
                leftPos + CONTENT_RIGHT, topPos + LIST_BOTTOM);

        renderGroupList(graphics);
        renderEntryList(graphics);
        drawStatus(graphics);
    }

    /**
     * 父类默认会画容器标题与「物品栏」标签，这里全都不要——本界面的文字已经在
     * {@link #renderBg} 里画好了，没有物品栏。
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 控件（按钮 / 输入框）由父类的 renderables 循环渲染，天然压在面板之上。
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 一次性诊断：确认 JEI 是否真能查到本界面的属性、以及侧栏的判定结果。
     * 用于定位「侧栏不显示」，定位完成后连同 {@link #diagnosticsLogged} 一起删掉。
     */
    private void logJeiDiagnosticsOnce() {
        if (diagnosticsLogged) {
            return;
        }
        IJeiRuntime jei = JEIPlugin.getRuntime();
        if (jei == null) {
            return;
        }
        diagnosticsLogged = true;
        Optional<IGuiProperties> props = jei.getScreenHelper().getGuiProperties(this);
        UselessMod.LOGGER.info(
                "[chain-group diag] runtimeClass={} screen={}x{} panel=({},{}) {}x{} guiRight={} "
                        + "jeiFoundProps={} props={} listDisplayed={}",
                getClass().getName(), width, height, leftPos, topPos, imageWidth, imageHeight,
                leftPos + imageWidth, props.isPresent(), props.orElse(null),
                jei.getIngredientListOverlay().isListDisplayed());
    }

    private void renderGroupList(GuiGraphics graphics) {
        List<List<String>> groups = layout.chainGroups();
        if (groups.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.useless_mod.chain_group.empty"),
                    leftPos + CONTENT_LEFT + 2, topPos + LIST_TOP,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
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
                graphics.fill(left, y - 1, right, y + LIST_ROW_HEIGHT - 1, MachineScreenStyle.HIGHLIGHT_COLOR);
                graphics.fill(left, y - 1, left + 2, y + LIST_ROW_HEIGHT - 1, SELECTED_BAR_COLOR);
            }

            // 行尾拖拽槽：JEI 拖进来的方块会追加到这一组。
            int zoneX = leftPos + GROUP_COLUMN_RIGHT - DROP_ZONE_SIZE;
            MachineScreenStyle.drawInset(graphics, zoneX, y, zoneX + DROP_ZONE_SIZE, y + DROP_ZONE_SIZE);

            String count = "(" + group.size() + ")";
            int countX = zoneX - font.width(count) - 3;
            String preview = group.isEmpty() ? "-" : entryDisplayName(group.get(0));
            int textBudget = Math.max(16, countX - 4 - (leftPos + CONTENT_LEFT + 2));
            graphics.drawString(font,
                    font.plainSubstrByWidth((index + 1) + ". " + preview, textBudget),
                    leftPos + CONTENT_LEFT + 2, y + 4,
                    index == selectedGroup ? MachineScreenStyle.TEXT_COLOR : MachineScreenStyle.SUBTLE_TEXT_COLOR,
                    false);
            graphics.drawString(font, count, countX, y + 4, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
    }

    private void renderEntryList(GuiGraphics graphics) {
        List<String> entries = selectedEntries();
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.useless_mod.chain_group.entry_empty"),
                    leftPos + ENTRY_COLUMN_LEFT + 2, topPos + LIST_TOP,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            return;
        }

        int deleteX = leftPos + CONTENT_RIGHT - 6;
        for (int row = 0; row < LIST_VISIBLE_ROWS; row++) {
            int index = entryScroll + row;
            if (index >= entries.size()) {
                break;
            }
            String entry = entries.get(index);
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
                graphics.drawString(font, name, textX, y + 4, MachineScreenStyle.TEXT_COLOR, false);
                // 名字后面缀上原始 ID：不同模组的方块重名很常见，光看名字分不出来。
                // 但标签 / 通配符没有方块名，显示名就是条目原文，这时只画一次。
                if (!display.equals(entry)) {
                    int idX = textX + font.width(name) + 4;
                    int idBudget = deleteX - 6 - idX;
                    if (idBudget > 12) {
                        graphics.drawString(font, font.plainSubstrByWidth(entry, idBudget), idX, y + 4,
                                MachineScreenStyle.MUTED_TEXT_COLOR, false);
                    }
                }
            } else {
                // 语法合法但当前解析不到（未知方块 ID / 尚未加载的标签）标红提示。
                graphics.drawString(font, font.plainSubstrByWidth(entry, budget), textX, y + 4,
                        MachineScreenStyle.ERROR_TEXT_COLOR, false);
            }
            graphics.drawString(font, "x", deleteX, y + 4, MachineScreenStyle.ERROR_TEXT_COLOR, false);
        }
    }

    private void drawStatus(GuiGraphics graphics) {
        if (statusKey == null) {
            return;
        }
        graphics.drawString(font, Component.translatable(statusKey), leftPos + CONTENT_LEFT,
                topPos + STATUS_Y, MachineScreenStyle.ERROR_TEXT_COLOR, false);
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
        if (localY < LIST_TOP || localY > LIST_BOTTOM) {
            return -1;
        }
        int row = (int) ((localY - LIST_TOP) / LIST_ROW_HEIGHT);
        return row >= 0 && row < LIST_VISIBLE_ROWS ? row : -1;
    }

    private int entryRowAt(double localX, double localY) {
        if (localX < ENTRY_COLUMN_LEFT || localX > CONTENT_RIGHT) {
            return -1;
        }
        if (localY < LIST_TOP || localY > LIST_BOTTOM) {
            return -1;
        }
        int row = (int) ((localY - LIST_TOP) / LIST_ROW_HEIGHT);
        return row >= 0 && row < LIST_VISIBLE_ROWS ? row : -1;
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
