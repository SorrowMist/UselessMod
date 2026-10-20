package com.sorrowmist.useless.api.entity;

/**
 * 海龟下蛋倒计时的访问接口。
 *
 * <p>{@code Turtle.layEggCounter} 是包级私有字段，原版也没有公开的 setter
 * （{@code setHasEgg} / {@code setLayingEgg} 同样包级私有）。造化杖的「生物加速」
 * 需要直接推进它，因此由 mixin 侧的 {@code TurtleAccessor} 生成访问器实现。</p>
 *
 * <p><b>为什么接口定义在主代码而不是 mixin 包</b>：本项目的主代码不能直接引用
 * {@code com.sorrowmist.useless.mixin.*} 下的类型（编译期解析不到），
 * 既有做法是「主代码定义普通接口 → mixin 实现它」，见
 * {@code PendingOmniversalPatternHolder} / {@code PatternEncodingLogicMixin}。</p>
 */
public interface TurtleTimerAccess {

    int uselessMod$getLayEggCounter();

    void uselessMod$setLayEggCounter(int value);
}
