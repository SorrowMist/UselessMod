package com.sorrowmist.useless.compat.create;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 应力在多个输出端之间的分配。
 *
 * <p>应力是「源网络能提供多少」与「目标网络需要多少」的比较，而目标可能不止一个。
 * 这里只做纯计算，不碰任何游戏对象，方便单独推敲。</p>
 */
public final class StressAllocationMath {

    private StressAllocationMath() {
    }

    /**
     * 一个耗力部件在给定转速下需要多少应力。
     *
     * <p>动力学里的耗力与转速成正比：方块给出的是「每 RPM 的耗力」，乘上转速才是实际占用。</p>
     */
    public static float power(float stressPerRpm, float rpm) {
        return Math.max(0.0F, stressPerRpm) * Math.abs(rpm);
    }

    /**
     * 把可用应力分给各输出端。
     *
     * <p><b>次序即优先级</b>：先按权重降序，权重相同再按「离最近的输入端更近者优先」。
     * 按次序<b>逐个满足</b>——前一个拿够了才轮到下一个，因此高优先级的输出不会被低优先级的
     * 稀释。这与无线物流里「数量是每个输出各搬多少、不互相瓜分」的语义不同，是刻意的：
     * 应力是有限的共享资源，而机器要的是「要么够、要么过载」，均分只会让所有机器一起降速。</p>
     *
     * <p>可用量为 0（源网络停转或没有容量）时全部给 0：此时输出端应停在过载状态，
     * 而不是拿一个凑合的量继续转。</p>
     *
     * @param demands 各输出端的需求，键用于回填结果
     * @param available 源网络能提供的应力总量
     */
    public static List<Allocation> allocate(List<OutputDemand> demands, float available) {
        if (demands.isEmpty()) {
            return List.of();
        }
        List<OutputDemand> ordered = new ArrayList<>(demands);
        ordered.sort(Comparator.comparingInt(OutputDemand::priority)
                .reversed()
                .thenComparingDouble(OutputDemand::distanceSquared));

        List<Allocation> result = new ArrayList<>(ordered.size());
        if (available <= 0.0F) {
            for (OutputDemand demand : ordered) {
                result.add(new Allocation(demand.key(), 0.0F));
            }
            return result;
        }

        float remaining = available;
        for (OutputDemand demand : ordered) {
            float need = Math.max(0.0F, demand.requestedPower());
            // <b>全给或一点不给。</b>
            //
            // Create 里一张网络的容量只要小于它的应力，整张网络就过载停转 —— 给一半并不能让它
            // 半速运行，那一半纯属白占：回馈会把「已经给出去的量」算成源网络的负载，
            // 于是源网络少了一份额度、目标端却一点没转起来。所以宁可把这份留给后面能吃饱的输出。
            if (need <= remaining) {
                result.add(new Allocation(demand.key(), need));
                remaining -= need;
            } else {
                result.add(new Allocation(demand.key(), 0.0F));
            }
        }
        return result;
    }

    /** 一个输出端分到的应力。 */
    public record Allocation(String key, float power) {
    }

    /**
     * 一个输出端的需求。
     *
     * @param requestedPower  它<b>还需要我们补多少</b>（已经扣掉目标网络自己已有的容量）
     * @param totalPower      它在目标转速下总共需要多少，只用于界面显示
     * @param priority        权重，越大越优先
     * @param distanceSquared 到最近输入端的距离平方，仅作同权重时的次序依据
     */
    public record OutputDemand(String key, float requestedPower, float totalPower,
                               int priority, double distanceSquared) {
    }
}
