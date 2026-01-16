package com.sorrowmist.useless.utils.pattern;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.level.BlockEvent;

/**
 * 样板供应器事件处理类，处理方块掉落、清理等事件
 */
public class PatternProviderEvent {
    /**
     * 监听方块掉落事件，防止从端样板供应器掉落样板
     */
    public static void onBlockDrops(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        BlockPos pos = event.getPos();
        
        // 检查是否有任何从端位于该方块位置
        for (com.sorrowmist.useless.utils.pattern.PatternProviderKey slaveKey : PatternProviderManager.getSlaveToMaster().keySet()) {
            if (slaveKey.getPos().equals(pos)) {
                // 从端被破坏，确保内部样板不会掉落
                BlockEntity blockEntity = levelAccessor.getBlockEntity(pos);
                if (blockEntity != null) {
                    // 使用反射和条件类加载来处理不同类型的样板供应器
                    try {
                        // 处理AE2普通样板供应器
                        if (blockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
                            // 再次确认清空样板，防止任何可能的掉落
                            var slaveLogic = slaveHost.getLogic();
                            var slaveInv = slaveLogic.getPatternInv();
                            slaveInv.clear();
                            slaveLogic.updatePatterns();
                        } 
                        // 处理高级AE样板供应器
                        else if (PatternProviderManager.checkIfClassImplementsInterface(blockEntity, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                            // 使用反射获取高级AE样板供应器的样板库存并清空
                            Object patternContainer = blockEntity;
                            Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                            java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                            Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                            
                            if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                                inv.clear();
                            }
                        }
                        // 处理面板形式
                        else if (blockEntity instanceof appeng.api.parts.IPartHost slavePartHost) {
                            // 检查指定方向的部件，再次确认清空样板
                            appeng.api.parts.IPart sidePart = slavePartHost.getPart(slaveKey.getDirection());
                            if (sidePart != null) {
                                // 处理AE2普通样板供应器面板
                                if (PatternProviderManager.checkIfClassImplementsInterface(sidePart, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                                    // 使用反射调用方法
                                    Object hostPart = sidePart;
                                    Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                                    java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                                    Object slaveLogic = getLogicMethod.invoke(hostPart);
                                    
                                    Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                                    java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                                    Object slaveInv = getPatternInvMethod.invoke(slaveLogic);
                                    
                                    if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                                        inv.clear();
                                    }
                                    
                                    java.lang.reflect.Method updatePatternsMethod = logicClass.getMethod("updatePatterns");
                                    updatePatternsMethod.invoke(slaveLogic);
                                }
                            }
                            // 处理高级AE样板供应器面板
                            else if (PatternProviderManager.checkIfClassImplementsInterface(sidePart, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                                // 使用反射获取高级AE样板供应器的样板库存并清空
                                Object patternContainer = sidePart;
                                Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                                java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                                Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                                
                                if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                                    inv.clear();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略任何异常，确保不会因为可选模组缺失而崩溃
                    }
                }
                break;
            }
        }
    }

    /**
     * 清除从方块的样板
     */
    public static void clearSlavePatterns(LevelAccessor levelAccessor, com.sorrowmist.useless.utils.pattern.PatternProviderKey slaveKey) {
        BlockEntity slaveBlockEntity = levelAccessor.getBlockEntity(slaveKey.getPos());
        if (slaveBlockEntity == null) return;
        
        // 使用反射和条件类加载来处理不同类型的样板供应器
        try {
            // 处理直接放置的样板供应器
            if (slaveBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
                // 获取从端的pattern inventory并清空
                var slaveLogic = slaveHost.getLogic();
                var slaveInv = slaveLogic.getPatternInv();
                slaveInv.clear();
            }
            // 处理高级AE样板供应器
            else if (PatternProviderManager.checkIfClassImplementsInterface(slaveBlockEntity, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                // 使用反射获取高级AE样板供应器的样板库存并清空
                Object patternContainer = slaveBlockEntity;
                Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                
                if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                    inv.clear();
                }
            }
            // 处理面板形式的样板供应器
            else if (slaveBlockEntity instanceof appeng.api.parts.IPartHost partHost) {
                // 对于面板形式，需要获取对应的部件并清空样板
                appeng.api.parts.IPart targetPart = partHost.getPart(slaveKey.getDirection());
                if (targetPart != null) {
                    if (PatternProviderManager.checkIfClassImplementsInterface(targetPart, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                        // 使用反射调用方法
                        Object hostPart = targetPart;
                        Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                        java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                        Object slaveLogic = getLogicMethod.invoke(hostPart);
                        
                        Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                        java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                        Object slaveInv = getPatternInvMethod.invoke(slaveLogic);
                        
                        if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                            inv.clear();
                        }
                    }
                    // 处理高级AE样板供应器面板
                    else if (PatternProviderManager.checkIfClassImplementsInterface(targetPart, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                        // 使用反射获取高级AE样板供应器的样板库存并清空
                        Object patternContainer = targetPart;
                        Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                        java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                        Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                        
                        if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                            inv.clear();
                        }
                    }
                }
                // 尝试中心部件
                else {
                    appeng.api.parts.IPart centerPart = partHost.getPart(null);
                    if (centerPart != null) {
                        if (PatternProviderManager.checkIfClassImplementsInterface(centerPart, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                            // 使用反射调用方法
                            Object hostPart = centerPart;
                            Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                            java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                            Object slaveLogic = getLogicMethod.invoke(hostPart);
                            
                            Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                            java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                            Object slaveInv = getPatternInvMethod.invoke(slaveLogic);
                            
                            if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                                inv.clear();
                            }
                        }
                        // 处理高级AE样板供应器中心部件
                        else if (PatternProviderManager.checkIfClassImplementsInterface(centerPart, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                            // 使用反射获取高级AE样板供应器的样板库存并清空
                            Object patternContainer = centerPart;
                            Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                            java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                            Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                            
                            if (slaveInv instanceof appeng.api.inventories.InternalInventory inv) {
                                inv.clear();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略清空样板失败的异常
        }
    }
}