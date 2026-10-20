package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.PagedMenuPageMemory;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

public final class MePatternAssemblyMenu extends PagedRecoverableMenu {
    public MePatternAssemblyMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public MePatternAssemblyMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.ME_PATTERN_ASSEMBLY_MENU.get(), containerId, inventory,
                handler(inventory, pos), pos, pageMemory(inventory, pos));
    }

    @Nullable
    private static PagedMenuPageMemory pageMemory(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly
                ? assembly.getPageMemory() : null;
    }

    private static RecoverableItemStackHandler handler(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly) {
            return assembly.getPatterns();
        }
        return new RecoverableItemStackHandler(ConfigManager::getOmniversalPatternSlots, stack -> false, () -> {});
    }
}
