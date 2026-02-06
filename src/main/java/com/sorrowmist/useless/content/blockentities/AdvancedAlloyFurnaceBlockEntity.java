package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.core.constants.NBTConstants;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModRecipeTypes;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdvancedAlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider {

    // ==================== 槽位常量 ====================
    public static final int INPUT_SLOTS_START = 0;
    public static final int INPUT_SLOTS_COUNT = 9;
    public static final int OUTPUT_SLOTS_START = 9;
    public static final int OUTPUT_SLOTS_COUNT = 9;
    public static final int CATALYST_SLOT = 18;
    public static final int MOLD_SLOT = 19;
    public static final int TOTAL_SLOTS = 20;

    // ==================== 流体槽常量 ====================
    public static final int FLUID_TANK_COUNT = 6;
    private static final int FLUID_TANK_CAPACITY = 16000;

    // ==================== 能量常量 ====================
    private static final int ENERGY_CAPACITY = 100000;
    private static final int ENERGY_MAX_RECEIVE = 10000;
    private static final int ENERGY_MAX_EXTRACT = 0;

    // ==================== 流体槽 ====================
    private final FluidTank[] inputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    private final FluidTank[] outputFluidTanks = new FluidTank[FLUID_TANK_COUNT];

    // ==================== 能量管理 ====================
    private final IEnergyManager energyManager = EnergyManager.builder()
                                                              .capacity(ENERGY_CAPACITY)
                                                              .maxReceive(ENERGY_MAX_RECEIVE)
                                                              .maxExtract(ENERGY_MAX_EXTRACT)
                                                              .onChange(this::setChanged)
                                                              .build();
    // ==================== 数据同步 ====================
    private final AdvancedAlloyFurnaceData data = new AdvancedAlloyFurnaceData(this);
    // ==================== 基础属性 ====================
    private int progress = 0;
    private int maxProgress = 200;
    private int currentParallel = 1;
    private int maxParallel = 8;
    private boolean hasMold = false;
    // ==================== 配方相关 ====================
    @Nullable
    private AdvancedAlloyFurnaceRecipe currentRecipe;
    private int catalystUsesRemaining = 0;    // ==================== 物品处理器 ====================
    public AdvancedAlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(), pos, state);

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            this.inputFluidTanks[i] = this.createTank(i, true);
            this.outputFluidTanks[i] = this.createTank(i, false);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AdvancedAlloyFurnaceBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.currentRecipe == null) {
            Optional<AdvancedAlloyFurnaceRecipe> match = entity.findMatchingRecipe();
            if (match.isPresent()) {
                AdvancedAlloyFurnaceRecipe recipe = match.get();

                if (!entity.hasOutputSpace(recipe)) {
                    return;
                }

                if (!entity.consumeRecipeInputs(recipe)) {
                    return;
                }

                entity.currentRecipe = recipe;
                entity.maxProgress = recipe.processTime();
                entity.progress = 0;
                entity.catalystUsesRemaining = recipe.catalystUses();
                entity.setChanged();
            }
        }

        if (entity.currentRecipe != null) {
            int energyRequired = entity.currentRecipe.energy() / entity.currentRecipe.processTime();
            if (!entity.energyManager.canWork(energyRequired)) {
                return;
            }

            entity.energyManager.tryConsumeEnergy(energyRequired);
            entity.progress++;

            if (entity.progress >= entity.maxProgress) {
                entity.produceRecipeOutputs(entity.currentRecipe);
                entity.resetProgress();
            }

            entity.setChanged();
        }
    }

    public ContainerData getData() {
        return this.data;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.literal("AdvancedAlloyFurnace");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AdvancedAlloyFurnaceMenu(id, inventory, this, this.getData());
    }

    public IItemHandler getItemHandler() {
        return this.itemHandler;
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyManager;
    }

    IEnergyManager getEnergyManager() {
        return this.energyManager;
    }

    public FluidTank getInputFluidTank(int index) {
        if (index >= 0 && index < 6) {
            return this.inputFluidTanks[index];
        }
        return new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        if (index >= 0 && index < 6) {
            return this.outputFluidTanks[index];
        }
        return new FluidTank(0);
    }

    public int getEnergy() {
        return this.energyManager.getEnergyStored();
    }
    public void setEnergy(int energy) {
        this.energyManager.setEnergyStored(energy);
        this.setChanged();
    }private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == CATALYST_SLOT) {
                return stack.is(ModTags.CATALYSTS);
            }
            return super.isItemValid(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            AdvancedAlloyFurnaceBlockEntity.this.setChanged();
            if (slot == MOLD_SLOT) {
                AdvancedAlloyFurnaceBlockEntity.this.updateMoldState();
            }
        }
    };

    public int getMaxEnergy() {
        return this.energyManager.getMaxEnergyStored();
    }

    int getCurrentParallel() {
        return this.currentParallel;
    }

    void setCurrentParallel(int parallel) {
        this.currentParallel = Math.min(parallel, this.maxParallel);
        this.setChanged();
    }

    // ==================== Tick处理 ====================

    int getMaxParallel() {
        return this.maxParallel;
    }

    // ==================== MenuProvider ====================

    void setMaxParallel(int maxParallel) {
        this.maxParallel = maxParallel;
    }

    boolean hasMold() {
        return this.hasMold;
    }

    // ==================== 访问器 ====================

    void setHasMold(boolean hasMold) {
        this.hasMold = hasMold;
        this.setChanged();
    }

    int getProgress() {
        return this.progress;
    }

    void setProgress(int progress) {
        this.progress = progress;
        this.setChanged();
    }

    int getMaxProgress() {
        return this.maxProgress;
    }

    void setMaxProgress(int maxProgress) {
        this.maxProgress = maxProgress;
        this.setChanged();
    }

    @Nullable
    public AdvancedAlloyFurnaceRecipe getCurrentRecipe() {
        return this.currentRecipe;
    }

    private FluidTank createTank(int index, boolean isInput) {
        return new FluidTank(FLUID_TANK_CAPACITY) {
            @Override
            protected void onContentsChanged() {
                AdvancedAlloyFurnaceBlockEntity.this.setChanged();
                if (AdvancedAlloyFurnaceBlockEntity.this.level != null
                        && !AdvancedAlloyFurnaceBlockEntity.this.level.isClientSide) {
                    AdvancedAlloyFurnaceBlockEntity.this.level.sendBlockUpdated(
                            AdvancedAlloyFurnaceBlockEntity.this.worldPosition,
                            AdvancedAlloyFurnaceBlockEntity.this.getBlockState(),
                            AdvancedAlloyFurnaceBlockEntity.this.getBlockState(), 3
                    );
                }
            }
        };
    }

    public IFluidHandler getInputFluidHandler() {
        return new FluidTankHandler(this.inputFluidTanks, true);
    }

    public IFluidHandler getOutputFluidHandler() {
        return new FluidTankHandler(this.outputFluidTanks, false);
    }

    public IFluidHandler getCombinedFluidHandler() {
        return new CombinedFluidTankHandler(this.inputFluidTanks, this.outputFluidTanks);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(NBTConstants.INVENTORY)) {
            this.itemHandler.deserializeNBT(registries, tag.getCompound(NBTConstants.INVENTORY));
        }

        if (tag.contains(NBTConstants.ENERGY)) {
            this.energyManager.setEnergyStored(tag.getInt(NBTConstants.ENERGY));
        }

        this.progress = tag.getInt(NBTConstants.PROGRESS);
        this.maxProgress = tag.getInt(NBTConstants.MAX_PROGRESS);
        this.currentParallel = tag.getInt(NBTConstants.CURRENT_PARALLEL);
        this.maxParallel = tag.getInt(NBTConstants.MAX_PARALLEL);
        this.hasMold = tag.getBoolean(NBTConstants.HAS_MOLD);

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            String inputFluidTag = NBTConstants.getInputFluidTag(i);
            if (tag.contains(inputFluidTag)) {
                FluidStack fluid = FluidStack.parseOptional(registries, tag.getCompound(inputFluidTag));
                this.inputFluidTanks[i].setFluid(fluid);
            } else {
                // 如果NBT中没有该标签，说明流体槽为空
                this.inputFluidTanks[i].setFluid(FluidStack.EMPTY);
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            String outputFluidTag = NBTConstants.getOutputFluidTag(i);
            if (tag.contains(outputFluidTag)) {
                FluidStack fluid = FluidStack.parseOptional(registries, tag.getCompound(outputFluidTag));
                this.outputFluidTanks[i].setFluid(fluid);
            } else {
                // 如果NBT中没有该标签，说明流体槽为空
                this.outputFluidTanks[i].setFluid(FluidStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.put(NBTConstants.INVENTORY, this.itemHandler.serializeNBT(registries));
        tag.putInt(NBTConstants.ENERGY, this.energyManager.getEnergyStored());
        tag.putInt(NBTConstants.PROGRESS, this.progress);
        tag.putInt(NBTConstants.MAX_PROGRESS, this.maxProgress);
        tag.putInt(NBTConstants.CURRENT_PARALLEL, this.currentParallel);
        tag.putInt(NBTConstants.MAX_PARALLEL, this.maxParallel);
        tag.putBoolean(NBTConstants.HAS_MOLD, this.hasMold);

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.inputFluidTanks[i].getFluid();
            if (!fluid.isEmpty()) {
                tag.put(NBTConstants.getInputFluidTag(i), fluid.save(registries));
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.outputFluidTanks[i].getFluid();
            if (!fluid.isEmpty()) {
                tag.put(NBTConstants.getOutputFluidTag(i), fluid.save(registries));
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.handleUpdateTag(tag, registries);
        this.loadAdditional(tag, registries);
    }

    private Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe() {
        if (this.level == null) return Optional.empty();

        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = this.level.getRecipeManager()
                                                                           .getAllRecipesFor(
                                                                                   ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());

        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value();
            if (this.canProcessRecipe(recipe)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    private boolean canProcessRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        List<ItemStack> currentInputs = new ArrayList<>();
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
            ItemStack stack = this.itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                currentInputs.add(stack);
            }
        }

        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count();
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (ItemStack stack : currentInputs) {
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
                }
            }

            if (foundCount < requiredCount) {
                return false;
            }
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            boolean found = false;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)
                        && tankFluid.getAmount() >= requiredFluid.getAmount()) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        if (!recipe.catalyst().isEmpty()) {
            ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
            if (!recipe.catalyst().test(catalystStack)) {
                return false;
            }
        }

        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            if (!recipe.mold().test(moldStack)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasOutputSpace(AdvancedAlloyFurnaceRecipe recipe) {
        for (ItemStack output : recipe.outputs()) {
            int remaining = output.getCount();

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (slotStack.isEmpty()) {
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(slotStack, output)) {
                    int space = slotStack.getMaxStackSize() - slotStack.getCount();
                    remaining -= space;
                    if (remaining <= 0) return true;
                }
            }

            if (remaining > 0) return false;
        }

        for (FluidStack outputFluid : recipe.outputFluids()) {
            boolean hasSpace = false;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                if (tankFluid.isEmpty()) {
                    hasSpace = true;
                    break;
                }
                if (FluidStack.isSameFluidSameComponents(tankFluid, outputFluid)) {
                    int space = this.outputFluidTanks[i].getCapacity() - tankFluid.getAmount();
                    if (space >= outputFluid.getAmount()) {
                        hasSpace = true;
                        break;
                    }
                }
            }
            if (!hasSpace) return false;
        }

        return true;
    }

    private boolean consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe) {
        for (var countedIng : recipe.inputs()) {
            long toConsume = countedIng.count();
            var ingredient = countedIng.ingredient();

            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT && toConsume > 0; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    int consumed = (int) Math.min(toConsume, stack.getCount());
                    stack.shrink(consumed);
                    toConsume -= consumed;
                }
            }

            if (toConsume > 0) {
                return false;
            }
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            int toDrain = requiredFluid.getAmount();
            for (int i = 0; i < 6 && toDrain > 0; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    FluidStack drained = this.inputFluidTanks[i].drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                    toDrain -= drained.getAmount();
                }
            }

            if (toDrain > 0) {
                return false;
            }
        }

        if (!recipe.catalyst().isEmpty()) {
            ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
            if (!recipe.catalyst().test(catalystStack)) {
                return false;
            }
        }

        return true;
    }

    // ==================== 流体处理器 ====================

    private void produceRecipeOutputs(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe.catalystUses() > 0 && !recipe.catalyst().isEmpty()) {
            this.catalystUsesRemaining--;
            if (this.catalystUsesRemaining <= 0) {
                ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
                catalystStack.shrink(1);
                this.catalystUsesRemaining = recipe.catalystUses();
            }
        }

        for (ItemStack output : recipe.outputs()) {
            ItemStack toInsert = output.copy();

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(slotStack, toInsert)) {
                    int space = slotStack.getMaxStackSize() - slotStack.getCount();
                    int toAdd = Math.min(space, toInsert.getCount());
                    slotStack.grow(toAdd);
                    toInsert.shrink(toAdd);
                }
            }

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (slotStack.isEmpty()) {
                    this.itemHandler.setStackInSlot(i, toInsert.copy());
                    toInsert.setCount(0);
                }
            }
        }

        for (FluidStack outputFluid : recipe.outputFluids()) {
            FluidStack toInsert = outputFluid.copy();

            for (int i = 0; i < FLUID_TANK_COUNT && !toInsert.isEmpty(); i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                if (tankFluid.isEmpty() || FluidStack.isSameFluidSameComponents(tankFluid, toInsert)) {
                    int filled = this.outputFluidTanks[i].fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
                    toInsert.shrink(filled);
                }
            }
        }
    }

    private void resetProgress() {
        this.progress = 0;
        this.currentRecipe = null;
        this.catalystUsesRemaining = 0;
        this.setChanged();
    }

    private void updateMoldState() {
        ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
        this.hasMold = !moldStack.isEmpty();
        this.setChanged();
    }

    public void clearFluidTank(int tankIndex, boolean isInput) {
        if (tankIndex < 0 || tankIndex >= FLUID_TANK_COUNT) return;

        if (isInput) {
            this.inputFluidTanks[tankIndex].setFluid(FluidStack.EMPTY);
        } else {
            this.outputFluidTanks[tankIndex].setFluid(FluidStack.EMPTY);
        }
        this.setChanged();

        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(
                    this.worldPosition,
                    this.getBlockState(),
                    this.getBlockState(), 3
            );
        }
    }

    // ==================== 数据持久化 ====================

    /**
     * 通用流体槽处理器
     */
    private record FluidTankHandler(FluidTank[] tanks, boolean allowFill) implements IFluidHandler {

        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!this.allowFill) {
                return 0;
            }
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidTank tank = this.tanks[i];
                if (tank.isFluidValid(resource)) {
                    if (tank.getFluid().isEmpty()
                            || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                        int filled = tank.fill(resource, action);
                        if (filled > 0) {
                            return filled;
                        }
                    }
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.tanks[i].drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.tanks[i].getFluid().isEmpty()) {
                    return this.tanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    /**
     * 复合流体槽处理器 - 同时管理输入和输出槽位
     */
    private class CombinedFluidTankHandler implements IFluidHandler {
        private final FluidTank[] inputTanks;
        private final FluidTank[] outputTanks;

        CombinedFluidTankHandler(FluidTank[] inputTanks, FluidTank[] outputTanks) {
            this.inputTanks = inputTanks;
            this.outputTanks = outputTanks;
        }

        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT * 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].getFluid();
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].getFluid();
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].getCapacity();
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].getCapacity();
            }
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].isFluidValid(stack);
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].isFluidValid(stack);
            }
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // 优先填充到输入槽
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidTank tank = this.inputTanks[i];
                if (tank.isFluidValid(resource)) {
                    if (tank.getFluid().isEmpty()
                            || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                        int filled = tank.fill(resource, action);
                        if (filled > 0) {
                            return filled;
                        }
                    }
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // 优先从输出槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.outputTanks[i].drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            // 输出槽没有再从输入槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.inputTanks[i].drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // 优先从输出槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.outputTanks[i].getFluid().isEmpty()) {
                    return this.outputTanks[i].drain(maxDrain, action);
                }
            }
            // 输出槽没有再从输入槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.inputTanks[i].getFluid().isEmpty()) {
                    return this.inputTanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    // ==================== 网络同步 ====================






    // ==================== 配方处理 ====================


    // ==================== 公共方法 ====================


}
