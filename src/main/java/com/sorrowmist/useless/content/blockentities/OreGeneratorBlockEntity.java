package com.sorrowmist.useless.content.blockentities;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import com.sorrowmist.useless.content.menus.OreGeneratorMenu;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Server-side state and AE host for the configurable ore generator. */
public final class OreGeneratorBlockEntity extends BlockEntity
        implements MenuProvider, IInWorldGridNodeHost,
        IGridNodeListener<OreGeneratorBlockEntity>, IActionHost {
    public static final int OUTPUT_INTERVAL_TICKS = 20;
    public static final int MAX_SAMPLE_SLOTS = 540;
    public static final long DEFAULT_OUTPUT_RATE = 1L;
    public static final int MENU_DATA_COUNT = 7;

    private static final TagKey<Item> RAW_MATERIALS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "raw_materials")
    );
    private static final TagKey<Item> ORES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "ores")
    );

    private final OreGeneratorSampleHandler samples = new OreGeneratorSampleHandler(
            ConfigManager::getOreGeneratorSlots,
            OreGeneratorBlockEntity::isValidSample,
            this::samplesChanged);
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> lowBits(outputRate);
                case 1 -> highBits(outputRate);
                case 2 -> getCountdownTicks();
                case 3 -> samples.getActiveSlots();
                case 4 -> ConfigManager.getOreGeneratorSlots();
                case 5 -> outputToAe ? 1 : 0;
                case 6 -> isAeNetworkOnline() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // The server owns the live values. Client menus use this method as
            // a packet-backed buffer populated by AbstractContainerMenu.
            switch (index) {
                case 0 -> outputRate = joinBits(value, highBits(outputRate));
                case 1 -> outputRate = joinBits(lowBits(outputRate), value);
                case 2 -> outputTickCounter = Math.max(0,
                        Math.min(OUTPUT_INTERVAL_TICKS - 1, OUTPUT_INTERVAL_TICKS - value));
                case 5 -> outputToAe = value > 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return MENU_DATA_COUNT;
        }
    };

    private long outputRate = DEFAULT_OUTPUT_RATE;
    private int outputTickCounter;
    private boolean outputToAe;
    private boolean unloading;

    public OreGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_GENERATOR.get(), pos, state);
        this.actionSource = IActionSource.ofMachine(this);
        this.mainNode = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setTagName("node")
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    public OreGeneratorSampleHandler getSamples() {
        return samples;
    }

    public long getOutputRate() {
        return outputRate;
    }

    public void setOutputRate(long value) {
        long normalized = Math.max(1L, value);
        if (outputRate != normalized) {
            outputRate = normalized;
            setChanged();
        }
    }

    public int getCountdownTicks() {
        return outputTickCounter <= 0
                ? OUTPUT_INTERVAL_TICKS
                : OUTPUT_INTERVAL_TICKS - outputTickCounter;
    }

    public boolean isOutputToAe() {
        return outputToAe;
    }

    public void setOutputToAe(boolean value) {
        if (outputToAe != value) {
            outputToAe = value;
            setChanged();
        }
    }

    public ContainerData getMenuData() {
        return menuData;
    }

    public boolean isAeNetworkOnline() {
        IGridNode node = mainNode.getNode();
        return mainNode.isActive() && node != null && node.isActive() && node.getGrid() != null;
    }

    public void tick() {
        if (level == null || level.isClientSide || unloading || isRemoved()) return;

        outputTickCounter++;
        if (outputTickCounter < OUTPUT_INTERVAL_TICKS) {
            setChanged();
            return;
        }

        outputTickCounter = 0;
        for (int slot = 0; slot < samples.getActiveSlots(); slot++) {
            ItemStack sample = samples.getStackInSlot(slot);
            if (!isValidSample(sample)) continue;
            outputSample(sample);
        }
        setChanged();
    }

    private void outputSample(ItemStack sample) {
        long remaining = outputRate;
        if (outputToAe) {
            long inserted = tryOutputToAe(sample, remaining);
            remaining -= Math.max(0L, Math.min(remaining, inserted));
        }
        if (remaining <= 0L) return;

        // ItemStack counts are int-sized. Keep this as one stack and let the
        // target handler decide how its own slots can accept that stack.
        ItemStack output = createExternalOutput(sample, remaining);
        if (!output.isEmpty()) {
            tryExportItem(output);
        }
    }

    /** Returns the largest count that can be represented by one external ItemStack. */
    public static int clampExternalCount(long amount) {
        if (amount <= 0L) return 0;
        return amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    static ItemStack createExternalOutput(ItemStack sample, long amount) {
        int count = clampExternalCount(amount);
        if (sample.isEmpty() || count == 0) return ItemStack.EMPTY;

        ItemStack output = sample.copy();
        output.setCount(count);
        return output;
    }

    private long tryOutputToAe(ItemStack sample, long amount) {
        if (amount <= 0L) return 0L;
        IGridNode node = mainNode.getNode();
        if (!mainNode.isActive() || node == null || !node.isActive()) return 0L;
        IGrid grid = node.getGrid();
        if (grid == null) return 0L;

        AEItemKey key = AEItemKey.of(sample);
        if (key == null) return 0L;
        MEStorage storage = grid.getStorageService().getInventory();
        long inserted = storage.insert(key, amount, Actionable.MODULATE, actionSource);
        return Math.max(0L, Math.min(amount, inserted));
    }

    private void tryExportItem(ItemStack stack) {
        if (level == null || stack.isEmpty()) return;

        ItemStack remainder = stack;
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = worldPosition.relative(direction);
            IItemHandler targetHandler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, adjacentPos, direction.getOpposite());
            if (targetHandler == null) continue;

            for (int slot = 0; slot < targetHandler.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = targetHandler.insertItem(slot, remainder, false);
            }
            if (remainder.isEmpty()) return;
        }
    }

    public static boolean isValidSample(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(RAW_MATERIALS) || stack.is(ORES));
    }

    private void samplesChanged() {
        setChanged();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.useless_mod.ore_generator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OreGeneratorMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Samples")) {
            samples.deserializeNBT(registries, tag.getCompound("Samples"));
            normalizeSamples();
        } else if (tag.contains("Inventory")) {
            // Migrate the old single-slot generator input into the first sample
            // slot while deliberately discarding its former stack multiplier.
            ItemStackHandler legacy = new ItemStackHandler(1);
            legacy.deserializeNBT(registries, tag.getCompound("Inventory"));
            ItemStack oldSample = legacy.getStackInSlot(0);
            if (isValidSample(oldSample)) samples.setStackInSlot(0, oldSample);
        }
        outputRate = Math.max(1L, tag.getLong("OutputRate"));
        outputTickCounter = Math.max(0,
                Math.min(OUTPUT_INTERVAL_TICKS - 1, tag.getInt("OutputTickCounter")));
        outputToAe = tag.getBoolean("OutputToAe");
        mainNode.loadFromNBT(tag);
    }

    private void normalizeSamples() {
        for (int slot = 0; slot < samples.getSlots(); slot++) {
            ItemStack sample = samples.getStackInSlot(slot);
            if (sample.isEmpty()) continue;
            if (!isValidSample(sample)) {
                samples.setStackInSlot(slot, ItemStack.EMPTY);
                continue;
            }
            if (sample.getCount() != 1) {
                ItemStack normalized = sample.copy();
                normalized.setCount(1);
                samples.setStackInSlot(slot, normalized);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Samples", samples.serializeNBT(registries));
        tag.putLong("OutputRate", outputRate);
        tag.putInt("OutputTickCounter", outputTickCounter);
        tag.putBoolean("OutputToAe", outputToAe);
        mainNode.saveToNBT(tag);
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(samples.getSlots());
        for (int slot = 0; slot < samples.getSlots(); slot++) {
            inventory.setItem(slot, samples.getStackInSlot(slot));
        }
        if (level != null) Containers.dropContents(level, worldPosition, inventory);
    }

    @Override
    public void onSaveChanges(OreGeneratorBlockEntity nodeOwner, IGridNode node) {
        setChanged();
    }

    @Override
    public void onGridChanged(OreGeneratorBlockEntity nodeOwner, IGridNode node) {
        setChanged();
    }

    @Override
    public void onStateChanged(OreGeneratorBlockEntity nodeOwner, IGridNode node,
                               IGridNodeListener.State state) {
        setChanged();
    }

    public @NotNull IManagedGridNode getMainNode() {
        return mainNode;
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
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        GridHelper.onFirstTick(this, blockEntity ->
                blockEntity.mainNode.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        unloading = true;
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    private static long joinBits(int low, int high) {
        return Integer.toUnsignedLong(low) | (long) high << 32;
    }

    private static int lowBits(long value) {
        return (int) value;
    }

    private static int highBits(long value) {
        return (int) (value >>> 32);
    }
}
