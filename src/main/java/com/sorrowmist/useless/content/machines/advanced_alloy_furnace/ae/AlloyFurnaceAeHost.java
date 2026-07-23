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

    default boolean canPublishPatterns() {
        return true;
    }

    default boolean acceptsPattern(IPatternDetails pattern) {
        return pattern != null;
    }
}
