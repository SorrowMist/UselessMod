package com.sorrowmist.useless.mixin.create;

import com.sorrowmist.useless.compat.create.StaffLinkStressState;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让被无线驱动的动力学方块对外表现为「自身动力源」。
 *
 * <p>动力学方块的自身转速是写死在方块类里的（水车永远是那个转速、轴永远是 0），
 * 没有任何接口能改写它。所以「把转速从远处注入」只能在询问的入口处拦截：
 * 方块被无线认领之后，它的自身转速就按认领的转速回答，于是它会像一台真正的动力源那样
 * 带动整个网络。</p>
 *
 * <p><b>只在服务端介入。</b>客户端该看到的转速由方块实体同步过去，客户端的判定与渲染
 * 都以那份同步值为准；在这里也改会让两边算出来的网络状态不一致。</p>
 */
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityMixin {

    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true, require = 1)
    private void uselessMod$wirelessGeneratedSpeed(CallbackInfoReturnable<Float> cir) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        StaffLinkStressState.INSTANCE.wirelessSpeed(self).ifPresent(cir::setReturnValue);
    }
}
