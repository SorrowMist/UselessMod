package com.sorrowmist.useless.mixin.accessor;

import com.sorrowmist.useless.api.entity.BeeTimerAccess;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 为 {@link BeeTimerAccess} 生成访问器，暴露 {@code Bee.setHasNectar(boolean)}（包级私有）。
 *
 * <p>蜜蜂采蜜归巢的「是否携带花粉」只有公开的 {@code hasNectar()} 读取，写入是包级私有的，
 * 造化杖的「生物加速」需要它来加速采蜜。</p>
 */
@Mixin(Bee.class)
public interface BeeAccessor extends BeeTimerAccess {

    @Override
    @Invoker("setHasNectar")
    void uselessMod$setBeeHasNectar(boolean value);
}
