package com.sorrowmist.useless.mixin.eae;



import com.sorrowmist.useless.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiAssemblerMatrix$PatternInfo")
public class GuiAssemblerMatrixPatternInfoMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 36))
    private int modifySlotCount(int original) {
        int newSize = 36 * ConfigManager.getMatrixPatternCount();
        System.out.println("Modifying PatternInfo slot count from " + original + " to " + newSize);
        return newSize;
    }
}