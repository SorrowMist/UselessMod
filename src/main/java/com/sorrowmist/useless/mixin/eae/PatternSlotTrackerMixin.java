package com.sorrowmist.useless.mixin.eae;

import appeng.util.inv.AppEngInternalInventory;
import com.sorrowmist.useless.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.glodblock.github.extendedae.container.ContainerAssemblerMatrix$PatternSlotTracker")
public class PatternSlotTrackerMixin {

    /**
     * 修改PatternSlotTracker构造函数中client库存的创建
     * 将硬编码的36改为从配置获取
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "appeng/util/inv/AppEngInternalInventory",
                    ordinal = 0
            ),
            remap = false
    )
    private AppEngInternalInventory modifyClientInventory(int size) {
        // 使用配置的倍数，而不是硬编码的36
        return new AppEngInternalInventory(36 * ConfigManager.getMatrixPatternCount());
    }
}