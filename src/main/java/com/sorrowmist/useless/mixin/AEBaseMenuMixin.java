package com.sorrowmist.useless.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
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
            
            if (isPatternMenu || isExPatternMenu) {
                // 获取当前菜单的区块位置，支持BlockEntity和IPart
                BlockPos pos = null;
                if (menu.getBlockEntity() != null) {
                    pos = menu.getBlockEntity().getBlockPos();
                } else {
                // 尝试获取IPart
                try {
                    java.lang.reflect.Field partField = AEBaseMenu.class.getDeclaredField("part");
                    partField.setAccessible(true);
                    Object part = partField.get(menu);
                    if (part != null) {
                        try {
                            // 首先尝试直接调用getBlockPos方法（适用于BlockEntity情况）
                            java.lang.reflect.Method getBlockPosMethod = part.getClass().getMethod("getBlockPos");
                            pos = (BlockPos) getBlockPosMethod.invoke(part);
                        } catch (NoSuchMethodException e) {
                            // 如果没有getBlockPos方法，尝试通过getBlockEntity方法获取（适用于Part情况）
                            try {
                                java.lang.reflect.Method getBlockEntityMethod = part.getClass().getMethod("getBlockEntity");
                                Object blockEntity = getBlockEntityMethod.invoke(part);
                                if (blockEntity != null) {
                                    java.lang.reflect.Method getBlockPosMethod = blockEntity.getClass().getMethod("getBlockPos");
                                    pos = (BlockPos) getBlockPosMethod.invoke(blockEntity);
                                }
                            } catch (Exception ex) {
                                // 忽略反射异常
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
                
                // 检查该位置是否是从端样板供应器
                if (pos != null && EndlessBeafItem.isSlavePatternProvider(pos)) {
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
            
            if (isPatternMenu || isExPatternMenu) {
                // 获取当前菜单的区块位置，支持BlockEntity和IPart
                BlockPos pos = null;
                if (menu.getBlockEntity() != null) {
                    pos = menu.getBlockEntity().getBlockPos();
                } else {
                // 尝试获取IPart
                try {
                    java.lang.reflect.Field partField = AEBaseMenu.class.getDeclaredField("part");
                    partField.setAccessible(true);
                    Object part = partField.get(menu);
                    if (part != null) {
                        try {
                            // 首先尝试直接调用getBlockPos方法（适用于BlockEntity情况）
                            java.lang.reflect.Method getBlockPosMethod = part.getClass().getMethod("getBlockPos");
                            pos = (BlockPos) getBlockPosMethod.invoke(part);
                        } catch (NoSuchMethodException e) {
                            // 如果没有getBlockPos方法，尝试通过getBlockEntity方法获取（适用于Part情况）
                            try {
                                java.lang.reflect.Method getBlockEntityMethod = part.getClass().getMethod("getBlockEntity");
                                Object blockEntity = getBlockEntityMethod.invoke(part);
                                if (blockEntity != null) {
                                    java.lang.reflect.Method getBlockPosMethod = blockEntity.getClass().getMethod("getBlockPos");
                                    pos = (BlockPos) getBlockPosMethod.invoke(blockEntity);
                                }
                            } catch (Exception ex) {
                                // 忽略反射异常
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
                
                // 检查该位置是否是从端样板供应器
                if (pos != null && EndlessBeafItem.isSlavePatternProvider(pos)) {
                    // 阻止所有点击操作
                    ci.cancel();
                }
            }
    }
}