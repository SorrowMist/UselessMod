package com.sorrowmist.useless.mixin.mek;

import mekanism.common.tile.machine.TileEntityDigitalMiner;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityDigitalMiner.class, remap = false)
public class TileEntityDigitalMinerMixin {
    @Shadow private int delayTicks;

    @Inject(
            method = "onUpdateServer",
            at = @At(
                    value = "FIELD",
                    target = "Lmekanism/common/tile/machine/TileEntityDigitalMiner;delayTicks:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void modifyDelayTicks(CallbackInfoReturnable<Boolean> cir) {
        // 获取被混入的实例并修改 delayTicks 为 1
        this.delayTicks = 0;
    }
}