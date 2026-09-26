package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.compat.ars.ArsSourceCompatLoader;
import com.sorrowmist.useless.compat.ars.SourceBridge;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 造化杖无线物流的「容器能力」解析与搬运实现。
 *
 * <p>物品 / 流体 / 能量走 NeoForge 的方块能力；化学品经本模组已有的
 * {@link ChemicalCompatProviders} 抽象层（因此本类<b>不</b>引用 Mekanism 类型）；
 * 魔源经 {@link ArsSourceCompatLoader} 提供的桥接口。</p>
 *
 * <p>所有解析都会先确认区块已加载，<b>绝不</b>触发强制加载。</p>
 */
public final class StaffLinkTargets {
    private static final Direction[] DIRECTIONS = Direction.values();

    /** 绑定新锚点时挑选默认资源类型的优先级。 */
    private static final LinkMedium[] DEFAULT_ORDER = {
            LinkMedium.ITEM, LinkMedium.FLUID, LinkMedium.ENERGY, LinkMedium.CHEMICAL, LinkMedium.SOURCE
    };

    private StaffLinkTargets() {
    }

    /** 一个都没搬动时，卡在哪一步。给界面显示用，方便一眼定位。 */
    public enum TransferBlocker {
        /** 没问题（搬动了，或还没跑过）。 */
        NONE,
        /** 源那边解析不出库存：区块没加载 / 方块没了 / 没有对应能力。 */
        SOURCE_UNREACHABLE,
        /** 目标那边解析不出库存。 */
        TARGET_UNREACHABLE,
        /** 源里确实有东西，但全被过滤器挡住了。 */
        FILTERED,
        /** 源里没有可搬的东西。 */
        SOURCE_EMPTY,
        /** 源有东西、过滤也过了，但目标不收（装满 / 只认别的物品）。 */
        TARGET_REJECTED
    }

    /**
     * 搬运量为 0 时判断卡在哪一步。
     *
     * <p>只在真的一次都没搬动时调用，正常路径不付代价。</p>
     */
    public static TransferBlocker diagnose(ServerLevel sourceLevel, StaffLinkRoute source,
                                           ServerLevel targetLevel, StaffLinkRoute target) {
        Object from = resolve(sourceLevel, source.anchor().pos(), source.side(), source.medium());
        if (from == null) {
            return TransferBlocker.SOURCE_UNREACHABLE;
        }
        Object to = resolve(targetLevel, target.anchor().pos(), target.side(), target.medium());
        if (to == null) {
            return TransferBlocker.TARGET_UNREACHABLE;
        }
        List<ItemStack> filter = source.activeFilters();
        return switch (source.medium()) {
            case ITEM -> diagnoseItems((IItemHandler) from, filter);
            case FLUID -> diagnoseFluid((IFluidHandler) from, filter);
            case CHEMICAL -> diagnoseChemical((ChemicalHandlerView) from, filter);
            default -> TransferBlocker.SOURCE_EMPTY;
        };
    }

    private static TransferBlocker diagnoseItems(IItemHandler from, List<ItemStack> filter) {
        boolean anyItem = false;
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack probe = from.extractItem(slot, 1, true);
            if (probe.isEmpty()) {
                continue;
            }
            anyItem = true;
            if (matches(filter, probe)) {
                // 源有能搬的东西 ⇒ 只可能是目标不收。
                return TransferBlocker.TARGET_REJECTED;
            }
        }
        return anyItem ? TransferBlocker.FILTERED : TransferBlocker.SOURCE_EMPTY;
    }

    private static TransferBlocker diagnoseFluid(IFluidHandler from, List<ItemStack> filter) {
        FluidStack probe = from.drain(1, IFluidHandler.FluidAction.SIMULATE);
        if (probe.isEmpty()) {
            return TransferBlocker.SOURCE_EMPTY;
        }
        return matchesFluid(filter, probe) ? TransferBlocker.TARGET_REJECTED : TransferBlocker.FILTERED;
    }

    private static TransferBlocker diagnoseChemical(ChemicalHandlerView from, List<ItemStack> filter) {
        ChemicalStackView probe = from.extractChemical(1L, true);
        if (probe == null || probe.isEmpty()) {
            return TransferBlocker.SOURCE_EMPTY;
        }
        return matchesChemical(filter, probe) ? TransferBlocker.TARGET_REJECTED : TransferBlocker.FILTERED;
    }

    // ------------------------------------------------------------------ 解析

    /** 该坐标是不是「有东西可搬」的容器——决定潜行右键要不要接管。 */
    public static boolean isBindable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        for (LinkMedium medium : DEFAULT_ORDER) {
            if (medium.isSupported() && resolve(level, pos, null, medium) != null) {
                return true;
            }
        }
        return false;
    }

    /** 该坐标最适合的默认资源类型；什么都解析不出来时返回 {@code null}。 */
    @Nullable
    public static LinkMedium defaultMediumFor(Level level, BlockPos pos) {
        for (LinkMedium medium : DEFAULT_ORDER) {
            if (medium.isSupported() && resolve(level, pos, null, medium) != null) {
                return medium;
            }
        }
        return null;
    }

    /**
     * 解析目标容器在指定资源类型下的处理器。
     *
     * @return {@code IItemHandler} / {@code IFluidHandler} / {@code IEnergyStorage} /
     *         {@code ChemicalHandlerView} / {@link SourceBridge}；不可用返回 {@code null}
     */
    @Nullable
    public static Object resolve(Level level, BlockPos pos, @Nullable Direction side, LinkMedium medium) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return switch (medium) {
            case ITEM -> capability(level, Capabilities.ItemHandler.BLOCK, pos, side);
            case FLUID -> capability(level, Capabilities.FluidHandler.BLOCK, pos, side);
            case ENERGY -> capability(level, Capabilities.EnergyStorage.BLOCK, pos, side);
            case CHEMICAL -> chemicalHandler(level, pos, side);
            case SOURCE -> sourceBridge(level, pos);
        };
    }

    @Nullable
    private static <T> T capability(Level level, BlockCapability<T, Direction> capability,
                                    BlockPos pos, @Nullable Direction side) {
        if (side != null) {
            return level.getCapability(capability, pos, side);
        }
        T unspecified = level.getCapability(capability, pos, null);
        if (unspecified != null) {
            return unspecified;
        }
        for (Direction direction : DIRECTIONS) {
            T found = level.getCapability(capability, pos, direction);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static ChemicalHandlerView chemicalHandler(Level level, BlockPos pos, @Nullable Direction side) {
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return provider.getAdjacentHandler(level, pos, state, entity, side);
    }

    @Nullable
    private static SourceBridge sourceBridge(Level level, BlockPos pos) {
        SourceBridge bridge = ArsSourceCompatLoader.bridge();
        return bridge != null && bridge.hasTile(level, pos) ? bridge : null;
    }

    // ------------------------------------------------------------------ 搬运

    /**
     * 把资源从 {@code source} 搬到 {@code target}。
     *
     * <p>先模拟后提交：模拟阶段算出目标能接多少，提交阶段只搬那个数量；万一提交时目标
     * 仍然吐回余量（同 tick 内不该发生），余量会退回源，源也塞不回时掉落到源脚边，
     * 保证不凭空消失。</p>
     *
     * @return 实际搬运量（0 表示没搬）
     */
    public static long transfer(ServerLevel sourceLevel, StaffLinkRoute source,
                                ServerLevel targetLevel, StaffLinkRoute target, long limit) {
        if (limit <= 0 || source.medium() != target.medium()) {
            return 0;
        }
        Object from = resolve(sourceLevel, source.anchor().pos(), source.side(), source.medium());
        if (from == null) {
            return 0;
        }
        Object to = resolve(targetLevel, target.anchor().pos(), target.side(), target.medium());
        if (to == null) {
            return 0;
        }
        return switch (source.medium()) {
            case ITEM -> moveItems(sourceLevel, source.anchor().pos(), (IItemHandler) from,
                    (IItemHandler) to, limit, source.activeFilters());
            case FLUID -> moveFluid((IFluidHandler) from, (IFluidHandler) to, limit, source.activeFilters());
            case ENERGY -> moveEnergy((IEnergyStorage) from, (IEnergyStorage) to, limit);
            case CHEMICAL -> moveChemical((ChemicalHandlerView) from, (ChemicalHandlerView) to, limit,
                    source.activeFilters());
            // 魔源那边的接口是 int，超出的部分只能夹掉——魔源本身也到不了那么大的量。
            case SOURCE -> ((SourceBridge) from).moveSource(
                    sourceLevel, source.anchor().pos(), targetLevel, target.anchor().pos(),
                    (int) Math.min(limit, Integer.MAX_VALUE));
        };
    }

    private static long moveItems(ServerLevel sourceLevel, BlockPos sourcePos,
                                  IItemHandler from, IItemHandler to, long limit, List<ItemStack> filter) {
        long moved = 0;
        for (int slot = 0; slot < from.getSlots() && moved < limit; slot++) {
            // 原版接口单次只收 int，超出部分靠外层循环继续搬。
            int want = (int) Math.min(limit - moved, Integer.MAX_VALUE);
            ItemStack simulated = from.extractItem(slot, want, true);
            if (simulated.isEmpty() || !matches(filter, simulated)) {
                continue;
            }

            ItemStack rejected = insertInto(to, simulated, true);
            int accepted = simulated.getCount() - rejected.getCount();
            if (accepted <= 0) {
                continue;
            }

            ItemStack extracted = from.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) {
                continue;
            }
            ItemStack leftover = insertInto(to, extracted, false);
            int actuallyMoved = extracted.getCount() - leftover.getCount();
            if (!leftover.isEmpty()) {
                ItemStack unreturned = insertInto(from, leftover, false);
                if (!unreturned.isEmpty()) {
                    net.minecraft.world.level.block.Block.popResource(sourceLevel, sourcePos, unreturned);
                    actuallyMoved -= unreturned.getCount();
                }
            }
            moved += Math.max(0, actuallyMoved);
        }
        return moved;
    }

    private static ItemStack insertInto(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private static boolean matches(List<ItemStack> filter, ItemStack stack) {
        if (filter.isEmpty()) {
            return true;
        }
        for (ItemStack candidate : filter) {
            if (ItemStack.isSameItemSameComponents(candidate, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 流体过滤：标记物是「装有该流体的容器」（水桶、流体罐…），比对的是容器里装的流体种类。
     *
     * <p>过滤非空但没有任何标记物装着可识别的流体时返回 {@code false}——即「设了过滤器就一定会
     * 限制搬运」，不会因为标记物放错而变成不限制。</p>
     */
    private static boolean matchesFluid(List<ItemStack> filter, FluidStack fluid) {
        if (filter.isEmpty()) {
            return true;
        }
        for (ItemStack marker : filter) {
            IFluidHandlerItem handler = marker.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack contained = handler.getFluidInTank(tank);
                if (!contained.isEmpty() && FluidStack.isSameFluidSameComponents(contained, fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 化学品过滤：标记物是「装有该化学品的容器」，比对的是容器里装的化学品种类。 */
    private static boolean matchesChemical(List<ItemStack> filter, ChemicalStackView chemical) {
        if (filter.isEmpty()) {
            return true;
        }
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return true;
        }
        for (ItemStack marker : filter) {
            ChemicalStackView contained = provider.chemicalInItem(marker);
            if (contained != null && chemical.isSameType(contained)) {
                return true;
            }
        }
        return false;
    }

    private static long moveFluid(IFluidHandler from, IFluidHandler to, long limit, List<ItemStack> filter) {
        int request = (int) Math.min(limit, Integer.MAX_VALUE);
        FluidStack simulated = from.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty() || !matchesFluid(filter, simulated)) {
            return 0;
        }
        int accepted = to.fill(simulated, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }

        FluidStack drained = from.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return 0;
        }
        int filled = to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled < drained.getAmount()) {
            from.fill(drained.copyWithAmount(drained.getAmount() - filled), IFluidHandler.FluidAction.EXECUTE);
        }
        return filled;
    }

    private static long moveEnergy(IEnergyStorage from, IEnergyStorage to, long limit) {
        int request = (int) Math.min(limit, Integer.MAX_VALUE);
        int simulated = from.extractEnergy(request, true);
        if (simulated <= 0) {
            return 0;
        }
        int accepted = to.receiveEnergy(simulated, true);
        if (accepted <= 0) {
            return 0;
        }

        int extracted = from.extractEnergy(accepted, false);
        if (extracted <= 0) {
            return 0;
        }
        int received = to.receiveEnergy(extracted, false);
        if (received < extracted) {
            from.receiveEnergy(extracted - received, false);
        }
        return received;
    }

    private static long moveChemical(ChemicalHandlerView from, ChemicalHandlerView to, long limit,
                                     List<ItemStack> filter) {
        ChemicalStackView simulated = from.extractChemical(limit, true);
        if (simulated == null || simulated.isEmpty() || !matchesChemical(filter, simulated)) {
            return 0;
        }
        ChemicalStackView rejected = to.insertChemical(simulated, true);
        long accepted = simulated.amount() - (rejected == null ? 0L : rejected.amount());
        if (accepted <= 0) {
            return 0;
        }

        ChemicalStackView taken = from.extractChemical(accepted, false);
        if (taken == null || taken.isEmpty()) {
            return 0;
        }
        ChemicalStackView leftover = to.insertChemical(taken, false);
        long moved = taken.amount() - (leftover == null ? 0L : leftover.amount());
        if (leftover != null && !leftover.isEmpty()) {
            from.insertChemical(leftover, false);
        }
        return moved;
    }
}
