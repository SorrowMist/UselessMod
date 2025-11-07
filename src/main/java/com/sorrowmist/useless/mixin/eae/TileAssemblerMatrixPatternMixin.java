package com.sorrowmist.useless.mixin.eae;

import appeng.util.inv.AppEngInternalInventory;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileAssemblerMatrixPattern.class,remap = false)
public class TileAssemblerMatrixPatternMixin {

    @Mutable @Shadow @Final private AppEngInternalInventory patternInventory;

    /**
     * 在构造函数完成后替换patternInventory
     */
    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void onConstructed(BlockPos pos, BlockState blockState, CallbackInfo ci) {
        TileAssemblerMatrixPattern self = (TileAssemblerMatrixPattern) (Object) this;

        // 创建新的库存实例
        int newSize = 36 * ConfigManager.getMatrixPatternCount();
        this.patternInventory = new AppEngInternalInventory(self, newSize, 1);
        this.patternInventory.setFilter(new TileAssemblerMatrixPattern.Filter(self::getLevel));
    }
}