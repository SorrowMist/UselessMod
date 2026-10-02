package com.sorrowmist.useless.mixin.create;

import com.sorrowmist.useless.compat.create.StaffLinkStressState;
import com.simibubi.create.content.kinetics.KineticNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把无线传输的应力容量与负载并进动力学网络的合计。
 *
 * <p>网络的容量与应力都是「遍历成员再相加」，而无线那部分不属于任何一个方块：
 * 容量是从远处借来的，负载是远处机器造成的。所以在两个求和的出口处把它们加上。</p>
 *
 * <p>这样做的关键好处是<b>不需要改动力学任何一处判定</b>：过载、停转、转速上限、
 * 各种机器的转速要求，全都是拿这两个合计值比较出来的，补进去之后它们自动就成立了。</p>
 *
 * <h2>为什么还要动 {@code unloaded*} 这三个字段</h2>
 *
 * <p>动力学把网络合计**按方块**存进存档：每个方块写自己的 {@code AddedCapacity} /
 * {@code AddedStress}，重载时用 {@code initFromTE(合计)} 填进 {@code unloadedCapacity} /
 * {@code unloadedStress}，再由 {@code addSilently} 逐块减掉各自的贡献，剩下的就是「还没加载的
 * 成员」的那一份。</p>
 *
 * <p><b>我们的虚拟贡献没有归属方块</b>，于是减不掉、永远留在 {@code unloaded*} 里 ——
 * 表现是「存档读一次，源网络就凭空多出一份负载 / 目标网络凭空多出一份容量」，而且再也回不去
 * （实测症状：源端零负载却报满消耗、可用恒为 0；目标端无动力源却报有容量）。</p>
 *
 * <p>对策：{@code unloadedMembers <= 0} 说明<b>没有任何成员处于未加载状态</b>，此时
 * {@code unloaded*} 按定义就该是 0，直接清掉 —— 既自愈已经存坏的世界，也顺手抹平其它来源的漂移。
 * 写入侧另见 {@code KineticBlockEntityMixin} 里的「存档时扣掉虚拟贡献」。</p>
 */
@Mixin(KineticNetwork.class)
public abstract class KineticNetworkMixin {

    @Shadow
    private float unloadedCapacity;

    @Shadow
    private float unloadedStress;

    @Shadow
    private int unloadedMembers;

    /**
     * 没有任何未加载成员时清掉 {@code unloaded*} 的残留。
     *
     * @return 是否真的清掉了东西（调用方据此决定要不要重新取值）
     */
    private boolean uselessMod$dropOrphanedUnloaded() {
        if (unloadedMembers > 0) {
            return false;
        }
        boolean changed = unloadedCapacity != 0.0F || unloadedStress != 0.0F;
        unloadedCapacity = 0.0F;
        unloadedStress = 0.0F;
        return changed;
    }

    @Inject(method = "calculateCapacity", at = @At("HEAD"), require = 1)
    private void uselessMod$clearOrphanedCapacity(CallbackInfoReturnable<Float> cir) {
        uselessMod$dropOrphanedUnloaded();
    }

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

    @Inject(method = "calculateStress", at = @At("HEAD"), require = 1)
    private void uselessMod$clearOrphanedStress(CallbackInfoReturnable<Float> cir) {
        uselessMod$dropOrphanedUnloaded();
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
