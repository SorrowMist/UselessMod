package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 连锁等价组界面的容器。
 *
 * <p>这个菜单<b>没有任何槽位，也不承载数据</b>：等价组存在玩家的 {@code BeefToolLayout} 里，
 * 编辑通过 {@code BeefToolLayoutUpdatePacket} 走服务端权威的校验与落库，
 * 菜单只在客户端本地 new 出来，从不经 {@code player.openMenu} 下发。</p>
 *
 * <p>它存在的唯一理由，是让界面能继承
 * {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen}：
 * JEI 与 EMI 的原料侧栏默认只画在容器界面旁边（JEI 内置的 handler 只覆盖
 * {@code AbstractContainerScreen}），普通 {@code Screen} 拿不到侧栏，也就没法拖拽。</p>
 */
public final class ChainGroupMenu extends AbstractContainerMenu {

    public ChainGroupMenu(int containerId, Inventory inventory) {
        super(ModMenuType.CHAIN_GROUP_MENU.get(), containerId);
    }

    /** {@code IMenuTypeExtension.create} 要求的工厂形态；本菜单不下发，参数直接忽略。 */
    public ChainGroupMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
