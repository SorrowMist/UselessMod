package com.sorrowmist.useless.api.crafting.bigint.cpu;

/**
 * 一个大数批次的终局状态。
 */
public enum AlloyFurnaceBigIntegerBatchResult {

    /** 产物已全部写回 ME 网络（或已全部回报给你），批次正常结束。 */
    SUCCESS,

    /**
     * 批次被取消：机器被拆、玩家点了取消、或任务被主动中止。
     *
     * <p>产物<b>不会丢失</b> —— 机器在取消时会把队列里剩余的产物强制写回 ME 网络，
     * 只是不再走正常的逐 tick 节奏。收到该状态时请按「这批已由机器交付，但我没拿到逐条回执」
     * 处理，不要重复补记产物。</p>
     */
    CANCELLED,

    /** 批次在提交阶段就被拒绝，材料未被消费。通常不需要处理（因为 {@code commit} 已经返回了 false）。 */
    REJECTED
}
