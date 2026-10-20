package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「可复用输入」判定：被消耗后仍会返还、<b>且返还物仍能当同一个槽的输入</b>的输入。
 *
 * <p>典型例子：神秘农业的注魔水晶（4 精华 + 1 水晶 → 1 高阶精华，水晶不消耗）、可复用模具、
 * 原样返还的工具。</p>
 *
 * <p><b>为什么整批只需要 1 份</b>：AE2 的合成模拟里，只要某个输入的
 * {@link IPatternDetails.IInput#getRemainingKey} 非空，{@code CraftingTreeProcess#updateLimitQty}
 * 就会置 {@code limitQty}，于是 {@code CraftingTreeNode#request} 走 {@code times = 1}
 * —— 一次只模拟一件，并把返还物塞回模拟库存供下一件复用。所以一份合成计划对这类输入
 * 永远只算 1 份，与实际下单份数无关。</p>
 *
 * <p><b>判据与 AE2 的复用条件逐字对齐</b>：AE2 在 {@code CraftingCpuHelper#getValidItemTemplates}
 * 里用 {@code input.isValid(返还键, level)} 过滤可用模板，只有通过过滤的返还物才会被复用。
 * 因此这里要求声明的<b>每一个</b>候选都满足「有返还键，且该键仍是这个槽的合法输入」——
 * 只要有一个候选不满足，AE2 就可能抽到它，此时把倍率压成 1 就会少抽输入。
 * 反过来，{@code CraftingTreeProcess} 的复用判断也只认 {@code isValid}，所以本判据与
 * 「计划需要几份」不可能对不上。</p>
 *
 * <p><b>只对合成样板有意义</b>：{@code AEProcessingPattern} 的返还键硬编码为 {@code null}，
 * 万象样板也恒由处理样板构建，所以处理样板与万象样板在这里恒得空结果，行为完全不变。</p>
 */
final class ReusablePatternInputs {
    private ReusablePatternInputs() {
    }

    /**
     * @param pattern 待判定的样板；{@code null} 或没有输入时返回空
     * @param level   用于 {@link IPatternDetails.IInput#isValid} 的关卡；{@code null} 时退回
     *                「原样返还」这一最保守的充分条件（组件不同的一律不认）
     * @return 槽位下标 → 该槽在整批里只需 1 份时 AE2 期望收回的返还键；无可复用槽时为空 map
     */
    static Map<Integer, AEKey> reusableRemainders(@Nullable IPatternDetails pattern,
                                                  @Nullable Level level) {
        if (pattern == null) {
            return Map.of();
        }
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null || inputs.length == 0) {
            return Map.of();
        }

        Map<Integer, AEKey> result = new LinkedHashMap<>();
        for (int slot = 0; slot < inputs.length; slot++) {
            AEKey remainder = reusableRemainder(inputs[slot], level);
            if (remainder != null) {
                result.put(slot, remainder);
            }
        }
        if (result.isEmpty()) {
            return Map.of();
        }
        // 保持槽位顺序：账本条目顺序与返还物记录顺序都要可复现。
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    /** @return 该槽可复用时返回返还键（以第一个候选算出的为准），否则 {@code null} */
    private static @Nullable AEKey reusableRemainder(@Nullable IPatternDetails.IInput input,
                                                     @Nullable Level level) {
        if (input == null) {
            return null;
        }
        GenericStack[] candidates = input.getPossibleInputs();
        if (candidates == null || candidates.length == 0) {
            return null;
        }

        AEKey remainder = null;
        for (GenericStack candidate : candidates) {
            if (candidate == null || candidate.what() == null) {
                return null;
            }
            AEKey key = candidate.what();
            AEKey remaining = input.getRemainingKey(key);
            if (remaining == null || !isStillUsableAsInput(remaining, key, input, level)) {
                return null;
            }
            if (remainder == null) {
                remainder = remaining;
            }
        }
        return remainder;
    }

    /**
     * 返还物是否仍能当这个槽的输入。
     *
     * <p>没有 {@code Level} 时不能用 {@code isValid}（AE2 对组件不同的返还物要跑
     * {@code recipe.matches}），只能退到「键完全相等」——它只覆盖原样返还那一类
     * （模具、大师注魔水晶），带耐久受损的返还物会判为不可复用。宁可少修，不可错修。</p>
     */
    private static boolean isStillUsableAsInput(AEKey remaining, AEKey key,
                                                IPatternDetails.IInput input,
                                                @Nullable Level level) {
        if (level == null) {
            return remaining.equals(key);
        }
        return input.isValid(remaining, level);
    }
}
