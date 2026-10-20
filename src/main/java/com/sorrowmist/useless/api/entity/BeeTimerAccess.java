package com.sorrowmist.useless.api.entity;

/**
 * 蜜蜂「是否携带花粉」的写入访问接口。
 *
 * <p>{@code Bee.setHasNectar(boolean)} 是包级私有的，只有公开的 {@code hasNectar()} 可读。
 * 造化杖的「生物加速」需要写入它来加速采蜜归巢，由 mixin 侧的 {@code BeeAccessor} 生成实现。</p>
 *
 * <p><b>为什么接口定义在主代码而不是 mixin 包</b>：本项目的主代码不能直接引用
 * {@code com.sorrowmist.useless.mixin.*} 下的类型（编译期解析不到），
 * 既有做法是「主代码定义普通接口 → mixin 实现它」。</p>
 */
public interface BeeTimerAccess {

    void uselessMod$setBeeHasNectar(boolean value);
}
