package com.sorrowmist.useless.compat.ars;

import net.minecraft.world.entity.LivingEntity;

/**
 * 「生物加速」对第三方工作生物的桥。
 *
 * <p>刻意<b>不</b>出现任何 Ars Nouveau 类型：{@code BeefTimeAcceleration} 只依赖这个接口，
 * 真正引用 {@code EntityDrygmy} / {@code DrygmyTile} / {@code Alakarkinos} / {@code Whirlisprig} 的
 * 实现类由 {@link ArsCreatureTimeCompatLoader} 在检测到模组后才反射加载，因此没装
 * 新生魔艺的整合包不会触发类解析错误。</p>
 */
public interface ArsCreatureTimeBridge {

    /**
     * 推进目标的工作进度 / 冷却。
     *
     * <p>与 {@code BeefTimeAcceleration#accelerateEntityTimers} 的原版分支同一语义：
     * <b>只写数值字段，不跑 AI、不驱动状态机、不做寻路与移动</b>。目标不是 Ars 工作生物时什么都不做。</p>
     *
     * @param target 被加速的生物
     * @param extra  本 tick 要推进的「计时器」量（= {@code 1 << tickSpeed}）
     */
    void accelerate(LivingEntity target, int extra);
}
