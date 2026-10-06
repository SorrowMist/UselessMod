package com.sorrowmist.useless.api.logistics;

/**
 * long 级气压处理器。
 *
 * <p>与 {@link LongEnergyHandler} 同一层契约，但多带两个「气压」特有的量：{@link #volume()} 与
 * {@link #pressure()}。气压与能量、流体都不同：它是<b>状态量</b>而不是纯流量——「搬多少」取决于
 * 目标离目标气压还差多少，而不是每轮固定搬一批。所以端点必须能报出自己的体积与当前空气量，
 * 调用方才算得出「要补多少 / 要抽多少」。</p>
 *
 * <p><b>空气量可以为负。</b> 气动工艺用 {@code air = pressure × volume} 表示空气量，真空就是负值
 * （下限 {@code -volume}，即 -1 bar 的绝对真空）。因此 {@link #stored()} 是有符号的，
 * {@link #capacity()} 只表示正压侧的上限。</p>
 */
public interface LongPressureHandler extends LongResourceHandler {

    /**
     * 移除最多 {@code amount} mL 空气，返回实际移除量。
     *
     * <p><b>下限是 0，不制造真空。</b> 这条是「把源端的气搬到别处」用的——抽到 0 bar（无压）为止，
     * 不会把源端抽成负压。要主动降成真空请用 {@link #setAirTo}。</p>
     */
    long extract(long amount, boolean simulate);

    /** 接收最多 {@code amount} mL 空气，返回实际接收量。 */
    long receive(long amount, boolean simulate);

    /** 当前空气量（mL）。<b>可为负</b>：负值表示真空。 */
    long stored();

    /**
     * 正压侧容量（mL）；AE 网络没有上限，取 {@link Long#MAX_VALUE}。
     *
     * <p>方块侧取的是「危险压力 × 体积」——即开始有爆炸风险的那个压力对应的空气量，
     * 而不是实现自己报的某个常量上限（气动的 {@code maxPressure()} 恒为 10 bar，不反映真实阈值）。</p>
     */
    long capacity();

    /**
     * 有效体积（mL）。
     *
     * <p>AE 网络这类「只有一堆空气、没有容器」的承载返回 {@code 0}——它没有压力概念，
     * 因此目标气压对它无意义。</p>
     */
    long volume();

    /** 当前压力（bar）；无体积的承载恒为 0。 */
    default double pressure() {
        long volume = volume();
        return volume > 0L ? (double) stored() / (double) volume : 0.0D;
    }

    /**
     * 把空气量直接调整到 {@code targetAir}（mL，<b>可为负</b> = 真空），返回实际变化量。
     *
     * <p>供「把多余空气排到环境」这类<b>没有源端</b>的调节使用：降真空时空气量会越过 0 继续下降，
     * 因此不能用 {@link #extract}。无体积的承载（AE 网络）不支持该操作，返回 0。</p>
     */
    default long setAirTo(long targetAir, boolean simulate) {
        return 0L;
    }
}
