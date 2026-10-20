package com.sorrowmist.useless.mixin.mek;
import com.sorrowmist.useless.utils.MekUtils;
import mekanism.api.math.FloatingLong;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.MekanismUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=MekanismUtils.class,remap=false)
public class MekanismUtilsMixin {
    @Inject(method="getTicks",at=@At("HEAD"),cancellable=true)
    private static void ticks(IUpgradeTile tile,int base,CallbackInfoReturnable<Integer> cir){
        if(tile.supportsUpgrades()) cir.setReturnValue(Math.max(1,(int)Math.ceil(base*MekUtils.time(tile))));
    }
    @Inject(method="getEnergyPerTick",at=@At("HEAD"),cancellable=true)
    private static void energy(IUpgradeTile tile,FloatingLong base,CallbackInfoReturnable<FloatingLong> cir){
        if(tile.supportsUpgrades()) cir.setReturnValue(base.multiply(MekUtils.electricity(tile)));
    }
    @Inject(method="getMaxEnergy(Lmekanism/common/tile/interfaces/IUpgradeTile;Lmekanism/api/math/FloatingLong;)Lmekanism/api/math/FloatingLong;",at=@At("HEAD"),cancellable=true)
    private static void capacity(IUpgradeTile tile,FloatingLong base,CallbackInfoReturnable<FloatingLong> cir){
        if(tile.supportsUpgrades()) cir.setReturnValue(base.multiply(MekUtils.capacity(tile)));
    }
}
