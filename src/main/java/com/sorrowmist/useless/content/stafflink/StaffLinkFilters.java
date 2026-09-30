package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

/**
 * 把「玩家手上的物品」或「JEI 拖来的原料」按线路的资源类型解释成一格过滤标记。
 *
 * <p>关键在于<b>按类型分流</b>：流体线路只认流体，物品线路只认物品，化学品线路只认能读出
 * 化学品的那种容器。这样就不会出现「往物品线路的过滤器里拖水、结果存了个水桶」，
 * 也不会有「没有桶的流体根本标记不上」。</p>
 *
 * <p>解释不了时一律返回 {@code null}，由调用方保持原样——绝不悄悄把格子清空或塞进错的东西。</p>
 */
public final class StaffLinkFilters {

    private StaffLinkFilters() {
    }

    /**
     * 按线路资源类型解释一个物品（手上拿的，或 JEI 里的物品原料）。
     *
     * <p>流体线路上，容器里装什么就标记什么：水桶 → 水，流体罐 → 罐里那种流体。</p>
     *
     * @return 解释不了时返回 {@code null}
     */
    @Nullable
    public static LinkFilterSlot fromItem(LinkMedium medium, ItemStack stack) {
        if (medium == null || stack == null || stack.isEmpty()) {
            return null;
        }
        if (medium.family() == ResourceFamily.FLUID) {
            FluidStack fluid = fluidInItem(stack);
            return fluid.isEmpty() ? null : LinkFilterSlot.ofFluid(fluid);
        }
        if (medium.family() == ResourceFamily.CHEMICAL) {
            // 化学品线路只认「读得出化学品」的容器；一块石头放进来什么也匹配不到，不如不接。
            ChemicalStackView chemical = chemicalInItem(stack);
            return chemical == null || chemical.isEmpty() ? null : LinkFilterSlot.ofItem(stack);
        }
        return LinkFilterSlot.ofItem(stack);
    }

    /**
     * 按线路资源类型解释一个 JEI 原料。
     *
     * @return 解释不了（类型对不上、或转换失败）时返回 {@code null}
     */
    @Nullable
    public static LinkFilterSlot fromIngredient(LinkMedium medium, Object raw) {
        if (medium == null || raw == null) {
            return null;
        }
        if (raw instanceof FluidStack fluid) {
            // 流体原料只进流体线路：拖到物品线路上不接，免得又变成「桶」。
            return medium.family() == ResourceFamily.FLUID && !fluid.isEmpty()
                    ? LinkFilterSlot.ofFluid(fluid) : null;
        }
        if (raw instanceof ItemStack stack) {
            return fromItem(medium, stack);
        }
        if (medium.family() == ResourceFamily.CHEMICAL) {
            // 化学品原料只有化学品集成自己认得；由它造一只装满该化学品的储罐当标记。
            ChemicalCompatProvider provider = ChemicalCompatProviders.get();
            if (!provider.isAvailable()) {
                return null;
            }
            ItemStack marker = provider.markerForChemical(raw);
            return marker.isEmpty() ? null : LinkFilterSlot.ofItem(marker);
        }
        return null;
    }

    /**
     * 容器物品里装的第一种流体；不是流体容器、或是空罐时返回空栈。
     *
     * <p>老存档迁移也要用，所以是 public。</p>
     */
    public static FluidStack fluidInItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return FluidStack.EMPTY;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contained = handler.getFluidInTank(tank);
            if (!contained.isEmpty()) {
                return contained;
            }
        }
        return FluidStack.EMPTY;
    }

    /** 物品里装的化学品；化学品集成缺失或读不出来时返回 {@code null}。 */
    @Nullable
    private static ChemicalStackView chemicalInItem(ItemStack stack) {
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        return provider.isAvailable() ? provider.chemicalInItem(stack) : null;
    }
}
