package com.sorrowmist.useless.utils.pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * 样板供应器处理器注册表，用于管理不同类型的样板供应器处理器
 */
public class PatternProviderHandlerRegistry {
    private static final Map<String, PatternProviderHandler> handlers = new HashMap<>();

    // 静态初始化，注册所有处理器
    static {
        registerHandler(new AEPatternProviderHandler());
        registerHandler(new ExPatternProviderHandler());
        registerHandler(new AdvPatternProviderHandler());
    }

    /**
     * 注册样板供应器处理器
     */
    public static void registerHandler(PatternProviderHandler handler) {
        handlers.put(handler.getProviderType(), handler);
    }

    /**
     * 根据类型获取样板供应器处理器
     */
    public static PatternProviderHandler getHandler(String type) {
        return handlers.get(type);
    }

    /**
     * 获取所有注册的处理器
     */
    public static Map<String, PatternProviderHandler> getAllHandlers() {
        return new HashMap<>(handlers);
    }
}