package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekanismAccelerationState;
import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.Upgrade;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to modify Mekanism upgrade limits.
 */
@Mixin(value = Upgrade.class, priority = 2000)
public abstract class UpgradeMixin {
    @ModifyVariable(
            method = {"<init>"},
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static String useless$captureName(String name) {
        MekanismAccelerationState.name = name;
        return name;
    }

    @ModifyVariable(
            method = {"<init>"},
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static int useless$maxUpgrade(int original) {
        return !"speed".equals(MekanismAccelerationState.name)
                && !"energy".equals(MekanismAccelerationState.name)
                ? original : MekUtils.getMaxUpgrade();
    }

    /**
     * maxStack is captured when the enum is constructed. Return the live config
     * value for runtime reads too, including config reloads.
     */
    @Inject(method = "getMax", at = @At("HEAD"), cancellable = true, remap = false)
    private void useless$getMax(CallbackInfoReturnable<Integer> cir) {
        Upgrade self = (Upgrade) (Object) this;
        if (self == Upgrade.SPEED || self == Upgrade.ENERGY) {
            cir.setReturnValue(MekUtils.getMaxUpgrade());
        }
    }

    /**
     * NBT loading reads the captured maxStack field directly, bypassing getMax().
     */
    @Redirect(
            method = "buildMap",
            at = @At(
                    value = "FIELD",
                    target = "Lmekanism/api/Upgrade;maxStack:I",
                    opcode = Opcodes.GETFIELD
            ),
            remap = false
    )
    private static int useless$nbtLoadMax(Upgrade upgrade) {
        return upgrade.getMax();
    }

    /**
     * NBT saving also reads maxStack directly; without this redirect a machine
     * with more than 16 upgrades would be clamped back to 16 on save.
     */
    @Redirect(
            method = "saveMap",
            at = @At(
                    value = "FIELD",
                    target = "Lmekanism/api/Upgrade;maxStack:I",
                    opcode = Opcodes.GETFIELD
            ),
            remap = false
    )
    private static int useless$nbtSaveMax(Upgrade upgrade) {
        return upgrade.getMax();
    }
}
