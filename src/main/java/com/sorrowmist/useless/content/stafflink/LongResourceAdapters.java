package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import com.sorrowmist.useless.energy.IEnergyManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 把 NeoForge 原生的 int 级能力包装成 {@code long} 级接口。
 *
 * <p><b>本模组自己的 long 能力不走分片。</b> {@link IEnergyManager} 从设计之初就是 long 契约，
 * 因此 {@link #energy(IEnergyManager)} 是<b>原生直通</b>：一次调用搬多少就是多少，不存在任何
 * int 中转，也没有补循环。只有外部模组的 int 能力才需要 {@link #energy(IEnergyStorage)}
 * 那种分片包装。</p>
 *
 * <p><b>模拟必须精确。</b> 原生接口的 {@code simulate} 调用不改动状态，因此重复调用会一直返回
 * 同一个上限——拿它循环累加会凭空放大可搬量。所以每个模拟路径要么只探一次（物品按槽独立，
 * 可以跨槽累加；同槽只探一次），要么像流体那样直接按容量/存量算出精确值。</p>
 */
public final class LongResourceAdapters {

    private LongResourceAdapters() {
    }

    // ------------------------------------------------------------------ 物品

    /**
     * 单次 extract / insert / drain / fill 调用内允许的最大分片步数。
     *
     * <p>存在的理由是一次真实的卡死事故：某些外部实现（例如 SophisticatedCore 的背包）
     * 每次只让渡 1 个物品，而每个槽位的存量可能是几十万。原来那种「循环到凑满 amount 为止」
     * 的写法会把一次调用拖成几十万轮，而每一轮它都要重建槽位索引、对 DataComponent 做
     * hashCode —— 主线程就此卡死，日志停在 tick 中间，游戏表现为完全无响应。</p>
     *
     * <p>更隐蔽的一种：实现返回了非空、但 {@code getCount() == 0} 的栈。
     * 此时 {@code moved} 永远不增加，是真正的死循环。所有循环都必须在
     * 「本步没有实际进展」时立刻退出。</p>
     *
     * <p>截断是安全的：搬运循环每个 tick 都会重新进来，剩下的量自然顺延到下一 tick，
     * 只影响速率，不影响正确性。</p>
     */
    private static final int MAX_CHUNK_STEPS = 64;

    /** 把 NeoForge 物品处理器包装成 long 级。 */
    public static LongItemHandler items(IItemHandler handler) {
        return new LongItemHandler() {
            @Override
            public int getSlots() {
                return handler.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                // 必须返回副本：原生的 getStackInSlot 交出的是容器内部栈的引用，
                // 一旦执行 extract 把槽位清空，外部持有的这个对象会跟着变成空栈，
                // 后续据此 insert 就等于塞了个空气，搬运量凭空蒸发。
                return handler.getStackInSlot(slot).copy();
            }

            @Override
            public long amountIn(int slot) {
                if (slot < 0 || slot >= handler.getSlots()) {
                    return 0L;
                }
                return handler.getStackInSlot(slot).getCount();
            }

            @Override
            public long extract(int slot, long amount, boolean simulate) {
                if (amount <= 0L || slot < 0 || slot >= handler.getSlots()) {
                    return 0L;
                }
                if (simulate) {
                    // 模拟不改状态，只能探一次；栈内数量本身就是精确上限。
                    ItemStack probe = handler.extractItem(
                            slot, (int) Math.min(amount, Integer.MAX_VALUE), true);
                    return probe.isEmpty() ? 0L : probe.getCount();
                }
                long moved = 0L;
                for (int step = 0; step < MAX_CHUNK_STEPS && moved < amount; step++) {
                    int chunk = (int) Math.min(amount - moved, Integer.MAX_VALUE);
                    ItemStack got = handler.extractItem(slot, chunk, false);
                    // 必须同时判空与判零：有的实现会返回「非空但 count 为 0」的栈，
                    // 只判 isEmpty 的话 moved 永远不增加，这里就是死循环。
                    if (got.isEmpty() || got.getCount() <= 0) {
                        break;
                    }
                    moved += got.getCount();
                }
                return moved;
            }

            @Override
            public long insert(ItemStack template, long amount, boolean simulate) {
                if (template.isEmpty() || amount <= 0L) {
                    return 0L;
                }
                long inserted = 0L;
                int steps = 0;
                for (int slot = 0; slot < handler.getSlots() && inserted < amount; slot++) {
                    while (inserted < amount && steps < MAX_CHUNK_STEPS) {
                        steps++;
                        int chunk = (int) Math.min(amount - inserted, Integer.MAX_VALUE);
                        ItemStack offer = template.copyWithCount(chunk);
                        ItemStack leftover = handler.insertItem(slot, offer, simulate);
                        long accepted = chunk - leftover.getCount();
                        if (accepted <= 0L) {
                            break;
                        }
                        inserted += accepted;
                        if (simulate) {
                            // 每个槽位只探一次；不同槽位互相独立，可以继续累加。
                            break;
                        }
                    }
                    if (steps >= MAX_CHUNK_STEPS) {
                        // 步数用尽：剩下的量留到下一次 tick，避免把主线程耗在这里。
                        break;
                    }
                }
                return inserted;
            }
        };
    }

    // ------------------------------------------------------------------ 流体

    /** 把 NeoForge 流体处理器包装成 long 级。 */
    public static LongFluidHandler fluids(IFluidHandler handler) {
        return new LongFluidHandler() {
            @Override
            public int getTanks() {
                return handler.getTanks();
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                // 同 getStackInSlot：必须返回副本。drain 执行后容器内部栈会被就地清空，
                // 引用型返回值会让外层手里那个「待注入的类型」也一起变成空，
                // 结果是抽走了、却既注入不了也回填不了——流体凭空消失。
                return handler.getFluidInTank(tank).copy();
            }

            @Override
            public long amountIn(int tank) {
                if (tank < 0 || tank >= handler.getTanks()) {
                    return 0L;
                }
                return handler.getFluidInTank(tank).getAmount();
            }

            @Override
            public long capacityOf(int tank) {
                if (tank < 0 || tank >= handler.getTanks()) {
                    return 0L;
                }
                return Math.max(0L, handler.getTankCapacity(tank));
            }

            @Override
            public long drain(int tank, long amount, boolean simulate) {
                if (amount <= 0L || tank < 0 || tank >= handler.getTanks()) {
                    return 0L;
                }
                FluidStack type = handler.getFluidInTank(tank).copy();
                if (type.isEmpty()) {
                    return 0L;
                }
                // 模拟与执行必须走同一组调用：过去模拟用「同类储罐存量求和」的公式推算，
                // 而执行走原生 drain，两者一旦分叉（模拟高报），外层就会多抽、多抽的部分
                // 再也塞不回去，直接变成死账。现在两侧共用 drainExact。
                return drainExact(handler, type, amount, simulate);
            }

            @Override
            public long fill(FluidStack type, long amount, boolean simulate) {
                if (type.isEmpty() || amount <= 0L) {
                    return 0L;
                }
                // 同上：模拟必须就是执行会得到的那个数，不能靠容量公式另算一套。
                return fillExact(handler, type, amount, simulate);
            }
        };
    }

    /**
     * 流体抽取：模拟与执行共用同一组原生调用。
     *
     * <p>原生 {@code FluidStack} 的 amount 是 int，所以单次调用天然以 {@link Integer#MAX_VALUE}
     * 为界分片。模拟不改状态，同一个分片重复调用只会一直返回同一个上限，因此模拟只探一片
     * ——对真实储罐（单罐存量本就 ≤ int）这就是精确值；即便入参超 int，模拟也只会少报，
     * 少报不会造成多抽，因而是安全的。</p>
     */
    private static long drainExact(IFluidHandler handler, FluidStack type, long amount,
                                   boolean simulate) {
        IFluidHandler.FluidAction action = simulate
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE;
        long drained = 0L;
        for (int step = 0; step < MAX_CHUNK_STEPS && drained < amount; step++) {
            int chunk = (int) Math.min(amount - drained, Integer.MAX_VALUE);
            FluidStack got = handler.drain(type.copyWithAmount(chunk), action);
            // 同物品侧：非空但量为 0 的返回值会让 drained 停滞，必须一并判掉。
            if (got.isEmpty() || got.getAmount() <= 0) {
                break;
            }
            drained += got.getAmount();
            if (simulate) {
                break;
            }
        }
        return drained;
    }

    /**
     * 流体注入：模拟与执行共用同一组原生调用，理由同 {@link #drainExact}。
     *
     * <p>模拟只探一片，超 int 的入参会被少报；少报只会让外层少搬，不会多搬。</p>
     */
    private static long fillExact(IFluidHandler handler, FluidStack type, long amount,
                                  boolean simulate) {
        IFluidHandler.FluidAction action = simulate
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE;
        long filled = 0L;
        for (int step = 0; step < MAX_CHUNK_STEPS && filled < amount; step++) {
            int chunk = (int) Math.min(amount - filled, Integer.MAX_VALUE);
            int accepted = handler.fill(type.copyWithAmount(chunk), action);
            if (accepted <= 0) {
                break;
            }
            filled += accepted;
            if (simulate) {
                break;
            }
        }
        return filled;
    }

    // ------------------------------------------------------------------ 能量

    /**
     * 本模组 long 能量能力的<b>原生直通</b>。
     *
     * <p>没有任何分片循环：{@code IEnergyManager} 的收发本来就是 long 语义，
     * 一次 {@code transfer} 能搬多少就搬多少。</p>
     */
    public static LongEnergyHandler energy(IEnergyManager manager) {
        return new LongEnergyHandler() {
            @Override
            public long extract(long amount, boolean simulate) {
                return amount <= 0L ? 0L : manager.extractEnergy(amount, simulate);
            }

            @Override
            public long receive(long amount, boolean simulate) {
                return amount <= 0L ? 0L : manager.receiveEnergy(amount, simulate);
            }

            @Override
            public long stored() {
                return manager.getEnergyStoredLong();
            }

            @Override
            public long capacity() {
                return manager.getMaxEnergyStoredLong();
            }

            @Override
            public boolean canExtract() {
                return manager.canExtract();
            }

            @Override
            public boolean canReceive() {
                return manager.canReceive();
            }
        };
    }

    /**
     * 外部模组 int 能量的 long 级包装。
     *
     * <p>能量没有槽位，执行时可以安全地按 int 分片循环；模拟路径只探一次上限。</p>
     */
    public static LongEnergyHandler energy(IEnergyStorage storage) {
        return new LongEnergyHandler() {
            @Override
            public long extract(long amount, boolean simulate) {
                if (amount <= 0L || !storage.canExtract()) {
                    return 0L;
                }
                int first = storage.extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
                if (first <= 0 || simulate) {
                    return Math.max(0L, first);
                }
                long moved = first;
                for (int step = 0; step < MAX_CHUNK_STEPS && moved < amount; step++) {
                    int chunk = (int) Math.min(amount - moved, Integer.MAX_VALUE);
                    int got = storage.extractEnergy(chunk, false);
                    if (got <= 0) {
                        break;
                    }
                    moved += got;
                }
                return moved;
            }

            @Override
            public long receive(long amount, boolean simulate) {
                if (amount <= 0L || !storage.canReceive()) {
                    return 0L;
                }
                int first = storage.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
                if (first <= 0 || simulate) {
                    return Math.max(0L, first);
                }
                long moved = first;
                for (int step = 0; step < MAX_CHUNK_STEPS && moved < amount; step++) {
                    int chunk = (int) Math.min(amount - moved, Integer.MAX_VALUE);
                    int got = storage.receiveEnergy(chunk, false);
                    if (got <= 0) {
                        break;
                    }
                    moved += got;
                }
                return moved;
            }

            @Override
            public long stored() {
                return storage.getEnergyStored();
            }

            @Override
            public long capacity() {
                return storage.getMaxEnergyStored();
            }

            @Override
            public boolean canExtract() {
                return storage.canExtract();
            }

            @Override
            public boolean canReceive() {
                return storage.canReceive();
            }
        };
    }
}
