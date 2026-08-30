package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import dev.xkmc.youkaishomecoming.content.item.fluid.IYHFluidItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.YHFluidHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Shared handling for Youkai's fluid recipes and their item containers. */
final class YoukaiRecipeAdapterUtils {
    private YoukaiRecipeAdapterUtils() {
    }

    @Nullable
    static IYHFluidItem fluidItem(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.isEmpty() || fluid.getAmount() <= 0) {
            return null;
        }
        Object handler = YHFluidHandler.of(fluid);
        return handler instanceof IYHFluidItem item ? item : null;
    }

    @Nullable
    static ItemStack bottledOutput(@Nullable FluidStack fluid) {
        IYHFluidItem item = fluidItem(fluid);
        if (item == null || fluid == null || item.amount() <= 0
                || fluid.getAmount() < item.amount()) {
            return null;
        }

        ItemStack output = item.toStack(fluid);
        return output.isEmpty() || output.getCount() <= 0 ? null : output;
    }

    @Nullable
    static ItemStack bottledOutputForOperations(@Nullable FluidStack fluid, int operations) {
        IYHFluidItem item = fluidItem(fluid);
        if (item == null || fluid == null || operations <= 0 || item.amount() <= 0
                || (long) fluid.getAmount() * operations != item.amount()) {
            return null;
        }

        ItemStack output = item.asStack(1);
        return output.isEmpty() || output.getCount() <= 0 ? null : output;
    }

    /** Returns the number of source fluid operations needed for one full container. */
    static int operationsPerContainer(@Nullable FluidStack fluid) {
        IYHFluidItem item = fluidItem(fluid);
        if (item == null || fluid == null || fluid.getAmount() <= 0 || item.amount() <= 0
                || item.amount() % fluid.getAmount() != 0) {
            return 0;
        }
        return item.amount() / fluid.getAmount();
    }

    @Nullable
    static Item emptyContainer(@Nullable FluidStack fluid) {
        IYHFluidItem item = fluidItem(fluid);
        if (item == null) {
            return null;
        }
        Item container = item.getContainer();
        return container == null || container == Items.AIR ? null : container;
    }
}
