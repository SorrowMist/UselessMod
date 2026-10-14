package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
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
    // 自动 IO 连续空转时扫描间隔的上限（tick）
    private static final int MAX_IDLE_BACKOFF_TICKS = 20;
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

        default long tryOutputChemicalToAE(ChemicalStackView stack) {
            return 0L;
        }

        default void handleUnreturnedChemical(ChemicalStackView stack) {
        }

        default ChemicalHandlerView getAdjacentChemicalHandler(Level level, BlockPos pos,
                                                                net.minecraft.world.level.block.state.BlockState state,
                                                                BlockEntity entity, Direction side) {
            return null;
        }

        void markChanged();

        /**
         * 本机物品/流体/化学品存储的修订号，任意内容变化都必须递增。
         * 自动 IO 闲置退避期间用它判断本机是否产出了新东西或腾出了空间，变了就立即重新扫描。
         */
        long getIoStateRevision();
    }

    private final Context context;
    private final ItemStackHandler itemHandler;
    private final FluidTank[] inputFluidTanks;
    private final FluidTank[] outputFluidTanks;
    private final FurnaceChemicalStorage inputChemicalStorage;
    private final FurnaceChemicalStorage outputChemicalStorage;
    private final BlockPos worldPosition;
    // 连续无搬运的扫描轮数，用于逐级拉长扫描间隔
    private int idleRounds = 0;
    // 距下一次扫描还需跳过的 tick 数
    private int idleWaitTicks = 0;
    // 上一轮扫描后看到的内容修订号，用于判断退避期间本机内容是否变化
    private long observedRevision;

    public FurnaceAutoIoController(Context context, ItemStackHandler itemHandler,
                                   FluidTank[] inputFluidTanks, FluidTank[] outputFluidTanks,
                                   BlockPos worldPosition) {
        this(context, itemHandler, inputFluidTanks, outputFluidTanks,
                FurnaceChemicalStorage.DISABLED, FurnaceChemicalStorage.DISABLED, worldPosition);
    }

    public FurnaceAutoIoController(Context context, ItemStackHandler itemHandler,
                                   FluidTank[] inputFluidTanks, FluidTank[] outputFluidTanks,
                                   FurnaceChemicalStorage inputChemicalStorage,
                                   FurnaceChemicalStorage outputChemicalStorage,
                                   BlockPos worldPosition) {
        this.context = context;
        this.itemHandler = itemHandler;
        this.inputFluidTanks = inputFluidTanks;
        this.outputFluidTanks = outputFluidTanks;
        this.inputChemicalStorage = inputChemicalStorage;
        this.outputChemicalStorage = outputChemicalStorage;
        this.worldPosition = worldPosition;
    }

    /**
     * 自动输入输出物品和流体到周围的容器。
     * <p>
     * 一整轮扫描没有任何搬运时，扫描间隔按 1、2、4、8、16 tick 逐级拉长，封顶
     * {@link #MAX_IDLE_BACKOFF_TICKS}，避免机器闲置（输出槽满、AE 网络满、周围容器已空）时
     * 逐 tick 反复查询能力、遍历相邻容器槽位并向 AE 重复提交必然失败的插入。
     * 本机内容或 IO 配置一旦变化立即恢复逐 tick 扫描：前者由修订号体现，后者由 {@link #wake()} 唤醒；
     * 周围容器与 AE 网络的变化无从得知，最迟一个封顶间隔后被发现。
     */
    public void tick(Level level) {
        if (level.isClientSide) return;

        if (this.idleWaitTicks > 0) {
            if (this.context.getIoStateRevision() == this.observedRevision) {
                this.idleWaitTicks--;
                return;
            }
            this.idleWaitTicks = 0;
        }

        boolean moved = false;
        if (this.context.isAutoInputEnabled()) {
            moved = this.autoInputFromSurroundings(level);
        }

        if (this.context.isAutoOutputEnabled()) {
            moved |= this.autoOutputToSurroundings(level);
        }

        this.observedRevision = this.context.getIoStateRevision();
        if (moved) {
            this.idleRounds = 0;
        } else {
            this.idleRounds = Math.min(this.idleRounds + 1, MAX_IDLE_BACKOFF_TICKS);
            this.idleWaitTicks = idleScanInterval(this.idleRounds) - 1;
        }
    }

    /** 连续空转 {@code idleRounds} 轮后的扫描间隔：1、2、4、8、16，之后封顶。 */
    private static int idleScanInterval(int idleRounds) {
        return Math.min(MAX_IDLE_BACKOFF_TICKS, 1 << Math.min(idleRounds - 1, 5));
    }

    /**
     * 立即恢复逐 tick 自动 IO（面模式或自动输入输出开关被改动时调用）。
     * 这类配置变化不改变本机内容，不会体现在修订号里，只能显式唤醒。
     */
    public void wake() {
        this.idleRounds = 0;
        this.idleWaitTicks = 0;
    }

    /**
     * 从周围容器自动输入物品和流体到机器。
     * 仅从开启了对应面模式的面进行输入。
     *
     * @return 本轮是否至少搬运了一份物品、流体或化学品
     */
    private boolean autoInputFromSurroundings(Level level) {
        boolean movedAny = false;
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
                            movedAny = true;
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
                            movedAny = true;
                            this.context.markChanged();
                        }
                    }
                }
            }

            // 输入化学品。提取和容量计算都使用 long，且只写入本机输入化学品槽。
            if (mode.allowsMaterialInput() && this.inputChemicalStorage.isAvailable()) {
                ChemicalHandlerView sourceChemicalHandler = this.context.getAdjacentChemicalHandler(
                        level, srcPos, srcEntity.getBlockState(), srcEntity, dir.getOpposite());
                if (sourceChemicalHandler != null) {
                    ChemicalStackView extracted = sourceChemicalHandler.extractChemical(Long.MAX_VALUE, true);
                    if (extracted != null && !extracted.isEmpty()) {
                        ChemicalStackView remainder = this.inputChemicalStorage.insertChemical(extracted, true);
                        long moved = acceptedAmount(extracted, remainder);
                        if (moved > 0L) {
                            ChemicalStackView actual = sourceChemicalHandler.extractChemical(extracted, moved, false);
                            if (actual != null && !actual.isEmpty()) {
                                ChemicalStackView localRemainder = this.inputChemicalStorage.insertChemical(actual, false);
                                if (acceptedAmount(actual, localRemainder) > 0L) {
                                    movedAny = true;
                                    this.context.markChanged();
                                }
                                restoreChemicalRemainder(sourceChemicalHandler, actual, localRemainder);
                            }
                        }
                    }
                }
            }
        }
        return movedAny;
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
     *
     * @return 本轮是否至少搬运了一份物品、流体或化学品
     */
    private boolean autoOutputToSurroundings(Level level) {
        boolean movedAny = false;

        // AE网络输出不受面模式控制
        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long inserted = this.context.tryOutputToAE(stack);
            if (inserted > 0) {
                // 必须走 setStackInSlot：直接 shrink 不触发变更回调，状态修订号与外部库存快照都不会更新，
                // 熔炉会以为输出槽仍满而推迟配方搜索
                this.itemHandler.setStackInSlot(slot, remainingAfterInsert(stack, inserted));
                movedAny = true;
                this.context.markChanged();
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.outputFluidTanks[i].getFluid();
            if (fluid.isEmpty()) continue;
            long inserted = this.context.tryOutputFluidToAE(fluid);
            if (inserted > 0) {
                this.outputFluidTanks[i].drain((int) inserted, IFluidHandler.FluidAction.EXECUTE);
                movedAny = true;
            }
        }

        for (int i = 0; i < this.outputChemicalStorage.size(); i++) {
            ChemicalStackView chemical = this.outputChemicalStorage.getStackInSlot(i);
            if (chemical.isEmpty()) continue;
            long inserted = clampAmount(this.context.tryOutputChemicalToAE(chemical), chemical.amount());
            if (inserted > 0L) {
                this.outputChemicalStorage.extractChemical(i, inserted, false);
                movedAny = true;
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
                    movedAny = true;
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
                    movedAny = true;
                    this.context.markChanged();
                    fluid = this.outputFluidTanks[tankIndex].getFluid();
                    if (fluid.isEmpty()) break;
                }
            }
        }

        // 输出化学品到周围容器（仅允许输出模式的面）。
        for (int tankIndex = 0; tankIndex < this.outputChemicalStorage.size(); tankIndex++) {
            ChemicalStackView chemical = this.outputChemicalStorage.getStackInSlot(tankIndex);
            if (chemical.isEmpty()) continue;
            for (Direction dir : ALL_DIRECTIONS) {
                FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
                if (face == null || !this.context.getFaceMode(face).allowsMaterialOutput()) continue;
                long moved = this.tryOutputChemicalToDirection(level, tankIndex, dir);
                if (moved > 0L) {
                    movedAny = true;
                    this.context.markChanged();
                    chemical = this.outputChemicalStorage.getStackInSlot(tankIndex);
                    if (chemical.isEmpty()) break;
                }
            }
        }

        return movedAny;
    }

    /** 扣除已写入 AE 的部分后的剩余栈；全部写入时返回空栈。 */
    private static ItemStack remainingAfterInsert(ItemStack stack, long inserted) {
        long remaining = stack.getCount() - Math.min(stack.getCount(), Math.max(0L, inserted));
        return remaining <= 0L ? ItemStack.EMPTY : stack.copyWithCount((int) remaining);
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

    private long tryOutputChemicalToDirection(Level level, int tankIndex, Direction direction) {
        BlockPos targetPos = this.worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (targetEntity == null) return 0L;

        ChemicalHandlerView targetHandler = this.context.getAdjacentChemicalHandler(
                level, targetPos, targetEntity.getBlockState(), targetEntity, direction.getOpposite());
        if (targetHandler == null) return 0L;

        ChemicalStackView chemical = this.outputChemicalStorage.getStackInSlot(tankIndex);
        if (chemical.isEmpty()) return 0L;
        ChemicalStackView remainder = targetHandler.insertChemical(chemical, true);
        long moved = acceptedAmount(chemical, remainder);
        if (moved <= 0L) return 0L;

        ChemicalStackView accepted = targetHandler.insertChemical(chemical.copyWithAmount(moved), false);
        long actualMoved = acceptedAmount(chemical.copyWithAmount(moved), accepted);
        if (actualMoved > 0L) {
            this.outputChemicalStorage.extractChemical(tankIndex, actualMoved, false);
        }
        return actualMoved;
    }

    private void restoreChemicalRemainder(ChemicalHandlerView source, ChemicalStackView extracted,
                                          ChemicalStackView remainder) {
        ChemicalStackView remaining = normalizeRemainder(extracted, remainder);
        if (remaining.isEmpty()) return;

        remaining = source.insertChemical(remaining, false);
        if (remaining == null || remaining.isEmpty()) return;
        remaining = this.inputChemicalStorage.insertChemical(remaining, false);
        if (remaining == null || remaining.isEmpty()) return;
        remaining = this.outputChemicalStorage.insertChemical(remaining, false);
        if (remaining != null && !remaining.isEmpty()) {
            this.context.handleUnreturnedChemical(remaining);
        }
    }

    private static long acceptedAmount(ChemicalStackView requested, ChemicalStackView remainder) {
        if (requested == null || requested.isEmpty() || remainder == null) return 0L;
        long remaining = clampAmount(remainder.amount(), requested.amount());
        return requested.amount() - remaining;
    }

    private static ChemicalStackView normalizeRemainder(ChemicalStackView requested, ChemicalStackView remainder) {
        if (requested == null || requested.isEmpty()) return ChemicalStackView.EMPTY;
        if (remainder == null) return requested;
        long amount = clampAmount(remainder.amount(), requested.amount());
        return amount <= 0L ? ChemicalStackView.EMPTY : requested.copyWithAmount(amount);
    }

    private static long clampAmount(long amount, long maximum) {
        return Math.max(0L, Math.min(Math.max(0L, maximum), amount));
    }
}
