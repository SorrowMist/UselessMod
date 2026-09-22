package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerApi;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerCapacity;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;

/**
 * {@link AlloyFurnaceBigIntegerTarget} 的多方块实现：一台万象合金炉站在「样板供应器」一侧的视图。
 *
 * <p>支持两类样板（与机器实际能力一致）：</p>
 * <ul>
 *   <li><b>万象样板</b> —— 解析绑定配方一次 → 产物 ×count，按 {@code count × 单份能耗} 收能量，
 *       容量受「配方可用性 / 材料窗口 / 产物分段 / 能量」四道闸限制；</li>
 *   <li><b>AE2 合成样板</b> —— 在虚拟 3×3 工作台上装配一次 → 产物 ×count，<b>不收能量</b>，
 *       容量只受「材料窗口 / 产物分段」限制。</li>
 * </ul>
 *
 * <p>这个类刻意只做<b>转发与解包</b>：容量算术在 {@link AlloyFurnaceBigIntegerCrafting}，
 * 提交在 {@link AdvancedAlloyFurnaceAeManager#pushBigIntegerBatch}。这样对外公开的 API
 * 与数据能源适配器共用同一套判定，不会出现「两条入口算出不同容量」的情况。</p>
 *
 * <p>{@code machineIdentity} 用的是全模组唯一的身份公式（
 * {@link AlloyFurnaceBigIntegerCrafting#machineIdentity}），所以 DE 与公开 API 认的是同一台物理机器。</p>
 */
public final class OmniversalBigIntegerTarget implements AlloyFurnaceBigIntegerTarget {
    private final MultiblockAlloyFurnaceCoreBlockEntity core;
    private final String routeIdentity;

    public OmniversalBigIntegerTarget(@NotNull MultiblockAlloyFurnaceCoreBlockEntity core,
                                      @NotNull String routeIdentity) {
        this.core = Objects.requireNonNull(core, "Multiblock alloy furnace core must not be null");
        this.routeIdentity = Objects.requireNonNull(routeIdentity, "Route identity must not be null");
    }

    @Override
    public @NotNull String routeIdentity() {
        return this.routeIdentity;
    }

    @Override
    public @NotNull String machineIdentity() {
        return AlloyFurnaceBigIntegerCrafting.machineIdentity(
                "machine@" + this.core.getBlockPos().asLong(),
                this.core.getLevel(),
                this.core.getBlockPos());
    }

    @Override
    public @Nullable IGrid grid() {
        return this.core.getAeGrid();
    }

    @Override
    public @NotNull Set<ResourceLocation> acceptedPatternKinds() {
        return AlloyFurnaceBigIntegerApi.acceptedPatternKinds();
    }

    @Override
    public @NotNull AlloyFurnaceBigIntegerCapacity capacity(IPatternDetails pattern,
                                                            KeyCounter @NotNull [] prototype,
                                                            @NotNull BigInteger requested) {
        if (requested == null || requested.signum() <= 0 || prototype == null || prototype.length == 0) {
            return AlloyFurnaceBigIntegerCapacity.none("");
        }
        // 单批分段预算由机器用 AIMD 控制器实测给出（见 CraftingTaskContext#outputSegmentBudget）：
        // 它把「一批大约多少 tick 交付完」收敛到最平滑的形态，而不是用固定值猜。
        long segmentBudget = this.core.outputSegmentBudget();
        IPatternDetails original = pattern == null ? null : SmartDoublingPatterns.unwrap(pattern);
        if (original instanceof OmniversalPatternDetails omniversal) {
            BigInteger accepted = AlloyFurnaceBigIntegerCrafting.maximumCount(
                    this.core, omniversal, prototype, threads(), segmentBudget, requested);
            if (accepted.signum() <= 0) {
                // 报 0 时给出原因（档次不够 / 缺模具 / 没能量），供调用方提示玩家。
                AdvancedAlloyFurnaceRecipe recipe = omniversal.recipe();
                return AlloyFurnaceBigIntegerCapacity.none(recipe == null
                        ? "" : this.core.getTaskAvailability(recipe).statusKey());
            }
            return AlloyFurnaceBigIntegerCapacity.of(requested.min(accepted));
        }
        if (original instanceof IMolecularAssemblerSupportedPattern) {
            // 合成样板：一次装配折叠任意份数、不收能量，所以只看材料窗口与产物分段预算。
            BigInteger accepted = AlloyFurnaceBigIntegerCrafting.maximumCraftingPatternCount(
                    original, prototype, threads(), segmentBudget, requested);
            return accepted.signum() <= 0
                    ? AlloyFurnaceBigIntegerCapacity.none("")
                    : AlloyFurnaceBigIntegerCapacity.of(requested.min(accepted));
        }
        return AlloyFurnaceBigIntegerCapacity.none("");
    }

    @Override
    public @Nullable AlloyFurnaceBigIntegerBatch admit(IPatternDetails pattern,
                                                       KeyCounter @NotNull [] prototype,
                                                       @NotNull BigInteger requested,
                                                       @Nullable AlloyFurnaceBigIntegerCpuBinding cpu) {
        AlloyFurnaceBigIntegerCapacity capacity = capacity(pattern, prototype, requested);
        if (!capacity.isAvailable()) {
            return null;
        }
        // 完整检查（配方可用性 / 背压 / 能量）在 commit 时重新执行：期间机器状态可能变化，
        // 最终以 commit 的返回值为准，与 DE 侧 BigIntegerCraftingAdmission 的语义一致。
        return new Batch(pattern, prototype, capacity.accepted(), cpu);
    }

    /** 本机当前线程数（多方块跟随线圈线程）。 */
    private int threads() {
        return this.core.getMaxAETaskCount();
    }

    /**
     * 本机（准确说是<b>整个服务端</b>）最近是否因耗时超预算而正在降频。
     *
     * <p>{@link AlloyFurnaceTickBudget} 的粒度是全局的：所有机器跑在同一个服务端线程上，多台机器的
     * 耗时会累加、共同把降频压下去。这与数据能源的「每网格预算」是同一个量级。</p>
     */
    @Override
    public boolean isThrottled() {
        return AlloyFurnaceTickBudget.isThrottled();
    }

    /** 一次性准入凭据：只保留到 commit 所需的引用，提交后立刻释放。 */
    private final class Batch implements AlloyFurnaceBigIntegerBatch {
        private final IPatternDetails pattern;
        private final KeyCounter @NotNull [] prototype;
        private final BigInteger count;
        @Nullable
        private final AlloyFurnaceBigIntegerCpuBinding cpu;
        private boolean committed;
        private boolean transferred;

        private Batch(IPatternDetails pattern,
                      KeyCounter @NotNull [] prototype,
                      BigInteger count,
                      @Nullable AlloyFurnaceBigIntegerCpuBinding cpu) {
            this.pattern = pattern;
            this.prototype = prototype;
            this.count = count;
            this.cpu = cpu;
        }

        @Override
        public @NotNull BigInteger count() {
            return this.count;
        }

        @Override
        public boolean commit(KeyCounter @NotNull [] prototype) {
            if (this.committed) {
                throw new IllegalStateException("BigInteger crafting batch has already been committed");
            }
            if (prototype != this.prototype) {
                throw new IllegalArgumentException(
                        "BigInteger crafting batch must be committed with its prepared prototype");
            }
            this.committed = true;
            boolean accepted = OmniversalBigIntegerTarget.this.core
                    .pushBigIntegerBatch(this.pattern, this.count, prototype, this.cpu);
            this.transferred = accepted;
            return accepted;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return this.transferred;
        }
    }
}
