package com.sorrowmist.useless.client;

import com.sorrowmist.useless.UselessMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 提示框可见性标记的帧末复位。
 *
 * <p>标记由物品的提示框渲染路径置位，按键事件读取；两者并不在同一阶段，因此需要在每帧
 * 渲染收尾时清除，使标记只反映「刚刚结束的这一帧是否绘制过提示框」。</p>
 *
 * <p>复位放在 {@code RenderGuiEvent.Post}：该项事件的 {@code Post} 阶段处于整帧 GUI 绘制完成之后，
 * 早于下一帧的按键分发，不会清除掉同一帧内刚置位的标记。</p>
 */
@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class TooltipVisibilityReset {

    private TooltipVisibilityReset() {
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        TooltipPageState.clearTooltipVisible();
    }
}