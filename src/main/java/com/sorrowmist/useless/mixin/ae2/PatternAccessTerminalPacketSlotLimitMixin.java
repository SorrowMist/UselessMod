package com.sorrowmist.useless.mixin.ae2;

import appeng.core.network.clientbound.PatternAccessTerminalPacket;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 放宽样板管理终端「单个样板容器」同步槽位的硬上限。
 *
 * <p>AE2 在 {@link PatternAccessTerminalPacket} 的静态初始化里用
 * {@code ByteBufCodecs.map(..., ItemStack.OPTIONAL_STREAM_CODEC, 128)} 构造
 * 「槽位下标 → 样板」映射的编解码器，其中第四个参数是该映射允许的<b>最大条目数</b>，
 * AE2 固定写作 128。该参数同时约束编码与解码两侧，因此它是整条同步链路的硬上限，
 * 与容器真实容量无关。</p>
 *
 * <p>万象合金炉样板总成的槽位容量由配置项 {@code pattern_slots} 决定（默认 108，
 * 经 {@code normalizeInventorySlots} 归一化后上界为 {@link RecoverableItemStackHandler#MAX_SLOTS}）。
 * 当容器内实际样板数超过 128 时，{@code PatternAccessTermMenu#sendFullUpdate} 会把全部非空槽位
 * 装进该映射，随后在 {@code write} 阶段抛
 * {@code EncoderException: N elements exceeded max size of: 128}。该异常发生在服务端编码
 * 自定义载荷时，网络层将其视为不可恢复的编码失败并中断连接，表现为打开样板管理终端或
 * 扩展样板管理终端立即掉线。</p>
 *
 * <p>此处把上限抬到与样板总成最大槽位数一致的取值。采用 {@code Math.max} 而非直接替换，
 * 是为了在 AE2 将来自行提高该上限时保留其更大的取值，避免本注入反向压低配额。</p>
 *
 * <p>注入点选在 {@code <clinit>} 中 {@code ByteBufCodecs.map} 的调用处，直接改写其第四个实参，
 * 因此不需要访问 AE2 的私有字段，也不依赖该类的构造细节；编解码两侧共用同一个
 * {@code SLOTS_STREAM_CODEC}，改一处即可同时放宽编码与解码。</p>
 */
@Mixin(value = PatternAccessTerminalPacket.class, remap = false)
public abstract class PatternAccessTerminalPacketSlotLimitMixin {

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/ByteBufCodecs;map(Ljava/util/function/IntFunction;Lnet/minecraft/network/codec/StreamCodec;Lnet/minecraft/network/codec/StreamCodec;I)Lnet/minecraft/network/codec/StreamCodec;"
            ),
            index = 3
    )
    private static int uselessMod$raiseSyncedSlotLimit(int originalLimit) {
        return Math.max(originalLimit, RecoverableItemStackHandler.MAX_SLOTS);
    }
}