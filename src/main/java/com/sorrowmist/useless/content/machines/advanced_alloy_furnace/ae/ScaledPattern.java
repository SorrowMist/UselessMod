package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;

/**
 * “一次 AE 推送代表多份操作”的包装契约。
 *
 * <p>处理样板与合成样板各有一个包装实现：处理样板放大的是机器要执行的配方次数，
 * 合成样板放大的是工作台合成的次数。两者都必须让 AE2 从声明值里看出倍率 ——
 * 输入倍率放大后 AE2 才会一次抽出 N 份材料，产物放大后 AE2 才会把 N 份预期产物写进
 * CPU 的 {@code waitingFor}，从而与“每次推送只扣 1 次任务计数”的原版记账保持一致。</p>
 */
public interface ScaledPattern {

    /** @return 被包装的原始样板 */
    IPatternDetails getOriginal();

    /** @return 本次推送代表的原始操作次数，恒为正数 */
    long getOperationsPerPush();
}
