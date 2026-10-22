package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import com.sorrowmist.useless.network.MaintenanceCalibrationSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 维护仓方块。
 *
 * <p>作为结构件参与多方块校验，同时提供「校准」小游戏的入口：右键打开容器界面。
 * 小游戏状态全部存在方块实体里，方块本身不持有任何状态。</p>
 */
public final class MaintenanceHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public MaintenanceHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaintenanceHatchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MaintenanceHatchBlockEntity hatch) {
            player.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.translatable("block.useless_mod.maintenance_hatch");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player ignored) {
                    return new MaintenanceHatchMenu(containerId, inventory, pos);
                }
            }, buffer -> buffer.writeBlockPos(pos));
            // 界面打开后立刻下发一次状态，避免客户端在收到首个同步包前画出一片空白。
            if (player instanceof ServerPlayer serverPlayer) {
                MaintenanceCalibrationSyncPacket.sendTo(serverPlayer, hatch);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}