package com.sorrowmist.useless.core.common;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
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
}