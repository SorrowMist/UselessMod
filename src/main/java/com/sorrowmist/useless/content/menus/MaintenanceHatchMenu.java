package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 维护仓校准界面的容器。
 *
 * <p>不承载任何数据，也没有物品槽：小游戏状态存在 {@code MaintenanceHatchBlockEntity} 里，
 * 通过方块实体的同步数据下发，点击结果由服务端判定后再回发。菜单存在的理由是
 * {@code Player#openMenu} 必须有一个容器，以及界面需要 {@code stillValid} 的距离校验。</p>
 */
public final class MaintenanceHatchMenu extends AbstractContainerMenu {

    /** 与服务端方块交互同一把尺子：超过该距离视为已离开界面。 */
    private static final double MAX_DISTANCE_SQR = 64.0D;

    private final BlockPos pos;

    public MaintenanceHatchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.MAINTENANCE_HATCH_MENU.get(), containerId);
        this.pos = pos;
    }

    /** {@code IMenuTypeExtension.create} 要求的客户端工厂形态。 */
    public MaintenanceHatchMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 玩家关闭界面时中止正在进行的校准。
     *
     * <p>只在服务端执行：客户端关闭界面不应影响方块实体状态。中止后进度清零，
     * 下次打开界面需要重新开始，避免玩家离开界面后指针仍在「合法区间」内被算作命中。</p>
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) {
            return;
        }
        if (player.level().getBlockEntity(pos) instanceof MaintenanceHatchBlockEntity hatch) {
            hatch.abortCalibration();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                <= MAX_DISTANCE_SQR;
    }
}
