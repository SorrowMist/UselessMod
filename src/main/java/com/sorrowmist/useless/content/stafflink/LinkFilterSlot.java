package com.sorrowmist.useless.content.stafflink;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 过滤器里的一格标记物。
 *
 * <p><b>一格只装一种资源</b>：物品线路装物品本身，流体线路装流体本身。</p>
 *
 * <p>早先这一格统一是 {@link ItemStack}，流体得先换成「装它的桶」才能存进去，于是
 * 「流体过滤」实际是在比物品：没有桶的流体根本标记不上，把流体拖到物品线路上还会变成
 * 一个水桶物品。现在流体以 {@link FluidStack} 直接存，与物品彻底分开。</p>
 *
 * <p>化学品仍是化学品集成自己的「储罐物品」表示（{@code ChemicalCompatProvider#markerForChemical}），
 * 因此落在 {@link #item()} 上——那套抽象只提供「从物品里读出化学品」，没有可序列化的化学品栈。</p>
 */
public record LinkFilterSlot(ItemStack item, FluidStack fluid) {
    public static final LinkFilterSlot EMPTY = new LinkFilterSlot(ItemStack.EMPTY, FluidStack.EMPTY);

    public LinkFilterSlot {
        item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        fluid = fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        if (!fluid.isEmpty()) {
            // 一格只装一种；两个都给时以流体为准（调用方本来也不该这么传）。
            item = ItemStack.EMPTY;
        }
    }

    public static LinkFilterSlot ofItem(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new LinkFilterSlot(stack, FluidStack.EMPTY);
    }

    public static LinkFilterSlot ofFluid(FluidStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new LinkFilterSlot(ItemStack.EMPTY, stack);
    }

    public boolean isEmpty() {
        return item.isEmpty() && fluid.isEmpty();
    }

    /** 这一格装的是流体（否则是物品）。 */
    public boolean isFluid() {
        return !fluid.isEmpty();
    }

    /** 这一格装的是物品。 */
    public boolean isItem() {
        return !item.isEmpty();
    }
}
