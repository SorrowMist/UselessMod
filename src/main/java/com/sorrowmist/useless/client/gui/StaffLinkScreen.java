package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.client.stafflink.StaffLinkStressClientState;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.LinkFilterCondition;
import com.sorrowmist.useless.content.stafflink.LinkFilterPattern;
import com.sorrowmist.useless.content.stafflink.LinkFilterSlot;
import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.ResourceFamily;
import com.sorrowmist.useless.content.stafflink.StaffLinkFilters;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.network.StaffLinkSyncPacket;
import com.sorrowmist.useless.network.StaffLinkStressStatusPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.ChatFormatting;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

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
    /**
     * 面板高度。
     *
     * <p>过滤槽<b>不画在主界面上</b>（18 格 + 每格两个限制框会撑得很高），主界面只留一个
     * 「过滤」按钮，点开在覆盖层里编辑。所以这里维持原来的高度。</p>
     */
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
    /** 应力线路上容器行尾那个数字占的宽度（紧凑写法最多 4~5 个字符）。 */
    private static final int STRESS_ROW_VALUE_WIDTH = 36;

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

    /**
     * 下拉菜单的绘制 z。
     *
     * <p>GUI 的深度测试是开着的（{@code RenderType.GUI}、文字与 AE2 面板都走 LEQUAL），
     * 而 {@link GuiGraphics} 给容器内容定的 z 依次是：槽位物品 150、堆叠数字 200、
     * 手持拖动物品 232（其数字再 +200）。下拉若留在 z=0，就比这些内容全都远，
     * 片段会被深度测试直接丢掉 —— 光 {@code flush()} 只保证「后画」，救不回来，
     * 表现就是下拉被背包物品和栏位数字盖住。</p>
     *
     * <p>抬到 500 可稳定压过全部容器内容（原版 tooltip 用 400；下拉展开期间不弹 tooltip，
     * 所以不必迁就它）。</p>
     */
    private static final float MEDIUM_MENU_Z = 500.0F;
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

    // ---- 过滤入口（主界面）+ 过滤面板（覆盖层）
    private static final int FILTER_X = 8;
    /** 主界面「过滤」按钮所在行；沿用原来过滤槽标题的位置。 */
    private static final int FILTER_Y = 204;
    /** 主界面「过滤」按钮 / 「应用到全部同名容器」/「解散网络」并排。 */
    private static final int FILTER_BUTTON_WIDTH = 66;
    private static final int APPLY_ALL_X = 82;
    private static final int APPLY_ALL_WIDTH = 92;
    private static final int DISSOLVE_X = 180;
    private static final int DISSOLVE_WIDTH = CONTENT_RIGHT - DISSOLVE_X;

    /**
     * 应力运行状态两行文字的位置。
     *
     * <p>取在「过滤」按钮那一行（{@link #FILTER_Y} + 16 = 220）与背包标题（242）之间的空白里：
     * 上面已经排满了四行配置，下面紧跟着背包，只有这一段是空的。</p>
     */
    private static final int STRESS_STATUS_Y = 224;
    /** 状态文字用色：出问题时换成醒目的琥珀色，正常时与其它次要文字一致。 */
    private static final int STRESS_WARN_COLOR = 0xFFB26A00;

    // ---- 过滤面板（覆盖层）几何
    /**
     * 面板尺寸。
     *
     * <p>v2 起每格只是一个 16×16 的槽位（模式框与两条条件都搬进了右键的详细面板），
     * 所以面板整体收窄到「刚好放下标题 / 提示 + 6 列格子 + 两个按钮」。窄面板还有个好处：
     * 右边必然留得下 JEI 的原料侧栏（{@code 427 − 2 − 138 = 287 ≥ 48}）。</p>
     *
     * <p>高度 = `6(上边距) + 21(标题 + 提示两行) + 3×22 − 6(三行) + 6 + 16(按钮) + 6 = 115`。</p>
     */
    private static final int FILTER_PANEL_WIDTH = 138;
    private static final int FILTER_PANEL_HEIGHT = 115;
    private static final int FILTER_PANEL_PAD = 6;
    /** 标题行 + 提示行占掉的高度，格子从这里往下排。 */
    private static final int FILTER_PANEL_HEADER = 21;
    /**
     * 一格 = 16×16 的槽位 + 6px 间隙。
     *
     * <p>v1 的 78×30 是给「模式框 + 两个限制框」留的位置，那两样搬进详细面板之后就不需要了，
     * 于是间距可以收到最小。</p>
     */
    private static final int FILTER_CELL_COL_STEP = 22;
    private static final int FILTER_CELL_ROW_STEP = 22;
    /**
     * 6 列 × 3 行 = 18 格，跟 {@link StaffLinkRoute#FILTER_LIMIT} 一致。
     *
     * <p>格子变成 16×16 的小方块之后，3 列会拼成一条细长的竖条、两侧留下大片空白；
     * 改成 6 列后网格正好铺满面板宽度。</p>
     */
    private static final int FILTER_COLUMNS = 6;
    /**
     * 格内槽位尺寸。
     *
     * <p>取 16 = 本模组自己的标准槽位（{@link MachineScreenStyle#drawSlotBackground} 也是
     * 16×16 + 顶部 1px 暗边），跟一级菜单的容器槽视觉一致；v1 用的 18 会让过滤格看起来比
     * 界面里其它槽位大一整圈。</p>
     */
    private static final int FILTER_SLOT_SIZE = 16;
    /** 格子网格在面板内的左上角；横向居中。 */
    private static final int FILTER_GRID_X = FILTER_PANEL_PAD
            + (FILTER_PANEL_WIDTH - FILTER_PANEL_PAD * 2
            - ((FILTER_COLUMNS - 1) * FILTER_CELL_COL_STEP + FILTER_SLOT_SIZE)) / 2;
    private static final int FILTER_GRID_Y = FILTER_PANEL_PAD + FILTER_PANEL_HEADER;
    /** 面板在「垂直居中」基础上再上移这么多，给下方的玩家背包让位。 */
    private static final int FILTER_PANEL_RAISE = 16;
    /**
     * 条件数值框 / 模式框的字符上限。
     *
     * <p>吃 {@code K / M / G / T / P / E} 后缀，一个缩写就能有 {@code 1.5K} 四个字符，
     * 纯数字留到 {@code 9999999} 也够。</p>
     */
    private static final int FILTER_LIMIT_MAX_CHARS = 8;
    /** 面板底部按钮行：高度、宽度与横向起点（两个按钮整体居中）。 */
    private static final int FILTER_BUTTON_HEIGHT = 16;
    private static final int FILTER_PANEL_BUTTON_WIDTH = 48;
    private static final int FILTER_PANEL_BUTTON_GAP = 6;
    private static final int FILTER_PANEL_BUTTON_X = (FILTER_PANEL_WIDTH
            - FILTER_PANEL_BUTTON_WIDTH * 2 - FILTER_PANEL_BUTTON_GAP) / 2;
    /** 按钮行 y：格子网格底(87) + 6 间隙。 */
    private static final int FILTER_PANEL_BUTTON_Y = 93;
    /**
     * 过滤面板（覆盖层）绘制 z。
     *
     * <p>面板是在 {@code super.render()} <b>之后</b>画的（不能早于它，否则 JEI 侧栏不显示），
     * 所以必须抬 z 才能压过普通深度的内容：槽位物品在 z=150、堆叠数字在 z=200。
     * 取 {@link #MEDIUM_MENU_Z} 同档的 500，可稳定压过全部容器内容。
     * 光 {@code flush()} 只解决「谁后画」，解决不了深度测试 —— 两件事都要做。</p>
     */
    private static final float FILTER_PANEL_Z = 500.0F;
    /**
     * 「光标上拿着的物品」的绘制 z。
     *
     * <p>vanilla 只在 {@code super.render()} 里画它，那时两个覆盖层面板（500 / 700）都还没画，
     * 所以从背包拿起物品来标记过滤格时，手上拿的东西会被面板整个盖住、看不见（用户实测反馈）。
     * 这里在覆盖层最后重画一遍。</p>
     *
     * <p>取 750：物品自身还会再被推 150 ⇒ 实际 900，刚好压过详细面板里那些物品（700+150=850），
     * 又不至于高到跟工具提示（700+400=1100）抢层。</p>
     */
    private static final float CARRIED_ITEM_Z = 750.0F;

    // ---- 详细编辑面板（每格右键打开）几何：独立宽度，跟过滤面板解耦
    private static final int DETAIL_PANEL_WIDTH = 250;
    private static final int DETAIL_PANEL_HEIGHT = 122;
    private static final int DETAIL_PANEL_PAD = 8;
    /**
     * 详细面板绘制 z：压过滤面板。
     *
     * <p><b>⛔ 必须比 {@link #FILTER_PANEL_Z} 高 150 以上</b>：{@code GuiGraphics.renderItem}
     * 内部会把物品再往前推 <b>150</b>（{@code pose.translate(x + 8, y + 8, 150 + …)}），
     * 所以过滤面板里画在 z=500 的物品实际落在 650 —— 详细面板的背景若只到 600，
     * 那些物品就会**穿透到详细面板之上**（用户实测：二级菜单的标记物品盖在三级菜单上）。
     * 取 +200 留出余量。</p>
     */
    private static final float DETAIL_PANEL_Z = FILTER_PANEL_Z + 200.0F;
    /** 面板内各行 y（面板内相对坐标）。 */
    private static final int DETAIL_TITLE_Y = 8;
    private static final int DETAIL_MARKER_ROW_Y = 26;
    private static final int DETAIL_OUT_ROW_Y = 50;
    private static final int DETAIL_IN_ROW_Y = 74;
    private static final int DETAIL_BUTTON_Y = 98;
    private static final int DETAIL_BUTTON_WIDTH = 70;
    private static final int DETAIL_BUTTON_HEIGHT = 16;
    /** 详细面板里槽位（A / B）的尺寸。 */
    private static final int DETAIL_SLOT_SIZE = FILTER_SLOT_SIZE;
    /** 行内控件 x（面板内相对坐标）。 */
    private static final int DETAIL_MARKER_X = DETAIL_PANEL_PAD;                                  // 8
    private static final int DETAIL_PATTERN_X = DETAIL_MARKER_X + DETAIL_SLOT_SIZE + 2;            // 28
    private static final int DETAIL_PATTERN_WIDTH = 140;
    private static final int DETAIL_EXCLUDE_X = 172;
    private static final int DETAIL_EXCLUDE_WIDTH = 70;
    private static final int DETAIL_LABEL_X = DETAIL_PANEL_PAD;                                    // 8
    private static final int DETAIL_COND_SLOT_X = 44;
    private static final int DETAIL_OP_X = DETAIL_COND_SLOT_X + DETAIL_SLOT_SIZE + 4;              // 66
    private static final int DETAIL_OP_WIDTH = 32;
    private static final int DETAIL_VALUE_X = DETAIL_OP_X + DETAIL_OP_WIDTH + 4;                   // 102
    private static final int DETAIL_VALUE_WIDTH = 140;
    private static final int DETAIL_ROW_HEIGHT = 18;

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
    private PressableAE2Button prevNetworkButton;
    private PressableAE2Button nextNetworkButton;
    private PressableAE2Button newNetworkButton;
    private PressableAE2Button dissolveButton;
    private PressableAE2Button selectAllButton;
    private PressableAE2Button clearSelectionButton;
    private PressableAE2Button applyAllButton;
    /** 主界面上打开过滤面板的入口按钮。 */
    private PressableAE2Button filterButton;
    /* 过滤面板（覆盖层）的按钮：只进 children、不进 renderables —— 放进 renderables 会被
       super.render() 画到底层 UI 之下。事件也要手动转发，见 mouseClicked/mouseReleased。 */
    private PressableAE2Button closeFilterButton;
    private PressableAE2Button clearFilterButton;

    private EditBox networkNameField;
    private EditBox searchField;
    private EditBox nameField;
    private EditBox weightField;
    private EditBox amountField;
    private EditBox intervalField;
    /** 数值框 + 标签 + 取值范围；标签宽度决定框的起点，范围用来做悬停提示。 */
    private final List<NumericSpec> numericFields = new ArrayList<>();

    /* ---- 过滤格的「详细编辑面板」（每格右键打开，嵌套在过滤面板之上） ----
       面板放：标记 A、模式框、包含/排除、输出端条件（B + ≥/≤ + 数值）、输入端条件、关闭。
       这些控件只 addWidget（进 children、不进 renderables），手动 render + 手动转发事件——
       理由同过滤面板的两个按钮：进 renderables 会被 super.render() 画到底层之下。 */
    private boolean detailPanelOpen;
    /** 详细面板正在编辑的格号；没打开时 -1。 */
    private int detailIndex = -1;
    private EditBox detailPatternField;
    private EditBox detailOutValueField;
    private EditBox detailInValueField;
    private PressableAE2Button detailExcludeButton;
    private PressableAE2Button detailOutOpButton;
    private PressableAE2Button detailInOpButton;
    private PressableAE2Button detailCloseButton;

    /**
     * 过滤面板（覆盖层）是否打开。
     *
     * <p>打开时独占点击与 ESC，并且<b>跳过底层界面的渲染</b>——否则底层槽位里的物品
     * 会在覆盖层之上显示（它们走 RenderBuffers 的固定缓冲，见 {@code DimensionConfigScreen}
     * 里同一问题的处理）。</p>
     */
    private boolean filterPanelOpen;

    private record NumericSpec(EditBox field, Component label, long min, long max, Component hint,
                               boolean scaled) {
    }

    /**
     * 数值框在应力线路上的替代标签与提示。
     *
     * <p>应力线路里同一行两个框的含义完全不同（目标转速、旋转方向），而标签是建框时就定死的，
     * 因此这里按框覆盖一层。替代标签刻意取得比原名短，这样画在框左边不会越过格子左边界。</p>
     */
    private record NumericOverride(Component label, Component hint) {
    }

    /** 需要替代标签的数值框；不在表里就按默认标签画。 */
    private final Map<EditBox, NumericOverride> numericOverrides = new HashMap<>();
    /** 当前数值区是不是按应力的语义在画；用来避免每帧重复设置。 */
    private boolean stressFields;
    /** 当前数值区是不是按气压的语义在画（数量格 = 目标气压 bar）；同样避免每帧重复设置。 */
    private boolean pressureFields;
    /** 应力线路的方向按钮：与周期输入框共用同一格，两者互斥显示。 */
    private PressableAE2Button stressDirectionButton;

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

        // 应力线路上这一格放的是旋转方向，用按钮比让人输 1 / 2 直观。
        // 它与周期输入框共用同一格位置，靠 visible 互斥显示。
        stressDirectionButton = addRenderableWidget(new PressableAE2Button(
                intervalField.getX(), intervalField.getY(),
                intervalField.getWidth(), intervalField.getHeight(),
                Component.empty(),
                button -> edit(config -> withNumbers(config, config.weight(), config.amount(),
                        nextStressDirection(config.interval())))));
        stressDirectionButton.visible = false;

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
                leftPos + DISSOLVE_X, topPos + FILTER_Y, DISSOLVE_WIDTH, 16,
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
        // 「应用到全部」明确只作用于<b>同名</b>容器：同名按界面显示的那个名字算。
        applyAllButton = addRenderableWidget(new PressableAE2Button(
                leftPos + APPLY_ALL_X, topPos + FILTER_Y, APPLY_ALL_WIDTH, 16,
                Component.translatable("gui.useless_mod.wireless_logistics.apply_all_same_name"),
                button -> applyAllSameName()));
        filterButton = addRenderableWidget(new PressableAE2Button(
                leftPos + FILTER_X, topPos + FILTER_Y, FILTER_BUTTON_WIDTH, 16,
                Component.empty(), button -> setFilterPanelOpen(!filterPanelOpen)));

        // 面板按钮只进 children：进 renderables 会被 super.render() 画到底层界面之下。
        closeFilterButton = addWidget(new PressableAE2Button(
                filterPanelCloseX(), filterPanelCloseY(), FILTER_PANEL_BUTTON_WIDTH, FILTER_BUTTON_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.filter_close"),
                button -> setFilterPanelOpen(false)));
        clearFilterButton = addWidget(new PressableAE2Button(
                filterPanelClearX(), filterPanelClearY(), FILTER_PANEL_BUTTON_WIDTH, FILTER_BUTTON_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.filter_clear"),
                button -> clearAllFilterSlots()));
        initDetailWidgets();
        syncFilterPanelButtons();

        // 开界面与下发快照是两个包；万一快照先到，这里把它捞回来，界面就不会空着。
        StaffLinkSyncPacket pending = ClientPacketHandlers.consumePendingStaffLinkSync(menu.getNetworkId());
        if (pending != null) {
            menu.receiveSync(pending.network(), pending.index(), pending.count());
        }

        // init() 会重建全部控件（缩放窗口也会走这里），而新的周期输入框默认是可见的。
        // 这里把「已按应力渲染过」的状态清掉，让下面的 updateControls 重新套用一次，
        // 否则缩放之后周期输入框会重新冒出来、压在方向按钮上。
        stressFields = false;
        pressureFields = false;
        numericOverrides.clear();
        updateControls();
    }

    /** 面板按钮的 visible 同时决定是否响应点击，每次开关面板都要同步。 */
    private void syncFilterPanelButtons() {
        closeFilterButton.visible = filterPanelOpen;
        clearFilterButton.visible = filterPanelOpen;
    }

    private void setFilterPanelOpen(boolean open) {
        filterPanelOpen = open;
        if (!open) {
            // 关过滤面板时把详细面板也一起收掉（会提交它），免得留下一个看不见却有焦点的框。
            closeDetailPanel();
            jeiDragActive = false;
        } else {
            // 过滤格必须挂在「某个已选中的锚点 × 线路」的配置上。开面板前先保证这份配置存在，
            // 否则 `getSelectedConfig()` 会是 null —— 界面渲染成一片灰格，JEI 那边
            // `getTargetsTyped` 也直接返回空列表，表现就是「能看见格子但拖不进去」。
            ensureRouteConfig();
        }
        syncFilterPanelButtons();
    }

    /** 「清空」：把 18 格全部置空。 */
    private void clearAllFilterSlots() {
        closeDetailPanel();
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            if (!currentFilterSlot(index).isEmpty()) {
                menu.setFilterSlot(index, LinkFilterSlot.EMPTY);
            }
        }
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

    // ---- 过滤面板：18 格 + 每格两个限制框 + 右键弹出的模式框

    /**
     * 建详细编辑面板的控件。
     *
     * <p>位置取<b>屏幕坐标</b>而不是 {@code leftPos/topPos}：它属于覆盖层面板，按窗口居中，
     * 跟主面板无关。一律先收起（{@code visible=false}）——{@link EditBox#isMouseOver} 不看
     * {@code visible}，收起时在别处要自己判断，否则收起的框照样会抢走点击。</p>
     */
    private void initDetailWidgets() {
        detailPatternField = new EditBox(font, 0, 0, DETAIL_PATTERN_WIDTH, DETAIL_ROW_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.filter_pattern_hint"));
        detailPatternField.setMaxLength(LinkFilterPattern.MAX_LENGTH);
        detailPatternField.setVisible(false);
        addWidget(detailPatternField);
        trackEdits(detailPatternField);

        detailOutValueField = addDetailValueField();
        detailInValueField = addDetailValueField();

        detailExcludeButton = addWidget(new PressableAE2Button(0, 0, DETAIL_EXCLUDE_WIDTH, DETAIL_ROW_HEIGHT,
                Component.empty(), button -> toggleDetailExclude()));
        detailOutOpButton = addWidget(new PressableAE2Button(0, 0, DETAIL_OP_WIDTH, DETAIL_ROW_HEIGHT,
                Component.empty(), button -> cycleDetailOp(false)));
        detailInOpButton = addWidget(new PressableAE2Button(0, 0, DETAIL_OP_WIDTH, DETAIL_ROW_HEIGHT,
                Component.empty(), button -> cycleDetailOp(true)));
        detailCloseButton = addWidget(new PressableAE2Button(0, 0, DETAIL_BUTTON_WIDTH, DETAIL_BUTTON_HEIGHT,
                Component.translatable("gui.useless_mod.wireless_logistics.filter_detail_done"),
                button -> closeDetailPanel()));
        setDetailWidgetsVisible(false);
    }

    /**
     * 一个只吃数字的条件数值框。
     *
     * <p>吃 {@code K / M / G / T / P / E} 后缀，跟一级菜单的「数量」同一套
     * （{@link ScaledEnergyAmount}）：搬上万的东西时不用数零。</p>
     */
    private EditBox addDetailValueField() {
        EditBox field = new EditBox(font, 0, 0, DETAIL_VALUE_WIDTH, DETAIL_ROW_HEIGHT, Component.empty());
        field.setMaxLength(FILTER_LIMIT_MAX_CHARS);
        field.setFilter(ScaledEnergyAmount::isValidInput);
        field.setVisible(false);
        addWidget(field);
        trackEdits(field);
        return field;
    }

    /** 收起 / 显示详细面板的全部控件。 */
    private void setDetailWidgetsVisible(boolean visible) {
        detailPatternField.setVisible(visible);
        detailOutValueField.setVisible(visible);
        detailInValueField.setVisible(visible);
        detailExcludeButton.visible = visible;
        detailOutOpButton.visible = visible;
        detailInOpButton.visible = visible;
        detailCloseButton.visible = visible;
    }

    /**
     * 摆位、刷新文案与显示值；每 tick 调一次（跟主界面按钮一样跟着 {@code resize()} 走）。
     *
     * <p>值用 {@link #setIfUnfocused} 同步：正在编辑的框保持不动，免得打字被打断。</p>
     */
    private void updateDetailControls() {
        if (!detailPanelOpen || detailIndex < 0) {
            setDetailWidgetsVisible(false);
            return;
        }
        int px = detailPanelX();
        int py = detailPanelY();
        detailPatternField.setX(px + DETAIL_PATTERN_X);
        detailPatternField.setY(py + DETAIL_MARKER_ROW_Y);
        detailExcludeButton.setX(px + DETAIL_EXCLUDE_X);
        detailExcludeButton.setY(py + DETAIL_MARKER_ROW_Y);
        detailOutOpButton.setX(px + DETAIL_OP_X);
        detailOutOpButton.setY(py + DETAIL_OUT_ROW_Y);
        detailOutValueField.setX(px + DETAIL_VALUE_X);
        detailOutValueField.setY(py + DETAIL_OUT_ROW_Y);
        detailInOpButton.setX(px + DETAIL_OP_X);
        detailInOpButton.setY(py + DETAIL_IN_ROW_Y);
        detailInValueField.setX(px + DETAIL_VALUE_X);
        detailInValueField.setY(py + DETAIL_IN_ROW_Y);
        detailCloseButton.setX(px + (DETAIL_PANEL_WIDTH - DETAIL_BUTTON_WIDTH) / 2);
        detailCloseButton.setY(py + DETAIL_BUTTON_Y);

        LinkFilterSlot slot = currentFilterSlot(detailIndex);
        boolean hasMarker = !slot.isEmpty();
        LinkFilterCondition out = slot.outCond();
        LinkFilterCondition in = slot.inCond();

        detailPatternField.setVisible(true);
        detailCloseButton.visible = true;
        detailExcludeButton.visible = hasMarker;
        detailOutOpButton.visible = hasMarker;
        detailInOpButton.visible = hasMarker;
        detailOutValueField.setVisible(hasMarker && !out.isOff());
        detailInValueField.setVisible(hasMarker && !in.isOff());

        detailExcludeButton.active = hasMarker;
        detailOutOpButton.active = hasMarker;
        detailInOpButton.active = hasMarker;
        detailOutValueField.setEditable(hasMarker && !out.isOff());
        detailInValueField.setEditable(hasMarker && !in.isOff());

        detailExcludeButton.setMessage(Component.translatable(slot.isExcluded()
                ? "gui.useless_mod.wireless_logistics.filter_exclude_on"
                : "gui.useless_mod.wireless_logistics.filter_exclude_off"));
        detailOutOpButton.setMessage(opLabel(out.op()));
        detailInOpButton.setMessage(opLabel(in.op()));

        setIfUnfocused(detailPatternField, slot.pattern() == null ? "" : slot.pattern());
        setIfUnfocused(detailOutValueField, conditionText(out));
        setIfUnfocused(detailInValueField, conditionText(in));
    }

    private static Component opLabel(LinkFilterCondition.Op op) {
        return Component.translatable(switch (op) {
            case OFF -> "gui.useless_mod.wireless_logistics.filter_cond_op_off";
            case AT_LEAST -> "gui.useless_mod.wireless_logistics.filter_cond_op_at_least";
            case AT_MOST -> "gui.useless_mod.wireless_logistics.filter_cond_op_at_most";
        });
    }

    /** 条件数值的显示文本：不启用时留空。 */
    private static String conditionText(LinkFilterCondition cond) {
        return cond.isOff() ? "" : limitText(cond.value());
    }

    /**
     * {@code 0} 在语义上是「不限制」，界面上就该是留空。
     *
     * <p>非零值复用一级菜单的 {@link #formatAmount}：<b>只在缩写能原样解析回来时</b>才缩写
     * （1000 → {@code 1K}），像 1024 这种缩写会丢精度的（{@code 1.02K} 只能解析回 1020）
     * 就照原样显示数字。否则玩家点一下别的框触发提交，限制值就被悄悄改小了。</p>
     */
    private static String limitText(long value) {
        return value > 0L ? formatAmount(value) : "";
    }

    private static void setIfUnfocused(EditBox field, String value) {
        if (!field.isFocused() && !value.equals(field.getValue())) {
            field.setValue(value);
        }
    }

    // ---- 详细编辑面板

    /** 打开某一格的详细面板（会先把上一格未提交的编辑落下去）。 */
    private void openDetailPanel(int index) {
        if (index < 0 || index >= StaffLinkRoute.FILTER_LIMIT || !menu.isFilterActive()) {
            return;
        }
        if (detailPanelOpen && detailIndex == index) {
            return;
        }
        if (detailPanelOpen) {
            commitDetail();
        }
        detailPanelOpen = true;
        detailIndex = index;
        setFocused(null);
        updateDetailControls();
    }

    /** 关闭详细面板（会提交它）。 */
    private void closeDetailPanel() {
        if (!detailPanelOpen) {
            return;
        }
        commitDetail();
        detailPanelOpen = false;
        detailIndex = -1;
        setFocused(null);
        setDetailWidgetsVisible(false);
    }

    /**
     * 按详细面板当前控件的内容，把「镜像里的那一格」重读一遍。
     *
     * <p>读的是<b>控件</b>而不是镜像：这样点 op / 排除按钮时，输入框里还没失焦提交的数值与模式
     * 不会丢。标记本身仍以镜像为准（它只能通过左键 / JEI 放置）。</p>
     *
     * @return 组装好的格子；模式非法时返回 {@code null}（已弹提示）
     */
    @Nullable
    private LinkFilterSlot readDetailSlot() {
        LinkFilterSlot slot = currentFilterSlot(detailIndex);
        if (slot.isEmpty()) {
            return LinkFilterSlot.EMPTY;
        }
        String text = detailPatternField.getValue().trim();
        if (!text.equals(slot.pattern() == null ? "" : slot.pattern())) {
            if (text.isEmpty()) {
                return LinkFilterSlot.EMPTY;
            }
            LinkFilterPattern parsed = LinkFilterPattern.parse(text);
            if (parsed == null) {
                showNotice(Component.translatable(
                        "gui.useless_mod.wireless_logistics.filter_pattern_invalid", text));
                return null;
            }
            slot = slot.withMarker(LinkFilterSlot.ofPattern(parsed));
        }
        slot = applyDetailValue(slot, false);
        return applyDetailValue(slot, true);
    }

    /** 把某一条条件的数值框内容读进来（只在该条件启用时）。 */
    private LinkFilterSlot applyDetailValue(LinkFilterSlot slot, boolean input) {
        LinkFilterCondition cond = input ? slot.inCond() : slot.outCond();
        if (cond.isOff()) {
            return slot;
        }
        EditBox field = input ? detailInValueField : detailOutValueField;
        long value = Math.max(0L, parseLimit(field.getValue(), cond.value()));
        if (value == cond.value()) {
            return slot;
        }
        LinkFilterCondition next = cond.withValue(value);
        return input ? slot.withConditions(slot.outCond(), next)
                : slot.withConditions(next, slot.inCond());
    }

    /** 把详细面板里未提交的编辑落成一次 {@code setFilterSlot}（只在真有变化时才发）。 */
    private void commitDetail() {
        if (!detailPanelOpen || detailIndex < 0) {
            return;
        }
        LinkFilterSlot slot = readDetailSlot();
        if (slot == null) {
            return;
        }
        if (slot.isEmpty()) {
            if (!currentFilterSlot(detailIndex).isEmpty()) {
                menu.setFilterSlot(detailIndex, LinkFilterSlot.EMPTY);
            }
            return;
        }
        if (!slot.equals(currentFilterSlot(detailIndex))) {
            menu.setFilterSlot(detailIndex, slot);
        }
    }

    /** 在详细面板上叠加一次改动（op / 排除），并把输入框里未提交的值一起带上。 */
    private void editDetail(java.util.function.UnaryOperator<LinkFilterSlot> change) {
        if (!detailPanelOpen || detailIndex < 0) {
            return;
        }
        LinkFilterSlot slot = readDetailSlot();
        if (slot == null || slot.isEmpty()) {
            return;
        }
        LinkFilterSlot updated = change.apply(slot);
        if (!updated.equals(currentFilterSlot(detailIndex))) {
            menu.setFilterSlot(detailIndex, updated);
        }
    }

    /** 切换这一格的「包含 / 排除」。 */
    private void toggleDetailExclude() {
        editDetail(slot -> slot.withExclude(!slot.isExcluded()));
    }

    /**
     * 循环这一格某条条件的方向：关 → ≥ → ≤ → 关。
     *
     * <p>从「关」启用时给一个符合直觉的默认：输出端先给 {@code ≥}（保留），输入端先给 {@code ≤}（封顶）。</p>
     */
    private void cycleDetailOp(boolean input) {
        editDetail(slot -> {
            LinkFilterCondition cond = input ? slot.inCond() : slot.outCond();
            LinkFilterCondition.Op next = switch (cond.op()) {
                case OFF -> input ? LinkFilterCondition.Op.AT_MOST : LinkFilterCondition.Op.AT_LEAST;
                case AT_LEAST -> LinkFilterCondition.Op.AT_MOST;
                case AT_MOST -> LinkFilterCondition.Op.OFF;
            };
            LinkFilterCondition updated = cond.withOp(next);
            return input ? slot.withConditions(slot.outCond(), updated)
                    : slot.withConditions(updated, slot.inCond());
        });
    }

    // ---- 详细面板里的槽位点击

    /** 详细面板里的 A 标记槽：手持物放下，空手清空这一格。 */
    private void clickDetailMarkerSlot() {
        LinkFilterSlot slot = readDetailSlot();
        if (slot == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            menu.setFilterSlot(detailIndex, LinkFilterSlot.EMPTY);
            closeDetailPanel();
            return;
        }
        LinkFilterSlot marker = StaffLinkFilters.fromItem(menu.getSelectedMedium(), carried);
        if (marker != null) {
            menu.setFilterSlot(detailIndex, slot.withMarker(marker));
        }
    }

    /**
     * 详细面板里的 B 控制材料槽：手持物放下，空手清回「测 A 自身」。
     *
     * <p>条件没启用（OFF）时槽位是灰的，点了不生效——先点 op 按钮把它打开。</p>
     */
    private void clickDetailControlSlot(boolean input) {
        LinkFilterCondition cond = input ? currentFilterSlot(detailIndex).inCond()
                : currentFilterSlot(detailIndex).outCond();
        if (cond.isOff()) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            menu.setFilterSlotConditionControl(detailIndex, input, LinkFilterSlot.EMPTY);
            return;
        }
        LinkFilterSlot marker = StaffLinkFilters.fromItem(menu.getSelectedMedium(), carried);
        if (marker != null) {
            menu.setFilterSlotConditionControl(detailIndex, input, marker);
        }
    }

    /** 详细面板里的点击：按钮 → 输入框 → A/B 槽 → 玩家背包 → 面板外吞掉。 */
    private boolean handleDetailPanelClick(double mouseX, double mouseY, int button) {
        if (detailCloseButton.mouseClicked(mouseX, mouseY, button)
                || detailExcludeButton.mouseClicked(mouseX, mouseY, button)
                || detailOutOpButton.mouseClicked(mouseX, mouseY, button)
                || detailInOpButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (detailPatternField.visible && detailPatternField.isMouseOver(mouseX, mouseY)) {
                focusDetailField(detailPatternField, mouseX, mouseY);
                return true;
            }
            if (detailOutValueField.visible && detailOutValueField.isMouseOver(mouseX, mouseY)) {
                focusDetailField(detailOutValueField, mouseX, mouseY);
                return true;
            }
            if (detailInValueField.visible && detailInValueField.isMouseOver(mouseX, mouseY)) {
                focusDetailField(detailInValueField, mouseX, mouseY);
                return true;
            }
            if (inDetailSlot(mouseX, mouseY, 0)) {
                clickDetailMarkerSlot();
                updateDetailControls();
                return true;
            }
            if (inDetailSlot(mouseX, mouseY, 1)) {
                clickDetailControlSlot(false);
                updateDetailControls();
                return true;
            }
            if (inDetailSlot(mouseX, mouseY, 2)) {
                clickDetailControlSlot(true);
                updateDetailControls();
                return true;
            }
        }
        // 玩家背包槽：面板开着时也允许正常「拿起 / 放下」——不然只能靠 JEI 拖。
        if (inventorySlotAt(mouseX, mouseY) != null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 点在面板空白处：让输入框失焦，但不关面板。
        setFocused(null);
        return true;
    }

    private boolean inDetailSlot(double mouseX, double mouseY, int which) {
        int x = detailSlotScreenX(which);
        int y = detailSlotScreenY(which);
        return mouseX >= x && mouseX < x + DETAIL_SLOT_SIZE
                && mouseY >= y && mouseY < y + DETAIL_SLOT_SIZE;
    }

    /**
     * 让详细面板里的输入框获得焦点。
     *
     * <p>跟过滤面板一样：覆盖层这条路径到不了框架的 {@code mouseClicked}，得自己补
     * {@code setFocused} + {@code field.mouseClicked}（摆光标）两步。</p>
     */
    private void focusDetailField(EditBox field, double mouseX, double mouseY) {
        if (getFocused() != field) {
            commitDetail();
        }
        setFocused(field);
        field.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** 当前在镜子里的第 index 格；越界或没有选中线路时回落到 {@link LinkFilterSlot#EMPTY}。 */
    private LinkFilterSlot currentFilterSlot(int index) {
        List<LinkFilterSlot> mirror = menu.getFilterMirror();
        return index >= 0 && index < mirror.size() ? mirror.get(index) : LinkFilterSlot.EMPTY;
    }

    /**
     * 空串 = 0（不限制）；写歪了回落到原值，不悄悄改配置。
     *
     * <p>和「数量」一样吃 {@code K / M / G / T / P / E} 后缀，走同一套
     * {@link ScaledEnergyAmount#parse}。</p>
     */
    private static long parseLimit(String text, long fallback) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        OptionalLong parsed = ScaledEnergyAmount.parse(text, Long.MAX_VALUE);
        return parsed.isPresent() ? parsed.getAsLong() : fallback;
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
        // 详细面板：摆位 + 显示值对齐（有焦点的框保持不动），每 tick 一次。
        updateDetailControls();
        // resize() 会重建控件，按钮的屏幕坐标要跟着走；顺便保证可见性跟着面板状态。
        if (filterPanelOpen) {
            closeFilterButton.setX(filterPanelCloseX());
            closeFilterButton.setY(filterPanelCloseY());
            clearFilterButton.setX(filterPanelClearX());
            clearFilterButton.setY(filterPanelClearY());
        }
        syncFilterPanelButtons();
    }

    /** 界面收到服务端快照。 */
    public void receiveSync(StaffLinkSyncPacket packet) {
        menu.receiveSync(packet.network(), packet.index(), packet.count());
        clampScroll();
        updateControls();
    }

    @Override
    public void onClose() {
        applyEdits();
        // 应力状态是服务端按「谁开着界面」推下来的，关掉就该忘掉，
        // 免得下次打开界面先闪一屏上一次的旧数字。
        StaffLinkStressClientState.clear();
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
        long amount = config.medium().family() == ResourceFamily.PRESSURE
                ? parsePressureMbar(amountField.getValue(), config.amount())
                : parseScaled(amountField.getValue(), config.amount(), StaffLinkRoute.MIN_AMOUNT);
        long interval = parsePlain(intervalField.getValue(), config.interval(),
                StaffLinkRoute.MIN_INTERVAL, StaffLinkRoute.MAX_INTERVAL);
        if (weight != config.weight() || amount != config.amount() || interval != config.interval()) {
            menu.applyRoute(withNumbers(config, (int) weight, amount, (int) interval));
        }
        // 详细面板里被改过、还没失焦提交的条件 / 模式。
        commitDetail();
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

    /**
     * 「目标气压」输入框：界面按 <b>bar</b> 输入，内部按毫巴（{@code amount}）。
     *
     * <p>解析不了（空串 / 只输了个负号）就回落到原值，与 {@link #parseScaled} 一致；
     * 解析成功则夹到 {@code [-1.0, 20.0]} bar——气动的物理下限是 -1 bar 的绝对真空。</p>
     */
    private static long parsePressureMbar(String text, long fallback) {
        try {
            double bar = Double.parseDouble(text.trim());
            return Math.round(Mth.clamp(bar, -1.0D, 20.0D) * 1000.0D);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** 毫巴 → bar 的显示形式；整 bar 不带小数点。 */
    private static String formatPressureMbar(long mbar) {
        return mbar % 1000L == 0L
                ? String.valueOf(mbar / 1000L)
                : String.valueOf(mbar / 1000.0D);
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
        // 「过滤」按钮始终可点（面板里能看到 18 空格的情况，也方便玩家确认当前资源类型吃不吃过滤器）。
        filterButton.active = true;
        filterButton.setMessage(Component.translatable(
                hasConfig ? "gui.useless_mod.wireless_logistics.filter_button"
                        : "gui.useless_mod.wireless_logistics.filter_button_none",
                filterFilledCount()));
        networkNameField.setEditable(true);
        nameField.setEditable(hasAnchor);
        // 一般介质是「释放端发起搬运」，所以「数量 / 周期」只在释放端可编辑，吸收端的禁掉以免误解。
        // 应力<b>正好相反</b>：它的目标转速与旋转方向是每个输出端各自的事（同一条线路上
        // 不同机器可以转不同的速度），所以这两个框配在吸收端。
        // 气压同理：这一格是「接收端要调到多少气压」，属于接收端，所以也配在吸收端。
        // （「权重」对两端都有意义：输入端之间排序、输出端之间排序都看它。）
        boolean initiates = hasConfig && config.flow() == LinkFlow.RELEASE;
        boolean stress = hasConfig && config.medium().family() == ResourceFamily.STRESS;
        boolean pressure = hasConfig && config.medium().family() == ResourceFamily.PRESSURE;
        boolean numbersEditable = (stress || pressure) ? !initiates : initiates;
        weightField.active = hasConfig;
        amountField.active = numbersEditable;
        // 应力时这一格被方向按钮顶掉，输入框整个隐起来（它的值仍由同步逻辑维持，按钮改的就是它）。
        intervalField.active = numbersEditable && !stress;
        weightField.setEditable(hasConfig);
        amountField.setEditable(numbersEditable);
        intervalField.setEditable(numbersEditable && !stress);
        applyNumericSemantics(stress, pressure);
        if (stressDirectionButton != null) {
            stressDirectionButton.visible = stress && hasConfig;
            stressDirectionButton.active = stress && hasConfig && numbersEditable;
            if (stress) {
                stressDirectionButton.setMessage(Component.translatable(
                        config.interval() == StaffLinkRoute.STRESS_COUNTER_CLOCKWISE
                                ? "gui.useless_mod.wireless_logistics.stress.counter_clockwise"
                                : "gui.useless_mod.wireless_logistics.stress.clockwise"));
            }
        }

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
            if (!amountField.isFocused()) setFieldValue(amountField,
                    config.medium().family() == ResourceFamily.PRESSURE
                            ? formatPressureMbar(config.amount())
                            : formatAmount(config.amount()));
            if (!intervalField.isFocused()) setFieldValue(intervalField, String.valueOf(config.interval()));
        }

        String signature = hasConfig
                ? config.flow() + "|" + config.medium() + "|" + config.enabled() + "|" + config.side()
                  + "|" + menu.getSelectedRoute()
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
        for (Slot slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    /**
     * 画过滤面板（覆盖层）。
     *
     * <p>只负责「画」，不负责事件：面板上的按钮是 {@code addWidget} 进来的（不在 renderables 里），
     * 由 {@link #render} 在这里手动调 {@code render}，点击则由 {@code mouseClicked/mouseReleased}
     * 手动转发。</p>
     *
     * <p>面板上的一切都按<b>屏幕坐标</b>画——它跟主面板位置无关，直接居中于窗口。</p>
     */
    private void renderFilterPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = filterPanelX();
        int panelY = filterPanelY();
        boolean active = menu.isFilterActive();

        MachineScreenStyle.drawPanel(graphics, panelX, panelY, FILTER_PANEL_WIDTH, FILTER_PANEL_HEIGHT);

        Component heading = Component.translatable("gui.useless_mod.wireless_logistics.filter_panel_title",
                filterFilledCount());
        graphics.drawString(font, heading, panelX + FILTER_PANEL_PAD, panelY + 6,
                MachineScreenStyle.TEXT_COLOR, false);
        // 面板只有 140 宽，标题 + 提示放不下一行，所以提示换到第二行（面板高度里已经留了位置）。
        Component hint = Component.translatable("gui.useless_mod.wireless_logistics.filter_panel_hint");
        graphics.drawString(font, hint, panelX + FILTER_PANEL_PAD, panelY + 16,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);

        // 面板切出来时底层 UI 已经被盖掉，所以要先把面板底子落地，
        // 免得下面 fill 出来的格子背景和先前那批混在一个缓冲里被重新排序。
        graphics.flush();

        // 详细面板开着时它整片盖住了 18 格，网格不必再画（也省得物品的 z 偏移穿透上来）。
        if (!detailPanelOpen) {
            renderFilterSlotBackgrounds(graphics, panelX, panelY, active);
            // JEI 拖拽悬停高亮要压在槽位底色之上，所以放这里。
            renderFilterDragHover(graphics, mouseX, mouseY);
            renderFilterItems(graphics);
            // 角标（排除 / 有条件）画在物品之上，先入批再落地。
            renderFilterBadges(graphics);
            graphics.flush();
        }

        closeFilterButton.render(graphics, mouseX, mouseY, 0.0F);
        clearFilterButton.render(graphics, mouseX, mouseY, 0.0F);

        // 面板自己的一套悬停提示；不走 super.render()，所以得单独调。
        // 详细面板开着时它整个压住了这两个按钮，按钮提示也别画（否则会浮在详细面板上）。
        renderFilterTooltip(graphics, mouseX, mouseY);
        if (!detailPanelOpen) {
            renderFilterButtonTooltip(graphics, mouseX, mouseY);
        }
    }

    /** 18 格的底板 + 有标记那一格的顶面高光 + 详细面板正在编辑的那一格描边。 */
    private void renderFilterSlotBackgrounds(GuiGraphics graphics, int panelX, int panelY, boolean active) {
        int fill = active ? MachineScreenStyle.SLOT_COLOR : 0xFF777B8D;
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            int x = filterSlotScreenX(index);
            int y = filterSlotScreenY(index);
            graphics.fill(x, y, x + FILTER_SLOT_SIZE, y + FILTER_SLOT_SIZE, fill);
            if (active) {
                graphics.fill(x, y, x + FILTER_SLOT_SIZE, y + 1, MachineScreenStyle.SLOT_SHADOW_COLOR);
            }
        }
        // 详细面板正在编辑的那一格描边——面板里空格子之间留白不少，不圈一下容易看错行。
        if (detailPanelOpen && detailIndex >= 0 && detailIndex < StaffLinkRoute.FILTER_LIMIT) {
            int x = filterSlotScreenX(detailIndex);
            int y = filterSlotScreenY(detailIndex);
            outline(graphics, x - 1, y - 1, FILTER_SLOT_SIZE + 2, FILTER_SLOT_SIZE + 2,
                    MULTI_SELECT_TEXT_COLOR);
        }
    }

    /**
     * 每格的状态角标：右上角红 {@code x} = 这一格是「排除」；右下角青点 = 这一格带了控制条件。
     *
     * <p>角标是必要的：格子本身只画标记，光看图标分不出「包含」还是「排除」，也看不出有没有条件。</p>
     */
    private void renderFilterBadges(GuiGraphics graphics) {
        if (!menu.isFilterActive()) {
            return;
        }
        List<LinkFilterSlot> mirror = menu.getFilterMirror();
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            LinkFilterSlot slot = index < mirror.size() ? mirror.get(index) : LinkFilterSlot.EMPTY;
            if (slot.isEmpty()) {
                continue;
            }
            int x = filterSlotScreenX(index);
            int y = filterSlotScreenY(index);
            if (slot.isExcluded()) {
                graphics.drawString(font, "x", x + FILTER_SLOT_SIZE - 6, y - 1,
                        MachineScreenStyle.ERROR_TEXT_COLOR, false);
            }
            if (slot.hasConditions()) {
                graphics.fill(x + FILTER_SLOT_SIZE - 5, y + FILTER_SLOT_SIZE - 5,
                        x + FILTER_SLOT_SIZE - 1, y + FILTER_SLOT_SIZE - 1,
                        MULTI_SELECT_TEXT_COLOR);
            }
        }
    }

    /**
     * JEI 拖拽悬停时，把鼠标底下那一格圈出来。
     *
     * <p><b>为什么不用 JEI 自己的高亮</b>：JEI 的目标高亮画在
     * {@code ContainerScreenEvent.Render.Foreground} 里，那是 {@code super.render()} 阶段、
     * z=0 的一批；而过滤面板是在 {@code super.render()}<b>之后</b>、抬到
     * {@link #FILTER_PANEL_Z} 才画的 —— JEI 那圈高亮会被面板整个盖住，
     * 玩家看不到任何「这一格能放」的反馈（功能其实是好的，只是看不见）。
     * 所以这里在面板同一层再画一遍。</p>
     */
    private void renderFilterDragHover(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!jeiDragActive) {
            return;
        }
        int index = filterSlotAtScreen(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        int x = filterSlotScreenX(index);
        int y = filterSlotScreenY(index);
        graphics.fill(x, y, x + FILTER_SLOT_SIZE, y + FILTER_SLOT_SIZE, 0x80FFFFFF);
        outline(graphics, x - 1, y - 1, FILTER_SLOT_SIZE + 2, FILTER_SLOT_SIZE + 2,
                MachineScreenStyle.HIGHLIGHT_COLOR);
    }

    /** 屏幕坐标命中的过滤格下标；没命中返回 -1。 */
    private int filterSlotAtScreen(double mouseX, double mouseY) {
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            int x = filterSlotScreenX(index);
            int y = filterSlotScreenY(index);
            if (mouseX >= x && mouseX < x + FILTER_SLOT_SIZE
                    && mouseY >= y && mouseY < y + FILTER_SLOT_SIZE) {
                return index;
            }
        }
        return -1;
    }

    /** 有标记的格数，标题里用。 */
    private int filterFilledCount() {
        int count = 0;
        for (LinkFilterSlot slot : menu.getFilterMirror()) {
            if (!slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** 悬停面板底部两个按钮时的一句话说明。 */
    private void renderFilterButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        String key;
        if (closeFilterButton.visible && closeFilterButton.isMouseOver(mouseX, mouseY)) {
            key = "filter_close_hint";
        } else if (clearFilterButton.visible && clearFilterButton.isMouseOver(mouseX, mouseY)) {
            key = "filter_clear_hint";
        } else {
            return;
        }
        graphics.renderTooltip(font,
                List.of(Component.translatable("gui.useless_mod.wireless_logistics." + key)),
                Optional.empty(), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        renderNetworkLabel(graphics);
        renderAnchorList(graphics);
        renderNumericLabels(graphics);
        renderStressStatus(graphics);
        renderAeWhitelistWarning(graphics);
        renderSelectionCount(graphics);
        renderNotice(graphics);
    }

    /**
     * Req3 的界面警告：源端是 AE、目标端又没有白名单时，这条线路一个物品都不会搬。
     *
     * <p>这条规则不看就完全看不出原因（线路是启用的、类型也对得上），所以必须在按钮那一行上方
     * 明说，否则玩家只会觉得「这机器坏了」。</p>
     */
    private void renderAeWhitelistWarning(GuiGraphics graphics) {
        StaffLinkRoute config = menu.getSelectedConfig();
        if (config == null) {
            return;
        }
        // blocksAeSourceWithoutWhitelist 内部已经处理了「本资源类型根本不吃过滤器」这件事
        // （能量 / 魔源恒 false —— 它们从网络出来只有一种，不需要白名单，也标记不上），
        // 这里只补「源端是不是 AE」。
        if (config.medium().isAe() && config.blocksAeSourceWithoutWhitelist()) {
            Component text = Component.translatable(
                    "gui.useless_mod.wireless_logistics.ae_whitelist_warning");
            graphics.drawString(font, text, CONTENT_LEFT, FILTER_Y - 11,
                    MachineScreenStyle.ERROR_TEXT_COLOR, false);
        }
    }

    /** 标题行右侧的一次性提示（复制/粘贴等）；{@link #NOTICE_TICKS} tick 后自己消失。 */
    private void renderNotice(GuiGraphics graphics) {
        if (notice == null) {
            return;
        }
        graphics.drawString(font, notice, CONTENT_RIGHT - font.width(notice), titleLabelY,
                NOTICE_COLOR, false);
    }

    /** 批量编辑计数：多选非空时在按钮行上方右对齐提示选了几台。 */
    private void renderSelectionCount(GuiGraphics graphics) {
        int count = menu.getMultiSelection().size();
        if (count <= 0) {
            return;
        }
        Component text = Component.translatable(
                "gui.useless_mod.wireless_logistics.multi_select_count", count);
        // 和 Req3 警告同一行：一个左对齐、一个右对齐，互不打架。
        graphics.drawString(font, text, CONTENT_RIGHT - font.width(text), FILTER_Y - 11,
                MULTI_SELECT_TEXT_COLOR, false);
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
            // 箭头方向按「相对这个名字所在行」看：输出端 < 指向外侧（发出去）、输入端 > 指向名字（收进来）。
            StaffLinkRoute routeConfig = menu.getConfig(anchor, menu.getSelectedRoute());
            String glyph = routeConfig == null ? "-" : routeConfig.flow() == LinkFlow.RELEASE ? "<" : ">";
            int glyphColor;
            if (routeConfig == null || !routeConfig.enabled()) {
                glyphColor = MachineScreenStyle.MUTED_TEXT_COLOR;
            } else if (routeConfig.flow() == LinkFlow.RELEASE) {
                glyphColor = 0xFF2E7D32;
            } else {
                glyphColor = 0xFF3B6EA5;
            }
            graphics.drawString(font, glyph, LIST_GLYPH_X, y, glyphColor, false);

            // 应力线路上行尾还要放一个「这一台自己」的应力量，名字预算里先给它留出位置。
            // 用紧凑写法（8.2k）—— 13px 高的行放不下长数字。
            StaffLinkStressStatusPacket.Entry stress = null;
            int valueWidth = 0;
            if (routeConfig != null && routeConfig.medium().family() == ResourceFamily.STRESS) {
                stress = StaffLinkStressClientState.find(
                        menu.getNetworkId(), anchor, menu.getSelectedRoute());
                valueWidth = STRESS_ROW_VALUE_WIDTH;
            }

            // 名字后面缀上坐标：多个同名容器（都叫「箱子」）光看名字根本分不出来。
            // 额外预留 8px 给行尾的批量 ✓，无条件预留，免得勾选/取消时名字左右跳。
            String coord = anchor.pos().toShortString();
            int nameBudget = Math.max(24,
                    CONTENT_WIDTH - LIST_TEXT_SHIFT - LIST_UNBIND_WIDTH - 16 - 8 - valueWidth
                            - font.width(coord));
            String name = font.plainSubstrByWidth(anchorDisplayName(anchor).getString(), nameBudget);
            graphics.drawString(font, name, LIST_NAME_X, y, MachineScreenStyle.TEXT_COLOR, false);
            graphics.drawString(font, coord, LIST_NAME_X + font.width(name) + 4, y,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            if (stress != null) {
                // 一行一台、各看各的：源看「这一台提供了多少」，目标看「这一台需要多少」。
                // 整条线路的合计在配置区的状态行里，两处分工不同。
                String value = formatStressCompact(stress.local().supplied());
                graphics.drawString(font, value,
                        CONTENT_RIGHT - LIST_UNBIND_WIDTH - 8 - 4 - font.width(value), y,
                        MachineScreenStyle.MUTED_TEXT_COLOR, false);
            }
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

    /**
     * 按资源类型切换数值区的语义。
     *
     * <p>应力线路上「数量 / 周期」两个格子改叫「转速 / 方向」：转速是纯整数（不再接受
     * K / M / G 这类缩写），方向只认 1 / 2，而且方向那一格整个换成按钮。</p>
     *
     * <p>气压线路上「数量」改叫「目标气压」：界面按 <b>bar</b> 显示与输入（-1.0 ~ 20.0），
     * 内部仍存毫巴（{@code amount}），因此过滤器要接受小数与负号。</p>
     *
     * <p>只在语义真的变了的时候动手：这些设置会清掉输入焦点与光标位置，每帧重设等于让人没法打字。</p>
     */
    private void applyNumericSemantics(boolean stress, boolean pressure) {
        if (stressFields == stress && pressureFields == pressure) {
            return;
        }
        stressFields = stress;
        pressureFields = pressure;
        if (stress) {
            numericOverrides.put(amountField, new NumericOverride(
                    Component.translatable("gui.useless_mod.wireless_logistics.stress.rpm_label"),
                    Component.translatable("gui.useless_mod.wireless_logistics.stress.rpm_hint")));
            amountField.setMaxLength(6);
            amountField.setFilter(value -> value.isEmpty() || value.matches("\\d{0,6}"));
            intervalField.setMaxLength(1);
            intervalField.setFilter(value -> value.isEmpty() || value.matches("[12]?"));
            intervalField.visible = false;
        } else if (pressure) {
            numericOverrides.put(amountField, new NumericOverride(
                    Component.translatable("gui.useless_mod.wireless_logistics.pressure.target_label"),
                    Component.translatable("gui.useless_mod.wireless_logistics.pressure.target_hint")));
            amountField.setMaxLength(6);
            amountField.setFilter(value -> value.isEmpty()
                    || value.matches("-?\\d{0,2}(\\.\\d{0,2})?"));
            intervalField.setMaxLength(6);
            intervalField.setFilter(value -> value.isEmpty() || value.matches("\\d{0,5}"));
            intervalField.visible = true;
        } else {
            numericOverrides.remove(amountField);
            amountField.setMaxLength(24);
            amountField.setFilter(ScaledEnergyAmount::isValidInput);
            intervalField.setMaxLength(6);
            intervalField.setFilter(value -> value.isEmpty() || value.matches("\\d{0,5}"));
            intervalField.visible = true;
        }
    }

    private Component numericLabel(NumericSpec spec) {
        NumericOverride override = numericOverrides.get(spec.field());
        return override == null ? spec.label() : override.label();
    }

    private Component numericHint(NumericSpec spec) {
        NumericOverride override = numericOverrides.get(spec.field());
        return override == null ? spec.hint() : override.hint();
    }

    private void renderNumericLabels(GuiGraphics graphics) {
        for (NumericSpec spec : numericFields) {
            if (!spec.field().visible) {
                continue;
            }
            Component label = numericLabel(spec);
            int labelX = spec.field().getX() - leftPos - font.width(label) - 4;
            graphics.drawString(font, label, labelX, ROW_FOURTH_Y + 4,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
            if (!spec.field().active) {
                // 不生效的输入框压一层暗色，一眼能看出来它不可编辑。
                graphics.fill(spec.field().getX(), spec.field().getY(),
                        spec.field().getX() + spec.field().getWidth(),
                        spec.field().getY() + spec.field().getHeight(), 0x66000000);
            }
        }
        // 应力时周期框被方向按钮顶掉，它的标签在这里单独补一次：按钮上写的是当前方向，
        // 而「方向」这两个字才是告诉玩家这一格是什么的那个标签。
        if (stressFields && stressDirectionButton != null && stressDirectionButton.visible) {
            Component direction = Component.translatable(
                    "gui.useless_mod.wireless_logistics.stress.direction_label");
            int labelX = stressDirectionButton.getX() - leftPos - font.width(direction) - 4;
            graphics.drawString(font, direction, labelX, ROW_FOURTH_Y + 4,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
    }

    /**
     * 应力线路的运行状态：源网络能提供多少、目标网络需要多少、这一端现在怎么样。
     *
     * <p>这些是服务端每 20 tick 推下来的<b>运行时数字</b>，不属于线路配置，因此整网快照里没有它们。
     * 还没收到过就什么都不画——显示一排 0 只会让人以为线路坏了。</p>
     *
     * <p>第二行只在出问题时才换成醒目的琥珀色：玩家最需要一眼看到的就是「为什么没转」。</p>
     */
    private void renderStressStatus(GuiGraphics graphics) {
        StaffLinkRoute config = menu.getSelectedConfig();
        GlobalPos anchor = menu.getSelectedAnchor();
        if (config == null || anchor == null || config.medium().family() != ResourceFamily.STRESS) {
            return;
        }
        StaffLinkStressStatusPacket.Entry entry =
                StaffLinkStressClientState.find(menu.getNetworkId(), anchor, config.route());
        if (entry == null) {
            // 线路关着的时候服务端根本不会为它记状态（桥只跑已启用的线路），快照里自然没有它。
            // 这时补一句「已关闭」——否则玩家看到一片空白，很容易以为功能坏了。
            if (!config.enabled()) {
                graphics.drawString(font, Component.translatable(
                                "gui.useless_mod.wireless_logistics.stress.status",
                                Component.translatable(
                                        "gui.useless_mod.wireless_logistics.stress.state.disabled")),
                        CONTENT_LEFT, STRESS_STATUS_Y, MachineScreenStyle.MUTED_TEXT_COLOR, false);
            }
            return;
        }
        // 第一行三个数，两种角色口径一致：
        //   自身     —— 这一台自己提供 / 消耗多少（源看它的容量 × 生成转速，目标看它的耗力 × 转速）
        //   网络总计 —— 这条线路上所有源网络能提供的容量合计
        //   剩余     —— 这条链路还能借出去多少（源网络自身负载扣掉之后的富余）
        // 「自身」与「网络总计」分开显示，是因为同一张网络上并了好几个源 / 好几台机器时，
        // 这两个数不是一回事。
        Component numbers = Component.translatable(
                "gui.useless_mod.wireless_logistics.stress.status_breakdown",
                formatStress(entry.local().supplied()),
                formatStress(entry.line().supplied()),
                formatStress(entry.line().extra()));
        graphics.drawString(font, numbers, CONTENT_LEFT, STRESS_STATUS_Y,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);

        Component state = Component.translatable("gui.useless_mod.wireless_logistics.stress.status",
                stressStateName(entry.state()));
        graphics.drawString(font, state, CONTENT_LEFT, STRESS_STATUS_Y + 9,
                isStressWarning(entry.state()) ? STRESS_WARN_COLOR : MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    /**
     * 这个状态码是「出问题了」还是「只是说明情况」。
     *
     * <p>服务端把状态码当普通字符串发过来，所以这里只能按字面认。
     * {@code fed_elsewhere}（应力由其它无线输出补足）与 {@code self_fed}（网络自身有动力）
     * 都说明机器转得好好的，用醒目的琥珀色会让人以为坏了。</p>
     */
    private static boolean isStressWarning(@Nullable String state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        return !"fed_elsewhere".equals(state) && !"self_fed".equals(state);
    }

    /** 应力量的显示：太大就写无穷，整数就不带小数点，否则保留一位小数。 */
    private static String formatStress(float value) {
        if (value >= 1.0E9F) {
            return "\u221e";
        }
        // 650.0 写成「650」而不是「650.0」——「注入 0.0」这种显示看起来像是坏了。
        if (value >= 100.0F || Math.abs(value - Math.round(value)) < 0.05F) {
            return String.valueOf(Math.round(value));
        }
        return String.format("%.1f", value);
    }

    /** 容器列表行尾的应力量：紧凑写法（8.2k / 1.5M），一行只有 13px 高，放不下长数字。 */
    private static String formatStressCompact(float value) {
        if (value >= 1.0E9F) {
            return "\u221e";
        }
        if (value >= 1_000_000.0F) {
            return String.format("%.1fM", value / 1_000_000.0F);
        }
        if (value >= 1_000.0F) {
            return String.format("%.1fk", value / 1_000.0F);
        }
        return String.valueOf(Math.round(value));
    }

    /** 状态码 → 可读文字；空串表示一切正常。 */
    private static Component stressStateName(@Nullable String state) {
        return state == null || state.isEmpty()
                ? Component.translatable("gui.useless_mod.wireless_logistics.stress.state.ok")
                : Component.translatable("gui.useless_mod.wireless_logistics.stress.state." + state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (filterPanelOpen) {
            // 先骨架渲染：super.render() 里除了底层界面，还负责触发 JEI 的
            // ContainerScreenEvent.Render.Background / Foreground —— JEI 的原料侧栏就在那两处画。
            // 之前这里直接 return，等于把 JEI 整个跳过，侧栏自然不显示（也没法往过滤格里拖）。
            super.render(graphics, mouseX, mouseY, partialTick);
            renderFilterOverlay(graphics, mouseX, mouseY);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        // 过滤格住在覆盖层面板里，`filterSlotScreenX/Y` 给的是**屏幕坐标**，面板关着时那些
        // 坐标指向的是主界面别的控件。所以这里**不能**再画过滤格物品——否则关掉面板后，
        // 标记会孤零零地停在一级菜单的容器列表上（用户实测反馈过）。
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
        renderNetworkTooltip(graphics, mouseX, mouseY);
        renderRouteTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 过滤面板打开时的收尾渲染（在 {@code super.render()} 之后调用）。
     *
     * <p><b>不再跳过 {@code super.render()}</b>。原因是 JEI 的原料侧栏是在
     * {@code ContainerScreenEvent.Render.Background / Foreground} 里画的，而这两个事件由
     * {@code super.render()} 触发——一旦跳过，JEI 侧栏就整个不显示，玩家也就没法从 JEI 往
     * 过滤格里拖物品（而这正是过滤面板存在的意义）。一级菜单的预览弹窗同样是
     * {@code AbstractContainerScreen}，它盖满全屏所以看不出侧栏消失。</p>
     *
     * <p>改为 {@code renderMediumMenu} 那套已经验证过的做法<b>两件套</b>：</p>
     * <ol>
     *   <li>{@code flush()} —— 把底层界面已入批的内容（槽位物品、堆叠数字、输入框）先提交定型，
     *       否则后画的填充会跟它们排进同一批、按图层重排，遮不住；</li>
     *   <li>整段抬 z 到 {@link #FILTER_PANEL_Z} —— GUI 的深度测试是开着的，槽位物品在 z=150、
     *       堆叠数字在 z=200，留在 z=0 的填充比它们都远，片段会被深度测试直接丢掉。</li>
     * </ol>
     *
     * <p><b>没有压暗层</b>（v2 修）：早先会在面板四周铺一圈半透明黑，但面板只占屏幕一小块，
     * 四块矩形与背包 / 侧栏的边界会露出一道道硬边，看着很乱（用户反馈「压暗层乱七八糟的」）。
     * 面板本身是 {@code drawPanel} 的不透明底，不压暗也照样能看清。</p>
     */
    private void renderFilterOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        // 底层已入批的内容先落地，之后画的面板才能稳定压在上面。
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, FILTER_PANEL_Z);
        renderFilterPanel(graphics, mouseX, mouseY);
        graphics.pose().popPose();
        // 详细面板再抬一层 z，压过滤面板。
        if (detailPanelOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, DETAIL_PANEL_Z);
            renderDetailPanel(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        }
        // 收尾再 flush 一次，保证面板立刻定型，不会被本帧后面的内容盖掉。
        graphics.flush();
        // 背包槽的悬停提示：它本来由 super.render() 画，但那时还没画面板，会被盖住。
        // 面板开着时从背包取物品是常规操作，提示必须在最上面重画一遍。
        if (inventorySlotAt(mouseX, mouseY) != null) {
            super.renderTooltip(graphics, mouseX, mouseY);
        }
        // 光标上拿着的物品同理：vanilla 只在 super.render() 里画它（z 也低于面板），
        // 从背包拿起物品来标记时会被面板整个盖住、看不见手上是什么。这里抬到最上面重画。
        renderCarriedItem(graphics, mouseX, mouseY);
    }

    /** 把「光标上拿着的物品」画在覆盖层最上面（含堆叠数字）。 */
    private void renderCarriedItem(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        int x = mouseX - 8;
        int y = mouseY - 8;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, CARRIED_ITEM_Z);
        graphics.renderItem(carried, x, y);
        graphics.renderItemDecorations(font, carried, x, y);
        graphics.pose().popPose();
    }

    /**
     * 画详细编辑面板。
     *
     * <p>跟过滤面板同一套约定：底板不透明、控件手动 render（它们只进了 children，
     * 进 renderables 会被 {@code super.render()} 画到下面去）。</p>
     */
    private void renderDetailPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = detailPanelX();
        int panelY = detailPanelY();
        MachineScreenStyle.drawPanel(graphics, panelX, panelY, DETAIL_PANEL_WIDTH, DETAIL_PANEL_HEIGHT);

        LinkFilterSlot slot = currentFilterSlot(detailIndex);
        graphics.drawString(font, Component.translatable(
                        "gui.useless_mod.wireless_logistics.filter_slot_title", detailIndex + 1),
                panelX + DETAIL_PANEL_PAD, panelY + DETAIL_TITLE_Y,
                MachineScreenStyle.TEXT_COLOR, false);

        // 输出端 / 输入端 标签
        graphics.drawString(font, Component.translatable(
                        "gui.useless_mod.wireless_logistics.filter_cond_out_label"),
                panelX + DETAIL_LABEL_X, panelY + DETAIL_OUT_ROW_Y + 5,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable(
                        "gui.useless_mod.wireless_logistics.filter_cond_in_label"),
                panelX + DETAIL_LABEL_X, panelY + DETAIL_IN_ROW_Y + 5,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);

        // 三个槽位（A 标记、输出端 B、输入端 B）的底板
        for (int which = 0; which < 3; which++) {
            int x = detailSlotScreenX(which);
            int y = detailSlotScreenY(which);
            graphics.fill(x, y, x + DETAIL_SLOT_SIZE, y + DETAIL_SLOT_SIZE,
                    MachineScreenStyle.SLOT_COLOR);
            graphics.fill(x, y, x + DETAIL_SLOT_SIZE, y + 1, MachineScreenStyle.SLOT_SHADOW_COLOR);
        }
        graphics.flush();

        // A 槽内容 + 两个 B 槽内容
        renderDetailMarker(graphics, detailSlotScreenX(0), detailSlotScreenY(0), slot);
        renderDetailControl(graphics, detailSlotScreenX(1), detailSlotScreenY(1), slot.outCond());
        renderDetailControl(graphics, detailSlotScreenX(2), detailSlotScreenY(2), slot.inCond());
        graphics.flush();

        // 控件手动 render（覆盖层阶段不在 super.render() 的渲染序列里）
        detailPatternField.render(graphics, mouseX, mouseY, 0.0F);
        detailOutValueField.render(graphics, mouseX, mouseY, 0.0F);
        detailInValueField.render(graphics, mouseX, mouseY, 0.0F);
        detailExcludeButton.render(graphics, mouseX, mouseY, 0.0F);
        detailOutOpButton.render(graphics, mouseX, mouseY, 0.0F);
        detailInOpButton.render(graphics, mouseX, mouseY, 0.0F);
        detailCloseButton.render(graphics, mouseX, mouseY, 0.0F);

        // 面板自己的一套悬停提示
        renderDetailTooltip(graphics, mouseX, mouseY);
    }

    /** A 标记槽的内容：模式字形 / 流体贴图 / 物品图标。 */
    private void renderDetailMarker(GuiGraphics graphics, int x, int y, LinkFilterSlot slot) {
        if (slot.isEmpty()) {
            return;
        }
        if (slot.isPattern()) {
            renderPatternMarker(graphics, x, y, slot);
        } else if (slot.isFluid()) {
            renderFluidMarker(graphics, x, y, slot.fluid());
        } else {
            graphics.renderItem(slot.item(), x, y);
        }
    }

    /** B 控制材料槽的内容：留空画「自身」，否则画物品 / 流体。 */
    private void renderDetailControl(GuiGraphics graphics, int x, int y, LinkFilterCondition cond) {
        if (cond.isOff()) {
            return;
        }
        if (cond.isSelf()) {
            graphics.drawString(font, Component.translatable(
                            "gui.useless_mod.wireless_logistics.filter_cond_self"),
                    x, y + 5, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        } else if (cond.isFluidControl()) {
            renderFluidMarker(graphics, x, y, cond.fluid());
        } else if (cond.isItemControl()) {
            graphics.renderItem(cond.item(), x, y);
        }
    }

    /** 详细面板的悬停提示：三个槽位 + 两个按钮。 */
    private void renderDetailTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = null;
        if (inDetailSlot(mouseX, mouseY, 0)) {
            lines = List.of(Component.translatable(
                    "gui.useless_mod.wireless_logistics.filter_hint"));
        } else if (inDetailSlot(mouseX, mouseY, 1) || inDetailSlot(mouseX, mouseY, 2)) {
            boolean input = inDetailSlot(mouseX, mouseY, 2);
            lines = List.of(Component.translatable(
                    "gui.useless_mod.wireless_logistics."
                            + (input ? "filter_cond_in_hint" : "filter_cond_out_hint")));
        } else if (detailExcludeButton.visible && detailExcludeButton.isMouseOver(mouseX, mouseY)) {
            lines = List.of(Component.translatable(
                    "gui.useless_mod.wireless_logistics.filter_exclude_hint"));
        }
        if (lines != null) {
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    /** 屏幕坐标下的玩家背包槽；没命中返回 {@code null}。 */
    @Nullable
    private Slot inventorySlotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
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

        // 再整体抬 z。flush() 只解决「谁后画」，解决不了深度测试：
        // 槽位物品画在 z=150、堆叠数字画在 z=200（见 GuiGraphics#renderItem 与
        // #renderItemDecorations），下拉留在 z=0 会比它们都远，片段照样被丢掉。
        // 两者都要做：先 flush 定序，再抬 z 让深度测试放行。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, MEDIUM_MENU_Z);
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
        graphics.pose().popPose();
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
            int x = filterSlotScreenX(index);
            int y = filterSlotScreenY(index);
            if (slot.isPattern()) {
                renderPatternMarker(graphics, x, y, slot);
            } else if (slot.isFluid()) {
                renderFluidMarker(graphics, x, y, slot.fluid());
            } else {
                // 槽位 18×18 就是原版槽位大小，物品正好填满、不用内缩。
                graphics.renderItem(slot.item(), x, y);
            }
        }
    }

    /**
     * 把 {@code #tag} / 通配符模式画进过滤槽。
     *
     * <p>画符号而不是画「第一个匹配到的物品」：模式的含义是一整类，挑一个代表物反而会让人
     * 以为这一格只过滤那一个。{@code #} 开头的画井号、通配符画星号，玩家一眼分得清两种模式。</p>
     *
     * <p>标签<b>没被任何物品使用</b>时用错误色画：这是最常见的写法错误（少打一个 {@code s}、
     * 忘了 {@code c:} 前缀），不管的话过滤器会静默失效——什么都不搬，而玩家看不出为什么。</p>
     */
    private void renderPatternMarker(GuiGraphics graphics, int x, int y, LinkFilterSlot slot) {
        String text = slot.pattern();
        if (text == null) {
            return;
        }
        LinkFilterPattern parsed = LinkFilterPattern.parse(text);
        int color = parsed != null && parsed.isTag() && !parsed.isResolvable()
                ? MachineScreenStyle.ERROR_TEXT_COLOR
                : MachineScreenStyle.TEXT_COLOR;
        String glyph = parsed != null && parsed.isTag() ? "#" : "*";
        // 槽位只有 16px：字形贴左上、尾巴压在下面一行（字号不缩，16px 高度塞得下两行 9px 的字体）。
        graphics.drawString(font, glyph, x + 1, y + 1, color, false);
        // 井号右侧再点一下模式里的关键字，不然一堆格子全是「#」根本分不出哪个是哪个。
        String tail = patternTail(text);
        if (!tail.isEmpty()) {
            graphics.drawString(font, tail, x, y + 8, color, false);
        }
    }

    /**
     * 从模式里取出「认得出是哪个」的那一段短字。
     *
     * <p>只取末段并截到 6 个字符：格子只有 16px 宽，画不下一整个 {@code #c:ingots/copper}，
     * 而这个尾巴（{@code copper}）比 {@code ingots} 更能区分相邻两格。</p>
     */
    private static String patternTail(String text) {
        int cut = Math.max(text.lastIndexOf('/'), text.lastIndexOf(':'));
        String tail = cut >= 0 && cut + 1 < text.length() ? text.substring(cut + 1) : text;
        // 通配符模式去掉前导的 *，不然尾巴全是星号。
        while (!tail.isEmpty() && (tail.charAt(0) == '*' || tail.charAt(0) == '#')) {
            tail = tail.substring(1);
        }
        return tail.length() > 6 ? tail.substring(0, 6) : tail;
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
        // 流体贴图是 16×16，槽位也是 16×16，直接铺满。
        graphics.blit(x, y, 0, 16, 16, sprite);
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
        // 详细面板开着时它压在最上面，格子提示会被它盖掉一半（玩家实测反馈）。
        // 这时候由详细面板自己出提示，格子提示整个不画。
        if (!filterPanelOpen || detailPanelOpen || !menu.isFilterActive()) {
            return;
        }
        int index = filterSlotAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        LinkFilterSlot marker = menu.getFilterMirror().get(index);
        List<Component> lines = new ArrayList<>(6);
        LinkFilterPattern pattern = marker.isPattern() ? LinkFilterPattern.parse(marker.pattern()) : null;
        if (pattern != null) {
            lines.add(Component.literal(pattern.displayName()));
            lines.add(Component.translatable(pattern.isTag()
                    ? "gui.useless_mod.wireless_logistics.filter_pattern_tag"
                    : "gui.useless_mod.wireless_logistics.filter_pattern_glob"));
            if (pattern.isTag() && !pattern.isResolvable()) {
                // 标签没被任何资源用到：静默失效是最坏的结果，必须标红。
                lines.add(Component.translatable(
                        "gui.useless_mod.wireless_logistics.filter_pattern_unresolved")
                        .withStyle(ChatFormatting.RED));
            }
            if (pattern.isTag() && chemicalRoute()) {
                // 化学品没有跨模组的通用标签体系，#tag 对它永远匹配不上——这条得说出来，
                // 否则玩家会以为是标签写错了。
                lines.add(Component.translatable(
                        "gui.useless_mod.wireless_logistics.filter_pattern_unsupported")
                        .withStyle(ChatFormatting.RED));
            }
        } else if (marker.isFluid()) {
            lines.add(marker.fluid().getHoverName());
        } else if (marker.isItem()) {
            lines.add(marker.item().getHoverName());
        }
        // 方向：包含 / 排除。
        lines.add(Component.translatable(marker.isExcluded()
                ? "gui.useless_mod.wireless_logistics.filter_exclude_on"
                : "gui.useless_mod.wireless_logistics.filter_exclude_off"));
        appendConditionLines(lines, marker);
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.filter_hint"));
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics.filter_detail_hint"));
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /**
     * 把两条控制条件写成 tooltip 行；没启用的不加。
     *
     * <p>数值走 {@link #limitText}，跟框里显示的写法一致——不然会出现
     * 「框里写 1K、提示里蹦出 1000」这种对不上的情况，玩家会以为两边是两个数。</p>
     */
    private static void appendConditionLines(List<Component> lines, LinkFilterSlot marker) {
        appendConditionLine(lines, marker.outCond(), "filter_cond_out_label");
        appendConditionLine(lines, marker.inCond(), "filter_cond_in_label");
    }

    private static void appendConditionLine(List<Component> lines, LinkFilterCondition cond, String labelKey) {
        if (cond.isOff()) {
            return;
        }
        Component who = cond.isSelf()
                ? Component.translatable("gui.useless_mod.wireless_logistics.filter_cond_self")
                : (cond.isFluidControl() ? cond.fluid().getHoverName() : cond.item().getHoverName());
        lines.add(Component.translatable("gui.useless_mod.wireless_logistics." + labelKey)
                .append(Component.literal(": "))
                .append(who)
                .append(Component.literal(" "))
                .append(opLabel(cond.op()))
                .append(Component.literal(" " + limitText(cond.value()))));
    }

    /** 当前选中的线路搬的是不是化学品；判不了（没选中）时返回 false。 */
    private boolean chemicalRoute() {
        StaffLinkRoute config = menu.getSelectedConfig();
        return config != null && config.medium().family() == ResourceFamily.CHEMICAL;
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
            if (!spec.field().visible || !spec.field().isMouseOver(mouseX, mouseY)) {
                continue;
            }
            boolean overridden = numericOverrides.containsKey(spec.field());
            List<Component> lines = new ArrayList<>(4);
            lines.add(numericLabel(spec));
            lines.add(numericHint(spec));
            if (!spec.field().active) {
                lines.add(Component.translatable(stressFields
                        ? "gui.useless_mod.wireless_logistics.stress.input_side_hint"
                        : "gui.useless_mod.wireless_logistics.release_only_hint"));
            }
            if (pressureFields && spec.field() == amountField) {
                // 气压的「数量」是目标气压，界面单位是 bar，范围就是气动的物理区间。
                lines.add(Component.translatable("gui.useless_mod.wireless_logistics.pressure.range"));
            } else {
                // 「数量」与应力转速都没有静态上限，只提示下限。
                lines.add(overridden || spec.scaled()
                        ? Component.translatable("gui.useless_mod.wireless_logistics.range_min", spec.min())
                        : Component.translatable("gui.useless_mod.wireless_logistics.range",
                                spec.min(), spec.max()));
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && detailPanelOpen) {
            // ESC 逐层退：先关详细面板，再关过滤面板，最后才是整个界面。
            closeDetailPanel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && filterPanelOpen) {
            // 面板是覆盖层，ESC 先关它、别顺手把整个界面关了。
            setFilterPanelOpen(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && mediumMenuOpen) {
            // 先收下拉，别顺手把整个界面关了。
            mediumMenuOpen = false;
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && anyFieldFocused()) {
            applyEdits();
            // 详细面板在回车时收起来：回车对玩家就是「打完了」，留在那儿会挡住下面的格子。
            closeDetailPanel();
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
        // 面板开着时，配置剪贴板与其他主界面快捷键都不该生效（玩家在编辑过滤，看不到底层）。
        if (filterPanelOpen) {
            return true;
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
                source.side(), source.weight(), source.filter()));
        updateControls();
        showNotice(Component.translatable("gui.useless_mod.wireless_logistics.config_pasted"));
    }

    /** 显示一条一次性提示，{@link #NOTICE_TICKS} tick 后自己消失。 */
    private void showNotice(Component message) {
        notice = message;
        noticeTicks = NOTICE_TICKS;
    }

    private List<EditBox> fields() {
        List<EditBox> all = new ArrayList<>(numericFields.size() + 6);
        all.add(networkNameField);
        all.add(searchField);
        all.add(nameField);
        for (NumericSpec spec : numericFields) {
            all.add(spec.field());
        }
        all.add(detailPatternField);
        all.add(detailOutValueField);
        all.add(detailInValueField);
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
        // 面板占满整个窗口，打开时它最优先：底层界面一个控件也不该收到这一下。
        if (filterPanelOpen) {
            return handleFilterPanelClick(mouseX, mouseY, button);
        }

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

        // 点到按钮等控件之前，先把输入框里已填但未提交的值提交掉。
        if (!isOverAnyField(mouseX, mouseY) && anyFieldFocused()) {
            applyEdits();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 过滤面板里的点击。
     *
     * <p>顺序有讲究：先给详细面板（它压在最上面），再给过滤面板的两个按钮（它们只进了 children，
     * 得手动转发），然后是玩家背包槽（要能从这里取物品来标记），最后才是 18 格。</p>
     */
    private boolean handleFilterPanelClick(double mouseX, double mouseY, int button) {
        if (detailPanelOpen) {
            return handleDetailPanelClick(mouseX, mouseY, button);
        }
        if (closeFilterButton.mouseClicked(mouseX, mouseY, button)
                || clearFilterButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 玩家背包槽：面板开着时也允许正常「拿起 / 放下」——不然只能靠 JEI 拖，或者先把物品
        // 拿在手上再开面板。转发给框架走标准的槽位点击（拾取到手、放下、右键分半等）。
        if (inventorySlotAt(mouseX, mouseY) != null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int filterIndex = filterSlotAt(mouseX, mouseY);
        if (filterIndex < 0) {
            // 点在面板空白处：让输入框失焦，但不关面板。
            setFocused(null);
            return true;
        }
        if (!menu.isFilterActive()) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 右键：打开这一格的详细编辑面板（模式、包含/排除、两条控制条件都在里面）。
            openDetailPanel(filterIndex);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        // 左键：按线路资源类型解释手上的东西——流体线路从容器里取出流体本身，
        // 解释不了就不动这一格（绝不把不匹配的东西塞进去）。空手 = 清掉这一格。
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            menu.setFilterSlot(filterIndex, LinkFilterSlot.EMPTY);
        } else {
            LinkFilterSlot marker = StaffLinkFilters.fromItem(menu.getSelectedMedium(), carried);
            if (marker != null) {
                // 换标记时保留这一格原有的包含/排除与条件。
                menu.setFilterSlot(filterIndex, currentFilterSlot(filterIndex).withMarker(marker));
            }
        }
        updateControls();
        return true;
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
     * 「应用到全部同名容器」：把当前线路配置刷给<b>跟当前这台显示名一样</b>的全部锚点。
     *
     * <p>只作用于同名而不是整个列表：一台机器上挂几条线路很常见，整片刷过去会把别的线路配置
     * 一起冲掉。「同名」按<b>界面显示的那个名字</b>算——玩家改过就用改过的，
     * 没改过就是方块本名，和列表里看到的一模一样（{@link #anchorDisplayName}）。</p>
     *
     * <p>走 {@link StaffLinkMenu#applyRouteTo}，<b>不改变</b>玩家当前的多选状态。</p>
     */
    private void applyAllSameName() {
        applyEdits();
        StaffLinkRoute config = menu.getSelectedConfig();
        GlobalPos selected = menu.getSelectedAnchor();
        if (config == null || selected == null) {
            return;
        }
        Component wanted = anchorDisplayName(selected);
        List<GlobalPos> targets = new ArrayList<>();
        for (GlobalPos anchor : menu.getAnchors()) {
            if (anchorDisplayName(anchor).equals(wanted)) {
                targets.add(anchor);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        menu.applyRouteTo(targets, config);
        updateControls();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 面板开着时框选文本全靠这里：跳过 super，就得自己转发，否则框里的选区永远拖不出来。
        if (filterPanelOpen) {
            EditBox focused = focusedField();
            if (focused != null && focused.canConsumeInput()) {
                return focused.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 面板按钮只进了 children，事件得手动转发，不然按下去会一直停在「按下」的视觉状态。
        if (closeFilterButton != null) {
            closeFilterButton.releaseVisualState();
            clearFilterButton.releaseVisualState();
            detailExcludeButton.releaseVisualState();
            detailOutOpButton.releaseVisualState();
            detailInOpButton.releaseVisualState();
            detailCloseButton.releaseVisualState();
            // EditBox 的 releaseVisualState 由它自己管，但松手这一刻要通知到，
            // 否则拖了一半的文本选区不会收尾。
            EditBox focused = focusedField();
            if (filterPanelOpen && focused != null) {
                focused.mouseReleased(mouseX, mouseY, button);
            }
        }
        flowButton.releaseVisualState();
        mediumButton.releaseVisualState();
        enabledButton.releaseVisualState();
        sideButton.releaseVisualState();
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
        if (filterPanelOpen) {
            // 面板是覆盖层，滚动轮不该偷偷滚下面的锚点列表。
            return true;
        }
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

    /** 命中的过滤格（参数是<b>屏幕</b>坐标，面板是居中的覆盖层）；没命中返回 -1。 */
    private int filterSlotAt(double screenX, double screenY) {
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            int x = filterSlotScreenX(index);
            int y = filterSlotScreenY(index);
            if (screenX >= x && screenX < x + FILTER_SLOT_SIZE
                    && screenY >= y && screenY < y + FILTER_SLOT_SIZE) {
                return index;
            }
        }
        return -1;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, visibleAnchors().size() - LIST_VISIBLE_ROWS);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    // ---- 过滤面板几何

    /**
     * 面板左上角。
     *
     * <p>横向居中即可（{@link #FILTER_PANEL_WIDTH} 只有 140，右边必然留得下 JEI 侧栏）。
     * 纵向在居中的基础上<b>上移一点</b>：面板下方紧挨着玩家背包，抬上去能给背包留出更多
     * 可见空间（用户要求「位置也上移」）。</p>
     */
    private int filterPanelX() {
        return Math.max(2, (width - FILTER_PANEL_WIDTH) / 2);
    }

    private int filterPanelY() {
        return Math.max(4, (height - FILTER_PANEL_HEIGHT) / 2 - FILTER_PANEL_RAISE);
    }

    /** 第 index 格槽位相对面板的偏移（网格整体在面板内横向居中）。 */
    private static int filterSlotPanelX(int index) {
        return FILTER_GRID_X + (index % FILTER_COLUMNS) * FILTER_CELL_COL_STEP;
    }

    private static int filterSlotPanelY(int index) {
        return FILTER_GRID_Y + (index / FILTER_COLUMNS) * FILTER_CELL_ROW_STEP;
    }

    /**
     * 过滤格槽位的<b>屏幕</b>坐标（绘制、命中检测、JEI 拖拽都用它）。
     *
     * <p>注意是屏幕坐标而不是 {@code leftPos/topPos} 的偏移：过滤格住在覆盖层面板里，
     * 跟主面板的位置无关。</p>
     */
    public int filterSlotScreenX(int index) {
        return filterPanelX() + filterSlotPanelX(index);
    }

    public int filterSlotScreenY(int index) {
        return filterPanelY() + filterSlotPanelY(index);
    }

    // ---- 详细编辑面板几何

    private int detailPanelX() {
        return Math.max(2, (width - DETAIL_PANEL_WIDTH) / 2);
    }

    private int detailPanelY() {
        return Math.max(4, (height - DETAIL_PANEL_HEIGHT) / 2);
    }

    /**
     * 详细面板里三个可放置槽的屏幕坐标。
     *
     * @param which {@code 0} = A 标记槽、{@code 1} = 输出端 B 槽、{@code 2} = 输入端 B 槽
     */
    public int detailSlotScreenX(int which) {
        int local = (which == 1 || which == 2) ? DETAIL_COND_SLOT_X : DETAIL_MARKER_X;
        return detailPanelX() + local;
    }

    public int detailSlotScreenY(int which) {
        int local = switch (which) {
            case 1 -> DETAIL_OUT_ROW_Y;
            case 2 -> DETAIL_IN_ROW_Y;
            default -> DETAIL_MARKER_ROW_Y;
        };
        return detailPanelY() + local;
    }

    public static int detailSlotSize() {
        return DETAIL_SLOT_SIZE;
    }

    /** 详细面板是否开着（给 JEI 拖拽切换靶点用）。 */
    public boolean isDetailPanelOpen() {
        return detailPanelOpen;
    }

    /** 详细面板正在编辑的格号；没打开时 -1。 */
    public int getDetailIndex() {
        return detailIndex;
    }

    /** 面板底部「关闭」按钮的屏幕坐标（两个按钮整体在面板内居中）。 */
    private int filterPanelCloseX() {
        return filterPanelX() + FILTER_PANEL_BUTTON_X;
    }

    private int filterPanelCloseY() {
        return filterPanelY() + FILTER_PANEL_BUTTON_Y;
    }

    /** 面板底部「清空」按钮的屏幕坐标。 */
    private int filterPanelClearX() {
        return filterPanelX() + FILTER_PANEL_BUTTON_X
                + FILTER_PANEL_BUTTON_WIDTH + FILTER_PANEL_BUTTON_GAP;
    }

    private int filterPanelClearY() {
        return filterPanelY() + FILTER_PANEL_BUTTON_Y;
    }

    public static int filterSlotSize() {
        return FILTER_SLOT_SIZE;
    }

    public static int filterSlotCount() {
        return StaffLinkRoute.FILTER_LIMIT;
    }

    /**
     * 过滤面板（覆盖层）是否开着。
     *
     * <p>给 JEI 拖拽用：{@link #filterSlotScreenX}/{@link #filterSlotScreenY} 给的是覆盖层的
     * 屏幕坐标，面板关着的时候那些坐标是没有意义的，拖放目标必须整个取消。</p>
     */
    public boolean isFilterPanelOpen() {
        return filterPanelOpen;
    }

    /**
     * JEI 拖拽是否正在进行（由 {@code StaffLinkGhostHandler} 在 {@code doStart=true} 时点亮，
     * 面板关闭或拖拽结束时熄灭）。
     *
     * <p>只有它为真时才画拖拽悬停高亮 —— 否则平时鼠标划过格子也变色，玩家会以为「这里能放」。</p>
     */
    private boolean jeiDragActive;

    public void setJeiDragActive(boolean active) {
        this.jeiDragActive = active;
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
                config.medium(), config.amount(), config.interval(), config.side(),
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withMedium(StaffLinkRoute config, LinkMedium medium) {
        boolean stress = medium.family() == ResourceFamily.STRESS;
        // 换到应力时顺手给一对有意义的初值：原来的「16 / 5」在应力语义下读作「16 RPM、方向 5」，
        // 而方向只认 1 / 2，5 会被服务端收敛掉——不如直接给对，免得玩家看到配置被悄悄改了。
        boolean pressure = medium.family() == ResourceFamily.PRESSURE;
        // 气压同理：原来的「16」在气压语义下读作 0.016 bar（几乎没压力），直接给 2 bar 更合理。
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                medium,
                stress ? StaffLinkRoute.STRESS_DEFAULT_RPM
                        : pressure ? StaffLinkRoute.PRESSURE_DEFAULT_MBAR : config.amount(),
                stress ? StaffLinkRoute.STRESS_CLOCKWISE : config.interval(),
                config.side(), config.weight(), pruneFilter(config.filter(), medium));
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
            if (!keep) {
                kept.add(LinkFilterSlot.EMPTY);
                continue;
            }
            // 标记留得下，但条件里的控制材料可能换了族：清掉（回到「测 A 自身」），
            // 否则会留下一个永远量不到、永远不成立的条件。
            kept.add(slot.withConditions(pruneCondition(slot.outCond(), fluidRoute),
                    pruneCondition(slot.inCond(), fluidRoute)));
        }
        return kept;
    }

    /** 换资源类型时清掉族不匹配的控制材料（保留方向与数值）。 */
    private static LinkFilterCondition pruneCondition(LinkFilterCondition cond, boolean fluidRoute) {
        if (cond.isOff() || cond.isSelf()) {
            return cond;
        }
        boolean compatible = fluidRoute ? cond.isFluidControl() : cond.isItemControl();
        return compatible ? cond : cond.withoutControl();
    }

    private static StaffLinkRoute withEnabled(StaffLinkRoute config, boolean enabled) {
        return new StaffLinkRoute(config.anchor(), config.route(), enabled, config.flow(),
                config.medium(), config.amount(), config.interval(), config.side(),
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withSide(StaffLinkRoute config, Direction side) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                config.medium(), config.amount(), config.interval(), side,
                config.weight(), config.filter());
    }

    private static StaffLinkRoute withNumbers(StaffLinkRoute config, int weight, long amount, int interval) {
        return new StaffLinkRoute(config.anchor(), config.route(), config.enabled(), config.flow(),
                config.medium(), amount, interval, config.side(),
                weight, config.filter());
    }

    /** 顺时针 ↔ 逆时针。 */
    private static int nextStressDirection(int current) {
        return current == StaffLinkRoute.STRESS_COUNTER_CLOCKWISE
                ? StaffLinkRoute.STRESS_CLOCKWISE
                : StaffLinkRoute.STRESS_COUNTER_CLOCKWISE;
    }
}
