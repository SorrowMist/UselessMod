package com.sorrowmist.useless.mixin.accessor;

import com.sorrowmist.useless.api.entity.TurtleTimerAccess;
import net.minecraft.world.entity.animal.Turtle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 为 {@link TurtleTimerAccess} 生成访问器，暴露 {@code Turtle.layEggCounter}（包级私有）。
 *
 * <p>海龟下蛋的倒计时在 {@code TurtleLayEggGoal#tick()} 里自增，超过
 * {@code adjustedTickDelay(200)} 才真正产卵。造化杖的「生物加速」需要直接推进它。</p>
 */
@Mixin(Turtle.class)
public interface TurtleAccessor extends TurtleTimerAccess {

    @Override
    @Accessor("layEggCounter")
    int uselessMod$getLayEggCounter();

    @Override
    @Accessor("layEggCounter")
    void uselessMod$setLayEggCounter(int value);
}
