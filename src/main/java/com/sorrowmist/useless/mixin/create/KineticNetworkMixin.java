package com.sorrowmist.useless.mixin.create;

import com.sorrowmist.useless.compat.create.StaffLinkStressState;
import com.simibubi.create.content.kinetics.KineticNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把无线传输的应力容量与负载并进动力学网络的合计。
 *
 * <p>网络的容量与应力都是「遍历所有成员再相加」，而无线那部分不属于任何一个方块：
 * 容量是从远处借来的，负载是远处机器造成的。所以在两个求和的出口处把它们加上。</p>
 *
 * <p>这样做的关键好处是<b>不需要改动力学任何一处判定</b>：过载、停转、转速上限、
 * 各种机器的转速要求，全都是拿这两个合计值比较出来的，补进去之后它们自动就成立了。</p>
 */
@Mixin(KineticNetwork.class)
public abstract class KineticNetworkMixin {

    @Inject(method = "calculateCapacity", at = @At("RETURN"), cancellable = true, require = 1)
    private void uselessMod$addWirelessCapacity(CallbackInfoReturnable<Float> cir) {
        KineticNetwork self = (KineticNetwork) (Object) this;
        if (self.id == null) {
            return;
        }
        float extra = StaffLinkStressState.INSTANCE.additionalCapacity(self.id);
        if (extra != 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + extra);
        }
    }

    @Inject(method = "calculateStress", at = @At("RETURN"), cancellable = true, require = 1)
    private void uselessMod$addWirelessStress(CallbackInfoReturnable<Float> cir) {
        KineticNetwork self = (KineticNetwork) (Object) this;
        if (self.id == null) {
            return;
        }
        float extra = StaffLinkStressState.INSTANCE.additionalStress(self.id);
        if (extra != 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + extra);
        }
    }
}
