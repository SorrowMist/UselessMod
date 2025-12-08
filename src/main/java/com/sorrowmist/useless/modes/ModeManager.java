package com.sorrowmist.useless.modes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 模式管理器，处理模式的激活状态和互斥逻辑
 */
public class ModeManager {
    // 互斥模式组：同一组内的模式不能同时激活
    private static final Set<Set<ToolMode>> MUTUAL_EXCLUSION_GROUPS = new HashSet<>();
    
    static {
        // 初始化互斥模式组
        // 精准采集和时运是互斥的
        Set<ToolMode> exclusiveGroup1 = new HashSet<>();
        exclusiveGroup1.add(ToolMode.SILK_TOUCH);
        exclusiveGroup1.add(ToolMode.FORTUNE);
        MUTUAL_EXCLUSION_GROUPS.add(exclusiveGroup1);
        
        // 六种工具模式是互斥的
        Set<ToolMode> exclusiveGroup2 = new HashSet<>();
        exclusiveGroup2.add(ToolMode.WRENCH_MODE);
        exclusiveGroup2.add(ToolMode.SCREWDRIVER_MODE);
        exclusiveGroup2.add(ToolMode.MALLET_MODE);
        exclusiveGroup2.add(ToolMode.CROWBAR_MODE);
        exclusiveGroup2.add(ToolMode.HAMMER_MODE);
        exclusiveGroup2.add(ToolMode.MEK_CONFIGURATOR);
        MUTUAL_EXCLUSION_GROUPS.add(exclusiveGroup2);
    }
    
    private final Map<ToolMode, Boolean> activeModes;
    
    /**
     * 构造一个新的模式管理器
     */
    public ModeManager() {
        this.activeModes = new EnumMap<>(ToolMode.class);
        // 初始化所有模式为非激活状态
        for (ToolMode mode : ToolMode.values()) {
            activeModes.put(mode, false);
        }
    }
    
    /**
     * 从物品栈加载模式状态
     */
    public void loadFromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag modesTag = tag.getCompound("ToolModes");
        
        for (ToolMode mode : ToolMode.values()) {
            boolean isActive = modesTag.getBoolean(mode.getName());
            activeModes.put(mode, isActive);
        }
        
        // 确保互斥模式的一致性
        ensureMutualExclusion();
    }
    
    /**
     * 将模式状态保存到物品栈
     */
    public void saveToStack(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag modesTag = new CompoundTag();
        
        for (Map.Entry<ToolMode, Boolean> entry : activeModes.entrySet()) {
            modesTag.putBoolean(entry.getKey().getName(), entry.getValue());
        }
        
        tag.put("ToolModes", modesTag);
    }
    
    /**
     * 切换指定模式的激活状态
     */
    public void toggleMode(ToolMode mode) {
        // 特殊处理精准采集和时运模式的互斥切换
        if ((mode == ToolMode.SILK_TOUCH || mode == ToolMode.FORTUNE)) {
            // 如果当前模式是激活的，切换到另一个模式
            if (activeModes.get(mode)) {
                // 切换到相反的模式
                ToolMode oppositeMode = mode == ToolMode.SILK_TOUCH ? ToolMode.FORTUNE : ToolMode.SILK_TOUCH;
                activeModes.put(mode, false);
                activeModes.put(oppositeMode, true);
            } else {
                // 如果当前模式是未激活的，激活它并禁用另一个模式
                activeModes.put(mode, true);
                ToolMode oppositeMode = mode == ToolMode.SILK_TOUCH ? ToolMode.FORTUNE : ToolMode.SILK_TOUCH;
                activeModes.put(oppositeMode, false);
            }
        } else {
            // 其他模式的正常切换逻辑
            boolean isActive = activeModes.get(mode);
            activeModes.put(mode, !isActive);
            
            // 处理互斥模式
            handleMutualExclusion(mode);
        }
    }
    
    /**
     * 设置指定模式的激活状态
     */
    public void setModeActive(ToolMode mode, boolean active) {
        // 特殊处理精准采集和时运模式的互斥设置
        if ((mode == ToolMode.SILK_TOUCH || mode == ToolMode.FORTUNE)) {
            // 如果要激活当前模式，直接激活并禁用另一个模式
            activeModes.put(mode, active);
            ToolMode oppositeMode = mode == ToolMode.SILK_TOUCH ? ToolMode.FORTUNE : ToolMode.SILK_TOUCH;
            activeModes.put(oppositeMode, !active);
        } else {
            // 其他模式的正常设置逻辑
            activeModes.put(mode, active);
            
            // 处理互斥模式
            if (active) {
                handleMutualExclusion(mode);
            }
        }
    }
    
    /**
     * 检查指定模式是否激活
     */
    public boolean isModeActive(ToolMode mode) {
        return activeModes.getOrDefault(mode, false);
    }
    
    /**
     * 获取所有激活的模式
     */
    public Set<ToolMode> getActiveModes() {
        Set<ToolMode> result = new HashSet<>();
        for (Map.Entry<ToolMode, Boolean> entry : activeModes.entrySet()) {
            if (entry.getValue()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
    
    /**
     * 处理互斥模式逻辑
     */
    private void handleMutualExclusion(ToolMode activatedMode) {
        if (!activeModes.get(activatedMode)) {
            return; // 如果模式没有被激活，不需要处理
        }
        
        // 查找包含当前模式的互斥组
        for (Set<ToolMode> group : MUTUAL_EXCLUSION_GROUPS) {
            if (group.contains(activatedMode)) {
                // 禁用同一组内的其他模式
                for (ToolMode mode : group) {
                    if (mode != activatedMode) {
                        activeModes.put(mode, false);
                    }
                }
            }
        }
    }
    
    /**
     * 确保所有互斥模式组的一致性
     */
    private void ensureMutualExclusion() {
        // 检查每个互斥组
        for (Set<ToolMode> group : MUTUAL_EXCLUSION_GROUPS) {
            // 计算组内激活的模式数量
            int activeCount = 0;
            ToolMode lastActive = null;
            
            for (ToolMode mode : group) {
                if (activeModes.get(mode)) {
                    activeCount++;
                    lastActive = mode;
                }
            }
            
            // 如果有多个模式被激活，只保留最后一个
            if (activeCount > 1) {
                for (ToolMode mode : group) {
                    activeModes.put(mode, mode == lastActive);
                }
            } else if (activeCount == 0 && group.size() >= 2) {
                // 如果没有模式被激活，并且是精准采集和时运的互斥组，默认激活时运模式
                boolean isSilkTouchFortuneGroup = group.contains(ToolMode.SILK_TOUCH) && group.contains(ToolMode.FORTUNE);
                if (isSilkTouchFortuneGroup) {
                    // 默认激活时运模式
                    activeModes.put(ToolMode.FORTUNE, true);
                    activeModes.put(ToolMode.SILK_TOUCH, false);
                }
            }
        }
    }
    
    /**
     * 获取模式的总数
     */
    public int getTotalModes() {
        return ToolMode.getTotalModes();
    }
    
    /**
     * 复制模式管理器的状态
     */
    public ModeManager copy() {
        ModeManager copy = new ModeManager();
        copy.activeModes.putAll(this.activeModes);
        return copy;
    }
}