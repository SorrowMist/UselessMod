package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.OreGeneratorBlockEntity;
import com.sorrowmist.useless.content.blockentities.OreGeneratorSampleHandler;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

public final class OreGeneratorMenu extends PagedRecoverableMenu {
    private final BlockPos blockPos;
    private final OreGeneratorBlockEntity generator;
    private final ContainerData data;

    public OreGeneratorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public OreGeneratorMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.ORE_GENERATOR_MENU.get(), containerId, inventory,
                handler(inventory, pos), pos, 8, 22, 44, 158, 44, 218);
        blockPos = pos.immutable();
        generator = inventory.player.level().getBlockEntity(pos) instanceof OreGeneratorBlockEntity value
                ? value : null;
        data = generator == null || inventory.player.level().isClientSide
                ? new SimpleContainerData(OreGeneratorBlockEntity.MENU_DATA_COUNT)
                : generator.getMenuData();
        addDataSlots(data);
    }

    private static RecoverableItemStackHandler handler(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof OreGeneratorBlockEntity generator) {
            return generator.getSamples();
        }
        return new OreGeneratorSampleHandler(ConfigManager::getOreGeneratorSlots,
                stack -> false, () -> { });
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public OreGeneratorBlockEntity getGenerator() {
        return generator;
    }

    public long getOutputRate() {
        return join(data.get(0), data.get(1));
    }

    public int getCountdownTicks() {
        return Math.max(0, data.get(2));
    }

    public int getActiveSlots() {
        return Math.max(0, data.get(3));
    }

    public int getConfiguredSlots() {
        return Math.max(1, data.get(4));
    }

    public boolean isOutputToAe() {
        return data.get(5) > 0;
    }

    public boolean isAeOnline() {
        return data.get(6) > 0;
    }

    public int getSampleSlotIndex(Slot slot) {
        int menuSlot = slots.indexOf(slot);
        return menuSlot >= 0 && menuSlot < SLOTS_PER_PAGE
                ? getPage() * SLOTS_PER_PAGE + menuSlot : -1;
    }

    private static long join(int low, int high) {
        return Integer.toUnsignedLong(low) | (long) high << 32;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return generator != null && player.level().getBlockEntity(blockPos) == generator
                && player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D) <= 64.0D;
    }
}
