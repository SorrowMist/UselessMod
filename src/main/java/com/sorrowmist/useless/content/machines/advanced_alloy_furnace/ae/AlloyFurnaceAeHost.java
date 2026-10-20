package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface AlloyFurnaceAeHost extends CraftingTaskContext {
    @Nullable
    IManagedGridNode getMainNode();

    int getMaxAETaskCount();

    Iterable<ItemStack> getPatternStacks();

    /** Called after a pending pattern snapshot has been rebuilt on the server thread. */
    default void onPatternsRebuilt() {
    }

    /**
     * 解析一次 ME 网络写入目标，供一次「产物回网」刷写 pass 内复用；网络不可达返回 {@code null}。
     *
     * <p>不提供返回 null 的 default 实现：那样会将「未实现」表现为<b>静默不回网</b>
     * （产物始终留在队列中），因此刻意声明为抽象方法，由编译器强制两个宿主方块实体实现。</p>
     */
    @Nullable
    CraftingAeOutputTarget resolveAeOutputTarget();

    default boolean canPublishPatterns() {
        return true;
    }

    default boolean acceptsPattern(IPatternDetails pattern) {
        return pattern != null;
    }
}
