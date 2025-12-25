package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.items.EndlessBeafItem;
import appeng.helpers.InventoryAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(appeng.menu.implementations.PatternAccessTermMenu.class)
public class PatternAccessTermMenuMixin {

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void onDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        // 获取实际的PatternAccessTermMenu实例
        appeng.menu.implementations.PatternAccessTermMenu menu = (appeng.menu.implementations.PatternAccessTermMenu)(Object)this;
        
        // 使用反射获取byId字段
        try {
            // 获取byId字段
            java.lang.reflect.Field byIdField = appeng.menu.implementations.PatternAccessTermMenu.class.getDeclaredField("byId");
            byIdField.setAccessible(true);
            
            // 获取byId映射
            var byId = (java.util.Map<Long, Object>) byIdField.get(menu);
            Object containerTracker = byId.get(id);
            
            if (containerTracker != null) {
                // 获取container字段
                java.lang.reflect.Field containerField = containerTracker.getClass().getDeclaredField("container");
                containerField.setAccessible(true);
                
                // 获取PatternContainer实例
                Object container = containerField.get(containerTracker);
                
                // 检查是否是从端样板供应器
                if (isSlavePatternProvider(container)) {
                    // 阻止所有取出操作
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
    }
    
    // 检查是否是从端样板供应器
    private boolean isSlavePatternProvider(Object container) {
        try {
            // 检查是否是普通AE2方块形式的样板供应器
            if (container instanceof appeng.blockentity.crafting.PatternProviderBlockEntity patternProvider) {
                // 检查是否是从端
                return EndlessBeafItem.isSlavePatternProvider(patternProvider.getBlockPos());
            }
            
            // 检查是否是AE2面板形式的普通样板供应器
            if (container instanceof appeng.parts.crafting.PatternProviderPart patternProviderPart) {
                // 获取位置，通过getHost()方法获取IPartHost，再获取BlockEntity，最后获取位置
                try {
                    appeng.api.parts.IPartHost host = patternProviderPart.getHost();
                    if (host != null) {
                        BlockEntity blockEntity = host.getBlockEntity();
                        if (blockEntity != null) {
                            // 检查是否是从端
                            return EndlessBeafItem.isSlavePatternProvider(blockEntity.getBlockPos());
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
            
            // 检查是否是方块形式的扩展样板供应器
            if (container.getClass().getName().equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider")) {
                // 获取位置
                java.lang.reflect.Method getBlockPosMethod = container.getClass().getMethod("getBlockPos");
                var pos = (net.minecraft.core.BlockPos) getBlockPosMethod.invoke(container);
                // 检查是否是从端
                return EndlessBeafItem.isSlavePatternProvider(pos);
            }
            
            // 检查是否是面板形式的扩展样板供应器
            if (container.getClass().getName().equals("com.glodblock.github.extendedae.common.parts.PartExPatternProvider")) {
                // 获取位置，通过getHost()方法获取IPartHost，再获取BlockEntity，最后获取位置
                try {
                    java.lang.reflect.Method getHostMethod = container.getClass().getMethod("getHost");
                    Object host = getHostMethod.invoke(container);
                    if (host != null) {
                        java.lang.reflect.Method getBlockEntityMethod = host.getClass().getMethod("getBlockEntity");
                        Object blockEntity = getBlockEntityMethod.invoke(host);
                        if (blockEntity != null) {
                            java.lang.reflect.Method getBlockPosMethod = blockEntity.getClass().getMethod("getBlockPos");
                            var pos = (net.minecraft.core.BlockPos) getBlockPosMethod.invoke(blockEntity);
                            // 检查是否是从端
                            return EndlessBeafItem.isSlavePatternProvider(pos);
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
            
            // 检查是否是方块形式的Advanced AE高级样板供应器
            if (container.getClass().getName().equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity")) {
                // 获取位置
                java.lang.reflect.Method getBlockPosMethod = container.getClass().getMethod("getBlockPos");
                var pos = (net.minecraft.core.BlockPos) getBlockPosMethod.invoke(container);
                // 检查是否是从端
                return EndlessBeafItem.isSlavePatternProvider(pos);
            }
            
            // 检查是否是方块形式的Advanced AE小型高级样板供应器
            if (container.getClass().getName().equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity")) {
                // 获取位置
                java.lang.reflect.Method getBlockPosMethod = container.getClass().getMethod("getBlockPos");
                var pos = (net.minecraft.core.BlockPos) getBlockPosMethod.invoke(container);
                // 检查是否是从端
                return EndlessBeafItem.isSlavePatternProvider(pos);
            }
            
            // 检查是否是面板形式的Advanced AE高级样板供应器
            if (container.getClass().getName().equals("net.pedroksl.advanced_ae.common.parts.AdvPatternProviderPart")) {
                // 获取位置，通过getHost()方法获取IPartHost，再获取BlockEntity，最后获取位置
                try {
                    java.lang.reflect.Method getHostMethod = container.getClass().getMethod("getHost");
                    Object host = getHostMethod.invoke(container);
                    if (host != null) {
                        java.lang.reflect.Method getBlockEntityMethod = host.getClass().getMethod("getBlockEntity");
                        Object blockEntity = getBlockEntityMethod.invoke(host);
                        if (blockEntity != null) {
                            java.lang.reflect.Method getBlockPosMethod = blockEntity.getClass().getMethod("getBlockPos");
                            var pos = (net.minecraft.core.BlockPos) getBlockPosMethod.invoke(blockEntity);
                            // 检查是否是从端
                            return EndlessBeafItem.isSlavePatternProvider(pos);
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
            
            // 检查是否是面板形式的Advanced AE小型高级样板供应器
            if (container.getClass().getName().equals("net.pedroksl.advanced_ae.common.parts.SmallAdvPatternProviderPart")) {
                // 获取位置，通过getHost()方法获取IPartHost，再获取BlockEntity，最后获取位置
                try {
                    java.lang.reflect.Method getHostMethod = container.getClass().getMethod("getHost");
                    Object host = getHostMethod.invoke(container);
                    if (host != null) {
                        java.lang.reflect.Method getBlockEntityMethod = host.getClass().getMethod("getBlockEntity");
                        Object blockEntity = getBlockEntityMethod.invoke(host);
                        if (blockEntity != null) {
                            java.lang.reflect.Method getBlockPosMethod = blockEntity.getClass().getMethod("getBlockPos");
                            var pos = (net.minecraft.core.BlockPos) getBlockPosMethod.invoke(blockEntity);
                            // 检查是否是从端
                            return EndlessBeafItem.isSlavePatternProvider(pos);
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
        
        return false;
    }
}