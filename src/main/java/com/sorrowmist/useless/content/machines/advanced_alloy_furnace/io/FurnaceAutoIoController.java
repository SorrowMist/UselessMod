package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;

/**
 * 高级合金炉自动输入输出控制器。
 * <p>
 * 负责与周围容器及 AE 网络之间的物品和流体自动搬运，
 * 从方块实体中剥离，降低其复杂度。
 */
public final class FurnaceAutoIoController {

    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final int[] MATERIAL_INPUT_SLOT_CANDIDATES;
    private static final int[] CATALYST_INPUT_SLOT_CANDIDATES = {CATALYST_SLOT};
    private static final int[] MOLD_INPUT_SLOT_CANDIDATES = {MOLD_SLOT};
    private static final int[] EMPTY_INPUT_SLOT_CANDIDATES = {};

    static {
        MATERIAL_INPUT_SLOT_CANDIDATES = new int[]{
                INPUT_SLOTS_START, INPUT_SLOTS_START + 1, INPUT_SLOTS_START + 2,
                INPUT_SLOTS_START + 3, INPUT_SLOTS_START + 4, INPUT_SLOTS_START + 5,
                INPUT_SLOTS_START + 6, INPUT_SLOTS_START + 7, INPUT_SLOTS_START + 8,
        };
    }

    /**
     * 自动 IO 控制器所需的上下文回调。
     */
    public interface Context extends FurnaceFaceAccessor {
        boolean isAutoInputEnabled();

        boolean isAutoOutputEnabled();

        long tryOutputToAE(ItemStack stack);

        long tryOutputFluidToAE(FluidStack stack);

        void markChanged();
    }

    private final Context context;
    private final ItemStackHandler itemHandler;
    private final FluidTank[] inputFluidTanks;
    private final FluidTank[] outputFluidTanks;
    private final BlockPos worldPosition;

    public FurnaceAutoIoController(Context context, ItemStackHandler itemHandler,
                                   FluidTank[] inputFluidTanks, FluidTank[] outputFluidTanks,
                                   BlockPos worldPosition) {
        this.context = context;
        this.itemHandler = itemHandler;
        this.inputFluidTanks = inputFluidTanks;
        this.outputFluidTanks = outputFluidTanks;
        this.worldPosition = worldPosition;
    }

    /**
     * 自动输入输出物品和流体到周围的容器。
     */
    public void tick(Level level) {
        if (level.isClientSide) return;

        if (this.context.isAutoInputEnabled()) {
            this.autoInputFromSurroundings(level);
        }

        if (this.context.isAutoOutputEnabled()) {
            this.autoOutputToSurroundings(level);
        }
    }

    /**
     * 从周围容器自动输入物品和流体到机器。
     * 仅从开启了对应面模式的面进行输入。
     */
    private void autoInputFromSurroundings(Level level) {
        Direction facing = this.context.getFacing();
        for (Direction dir : Direction.values()) {
            FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
            if (face == null) continue;
            FurnaceFaceMode mode = this.context.getFaceMode(face);
            if (!mode.allowsAny()) continue;

            BlockPos srcPos = this.worldPosition.relative(dir);
            BlockEntity srcEntity = level.getBlockEntity(srcPos);
            if (srcEntity == null) continue;

            // 输入物品
            if (mode.allowsMaterialInput() || mode.allowsCatalystInput() || mode.allowsMoldInput()) {
                IItemHandler srcHandler = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, srcPos, srcEntity.getBlockState(), srcEntity, dir.getOpposite());
                if (srcHandler != null) {
                    IItemHandler selfHandler = new FurnaceSidedItemHandler(this.itemHandler, dir, this.context);
                    int[] targetSlots = getAutoInputSlotCandidates(mode);
                    for (int srcSlot = 0; srcSlot < srcHandler.getSlots(); srcSlot++) {
                        ItemStack extracted = srcHandler.extractItem(srcSlot, Integer.MAX_VALUE, true);
                        if (extracted.isEmpty()) continue;

                        ItemStack remaining = extracted;
                        for (int machineSlot : targetSlots) {
                            remaining = selfHandler.insertItem(machineSlot, remaining, false);
                            if (remaining.isEmpty()) break;
                        }

                        int moved = extracted.getCount() - remaining.getCount();
                        if (moved > 0) {
                            srcHandler.extractItem(srcSlot, moved, false);
                            this.context.markChanged();
                        }
                    }
                }
            }

            // 输入流体
            if (mode.allowsMaterialInput()) {
                IFluidHandler srcFluidHandler = level.getCapability(
                        Capabilities.FluidHandler.BLOCK, srcPos, srcEntity.getBlockState(), srcEntity, dir.getOpposite());
                if (srcFluidHandler != null) {
                    IFluidHandler selfFluidHandler = new FurnaceSidedFluidHandler(this.inputFluidTanks, this.outputFluidTanks, dir, this.context);
                    FluidStack drained = srcFluidHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                    if (!drained.isEmpty()) {
                        int filled = selfFluidHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            srcFluidHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                            this.context.markChanged();
                        }
                    }
                }
            }
        }
    }

    private static int[] getAutoInputSlotCandidates(FurnaceFaceMode mode) {
        if (mode.allowsMaterialInput()) return MATERIAL_INPUT_SLOT_CANDIDATES;
        if (mode.allowsCatalystInput()) return CATALYST_INPUT_SLOT_CANDIDATES;
        if (mode.allowsMoldInput()) return MOLD_INPUT_SLOT_CANDIDATES;
        return EMPTY_INPUT_SLOT_CANDIDATES;
    }

    /**
     * 自动输出物品和流体到周围容器和 AE 网络。
     * 仅输出到开启了"原材料输出"模式的面。
     */
    private void autoOutputToSurroundings(Level level) {
        // AE网络输出不受面模式控制
        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long inserted = this.context.tryOutputToAE(stack);
            if (inserted > 0) {
                stack.shrink((int) inserted);
                this.context.markChanged();
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.outputFluidTanks[i].getFluid();
            if (fluid.isEmpty()) continue;
            long inserted = this.context.tryOutputFluidToAE(fluid);
            if (inserted > 0) {
                this.outputFluidTanks[i].drain((int) inserted, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        Direction facing = this.context.getFacing();

        // 输出物品到周围容器（仅允许输出模式的面）
        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            for (Direction dir : ALL_DIRECTIONS) {
                FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
                if (face == null) continue;
                if (!this.context.getFaceMode(face).allowsMaterialOutput()) continue;
                if (this.tryOutputItemToDirection(level, slot, dir)) {
                    this.context.markChanged();
                    stack = this.itemHandler.getStackInSlot(slot);
                    if (stack.isEmpty()) break;
                }
            }
        }

        // 输出流体到周围容器（仅允许输出模式的面）
        for (int tankIndex = 0; tankIndex < FLUID_TANK_COUNT; tankIndex++) {
            FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
            if (fluid.isEmpty()) continue;
            for (Direction dir : ALL_DIRECTIONS) {
                FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
                if (face == null) continue;
                if (!this.context.getFaceMode(face).allowsMaterialOutput()) continue;
                int filled = this.tryOutputFluidToDirection(level, tankIndex, dir);
                if (filled > 0) {
                    this.context.markChanged();
                    fluid = this.outputFluidTanks[tankIndex].getFluid();
                    if (fluid.isEmpty()) break;
                }
            }
        }
    }

    /**
     * 尝试向指定方向输出物品。
     *
     * @return 是否成功输出至少一部分物品
     */
    private boolean tryOutputItemToDirection(Level level, int slot, Direction direction) {
        BlockPos targetPos = this.worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null) return false;

        IItemHandler targetHandler = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                targetPos,
                targetEntity.getBlockState(),
                targetEntity,
                direction.getOpposite()
        );

        if (targetHandler == null) return false;

        ItemStack stack = this.itemHandler.getStackInSlot(slot);
        if (stack.isEmpty()) return false;

        for (int targetSlot = 0; targetSlot < targetHandler.getSlots(); targetSlot++) {
            ItemStack remaining = targetHandler.insertItem(targetSlot, stack, false);
            if (remaining.getCount() != stack.getCount()) {
                this.itemHandler.setStackInSlot(slot, remaining);
                this.context.markChanged();
                if (remaining.isEmpty()) {
                    return true;
                } else {
                    stack = remaining;
                }
            }
        }

        return false;
    }

    /**
     * 尝试向指定方向输出流体。
     *
     * @return 成功输出的流体量
     */
    private int tryOutputFluidToDirection(Level level, int tankIndex, Direction direction) {
        BlockPos targetPos = this.worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null) return 0;

        IFluidHandler targetHandler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                targetPos,
                targetEntity.getBlockState(),
                targetEntity,
                direction.getOpposite()
        );

        if (targetHandler == null) return 0;

        FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
        if (fluid.isEmpty()) return 0;

        int filled = targetHandler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            this.outputFluidTanks[tankIndex].drain(filled, IFluidHandler.FluidAction.EXECUTE);
            this.context.markChanged();
        }

        return filled;
    }
}
