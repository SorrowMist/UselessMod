package com.sorrowmist.useless.content.blockentities.multiblock;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.sorrowmist.useless.content.menus.MePatternAssemblyMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

/** AE node and pattern inventory for the multiblock furnace. */
public final class MePatternAssemblyBlockEntity extends AEBaseBlockEntity
        implements ICraftingProvider, IInWorldGridNodeHost,
        IGridNodeListener<MePatternAssemblyBlockEntity>, IActionHost, PatternContainer, MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RecoverableItemStackHandler patterns = new RecoverableItemStackHandler(
            ConfigManager::getOmniversalPatternSlots,
            stack -> stack.is(ModItems.OMNIVERSAL_PATTERN.get()),
            this::inventoryChanged);
    private final IManagedGridNode mainNode;
    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    /**
     * AE2 snapshots a provider's patterns when its node is mounted.  The node
     * can become ready before the multiblock controller is linked (or before
     * the grid finishes booting), so keep a dirty bit and retry once the node
     * is actually usable.
     */
    private boolean providerRefreshPending = true;
    private boolean unloading;

    public MePatternAssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ME_PATTERN_ASSEMBLY.get(), pos, state);
        mainNode = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setTagName("node")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this);
    }

    private void inventoryChanged() {
        if (unloading || isRemoved()) return;
        setChanged();
        if (level == null || level.isClientSide) return;
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        if (controller != null) {
            controller.updatePatterns();
        } else {
            requestProviderRefresh();
        }
    }

    public RecoverableItemStackHandler getPatterns() {
        return patterns;
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            setChanged();
            if (level == null || level.isClientSide) return;
            MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
            if (controller != null) {
                controller.updatePatterns();
            } else {
                requestProviderRefresh();
            }
        }
    }

    /** Marks the AE provider cache stale for the controller's next server tick. */
    public void requestProviderRefresh() {
        if (unloading || isRemoved()) return;
        providerRefreshPending = true;
    }

    /**
     * Called by the controller every server tick.  Keeping this retry here is
     * intentional: AE2 may report a grid before its crafting service has
     * completed booting, and a refresh during that window is discarded.
     */
    public void refreshProviderIfReady() {
        if (unloading || isRemoved() || !providerRefreshPending
                || level == null || level.isClientSide) return;

        IGridNode node = mainNode.getNode();
        if (node == null || !mainNode.isReady() || !mainNode.isActive()
                || node.getGrid() == null || !mainNode.hasGridBooted()) {
            return;
        }

        providerRefreshPending = false;
        try {
            ICraftingProvider.requestUpdate(mainNode);
            LOGGER.debug("Refreshed multiblock alloy furnace provider at {} (patterns={})",
                    worldPosition, getAvailablePatterns().size());
            for (IPatternDetails pattern : getAvailablePatterns()) {
                var output = pattern.getPrimaryOutput();
                if (!node.getGrid().getCraftingService().getCraftingFor(output.what()).contains(pattern)) {
                    LOGGER.warn("AE grid did not index multiblock alloy furnace pattern at {} (output={})",
                            worldPosition, output);
                }
            }
        } catch (RuntimeException exception) {
            // Keep the bit set so the next server tick retries after a
            // transient grid/pathing update.
            providerRefreshPending = true;
            LOGGER.debug("Deferred multiblock alloy furnace provider refresh at {}", worldPosition, exception);
        }
    }

    @Nullable
    public MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (unloading || isRemoved() || level == null || controllerPos == null
                || !level.isLoaded(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.isFormed() && core.getStructureGeneration() == structureGeneration) {
            return core;
        }
        return null;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller == null ? List.of() : controller.getAvailablePatterns();
    }

    @Override
    public int getPatternPriority() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller == null ? 0 : controller.getPatternPriority();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && controller.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller == null || controller.isBusy();
    }

    private final InternalInventory terminalInventory = new InternalInventory() {
        @Override
        public int size() {
            return patterns.getActiveSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return slotIndex >= 0 && slotIndex < patterns.getActiveSlots()
                    ? patterns.getStackInSlot(slotIndex)
                    : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (slotIndex >= 0 && slotIndex < patterns.getActiveSlots()
                    && (stack.isEmpty() || patterns.isItemValid(slotIndex, stack))) {
                patterns.setStackInSlot(slotIndex, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return patterns.isItemValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    @Override
    public @Nullable IGrid getGrid() {
        return unloading ? null : mainNode.getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        return true;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return terminalInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        return (long) worldPosition.getZ() << 24 ^ (long) worldPosition.getX() << 8 ^ worldPosition.getY();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return new PatternContainerGroup(AEItemKey.of(new ItemStack(getBlockState().getBlock())),
                Component.translatable("block.useless_mod.me_pattern_assembly"), List.of());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.me_pattern_assembly");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MePatternAssemblyMenu(containerId, inventory, worldPosition);
    }

    @Override
    public void onSaveChanges(MePatternAssemblyBlockEntity nodeOwner, IGridNode node) {
        if (unloading || isRemoved()) return;
        setChanged();
    }

    @Override
    public void onGridChanged(MePatternAssemblyBlockEntity nodeOwner, IGridNode node) {
        if (unloading || isRemoved() || level == null || level.isClientSide) return;
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        if (controller != null) controller.onAeGridChanged();
        requestProviderRefresh();
    }

    @Override
    public void onStateChanged(MePatternAssemblyBlockEntity nodeOwner, IGridNode node,
                               IGridNodeListener.State state) {
        onGridChanged(nodeOwner, node);
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction direction) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        patterns.deserializeNBT(registries, tag.getCompound("Patterns"));
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
        mainNode.loadFromNBT(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Patterns", patterns.serializeNBT(registries));
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
        mainNode.saveToNBT(tag);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        providerRefreshPending = true;
        GridHelper.onFirstTick(this, blockEntity -> {
            if (blockEntity.unloading || blockEntity.isRemoved()) return;
            blockEntity.mainNode.create(getLevel(), getBlockPos());
            blockEntity.requestProviderRefresh();
            blockEntity.inventoryChanged();
        });
    }

    @Override
    public void setRemoved() {
        unloading = true;
        providerRefreshPending = false;
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        providerRefreshPending = false;
        super.onChunkUnloaded();
        mainNode.destroy();
    }
}
