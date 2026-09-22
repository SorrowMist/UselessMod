package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.core.config.AlloyFurnaceTierRules;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.energy.IEnergyManager;
import appeng.api.networking.IGrid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CraftingTask 的上下文接口，提供对 AdvancedAlloyFurnaceBlockEntity 必要成员的访问
 */
public interface CraftingTaskContext {
    
    // 槽位常量
    int getInputSlotsStart();
    int getInputSlotsCount();
    int getOutputSlotsStart();
    int getOutputSlotsCount();
    int getCatalystSlot();
    int getMoldSlot();
    int getFluidTankCount();
    
    // 基础访问
    Level getLevel();
    net.minecraft.core.BlockPos getBlockPos();
    ItemStackHandler getItemHandler();
    IEnergyManager getEnergyManager();

    @Nullable
    default IGrid getAeGrid() {
        return null;
    }

    default int getMachineTier() {
        return AlloyFurnaceTierRules.NO_MACHINE_TIER;
    }
    
    // 状态更新
    void markChanged();
    void sendAETaskProgressToClients();
    
    // 催化剂相关
    int getCatalystMaxParallel();
    
    // AE网络输出
    long tryOutputToAE(net.minecraft.world.item.ItemStack stack);
    long tryOutputFluidToAE(FluidStack stack);
    long tryOutputKeyToAE(AEKey key, long amount);

    default long tryOutputChemicalToAE(ChemicalStackView stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        GenericStack generic = getChemicalKeyProvider().toGenericStack(stack);
        return generic == null ? 0L : tryOutputKeyToAE(generic.what(), generic.amount());
    }

    default FurnaceChemicalStorage getInputChemicalStorage() {
        return FurnaceChemicalStorage.DISABLED;
    }

    default FurnaceChemicalStorage getOutputChemicalStorage() {
        return FurnaceChemicalStorage.DISABLED;
    }

    default ChemicalKeyProvider getChemicalKeyProvider() {
        return ChemicalKeyProviders.get();
    }

    // 产物输出模式
    boolean isReturnOutputToAe();

    /** 暂存未能返还的输入（AE 写入失败时防丢失，由管理器逐 tick 重试写回） */
    void stashUnreturnedInput(AEKey key, long amount);

    /** Stores a produced key that could not be returned to AE or an output slot. */
    default void stashUnreturnedOutput(AEKey key, long amount) {
        stashUnreturnedInput(key, amount);
    }

    /**
     * 统一 AE 输出端口：受“产物返回AE”开关约束，开关关闭时不写入 AE 网络，
     * 剩余部分由调用方回退到本地槽位。
     */
    default FurnaceOutputPort.AeOutput createAeOutputPort() {
        return new FurnaceOutputPort.AeOutput() {
            @Override
            public long insertItem(ItemStack stack) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputToAE(stack);
            }

            @Override
            public long insertFluid(FluidStack stack) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputFluidToAE(stack);
            }

            @Override
            public long insertKey(AEKey key, long amount) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputKeyToAE(key, amount);
            }
        };
    }
    
    // 任务进度管理
    ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressMap();
    AtomicInteger getTotalAEMaxProgressAtomic();
    AtomicInteger getTotalAEProgressAtomic();
    
    // 锁和流体罐
    ReentrantLock getCraftingLock();
    FluidTank[] getInputFluidTanks();
    FluidTank[] getOutputFluidTanks();

    default AdvancedAlloyFurnaceRecipe resolveTaskRecipe(
            IPatternDetails pattern, List<ItemStack> items, List<FluidStack> fluids,
            List<GenericStack> keys, long operations) {
        if (pattern != null) {
            IPatternDetails original = SmartDoublingPatterns.unwrap(pattern);
            if (original instanceof OmniversalPatternDetails omniversal) {
                return omniversal.recipe();
            }
        }

        ItemStack mold = getItemHandler().getStackInSlot(getMoldSlot());
        return AlloyFurnaceRecipeManager.getInstance().findRecipeForCraftingWithConstraints(
                getLevel(), items, fluids, keys, mold,
                AdvancedAlloyFurnacePatternPolicy.outputConstraints(pattern), operations,
                getMachineTier());
    }

    default boolean isTaskRecipeAvailable(AdvancedAlloyFurnaceRecipe recipe) {
        return getTaskAvailability(recipe).available();
    }

    default TaskAvailability getTaskAvailability(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null) {
            return TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_recipe", "");
        }
        int machineTier = getMachineTier();
        int requiredTier = AlloyFurnaceTierRules.requiredTier(recipe.id(), recipe.tier());
        if (machineTier != AlloyFurnaceTierRules.NO_MACHINE_TIER && machineTier < requiredTier) {
            return TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_tier", "");
        }
        if (recipe.molds().isEmpty()) return TaskAvailability.ready();
        if (recipe.molds().size() > 1) {
            return TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                    describeRequiredMolds(recipe.molds()));
        }
        ItemStackHandler itemHandler = getItemHandler();
        int moldSlot = getMoldSlot();
        if (itemHandler == null || moldSlot < 0 || moldSlot >= itemHandler.getSlots()) {
            return TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                    describeRequiredMolds(recipe.molds()));
        }
        ItemStack mold = itemHandler.getStackInSlot(moldSlot);
        return AdapterUtils.matchesMold(recipe.molds().getFirst(), mold)
                ? TaskAvailability.ready()
                : TaskAvailability.unavailable(
                        "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                        describeRequiredMolds(recipe.molds()));
    }

    static String describeRequiredMold(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return "";
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty()) return candidate.getHoverName().getString();
        }
        return "";
    }

    static String describeRequiredMolds(List<Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "";
        List<String> descriptions = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            String description = describeRequiredMold(ingredient);
            if (!description.isEmpty()) descriptions.add(description);
        }
        return String.join(", ", descriptions);
    }

    record TaskAvailability(boolean available, String statusKey, String statusDetail) {
        public TaskAvailability {
            statusKey = statusKey == null ? "" : statusKey;
            statusDetail = statusDetail == null ? "" : statusDetail;
        }

        public static TaskAvailability ready() {
            return new TaskAvailability(true, "", "");
        }

        public static TaskAvailability unavailable(String statusKey, String statusDetail) {
            return new TaskAvailability(false, statusKey, statusDetail);
        }
    }

    default ResolvedCatalystEffect resolveTaskEffect(AdvancedAlloyFurnaceRecipe recipe) {
        int baseTime = recipe == null ? 200 : Math.max(1, recipe.processTime());
        return CatalystEffectResolver.resolve(recipe, getItemHandler().getStackInSlot(getCatalystSlot()), baseTime);
    }

    default int getTaskProcessTime(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        return effect == null ? Math.max(1, recipe == null ? 200 : recipe.processTime())
                : Math.max(1, effect.processTime());
    }

    default long getTaskParallel(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        return recipe == null || effect == null ? 1
                : AlloyFurnaceParallelCalculator.calculateAeTaskParallel(recipe, effect);
    }

    /** Maximum number of crafting-pattern operations one furnace thread may accept in one push. */
    default long getCraftingPatternCapacity() {
        return 1L;
    }

    /** Long-count AE contexts keep item and fluid amounts as AE keys instead of int-sized stacks. */
    default boolean supportsLongAeAmounts() {
        return false;
    }

    /**
     * 多方块核心返回 {@code true}：本机支持把万象样板折叠成 BigInteger 批次，并按 count 一次性计费。
     *
     * <p>单方块高级合金炉保持 {@code false} —— 它的万象样板仍走长版 counted 路径。这个能力位同时被
     * Data Energistics 适配器与对外公开 API 用来决定「是否给万象样板发布 machine identity」，
     * 因此**不能**与 {@link #supportsLongAeAmounts()} 混用（后者单方块与多方块都是 true，无法区分）。</p>
     *
     * <p>为什么需要它：DE 的 exact 分支一旦接管某个 provider 就不会再回落长版路径，所以必须能
     * 精确判断「本机是否真的实现了 bigint 语义」，否则会给没有 bigint 能力的机器发布 machine identity。</p>
     */
    default boolean supportsBigIntegerRecipeBatches() {
        return false;
    }

    /**
     * 本 tick 允许<b>单个批次</b>产生的产物分段数（AIMD 控制器的输出）。
     *
     * <p>调用方约定：把它当作「本批最多产生多少段」传进容量计算
     * （{@code AlloyFurnaceBigIntegerCrafting.maximumSegmentedCount}）。</p>
     *
     * <p><b>为什么由机器给出而不是调用方自己定</b>：单批该多大取决于<b>本机的交付能力</b>
     * （回网每 tick 能插多少次）与<b>调度侧的派发频率</b>，两者都只有机器自己测得到。
     * 机器用 TCP 拥塞控制那套（慢启动 / 乘法减小 / 线性回升）探测出合适值，
     * 收敛到「一批大约一两个 tick 交付完」—— 那是最平滑的形态。</p>
     *
     * <p>实现方必须保证返回值 <b>≥ 1</b>：容量不会因积压归零，调度侧才能持续派发而无需停顿重启。</p>
     */
    default long outputSegmentBudget() {
        return 1L;
    }

    default boolean isTaskExecutionEnabled() {
        return true;
    }

    default void handleUnreturnedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || getLevel() == null) return;
        var pos = getBlockPos();
        net.minecraft.world.Containers.dropItemStack(
                getLevel(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, stack);
    }

    default void handleUnreturnedFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var key = appeng.api.stacks.AEFluidKey.of(stack);
        if (key != null) stashUnreturnedInput(key, stack.getAmount());
    }
}
