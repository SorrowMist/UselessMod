package com.sorrowmist.useless.mixin.eae;

import com.sorrowmist.useless.core.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern", remap = false)
public class TileAssemblerMatrixPatternMixin {

    /**
     * 修改构造函数中 patternInventory 的硬编码容量。
     * 必须在 exposedInventory 包装它之前改掉，否则两个库存容量会不一致。
     */
    @ModifyConstant(
            method = "<init>",
            constant = @Constant(intValue = 36),
            remap = false
    )
    private int useless$modifyPatternInventorySize(int original) {
        return 36 * ConfigManager.getMatrixPatternCount();
    }
}
