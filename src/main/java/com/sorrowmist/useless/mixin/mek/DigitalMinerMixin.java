package com.sorrowmist.useless.mixin.mek;

import mekanism.api.Upgrade;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import com.sorrowmist.useless.utils.MekTemp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityDigitalMiner.class, remap = false)
public abstract class DigitalMinerMixin {

    @Shadow private int delay;
    @Shadow protected abstract void tryMineBlock();
    @Shadow public abstract void recalculateUpgrades(Upgrade upgrade);
    @Shadow public abstract int getDelay();

    @Inject(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/capabilities/energy/MinerEnergyContainer;extract(JLmekanism/api/Action;Lmekanism/api/AutomationType;)J",
                    ordinal = 1,        // 第2次 extract（EXECUTE）
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void onEnergyExtracted(CallbackInfoReturnable<Boolean> cir) {
        if (this.delay < 0) {
            // 补足所有负 delay 的挖掘
            int extraOperations = Math.min(-this.delay, MekTemp.MAX_EXTRA_OPERATIONS_PER_TICK);
            for (int i = 0; i < extraOperations; i++) {
                this.tryMineBlock();
            }
            // 重新计算 Speed 升级（delay 依赖它）
            this.recalculateUpgrades(Upgrade.SPEED);
            // 重置 delay 为正确值
            this.delay = this.getDelay();
        }
    }
}
