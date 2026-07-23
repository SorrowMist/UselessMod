package com.sorrowmist.useless.compat;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * ExtendedAE Plus 兼容层。
 * <p>
 * EAP 的智能翻倍机制通过 {@link ISmartDoublingAwarePattern} 接口标记样板允许翻倍。
 * <p>
 * 翻倍流程：
 * <ol>
 *   <li>标记样板 {@code eap$setAllowScaling(true)}</li>
 *   <li>EAP 在合成计划阶段将样板包装为 {@link ScaledProcessingPattern}</li>
 *   <li>供应器查找时回退为原样板</li>
 *   <li>发配时使用翻倍样板，合金炉在此回退并调整 craftCount</li>
 * </ol>
 */
public final class EapCompat {

    private static final String MOD_ID = "extendedae_plus";

    @Nullable
    private static Boolean isLoaded;

    private EapCompat() {}

    public static boolean isLoaded() {
        if (isLoaded == null) {
            isLoaded = ModList.get().isLoaded(MOD_ID);
        }
        return isLoaded;
    }

    // ==================== 样板标记 ====================

    /**
     * 尝试将样板标记为允许智能翻倍。
     * 如果 EAP 未加载或样板不支持翻倍接口，则静默跳过。
     *
     * @param pattern         要标记的样板
     * @param multiplierLimit 倍数上限，0 表示不限制
     */
    public static void tryMarkPatternForScaling(IPatternDetails pattern, int multiplierLimit) {
        if (!isLoaded() || !(pattern instanceof ISmartDoublingAwarePattern aware)) return;

        aware.eap$setAllowScaling(true);

        if (multiplierLimit > 0) {
            aware.eap$setMultiplierLimit(multiplierLimit);
        }
    }

    /**
     * 尝试将样板标记为允许智能翻倍（无倍数上限）。
     */
    public static void tryMarkPatternForScaling(IPatternDetails pattern) {
        tryMarkPatternForScaling(pattern, 0);
    }

    // ==================== 缩放样板回退 ====================

    /**
     * 如果是 EAP 的 {@link ScaledProcessingPattern}，返回原始样板；
     * 否则返回自身。
     */
    public static IPatternDetails unwrap(IPatternDetails pattern) {
        if (isLoaded() && pattern instanceof ScaledProcessingPattern scaled) {
            return scaled.getOriginal();
        }
        return pattern;
    }

    /**
     * 如果是 ScaledProcessingPattern，返回翻倍倍数；否则返回 1。
     */
    public static int getMultiplier(IPatternDetails pattern) {
        return (int) Math.min(getMultiplierLong(pattern), Integer.MAX_VALUE);
    }

    /** Returns the exact scaling factor without truncating it to an int. */
    public static long getMultiplierLong(IPatternDetails pattern) {
        if (!isLoaded() || !(pattern instanceof ScaledProcessingPattern scaled)) return 1;

        // multiplier 字段为 protected，通过输出量推算
        IPatternDetails original = scaled.getOriginal();
        var outputs = scaled.getOutputs();
        var origOutputs = original.getOutputs();
        if (!outputs.isEmpty() && !origOutputs.isEmpty()) {
            long origAmt = origOutputs.getFirst().amount();
            long scaledAmt = outputs.getFirst().amount();
            if (origAmt > 0) {
                long mul = scaledAmt / origAmt;
                if (mul > 0) return mul;
            }
        }
        return 1;
    }
}
