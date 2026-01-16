package com.sorrowmist.useless.utils.pattern;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * 样板供应器管理器，用于处理主从关系和同步逻辑
 */
public class PatternProviderManager {
    // 扩展样板供应器主从同步相关
    private static final Map<PatternProviderKey, Set<PatternProviderKey>> masterToSlaves = new HashMap<>();
    private static final Map<PatternProviderKey, PatternProviderKey> slaveToMaster = new HashMap<>();
    private static final Map<PatternProviderKey, Long> lastSyncTime = new HashMap<>();
    private static final long SYNC_INTERVAL = 1000; // 同步间隔，防止频繁同步
    private static final String SYNC_DATA_TAG = "PatternProviderSyncData";
    public static PatternProviderKey currentSelectedMaster = null; // 当前选择的主方块

    /**
     * 清理无效的主从关系
     */
    public static void cleanupInvalidLinks(LevelAccessor levelAccessor) {
        List<PatternProviderKey> mastersToRemove = new ArrayList<>();
        List<PatternProviderKey> slavesToRemove = new ArrayList<>();

        // 检查主方块是否存在
        if (!masterToSlaves.isEmpty()) {
            for (PatternProviderKey key : masterToSlaves.keySet()) {
                if (!isValidProvider(levelAccessor, key)) {
                    mastersToRemove.add(key);
                }
            }
        }

        // 检查从方块是否存在
        if (!slaveToMaster.isEmpty()) {
            for (PatternProviderKey key : slaveToMaster.keySet()) {
                if (!isValidProvider(levelAccessor, key)) {
                    slavesToRemove.add(key);
                }
            }
        }

        // 移除无效的主方块
        for (PatternProviderKey masterKey : mastersToRemove) {
            Set<PatternProviderKey> slaves = masterToSlaves.remove(masterKey);
            if (slaves != null) {
                for (PatternProviderKey slaveKey : slaves) {
                    slaveToMaster.remove(slaveKey);
                }
            }
        }

        // 移除无效的从方块
        for (PatternProviderKey slaveKey : slavesToRemove) {
            PatternProviderKey masterKey = slaveToMaster.remove(slaveKey);
            if (masterKey != null) {
                Set<PatternProviderKey> slaves = masterToSlaves.get(masterKey);
                if (slaves != null) {
                    slaves.remove(slaveKey);
                    if (slaves.isEmpty()) {
                        masterToSlaves.remove(masterKey);
                    }
                }
            }
        }
    }

    /**
     * 检查样板供应器是否有效
     */
    private static boolean isValidProvider(LevelAccessor levelAccessor, PatternProviderKey key) {
        BlockEntity blockEntity = levelAccessor.getBlockEntity(key.getPos());
        // 检查方块实体是否存在
        if (blockEntity == null) {
            return false;
        }

        // 检查方块实体类型是否为有效的样板供应器
        String className = blockEntity.getClass().getName();
        return blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") ||
                className.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity");
    }

    /**
     * 处理主方块被破坏的情况
     */
    public static void handleMasterBreak(LevelAccessor levelAccessor, PatternProviderKey masterKey) {
        Set<PatternProviderKey> slaves = masterToSlaves.remove(masterKey);
        if (slaves != null) {
            for (PatternProviderKey slaveKey : slaves) {
                slaveToMaster.remove(slaveKey);
                clearSlavePatterns(levelAccessor, slaveKey);
            }
        }
    }

    /**
     * 处理从方块被破坏的情况
     */
    public static void handleSlaveBreak(LevelAccessor levelAccessor, PatternProviderKey slaveKey) {
        PatternProviderKey masterKey = slaveToMaster.remove(slaveKey);
        if (masterKey != null) {
            Set<PatternProviderKey> slaves = masterToSlaves.get(masterKey);
            if (slaves != null) {
                slaves.remove(slaveKey);
                if (slaves.isEmpty()) {
                    masterToSlaves.remove(masterKey);
                }
            }
        }
    }

    /**
     * 清除从方块的样板
     */
    public static void clearSlavePatterns(LevelAccessor levelAccessor, PatternProviderKey slaveKey) {
        // 这个方法将在实际需要时实现，目前暂时为空
        // 清除样板的逻辑会在具体的同步方法中实现
    }

    /**
     * 保存同步数据到世界保存
     */
    public static void saveSyncData(ServerLevel serverLevel) {
        PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                PatternProviderSyncData::load,
                PatternProviderSyncData::new,
                SYNC_DATA_TAG
        );

        // 保存当前的主从关系到SavedData
        syncData.clear();
        syncData.getMasterToSlaves().putAll(masterToSlaves);
        syncData.getSlaveToMaster().putAll(slaveToMaster);

        // 标记数据为已更改
        syncData.setDirty();
    }

    /**
     * 从世界保存加载同步数据
     */
    public static void loadSyncData(ServerLevel serverLevel) {
        PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                PatternProviderSyncData::load,
                PatternProviderSyncData::new,
                SYNC_DATA_TAG
        );

        // 加载主从关系
        masterToSlaves.clear();
        masterToSlaves.putAll(syncData.getMasterToSlaves());
        
        slaveToMaster.clear();
        slaveToMaster.putAll(syncData.getSlaveToMaster());
    }

    /**
     * 获取主从关系映射
     */
    public static Map<PatternProviderKey, Set<PatternProviderKey>> getMasterToSlaves() {
        return masterToSlaves;
    }

    /**
     * 获取从主关系映射
     */
    public static Map<PatternProviderKey, PatternProviderKey> getSlaveToMaster() {
        return slaveToMaster;
    }

    /**
     * 获取同步间隔
     */
    public static long getSyncInterval() {
        return SYNC_INTERVAL;
    }

    /**
     * 获取最后同步时间映射
     */
    public static Map<PatternProviderKey, Long> getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * 获取当前选中的主方块
     */
    public static PatternProviderKey getCurrentSelectedMaster() {
        return currentSelectedMaster;
    }

    /**
     * 设置当前选中的主方块
     */
    public static void setCurrentSelectedMaster(PatternProviderKey masterKey) {
        currentSelectedMaster = masterKey;
    }

    /**
     * 定期同步所有从样板供应器
     */
    public static void syncAllSlaves(net.minecraft.world.level.Level world) {
        long currentTime = System.currentTimeMillis();
        
        // 遍历所有主从关系
        for (Map.Entry<PatternProviderKey, Set<PatternProviderKey>> entry : masterToSlaves.entrySet()) {
            PatternProviderKey masterKey = entry.getKey();
            Set<PatternProviderKey> slaves = entry.getValue();
            
            if (slaves.isEmpty()) continue;
            
            // 同步到所有从方块
            for (PatternProviderKey slaveKey : slaves) {
                // 检查同步间隔
                Long lastSync = lastSyncTime.get(slaveKey);
                if (lastSync == null || currentTime - lastSync > SYNC_INTERVAL) {
                    // 直接调用syncPatternsFromMaster方法进行同步
                    syncPatternsFromMaster(world, slaveKey, masterKey);
                }
            }
        }
    }
    
    /**
     * 从主样板供应器同步样板到从样板供应器
     */
    public static void syncPatternsFromMaster(net.minecraft.world.level.Level world, PatternProviderKey slaveKey, PatternProviderKey masterKey) {
        if (masterKey == null) return;
        
        // 检查同步间隔
        long currentTime = System.currentTimeMillis();
        Long lastSync = lastSyncTime.get(slaveKey);
        if (lastSync != null && currentTime - lastSync < SYNC_INTERVAL) {
            return;
        }
        
        // 获取主方块的BlockEntity
        net.minecraft.world.level.block.entity.BlockEntity masterBlockEntity = world.getBlockEntity(masterKey.getPos());
        if (masterBlockEntity == null) return;
        
        // 获取从方块的BlockEntity
        net.minecraft.world.level.block.entity.BlockEntity slaveBlockEntity = world.getBlockEntity(slaveKey.getPos());
        if (slaveBlockEntity == null) return;
        
        // 使用反射和条件类加载来处理不同类型的样板供应器，避免直接引用可选模组的类
        try {
            List<net.minecraft.world.item.ItemStack> masterPatterns = new ArrayList<>();
            
            // 检查是否是高级AE样板供应器（AdvPatternProviderLogicHost）
            boolean isAdvPatternProviderHost = checkIfClassImplementsInterface(masterBlockEntity, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost");
            if (isAdvPatternProviderHost) {
                // 使用反射获取高级AE样板供应器的样板库存
                Object patternContainer = masterBlockEntity;
                Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                Object masterInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                
                // 遍历样板库存
                if (masterInv instanceof appeng.api.inventories.InternalInventory inv) {
                    for (int i = 0; i < inv.size(); i++) {
                        net.minecraft.world.item.ItemStack stack = inv.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            masterPatterns.add(stack.copy());
                        }
                    }
                }
            }
            // 检查是否是AE2普通样板供应器或其扩展（使用接口而非具体类）
            else if (checkIfClassImplementsInterface(masterBlockEntity, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                // 使用反射调用getLogic()方法
                Object masterHost = masterBlockEntity;
                Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                Object masterLogic = getLogicMethod.invoke(masterHost);
                
                // 获取样板
                Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                Object masterInv = getPatternInvMethod.invoke(masterLogic);
                
                // 遍历样板库存
                if (masterInv instanceof appeng.api.inventories.InternalInventory inv) {
                    for (int i = 0; i < inv.size(); i++) {
                        net.minecraft.world.item.ItemStack stack = inv.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            masterPatterns.add(stack.copy());
                        }
                    }
                }
            }
            // 检查是否是AE2普通样板供应器或其扩展的面板形式
            else if (masterBlockEntity instanceof appeng.api.parts.IPartHost masterPartHost) {
                // 尝试从面板获取样板
                masterPatterns = getPatternsFromPartHost(masterPartHost, masterKey.getDirection());
            }
            
            // 无论主端是否有样板，都需要同步到从端
            // 当主端没有样板时，清空从端的样板
            // 从slaveKey中获取从端的方向，确保同步到正确的面板
            syncToSlavePatternProvider(slaveBlockEntity, slaveKey.getDirection(), masterPatterns);
        } catch (Exception e) {
            // 忽略所有异常，确保不会因为可选模组缺失而崩溃
        }
        
        // 更新同步时间
        lastSyncTime.put(slaveKey, currentTime);
    }
    
    /**
     * 检查类是否实现了指定接口
     */
    public static boolean checkIfClassImplementsInterface(Object obj, String interfaceName) {
        try {
            Class<?> interfaceClass = Class.forName(interfaceName);
            return interfaceClass.isInstance(obj);
        } catch (ClassNotFoundException e) {
            // 接口不存在，说明对应模组未安装
            return false;
        }
    }
    
    /**
     * 从IPartHost获取样板
     */
    private static List<net.minecraft.world.item.ItemStack> getPatternsFromPartHost(appeng.api.parts.IPartHost partHost, net.minecraft.core.Direction direction) {
        List<net.minecraft.world.item.ItemStack> patterns = new ArrayList<>();
        
        try {
            // 检查指定方向的部件
            appeng.api.parts.IPart targetPart = partHost.getPart(direction);
            if (targetPart != null) {
                patterns = getPatternsFromPart(targetPart);
                return patterns; // 即使patterns为空，也要返回，确保正确传递空状态
            }
            
            // 检查中心部件
            appeng.api.parts.IPart centerPart = partHost.getPart(null);
            if (centerPart != null) {
                patterns = getPatternsFromPart(centerPart);
                return patterns; // 即使patterns为空，也要返回，确保正确传递空状态
            }
            
            // 检查所有方向的部件
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                appeng.api.parts.IPart sidePart = partHost.getPart(dir);
                if (sidePart != null) {
                    patterns = getPatternsFromPart(sidePart);
                    return patterns; // 即使patterns为空，也要返回，确保正确传递空状态
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        
        return patterns;
    }
    
    /**
     * 从IPart获取样板
     */
    private static List<net.minecraft.world.item.ItemStack> getPatternsFromPart(appeng.api.parts.IPart part) {
        List<net.minecraft.world.item.ItemStack> patterns = new ArrayList<>();
        
        try {
            // 检查部件是否实现了PatternProviderLogicHost接口（AE2普通样板供应器）
            if (checkIfClassImplementsInterface(part, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                // 使用反射获取样板
                Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                Object logic = getLogicMethod.invoke(part);
                
                Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                Object inv = getPatternInvMethod.invoke(logic);
                
                if (inv instanceof appeng.api.inventories.InternalInventory internalInv) {
                    for (int i = 0; i < internalInv.size(); i++) {
                        net.minecraft.world.item.ItemStack stack = internalInv.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            patterns.add(stack.copy());
                        }
                    }
                }
            }
            // 检查部件是否实现了AdvPatternProviderLogicHost接口（高级AE样板供应器）
            else if (checkIfClassImplementsInterface(part, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                // 使用反射获取高级AE样板供应器的样板库存
                Object patternContainer = part;
                Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                Object masterInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                
                if (masterInv instanceof appeng.api.inventories.InternalInventory inv) {
                    for (int i = 0; i < inv.size(); i++) {
                        net.minecraft.world.item.ItemStack stack = inv.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            patterns.add(stack.copy());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        
        return patterns;
    }
    
    /**
     * 同步样板到从端样板供应器
     */
    private static void syncToSlavePatternProvider(net.minecraft.world.level.block.entity.BlockEntity slaveBlockEntity, net.minecraft.core.Direction slaveDirection, List<net.minecraft.world.item.ItemStack> masterPatterns) {
        try {
            // 检查是否是高级AE样板供应器（AdvPatternProviderLogicHost）
            boolean isAdvPatternProviderHost = checkIfClassImplementsInterface(slaveBlockEntity, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost");
            if (isAdvPatternProviderHost) {
                // 使用反射获取高级AE样板供应器的样板库存
                Object patternContainer = slaveBlockEntity;
                Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                
                if (slaveInv instanceof appeng.api.inventories.InternalInventory internalInv) {
                    // 清空从方块的inventory
                    for (int i = 0; i < internalInv.size(); i++) {
                        internalInv.setItemDirect(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    
                    // 复制主方块的所有pattern到从方块
                    for (int i = 0; i < masterPatterns.size() && i < internalInv.size(); i++) {
                        internalInv.setItemDirect(i, masterPatterns.get(i));
                    }
                    
                    // 高级AE样板供应器不需要updatePatterns方法，直接更新inventory即可
                }
            }
            // 检查是否是PatternProviderLogicHost类型
            else if (checkIfClassImplementsInterface(slaveBlockEntity, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                // 使用反射获取逻辑并同步
                Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                Object slaveLogic = getLogicMethod.invoke(slaveBlockEntity);
                
                Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                Object slaveInv = getPatternInvMethod.invoke(slaveLogic);
                
                if (slaveInv instanceof appeng.api.inventories.InternalInventory internalInv) {
                    // 清空从方块的inventory
                    for (int i = 0; i < internalInv.size(); i++) {
                        internalInv.setItemDirect(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    
                    // 复制主方块的所有pattern到从方块
                    for (int i = 0; i < masterPatterns.size() && i < internalInv.size(); i++) {
                        internalInv.setItemDirect(i, masterPatterns.get(i));
                    }
                    
                    // 更新从方块的patterns
                    java.lang.reflect.Method updatePatternsMethod = logicClass.getMethod("updatePatterns");
                    updatePatternsMethod.invoke(slaveLogic);
                    
                    // 为从端设置过滤器，防止取出样板
                    setPatternFilter(slaveLogic);
                }
            }
            // 检查是否是面板形式
            else if (slaveBlockEntity instanceof appeng.api.parts.IPartHost slavePartHost) {
                // 尝试同步到面板，传递从端的方向
                syncToSlavePartHost(slavePartHost, slaveDirection, masterPatterns);
            }
        } catch (Exception e) {
            // 忽略所有异常
        }
    }
    
    /**
     * 同步样板到从端面板
     */
    private static void syncToSlavePartHost(appeng.api.parts.IPartHost slavePartHost, net.minecraft.core.Direction targetDirection, List<net.minecraft.world.item.ItemStack> masterPatterns) {
        try {
            // 首先检查指定方向的部件
            appeng.api.parts.IPart targetPart = slavePartHost.getPart(targetDirection);
            if (targetPart != null) {
                // 处理AE2普通样板供应器面板
                if (checkIfClassImplementsInterface(targetPart, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                    // 使用反射同步
                    Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                    java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                    Object slaveLogic = getLogicMethod.invoke(targetPart);
                    
                    Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                    java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                    Object slaveInv = getPatternInvMethod.invoke(slaveLogic);
                    
                    if (slaveInv instanceof appeng.api.inventories.InternalInventory internalInv) {
                        // 清空从方块的inventory
                        for (int i = 0; i < internalInv.size(); i++) {
                            internalInv.setItemDirect(i, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                        
                        // 复制主方块的所有pattern到从方块
                        for (int i = 0; i < masterPatterns.size() && i < internalInv.size(); i++) {
                            internalInv.setItemDirect(i, masterPatterns.get(i));
                        }
                        
                        // 更新从方块的patterns
                        java.lang.reflect.Method updatePatternsMethod = logicClass.getMethod("updatePatterns");
                        updatePatternsMethod.invoke(slaveLogic);
                        
                        // 为从端设置过滤器，防止取出样板
                        setPatternFilter(slaveLogic);
                    }
                    return; // 已找到并同步指定方向的面板
                }
                // 处理高级AE样板供应器面板
                else if (checkIfClassImplementsInterface(targetPart, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                    // 使用反射同步高级AE样板供应器
                    Object patternContainer = targetPart;
                    Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                    java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                    Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                    
                    if (slaveInv instanceof appeng.api.inventories.InternalInventory internalInv) {
                        // 清空从方块的inventory
                        for (int i = 0; i < internalInv.size(); i++) {
                            internalInv.setItemDirect(i, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                        
                        // 复制主方块的所有pattern到从方块
                        for (int i = 0; i < masterPatterns.size() && i < internalInv.size(); i++) {
                            internalInv.setItemDirect(i, masterPatterns.get(i));
                        }
                        
                        // 高级AE样板供应器不需要updatePatterns方法，直接更新inventory即可
                    }
                    return; // 已找到并同步指定方向的面板
                }
            }
            
            // 如果指定方向没有匹配的部件，遍历所有方向寻找第一个匹配的部件
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                // 跳过已经检查过的指定方向
                if (dir == targetDirection) continue;
                
                appeng.api.parts.IPart sidePart = slavePartHost.getPart(dir);
                if (sidePart != null) {
                    // 处理AE2普通样板供应器面板
                    if (checkIfClassImplementsInterface(sidePart, "appeng.helpers.patternprovider.PatternProviderLogicHost")) {
                        // 使用反射同步
                        Class<?> hostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                        java.lang.reflect.Method getLogicMethod = hostClass.getMethod("getLogic");
                        Object slaveLogic = getLogicMethod.invoke(sidePart);
                        
                        Class<?> logicClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogic");
                        java.lang.reflect.Method getPatternInvMethod = logicClass.getMethod("getPatternInv");
                        Object slaveInv = getPatternInvMethod.invoke(slaveLogic);
                        
                        if (slaveInv instanceof appeng.api.inventories.InternalInventory internalInv) {
                            // 清空从方块的inventory
                            for (int i = 0; i < internalInv.size(); i++) {
                                internalInv.setItemDirect(i, net.minecraft.world.item.ItemStack.EMPTY);
                            }
                            
                            // 复制主方块的所有pattern到从方块
                            for (int i = 0; i < masterPatterns.size() && i < internalInv.size(); i++) {
                                internalInv.setItemDirect(i, masterPatterns.get(i));
                            }
                            
                            // 更新从方块的patterns
                            java.lang.reflect.Method updatePatternsMethod = logicClass.getMethod("updatePatterns");
                            updatePatternsMethod.invoke(slaveLogic);
                            
                            // 为从端设置过滤器，防止取出样板
                            setPatternFilter(slaveLogic);
                        }
                        break; // 只同步第一个匹配的面板
                    }
                    // 处理高级AE样板供应器面板
                    else if (checkIfClassImplementsInterface(sidePart, "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost")) {
                        // 使用反射同步高级AE样板供应器
                        Object patternContainer = sidePart;
                        Class<?> patternContainerClass = Class.forName("appeng.helpers.patternprovider.PatternContainer");
                        java.lang.reflect.Method getTerminalPatternInventoryMethod = patternContainerClass.getMethod("getTerminalPatternInventory");
                        Object slaveInv = getTerminalPatternInventoryMethod.invoke(patternContainer);
                        
                        if (slaveInv instanceof appeng.api.inventories.InternalInventory internalInv) {
                            // 清空从方块的inventory
                            for (int i = 0; i < internalInv.size(); i++) {
                                internalInv.setItemDirect(i, net.minecraft.world.item.ItemStack.EMPTY);
                            }
                            
                            // 复制主方块的所有pattern到从方块
                            for (int i = 0; i < masterPatterns.size() && i < internalInv.size(); i++) {
                                internalInv.setItemDirect(i, masterPatterns.get(i));
                            }
                            
                            // 高级AE样板供应器不需要updatePatterns方法，直接更新inventory即可
                        }
                        break; // 只同步第一个匹配的面板
                    }
                }
            }
        } catch (Exception e) {
            // 忽略所有异常
        }
    }
    
    /**
     * 为样板供应器设置过滤器，防止取出样板
     */
    private static void setPatternFilter(Object logic) {
        try {
            // 获取PatternProviderLogic的patternInventory字段
            java.lang.reflect.Field logicField = logic.getClass().getDeclaredField("patternInventory");
            logicField.setAccessible(true);
            Object inventoryObj = logicField.get(logic);
            
            // 检查是否为AppEngInternalInventory类型
            if (inventoryObj instanceof appeng.util.inv.AppEngInternalInventory appEngInv) {
                // 设置过滤器，阻止提取样板
                appEngInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
                    @Override
                    public boolean allowExtract(appeng.api.inventories.InternalInventory inv, int slot, int amount) {
                        return false;
                    }
                });
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
    }
}