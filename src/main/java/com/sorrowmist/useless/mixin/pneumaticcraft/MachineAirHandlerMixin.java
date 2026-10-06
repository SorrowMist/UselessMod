package com.sorrowmist.useless.mixin.pneumaticcraft;

import com.sorrowmist.useless.content.stafflink.PressureSealRegistry;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.capabilities.MachineAirHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让被无线物流绑定的气动方块「视同已连接」，不再向环境漏气。
 *
 * <h2>为什么要注入</h2>
 *
 * <p>气动工艺的方块在某个面没接空气处理器时会持续漏气：{@code MachineAirHandler#tick} 末尾会调
 * {@code handleAirLeak(...)}，每 tick 销毁 {@code 压力 × 40 + 20} mL。典型受害者是压力室气阀
 * （{@code PressureChamberValveBlockEntity#checkForAirLeak} 只在朝外那面真的挂着
 * {@code AIR_HANDLER_MACHINE} 能力时才认为密封）。无线物流的注入只是 {@code addAir(...)}，
 * 不会建立这样一个邻居 ⇒ 方块永远认为自己漏气，玩家灌进去的气被持续抽走。</p>
 *
 * <h2>为什么注入 {@code tick} 的开头</h2>
 *
 * <p>{@code MachineAirHandler#tick} 的第一行就是 {@code Direction actualLeakDir = leakDir;}
 * —— <b>在方法开头读一次</b>，之后用它决定要不要 {@code handleAirLeak}、要不要发同步包。所以只要在
 * HEAD 把 {@code leakDir} 清成 null，方块<b>自己</b>从头到尾都不会认为在漏气：既不销毁空气，
 * 也不发 {@code PacketUpdatePressureBlock}（客户端因此也没有漏气粒子 / 音效）。</p>
 *
 * <p>清 {@code leakDir} 走的是公开 API {@link IAirHandlerMachine#setSideLeaking}，等价于把那个私有字段
 * 置 null，<b>不需要 {@code @Shadow} 任何私有字段</b>，比直接改字段稳。</p>
 *
 * <h2>不影响什么</h2>
 *
 * <p>{@code leakDir} 只管「未连接漏气」。超压泄压走的是独立的 {@code safetyLeaking / safetyLeakDir}，
 * 爆炸判定 {@code doOverpressureChecks} 也不看它；{@code disperseAir}（向真正相邻的空气处理器扩散）
 * 同样不受影响。所以<b>超压仍会泄压 / 爆炸，与真正相邻的方块仍会正常交换空气</b>。</p>
 */
@Mixin(MachineAirHandler.class)
public abstract class MachineAirHandlerMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void uselessmod$sealWirelessLink(BlockEntity ownerTE, CallbackInfo ci) {
        Level level = ownerTE.getLevel();
        // 顺序要紧：先按侧别早退，保证客户端线程永远不碰服务端集合（单人存档下两者不同线程）。
        if (level == null || level.isClientSide()) {
            return;
        }
        if (PressureSealRegistry.isEmpty()) {
            return;
        }
        if (PressureSealRegistry.isSealed(level, ownerTE.getBlockPos())) {
            ((IAirHandlerMachine) (Object) this).setSideLeaking(null);
        }
    }
}
