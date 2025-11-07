package com.sorrowmist.useless.mixin.mek;

import com.sorrowmist.useless.utils.MekTemp;
import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.Upgrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to modify Mekanism upgrade limits
 */
@Mixin({Upgrade.class})
public abstract class UpgradeMixin {
    @ModifyVariable(
            method = {"<init>"},
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static String getName(String s) {
        MekTemp.name = s;
        return s;
    }

    @ModifyVariable(
            method = {"<init>"},
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static int toFullStack(int i) {
        return !MekTemp.name.equals("speed") && !MekTemp.name.equals("energy") ? i : MekUtils.MAX_UPGRADE;
    }
}