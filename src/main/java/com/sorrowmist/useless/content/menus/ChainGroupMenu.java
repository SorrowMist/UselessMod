package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 连锁等价组界面的容器。
 *
 * <p>这个菜单<b>不承载任何数据</b>：等价组存在玩家的 {@code BeefToolLayout} 里，
 * 编辑通过 {@code BeefToolLayoutUpdatePacket} 走服务端权威的校验与落库，
 * 菜单只在客户端本地 new 出来，从不经 {@code player.openMenu} 下发。</p>
 *
 * <p>它存在的唯一理由，是让界面能继承
 * {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen}：
 * JEI 与 EMI 的原料侧栏默认只画在容器界面旁边（JEI 内置的 handler 只覆盖
 * {@code AbstractContainerScreen}），普通 {@code Screen} 拿不到侧栏，也就没法拖拽。</p>
 *
 * <p><b>但「是容器屏」对 EMI 还不够。</b>EMI 的
 * {@code dev.emi.emi.screen.EmiScreenBase#of(Screen)} 在
 * {@code instanceof AbstractContainerScreen} 分支里还有一句
 * {@code if (menu.slots.isEmpty()) return EMPTY;} —— 返回 {@code EMPTY} 会让
 * {@code EmiScreenManager#recalculate()} 首句 {@code if (base.isEmpty()) return;} 直接退出，
 * 侧栏的 {@code ScreenSpace} 一个都不建，EMI 侧栏整块不显示（JEI 不看槽位，所以不受影响）。
 * 因此这里塞了一个<b>屏幕外的惰性槽位</b>把 {@code slots} 撑成非空：坐标在屏幕外，
 * {@code getSlotUnderMouse} 的 {@code isHovering} 永远不命中，
 * {@code AbstractContainerScreen#renderSlots} 又会被 {@code isActive()} 挡掉，
 * 所以它既不显示也不可交互，纯粹是给 EMI 看的「这是一个真容器」的标记。</p>
 */
public final class ChainGroupMenu extends AbstractContainerMenu {

    /**
     * 惰性占位槽的坐标。放到任何 GUI 缩放都够不到的地方：面板最宽也就几百像素，
     * 而 {@code getSlotUnderMouse} 用 {@code isHovering(slot.x, slot.y, 16, 16, ...)} 判定，
     * 屏幕外的坐标永远不命中，所以这个槽位不可能被点到、也不可能收到拖拽。
     */
    private static final int OFFSCREEN_SLOT_X = -10000;
    private static final int OFFSCREEN_SLOT_Y = -10000;

    public ChainGroupMenu(int containerId, Inventory inventory) {
        super(ModMenuType.CHAIN_GROUP_MENU.get(), containerId);
        // 见类注释：EMI 要求 slots 非空才给侧栏。这个槽位不渲染、不可交互，
        // 也不参与任何数据流（quickMoveStack 恒返回空）。
        addSlot(new Slot(inventory, 0, OFFSCREEN_SLOT_X, OFFSCREEN_SLOT_Y) {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
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
