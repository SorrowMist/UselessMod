/**
 * long 级资源搬运接口。
 *
 * <p>NeoForge 原生的槽位能力（{@code IItemHandler} / {@code IFluidHandler} /
 * {@code IEnergyStorage}）每一步都收 {@code int}。于是「一次搬 5×10^9 个」这种请求会被先截断成
 * {@link Integer#MAX_VALUE}，剩下的只能靠调用方在外面反复循环补足——既慢，又让调用方必须自己
 * 维护「还剩多少没搬」的状态。</p>
 *
 * <p>这一层把 long 语义提到接口本身：调用方说一次「最多搬这么多」，由实现决定用多少次底层
 * {@code int} 调用把它凑出来。本模组自己的 long 能力（如 {@code IEnergyManager}）会被直接识别并
 * 原生长途直通，不再经过任何 int 中转。</p>
 */
package com.sorrowmist.useless.api.logistics;
