package com.sorrowmist.useless.content.stafflink;

/**
 * 魔源端点契约。
 *
 * <p>魔源刻意<b>不</b>追求 long：Ars Nouveau 自身的 {@code ISourceTile} 全程就是 int，
 * 单个魔源罐的容量与传输速率都离 {@link Integer#MAX_VALUE} 极远，包一层 long 只会多一次
 * 无意义的转换。因此这里沿用 int，与化学品那边「本来就是 long」的情况不同。</p>
 *
 * <p>引入它的原因是搬运路径要统一：方块魔源容器与 <b>AE 网络里的魔源</b>（Ars Énergistique
 * 提供的 {@code SourceKey}）在搬运语义上完全等价，只有先抽象成同一个端点，
 * 「魔源罐 ↔ ME 网络」才配得起来。此前的位置对位置接口（{@code moveSource}）无法表达
 * 「没有方块坐标」的 ME 网络那一端。</p>
 */
public interface SourceHandlerView {

    /** 当前魔源量。 */
    int amount();

    /** 容量。 */
    int capacity();

    /** 抽出最多 {@code amount}，返回实际抽出量。 */
    int extract(int amount, boolean simulate);

    /** 接收最多 {@code amount}，返回实际接收量。 */
    int receive(int amount, boolean simulate);

    /** 单次可搬运上限：两端都取各自速率与请求量的最小值。 */
    default int transferRate() {
        return Integer.MAX_VALUE;
    }
}
