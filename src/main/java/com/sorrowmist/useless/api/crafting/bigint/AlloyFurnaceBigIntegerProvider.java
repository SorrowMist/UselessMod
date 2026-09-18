package com.sorrowmist.useless.api.crafting.bigint;

import org.jetbrains.annotations.Nullable;

/**
 * 由「支持 BigInteger 批次」的样板供应器方块实体实现的标记接口。
 *
 * <p>{@link AlloyFurnaceBigIntegerApi#findTargets} 就是遍历 AE 网格节点、
 * 用 {@code IGridNode#getOwner() instanceof AlloyFurnaceBigIntegerProvider} 找出候选，
 * 再向它索取 {@link #bigIntegerTarget()}。这样调用方不需要认识本模组的具体方块实体类，
 * 也不需要做反射或 {@code Class.forName}。</p>
 *
 * <p>实现方返回的实例必须<b>仅服务器线程</b>可用。</p>
 */
public interface AlloyFurnaceBigIntegerProvider {

    /**
     * @return 本机当前可用的 bigint 目标；机器未成形、未联网或链接尚未解析时为 {@code null}
     */
    @Nullable AlloyFurnaceBigIntegerTarget bigIntegerTarget();
}
