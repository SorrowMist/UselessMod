package com.sorrowmist.useless.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEBaseMenu.class)
public class AEBaseMenuMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void onQuickMoveStack(Player player, int idx, CallbackInfoReturnable<ItemStack> cir) {
        // 获取实际的AEBaseMenu实例
        AEBaseMenu menu = (AEBaseMenu)(Object)this;
        
        // 检查是否是PatternProviderMenu或扩展样板供应器菜单实例
        boolean isPatternMenu = menu instanceof PatternProviderMenu;
        boolean isExPatternMenu = menu.getClass().getName().equals("com.glodblock.github.extendedae.container.ContainerExPatternProvider");
        boolean isAaeAdvPatternMenu = menu.getClass().getName().contains("AdvPatternProvider");
        
        if (isPatternMenu || isExPatternMenu || isAaeAdvPatternMenu) {
            // 检查当前菜单是否属于从端样板供应器
            boolean isSlave = isSlaveMenu(menu);
            
            if (isSlave) {
                // 如果是从端，返回空栈，阻止快速转移
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
    
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void onClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        // 获取实际的AEBaseMenu实例
        AEBaseMenu menu = (AEBaseMenu)(Object)this;
        
        // 检查是否是PatternProviderMenu或扩展样板供应器菜单实例
        boolean isPatternMenu = menu instanceof PatternProviderMenu;
        boolean isExPatternMenu = menu.getClass().getName().equals("com.glodblock.github.extendedae.container.ContainerExPatternProvider");
        boolean isAaeAdvPatternMenu = menu.getClass().getName().contains("AdvPatternProvider");
        
        if (isPatternMenu || isExPatternMenu || isAaeAdvPatternMenu) {
            // 检查当前菜单是否属于从端样板供应器
            boolean isSlave = isSlaveMenu(menu);
            
            if (isSlave) {
                // 阻止所有点击操作
                ci.cancel();
            }
        }
    }
    
    /**
     * 检查当前菜单是否属于从端样板供应器
     * 对于方块形式：检查位置
     * 对于面板形式：检查位置和方向
     */
    private boolean isSlaveMenu(AEBaseMenu menu) {
        try {
            // 检查BlockEntity情况（方块形式）
            if (menu.getBlockEntity() != null) {
                BlockPos pos = menu.getBlockEntity().getBlockPos();
                // 方块形式：直接检查位置
                return com.sorrowmist.useless.items.EndlessBeafItem.isSlavePatternProvider(pos);
            }
            
            // 检查Part情况（面板形式）
            java.lang.reflect.Field partField = AEBaseMenu.class.getDeclaredField("part");
            partField.setAccessible(true);
            Object part = partField.get(menu);
            
            if (part != null) {
                try {
                    // 获取位置
                    BlockPos pos = null;
                    Direction direction = null;
                    
                    // 尝试获取BlockEntity和位置
                    java.lang.reflect.Method getBlockEntityMethod = part.getClass().getMethod("getBlockEntity");
                    Object blockEntity = getBlockEntityMethod.invoke(part);
                    if (blockEntity != null) {
                        java.lang.reflect.Method getBlockPosMethod = blockEntity.getClass().getMethod("getBlockPos");
                        pos = (BlockPos) getBlockPosMethod.invoke(blockEntity);
                    }
                    
                    // 尝试获取方向
                    try {
                        java.lang.reflect.Method getSideMethod = part.getClass().getMethod("getSide");
                        direction = (Direction) getSideMethod.invoke(part);
                    } catch (NoSuchMethodException e) {
                        // 有些面板可能没有getSide方法，尝试获取方向字段
                        try {
                            java.lang.reflect.Field sideField = part.getClass().getDeclaredField("side");
                            sideField.setAccessible(true);
                            direction = (Direction) sideField.get(part);
                        } catch (Exception ex) {
                            // 忽略方向获取异常，继续执行
                        }
                    }
                    
                    if (pos != null) {
                        // 面板形式：检查该位置的所有从端，找到匹配的方向
                        for (com.sorrowmist.useless.items.EndlessBeafItem.PatternProviderKey slaveKey : 
                             com.sorrowmist.useless.items.EndlessBeafItem.slaveToMaster.keySet()) {
                            if (slaveKey.getPos().equals(pos)) {
                                // 如果方向匹配或者方向为null（中心部件），则是从端
                                if (direction == null || slaveKey.getDirection().equals(direction)) {
                                    return true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常，继续执行
                }
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
        
        return false;
    }
}