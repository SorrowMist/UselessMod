package com.sorrowmist.useless.mixin.create;

import com.sorrowmist.useless.compat.create.StaffLinkStressState;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
 *
 * <p>另外还负责在<b>存档时</b>把无线虚拟贡献从网络合计里扣掉，理由见下面那个注入点。</p>
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

    /**
     * 补回「上次退出前被无线驱动」的认领。
     *
     * <p><b>为什么必须补。</b>认领表是运行时状态，重启就没了。而方块实体自己的 {@code speed}
     * 是写进存档的 —— 于是重进游戏后，那些被无线驱动的方块会出现一种矛盾状态：
     * 身上还留着转速，但 {@code getGeneratedSpeed()} 因为查不到认领而返回 0。
     * 动力学既不当它是动力源、又看到它在转，接下来 {@code validateKinetics()} 会清掉它的转速、
     * 依赖它的邻居会去 {@code propagateMissingSource}，而传播逻辑把「同一张网络里转速不一致」
     * 判成环路，直接 {@code destroyBlock} 打掉方块。实测症状：**重进游戏后成排的动力合成器
     * 两侧变成掉落物**。</p>
     *
     * <p>{@code initialize()} 在方块实体的第一次 {@code tick()} 里、{@code validateKinetics()}
     * <b>之前</b>执行，所以在这里补上就来得及。补上之后它从第一 tick 起仍然表现为动力源，
     * 引擎随后会把它换成正式认领；若这条线路已经不在了，临时认领会自然过期并被停掉。</p>
     */
    @Inject(method = "initialize", at = @At("TAIL"), require = 1)
    private void uselessMod$reseedWirelessClaim(CallbackInfo ci) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        // 判据：自己在转、却不是任何东西的下游、自身又产生不了转速 —— 只可能是它。
        // 真发电机（水车/风车轴承/创造马达等）自己覆写了 getGeneratedSpeed()，非 0，会被排除；
        // 普通传动件有 source，也会被排除。
        if (self.getTheoreticalSpeed() == 0.0F || self.hasSource()
                || self.getGeneratedSpeed() != 0.0F) {
            return;
        }
        StaffLinkStressState.INSTANCE.reseedClaim(self);
    }

    /**
     * 存档时把「无线借来的容量 / 无线造成的负载」从网络合计里扣掉。
     *
     * <p><b>为什么必须扣。</b>动力学把网络合计<b>按方块</b>存盘：每个方块写自己的
     * {@code AddedCapacity} / {@code AddedStress}，重载时用合计填 {@code unloadedCapacity} /
     * {@code unloadedStress}，再由 {@code addSilently} 逐块减掉各自的贡献。而我们的虚拟贡献
     * <b>没有归属方块</b> —— 减不掉，于是永久留在 {@code unloaded*} 里，表现为「读一次存档，
     * 源网络凭空多出一份负载、目标网络凭空多出一份容量」，而且再也回不去。</p>
     *
     * <p>扣掉之后，写进磁盘的合计与「各方块贡献之和」一致，重载时的加减就能对上账。
     * 只扣 {@code !clientPacket}（真正写盘的那一次）：客户端同步要保留虚拟贡献，
     * 护目镜与界面看到的应当是该网络真实的合计。</p>
     */
    @Inject(
            method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"),
            require = 1)
    private void uselessMod$stripWirelessContribution(CompoundTag compound,
                                                      HolderLookup.Provider registries,
                                                      boolean clientPacket,
                                                      CallbackInfo ci) {
        if (clientPacket) {
            return;
        }
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (self.network == null) {
            return;
        }
        CompoundTag networkTag = compound.getCompound("Network");
        if (networkTag.isEmpty()) {
            return;
        }
        float extraCapacity = StaffLinkStressState.INSTANCE.additionalCapacity(self.network);
        if (extraCapacity != 0.0F) {
            networkTag.putFloat("Capacity",
                    Math.max(0.0F, networkTag.getFloat("Capacity") - extraCapacity));
        }
        float extraStress = StaffLinkStressState.INSTANCE.additionalStress(self.network);
        if (extraStress != 0.0F) {
            networkTag.putFloat("Stress",
                    Math.max(0.0F, networkTag.getFloat("Stress") - extraStress));
        }
    }
}
