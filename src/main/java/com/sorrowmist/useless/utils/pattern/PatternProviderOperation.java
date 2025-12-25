package com.sorrowmist.useless.utils.pattern;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import java.util.HashSet;
import java.util.Set;

/**
 * 样板供应器操作类，处理主从关系的设置和同步
 */
public class PatternProviderOperation {
    /**
     * 设置主样板供应器
     */
    public static void setAsMaster(Level world, BlockPos masterPos, Direction direction, Player player) {
        // 检查方块位置是否包含有效的样板供应器
        boolean hasValidProvider = false;
        String providerType = "Unknown";
        
        // 获取方块实体
        BlockEntity blockEntity = world.getBlockEntity(masterPos);
        if (blockEntity != null) {
            // 检查是否是直接放置的样板供应器
            String className = blockEntity.getClass().getName();
            if (className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                    blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                    className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") ||
                    className.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity")) {
                providerType = PatternProviderUtils.getPatternProviderType(blockEntity, null);
                hasValidProvider = !providerType.equals("Unknown");
            } 
            // 如果不是，检查是否包含样板供应器部件
            else {
                try {
                    // 检查是否是IPartHost，包含部件
                    if (blockEntity instanceof IPartHost partHost) {
                        // 找到与点击位置最匹配的样板供应器方向
                        Direction actualDirection = PatternProviderUtils.findMatchingDirection(partHost, masterPos, direction, player);
                        
                        // 使用实际方向检查部件
                        IPart targetPart = partHost.getPart(actualDirection);
                        if (targetPart != null) {
                            String partType = PatternProviderUtils.getPatternProviderType(null, targetPart);
                            if (!partType.equals("Unknown")) {
                                hasValidProvider = true;
                                providerType = partType;
                                direction = actualDirection;
                            } else {
                                // 检查中心部件（线缆）
                                IPart centerPart = partHost.getPart(null);
                                if (centerPart != null) {
                                    String centerType = PatternProviderUtils.getPatternProviderType(null, centerPart);
                                    if (!centerType.equals("Unknown")) {
                                        hasValidProvider = true;
                                        providerType = centerType;
                                        direction = null;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    UselessMod.LOGGER.error("检查面板形式样板供应器失败: {}", e.getMessage());
                }
            }
        }
        
        if (hasValidProvider) {
            // 创建主方块键
            com.sorrowmist.useless.utils.pattern.PatternProviderKey masterKey = new com.sorrowmist.useless.utils.pattern.PatternProviderKey(masterPos, direction != null ? direction : Direction.UP);
            
            // 如果当前主方块已经是从端，先移除它与原主端的关系
            if (PatternProviderManager.getSlaveToMaster().containsKey(masterKey)) {
                com.sorrowmist.useless.utils.pattern.PatternProviderKey oldMaster = PatternProviderManager.getSlaveToMaster().remove(masterKey);
                Set<com.sorrowmist.useless.utils.pattern.PatternProviderKey> oldSlaves = PatternProviderManager.getMasterToSlaves().get(oldMaster);
                if (oldSlaves != null) {
                    oldSlaves.remove(masterKey);
                    if (oldSlaves.isEmpty()) {
                        PatternProviderManager.getMasterToSlaves().remove(oldMaster);
                    }
                }
            }
            
            // 确保主端映射存在
            if (!PatternProviderManager.getMasterToSlaves().containsKey(masterKey)) {
                PatternProviderManager.getMasterToSlaves().put(masterKey, new HashSet<>());
            }
            
            // 更新当前选择的主方块
            PatternProviderManager.setCurrentSelectedMaster(masterKey);
            
            if (player != null) {
                player.sendSystemMessage(Component.literal("已将 " + providerType + " 设置为主样板供应器").withStyle(ChatFormatting.GREEN));
            }
            
            // 保存同步数据
            if (world instanceof ServerLevel) {
                PatternProviderManager.saveSyncData((ServerLevel) world);
            }
        } else {
            if (player != null) {
                player.sendSystemMessage(Component.literal("该位置不包含有效的样板供应器").withStyle(ChatFormatting.RED));
            }
        }
    }

    /**
     * 添加为从样板供应器
     */
    public static void addAsSlave(Level world, BlockPos slavePos, Direction direction, Player player) {
        // 使用当前选择的主方块
        com.sorrowmist.useless.utils.pattern.PatternProviderKey masterKey = PatternProviderManager.getCurrentSelectedMaster();
        if (masterKey == null) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("请先设置一个主样板供应器").withStyle(ChatFormatting.RED));
            }
            return;
        }
        
        // 检查从方块位置是否包含有效的样板供应器
        boolean hasValidProvider = false;
        String slaveProviderType = "Unknown";
        
        // 获取从方块实体
        BlockEntity blockEntity = world.getBlockEntity(slavePos);
        if (blockEntity != null) {
            // 检查是否是直接放置的样板供应器
            String className = blockEntity.getClass().getName();
            if (className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                    blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                    className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") ||
                    className.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity")) {
                slaveProviderType = PatternProviderUtils.getPatternProviderType(blockEntity, null);
                hasValidProvider = !slaveProviderType.equals("Unknown");
            } 
            // 如果不是，检查是否包含样板供应器部件
            else {
                try {
                    // 检查是否是IPartHost，包含部件
                    if (blockEntity instanceof IPartHost partHost) {
                        // 找到与点击位置最匹配的样板供应器方向
                        Direction actualDirection = PatternProviderUtils.findMatchingDirection(partHost, slavePos, direction, player);
                        
                        // 使用实际方向检查部件
                        IPart targetPart = partHost.getPart(actualDirection);
                        if (targetPart != null) {
                            String partType = PatternProviderUtils.getPatternProviderType(null, targetPart);
                            if (!partType.equals("Unknown")) {
                                hasValidProvider = true;
                                slaveProviderType = partType;
                                direction = actualDirection;
                            } else {
                                // 检查中心部件（线缆）
                                IPart centerPart = partHost.getPart(null);
                                if (centerPart != null) {
                                    String centerType = PatternProviderUtils.getPatternProviderType(null, centerPart);
                                    if (!centerType.equals("Unknown")) {
                                        hasValidProvider = true;
                                        slaveProviderType = centerType;
                                        direction = null;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    UselessMod.LOGGER.error("检查从端面板形式样板供应器失败: {}", e.getMessage());
                }
            }
        }
        
        if (hasValidProvider) {
            // 确保主端映射存在
            if (!PatternProviderManager.getMasterToSlaves().containsKey(masterKey)) {
                PatternProviderManager.getMasterToSlaves().put(masterKey, new HashSet<>());
            }
            
            // 获取主方块的类型
            String masterProviderType = PatternProviderUtils.getMasterProviderType(world, masterKey);
            
            // 确保只有同类样板供应器可以绑定
            if (!slaveProviderType.equals(masterProviderType)) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("只有同类样板供应器可以绑定").withStyle(ChatFormatting.RED));
                }
                return;
            }
            
            // 创建从方块的键
            com.sorrowmist.useless.utils.pattern.PatternProviderKey slaveKey = new com.sorrowmist.useless.utils.pattern.PatternProviderKey(slavePos, direction != null ? direction : Direction.UP);
            
            // 检查是否是方块形式
            String className = blockEntity.getClass().getName();
            boolean isBlockForm = className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                                 blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                                 className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") ||
                                 className.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity");
            
            // 如果该方块已经是从方块，先移除其从方块关系
            if (isBlockForm) {
                // 方块形式：检查该位置是否有任何从端关系（不考虑方向）
                for (com.sorrowmist.useless.utils.pattern.PatternProviderKey existingSlaveKey : new HashSet<>(PatternProviderManager.getSlaveToMaster().keySet())) {
                    if (existingSlaveKey.getPos().equals(slavePos)) {
                        com.sorrowmist.useless.utils.pattern.PatternProviderKey oldMaster = PatternProviderManager.getSlaveToMaster().remove(existingSlaveKey);
                        if (oldMaster != null) {
                            Set<com.sorrowmist.useless.utils.pattern.PatternProviderKey> oldSlaves = PatternProviderManager.getMasterToSlaves().get(oldMaster);
                            if (oldSlaves != null) {
                                oldSlaves.remove(existingSlaveKey);
                                if (oldSlaves.isEmpty()) {
                                    PatternProviderManager.getMasterToSlaves().remove(oldMaster);
                                }
                            }
                        }
                        break; // 只需要移除一个，因为一个位置只能有一个从端
                    }
                }
            } else {
                // 面板形式：需要考虑方向
                if (PatternProviderManager.getSlaveToMaster().containsKey(slaveKey)) {
                    com.sorrowmist.useless.utils.pattern.PatternProviderKey oldMaster = PatternProviderManager.getSlaveToMaster().remove(slaveKey);
                    if (oldMaster != null) {
                        Set<com.sorrowmist.useless.utils.pattern.PatternProviderKey> oldSlaves = PatternProviderManager.getMasterToSlaves().get(oldMaster);
                        if (oldSlaves != null) {
                            oldSlaves.remove(slaveKey);
                            if (oldSlaves.isEmpty()) {
                                PatternProviderManager.getMasterToSlaves().remove(oldMaster);
                            }
                        }
                    }
                }
            }
            
            // 如果该方块是主方块，先移除其主方块关系
            if (isBlockForm) {
                // 方块形式：检查该位置是否有任何主端关系（不考虑方向）
                for (com.sorrowmist.useless.utils.pattern.PatternProviderKey existingMasterKey : new HashSet<>(PatternProviderManager.getMasterToSlaves().keySet())) {
                    if (existingMasterKey.getPos().equals(slavePos)) {
                        Set<com.sorrowmist.useless.utils.pattern.PatternProviderKey> oldSlaves = PatternProviderManager.getMasterToSlaves().remove(existingMasterKey);
                        if (oldSlaves != null) {
                            for (com.sorrowmist.useless.utils.pattern.PatternProviderKey oldSlave : oldSlaves) {
                                PatternProviderManager.getSlaveToMaster().remove(oldSlave);
                            }
                        }
                        break; // 只需要移除一个，因为一个位置只能有一个主端
                    }
                }
            } else {
                // 面板形式：需要考虑方向
                if (PatternProviderManager.getMasterToSlaves().containsKey(slaveKey)) {
                    Set<com.sorrowmist.useless.utils.pattern.PatternProviderKey> oldSlaves = PatternProviderManager.getMasterToSlaves().remove(slaveKey);
                    if (oldSlaves != null) {
                        for (com.sorrowmist.useless.utils.pattern.PatternProviderKey oldSlave : oldSlaves) {
                            PatternProviderManager.getSlaveToMaster().remove(oldSlave);
                        }
                    }
                }
            }
            
            // 检查从方块是否已经是当前主方块的从端
            if (PatternProviderManager.getMasterToSlaves().get(masterKey).contains(slaveKey)) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("该样板供应器已经是当前主端的从端").withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
            
            // 检查从方块是否是主方块本身
            if (slaveKey.equals(masterKey)) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("不能将主方块添加为自己的从端").withStyle(ChatFormatting.RED));
                }
                return;
            }
            
            // 添加为从方块
            PatternProviderManager.getMasterToSlaves().get(masterKey).add(slaveKey);
            PatternProviderManager.getSlaveToMaster().put(slaveKey, masterKey);
            
            if (player != null) {
                String providerTypeName = "样板供应器";
                if (slaveProviderType.equals("ExPatternProvider")) {
                    providerTypeName = "扩展样板供应器";
                } else if (slaveProviderType.equals("AECraftingPatternProvider")) {
                    providerTypeName = "AE2普通样板供应器";
                } else if (slaveProviderType.equals("AAEAdvPatternProvider")) {
                    providerTypeName = "高级AE高级样板供应器";
                }
                player.sendSystemMessage(Component.literal("已将此" + providerTypeName + "设为从方块，跟随主方块").withStyle(ChatFormatting.BLUE));
            }
            
            // 立即同步一次
            PatternProviderManager.syncPatternsFromMaster(world, slaveKey, masterKey);
            
            // 获取从端样板供应器并设置为不在终端显示
            if (world instanceof ServerLevel serverLevel) {
                BlockEntity slaveBlockEntity = serverLevel.getBlockEntity(slavePos);
                if (slaveBlockEntity != null) {
                    // 使用反射和条件类加载来设置从端样板供应器不在终端显示
                    try {
                        // 检查是否是IPartHost，包含部件
                        if (slaveBlockEntity instanceof appeng.api.parts.IPartHost partHost) {
                            // 检查指定方向的部件，设置从端样板供应器不在终端显示
                            appeng.api.parts.IPart targetPart = partHost.getPart(direction);
                            if (targetPart != null) {
                                // 处理AE2普通样板供应器
                                if (targetPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                    // 设置AE2普通从端样板供应器不在终端显示
                                    appeng.api.util.IConfigManager configManager = slaveHostPart.getConfigManager();
                                    configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                                } 
                                // 处理其他类型的样板供应器，使用反射
                                else if (PatternProviderManager.checkIfClassImplementsInterface(targetPart, "appeng.api.util.IConfigurableObject")) {
                                    // 使用反射调用getConfigManager方法
                                    Object configurableObject = targetPart;
                                    Class<?> configurableClass = Class.forName("appeng.api.util.IConfigurableObject");
                                    java.lang.reflect.Method getConfigManagerMethod = configurableClass.getMethod("getConfigManager");
                                    Object configManagerObj = getConfigManagerMethod.invoke(configurableObject);
                                    
                                    if (configManagerObj instanceof appeng.api.util.IConfigManager configManager) {
                                        // 设置从端样板供应器不在终端显示
                                        configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                                    }
                                }
                            } else {
                                // 检查中心部件（线缆）
                                appeng.api.parts.IPart centerPart = partHost.getPart(null);
                                if (centerPart != null) {
                                    // 处理AE2普通样板供应器
                                    if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                        // 设置AE2普通从端样板供应器不在终端显示
                                        appeng.api.util.IConfigManager configManager = slaveHostPart.getConfigManager();
                                        configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                                    } 
                                    // 处理其他类型的样板供应器，使用反射
                                    else if (PatternProviderManager.checkIfClassImplementsInterface(centerPart, "appeng.api.util.IConfigurableObject")) {
                                        // 使用反射调用getConfigManager方法
                                        Object configurableObject = centerPart;
                                        Class<?> configurableClass = Class.forName("appeng.api.util.IConfigurableObject");
                                        java.lang.reflect.Method getConfigManagerMethod = configurableClass.getMethod("getConfigManager");
                                        Object configManagerObj = getConfigManagerMethod.invoke(configurableObject);
                                        
                                        if (configManagerObj instanceof appeng.api.util.IConfigManager configManager) {
                                            // 设置从端样板供应器不在终端显示
                                            configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                                        }
                                    }
                                }
                            }
                        } 
                        // 直接放置的样板供应器
                        else {
                            // 处理AE2普通样板供应器
                            if (slaveBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
                                // 直接放置的AE2普通样板供应器
                                appeng.api.util.IConfigManager configManager = slaveHost.getConfigManager();
                                configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                            } 
                            // 处理其他类型的样板供应器，使用反射
                            else if (PatternProviderManager.checkIfClassImplementsInterface(slaveBlockEntity, "appeng.api.util.IConfigurableObject")) {
                                // 使用反射调用getConfigManager方法
                                Object configurableObject = slaveBlockEntity;
                                Class<?> configurableClass = Class.forName("appeng.api.util.IConfigurableObject");
                                java.lang.reflect.Method getConfigManagerMethod = configurableClass.getMethod("getConfigManager");
                                Object configManagerObj = getConfigManagerMethod.invoke(configurableObject);
                                
                                if (configManagerObj instanceof appeng.api.util.IConfigManager configManager) {
                                    // 设置从端样板供应器不在终端显示
                                    configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略任何异常，确保不会因为可选模组缺失而崩溃
                        UselessMod.LOGGER.debug("Error setting slave pattern provider config: {}", e.getMessage());
                    }
                }
            }
            
            // 保存同步数据
            if (world instanceof ServerLevel) {
                PatternProviderManager.saveSyncData((ServerLevel) world);
            }
        } else {
            if (player != null) {
                player.sendSystemMessage(Component.literal("该位置不包含有效的样板供应器").withStyle(ChatFormatting.RED));
            }
        }
    }

    /**
     * 从主样板供应器同步样板到从样板供应器
     */
    public static void syncPatternsFromMaster(Level world, com.sorrowmist.useless.utils.pattern.PatternProviderKey slaveKey, com.sorrowmist.useless.utils.pattern.PatternProviderKey masterKey) {
        // 直接调用PatternProviderManager的同步方法
        PatternProviderManager.syncPatternsFromMaster(world, slaveKey, masterKey);
    }

    /**
     * 重置主样板供应器选择
     */
    public static void resetMasterPatternProvider(Level world) {
        // 取消当前的主方块选择状态
        PatternProviderManager.setCurrentSelectedMaster(null);
        
        // 清空临时的同步时间记录
        PatternProviderManager.getLastSyncTime().clear();
        
        // 保存同步数据（这里实际上不需要保存，因为我们没有修改主从关系）
        // 但为了保持代码一致性，仍然调用保存方法
        if (world instanceof ServerLevel serverLevel) {
            com.sorrowmist.useless.utils.pattern.PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                    com.sorrowmist.useless.utils.pattern.PatternProviderSyncData::load,
                    com.sorrowmist.useless.utils.pattern.PatternProviderSyncData::new,
                    "PatternProviderSyncData"
            );
            syncData.setDirty();
        }
    }
}