package com.sorrowmist.useless.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_USELESS = "key.category.useless_mod.useless";
    public static final String KEY_SWITCH_SILK_TOUCH = "key.useless_mod.switch_silk_touch";
    public static final String KEY_SWITCH_FORTUNE = "key.useless_mod.switch_fortune";
    public static final String KEY_SWITCH_CHAIN_MINING = "key.useless_mod.chain_mining";
    public static final String KEY_SWITCH_ENHANCED_CHAIN_MINING = "key.useless_mod.enhanced_chain_mining";
    public static final String KEY_SWITCH_MODE_WHEEL = "key.useless_mod.switch_mode_wheel";

    public static final KeyMapping SWITCH_SILK_TOUCH_KEY = new KeyMapping(KEY_SWITCH_SILK_TOUCH,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            KEY_CATEGORY_USELESS);

    public static final KeyMapping SWITCH_FORTUNE_KEY = new KeyMapping(KEY_SWITCH_FORTUNE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            KEY_CATEGORY_USELESS);

    public static final KeyMapping SWITCH_CHAIN_MINING_KEY = new KeyMapping(KEY_SWITCH_CHAIN_MINING,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_7,
            KEY_CATEGORY_USELESS);

    public static final KeyMapping SWITCH_ENHANCED_CHAIN_MINING_KEY = new KeyMapping(KEY_SWITCH_ENHANCED_CHAIN_MINING,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_8,
            KEY_CATEGORY_USELESS);
    
    public static final KeyMapping SWITCH_MODE_WHEEL_KEY = new KeyMapping(KEY_SWITCH_MODE_WHEEL,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY_USELESS);
    
    // 跟踪连锁挖掘按键的按下状态
    public static boolean SWITCH_CHAIN_MINING_KEY_WAS_DOWN = false;
}