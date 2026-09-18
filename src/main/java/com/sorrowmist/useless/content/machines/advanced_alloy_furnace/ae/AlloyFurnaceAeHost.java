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
     * <p>为什么不让实现方用 default 返回 null 兜底：那会让「忘记实现」表现为<b>静默不回网</b>
     * （产物永远留在队列里），所以刻意声明为抽象方法，让编译器强制两个宿主方块实体都实现。</p>
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
