package com.sorrowmist.useless.core.common;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * Useless Mod 的所有按键绑定定义
 */
public class KeyBindings {

    // ==================== 按键分类和翻译键 ====================
    private static final String CATEGORY = "key.category.useless_mod.useless";

    // 精准 (Silk Touch)
    private static final String SWITCH_SILK_TOUCH = "key.useless_mod.switch_silk_touch";
    // ==================== 懒加载的 KeyMapping ====================
    public static final Lazy<KeyMapping> SWITCH_SILK_TOUCH_KEY = Lazy.of(() -> new KeyMapping(
            SWITCH_SILK_TOUCH,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            CATEGORY
    ));
    // 时运（Fortune）
    private static final String SWITCH_FORTUNE = "key.useless_mod.switch_fortune";
    public static final Lazy<KeyMapping> SWITCH_FORTUNE_KEY = Lazy.of(() -> new KeyMapping(
            SWITCH_FORTUNE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            CATEGORY
    ));
    // 连锁模式切换（普通 ↔ 增强）
    private static final String TOGGLE_CHAIN_MODE = "key.useless_mod.toggle_chain_mode";
    public static final Lazy<KeyMapping> TOGGLE_CHAIN_MODE_KEY = Lazy.of(() -> new KeyMapping(
            TOGGLE_CHAIN_MODE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    ));
    // 模式轮盘
    private static final String SWITCH_MODE_WHEEL = "key.useless_mod.switch_mode_wheel";
    public static final Lazy<KeyMapping> SWITCH_MODE_WHEEL_KEY = Lazy.of(() -> new KeyMapping(
            SWITCH_MODE_WHEEL,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    ));
    // 强制挖掘功能
    private static final String SWITCH_FORCE_MINING = "key.useless_mod.force_mining";
    public static final Lazy<KeyMapping> SWITCH_FORCE_MINING_KEY = Lazy.of(() -> new KeyMapping(
            SWITCH_FORCE_MINING,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_9,
            CATEGORY
    ));
    // 触发强制挖掘
    private static final String TRIGGER_FORCE_MINING = "key.useless_mod.trigger_force_mining";
    public static final Lazy<KeyMapping> TRIGGER_FORCE_MINING_KEY = Lazy.of(() -> new KeyMapping(
            TRIGGER_FORCE_MINING,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    ));
    // Tab键触发连锁挖掘
    private static final String TRIGGER_CHAIN_MINING = "key.useless_mod.trigger_chain_mining";
    public static final Lazy<KeyMapping> TRIGGER_CHAIN_MINING_KEY = Lazy.of(() -> new KeyMapping(
            TRIGGER_CHAIN_MINING,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY
    ));
    // 耕地/草径模式切换（右键泥土时的优先行为）
    private static final String SWITCH_FARMLAND_MODE = "key.useless_mod.switch_farmland_mode";
    public static final Lazy<KeyMapping> SWITCH_FARMLAND_MODE_KEY = Lazy.of(() -> new KeyMapping(
            SWITCH_FARMLAND_MODE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    ));
    // 顺手收菜开关
    private static final String TOGGLE_CROP_HARVEST = "key.useless_mod.toggle_crop_harvest";
    public static final Lazy<KeyMapping> TOGGLE_CROP_HARVEST_KEY = Lazy.of(() -> new KeyMapping(
            TOGGLE_CROP_HARVEST,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    ));
    // 剪刀功能开关
    private static final String TOGGLE_SHEARS = "key.useless_mod.toggle_shears";
    public static final Lazy<KeyMapping> TOGGLE_SHEARS_KEY = Lazy.of(() -> new KeyMapping(
            TOGGLE_SHEARS,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    ));
    // 打火石功能开关
    private static final String TOGGLE_FLINT_AND_STEEL = "key.useless_mod.toggle_flint_and_steel";
    public static final Lazy<KeyMapping> TOGGLE_FLINT_AND_STEEL_KEY = Lazy.of(() -> new KeyMapping(
            TOGGLE_FLINT_AND_STEEL,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY
    ));
    /**
     * 恒为非激活的冲突上下文。
     *
     * <p>用于保留组合键在按键设置中的可配置性，同时使其不参与运行时分发。
     * 若改用 {@code KeyConflictContext.IN_GAME}，按住 Shift 时同键位的原版
     * {@code keyUse} 会被该修饰键桶遮蔽，方块交互将整体失效。</p>
     */
    private static final IKeyConflictContext ALWAYS_INACTIVE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };

    // 短距传送（造化杖）
    private static final String SHORT_TELEPORT = "key.useless_mod.short_teleport";

    /**
     * 短距传送绑定。
     *
     * <p>该绑定挂在恒为非激活的冲突上下文上：它在按键设置界面中可见、可改键，
     * 但无法通过 {@code KeyMappingLookup} 的激活过滤。按住 Shift 时其所在修饰键桶
     * 过滤后为空，查找逻辑转而回退到无修饰键桶，原版 {@code keyUse} 因而仍能取得
     * 该次点击，方块交互不受影响。触发判定由 {@code InputEvent.MouseButton.Pre}
     * 按鼠标按下边沿完成，见 {@code ClientEventBusSubscriber}。</p>
     */
    public static final Lazy<KeyMapping> SHORT_TELEPORT_KEY = Lazy.of(() -> new KeyMapping(
            SHORT_TELEPORT,
            ALWAYS_INACTIVE,
            KeyModifier.SHIFT,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY
    ));

    // 连点模式开关（默认未绑定，由玩家在「按键设置」里自行指定）
    private static final String TOGGLE_AUTO_CLICK = "key.useless_mod.toggle_auto_click";
    public static final Lazy<KeyMapping> TOGGLE_AUTO_CLICK_KEY = Lazy.of(() -> new KeyMapping(
            TOGGLE_AUTO_CLICK,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    ));
}
