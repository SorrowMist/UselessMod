package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.recipe;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceInputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;

/**
 * 高级合金炉配方匹配与并行数计算器。
 * <p>
 * 承担纯粹的只读计算职责：配方匹配、可处理性校验、并行数计算。
 * 不持有任何机器运行状态（进度、缓存等），这些仍由方块实体管理。
 */
public final class AlloyFurnaceRecipeCalculator {

    private final ItemStackHandler itemHandler;
    private final FluidTank[] inputFluidTanks;
    private final FluidTank[] outputFluidTanks;
    private final FurnaceChemicalStorage inputChemicalStorage;
    private final FurnaceChemicalStorage outputChemicalStorage;
    private final ChemicalKeyProvider chemicalKeyProvider;
    public AlloyFurnaceRecipeCalculator(ItemStackHandler itemHandler, FluidTank[] inputFluidTanks,
                                        FluidTank[] outputFluidTanks) {
        this(itemHandler, inputFluidTanks, outputFluidTanks,
                FurnaceChemicalStorage.DISABLED, FurnaceChemicalStorage.DISABLED,
                ChemicalKeyProvider.NONE);
    }

    public AlloyFurnaceRecipeCalculator(ItemStackHandler itemHandler, FluidTank[] inputFluidTanks,
                                        FluidTank[] outputFluidTanks,
                                        FurnaceChemicalStorage inputChemicalStorage,
                                        FurnaceChemicalStorage outputChemicalStorage,
                                        ChemicalKeyProvider chemicalKeyProvider) {
        this.itemHandler = itemHandler;
        this.inputFluidTanks = inputFluidTanks;
        this.outputFluidTanks = outputFluidTanks;
        this.inputChemicalStorage = inputChemicalStorage == null ? FurnaceChemicalStorage.DISABLED : inputChemicalStorage;
        this.outputChemicalStorage = outputChemicalStorage == null ? FurnaceChemicalStorage.DISABLED : outputChemicalStorage;
        this.chemicalKeyProvider = chemicalKeyProvider == null ? ChemicalKeyProvider.NONE : chemicalKeyProvider;
    }

    /**
     * 查找匹配的配方（统一匹配，支持物品+流体+模具优先级）。
     * <p>
     * 每次都通过统一管理器取得当前最具体配方；管理器的不可变输入键负责缓存，
     * 避免复用旧配方导致后来变得可用的更具体配方长期饥饿。
     *
     * @param level 世界
     * @return 匹配的配方，如果没有则返回空
     */
    public Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe(@Nullable Level level) {
        if (level == null) return Optional.empty();

        // 构建输入列表（用于 AlloyFurnaceRecipeManager 查找）
        List<ItemStack> currentInputs = new ArrayList<>();
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
            ItemStack stack = this.itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                currentInputs.add(stack);
            }
        }

        List<FluidStack> currentFluids = new ArrayList<>();
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.inputFluidTanks[i].getFluid();
            if (!fluid.isEmpty()) {
                currentFluids.add(fluid.copy());
            }
        }

        List<appeng.api.stacks.GenericStack> currentChemicals = new ArrayList<>();
        ChemicalKeyProvider provider = currentChemicalKeyProvider();
        for (int i = 0; i < this.inputChemicalStorage.size(); i++) {
            var generic = provider.toGenericStack(this.inputChemicalStorage.getStackInSlot(i));
            if (generic != null && generic.amount() > 0L) currentChemicals.add(generic);
        }

        if (currentInputs.isEmpty() && currentFluids.isEmpty() && currentChemicals.isEmpty()) return Optional.empty();

        ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);

        AdvancedAlloyFurnaceRecipe bestRecipe = AlloyFurnaceRecipeManager.getInstance().findRecipe(
                level, currentInputs, currentFluids, currentChemicals, moldStack
        );

        if (bestRecipe != null && canProcessRecipe(bestRecipe)) {
            return Optional.of(bestRecipe);
        }

        return Optional.empty();
    }

    /**
     * 检查配方是否可处理（直接遍历slot，不构建中间列表）。
     * <p>
     * 模具检查提前，便于快速失败。
     */
    public boolean canProcessRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        return this.canConsumeRecipeInputs(recipe, 1);
    }

    /**
     * 检查是否有足够的输入材料支持指定的并行数。
     *
     * @param recipe   配方
     * @param parallel 并行数
     * @return 如果有足够的材料返回true
     */
    public boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        if (recipe == null || parallel <= 0) return false;
        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            if (!AdapterUtils.matchesMold(recipe.mold(), moldStack)) return false;
        }
        return FurnaceInputPort.canConsumeRecipeInputs(
                recipe, parallel, this.itemHandler, INPUT_SLOTS_START, INPUT_SLOTS_COUNT,
                this.inputFluidTanks, FLUID_TANK_COUNT,
                this.inputChemicalStorage, currentChemicalKeyProvider());
    }

    /**
     * 检查是否有足够的输入材料支持至少一次配方
     * （用于开始新配方前的检查）。
     */
    public boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe) {
        return canConsumeRecipeInputs(recipe, 1);
    }

    /**
     * 计算实际可用的并行数。
     * 按照以下顺序计算，避免数据溢出：
     * 1. 通过配方及能量上限，计算当前配方理论允许的最大并行
     * 2. 通过催化剂获取当前催化剂允许的并行量
     * 3. 通过输入物品，匹配配方实际能运行的并行量
     * 4. 通过输出空间，计算能容纳的并行量
     * <p>
     * 所有计算都遵循"先除再乘"原则，避免溢出。
     */
    public int calculateActualParallel(AdvancedAlloyFurnaceRecipe recipe) {
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        ResolvedCatalystEffect resolvedCatalystEffect = CatalystEffectResolver.resolve(recipe, catalystStack, recipe.processTime());
        long energyParallel = AlloyFurnaceParallelCalculator.calculateEnergyParallel(recipe, resolvedCatalystEffect);
        long catalystParallel = resolvedCatalystEffect.recipeParallel();
        int materialParallel = this.calculateMaterialParallel(recipe);
        int outputParallel = this.calculateOutputParallel(recipe);
        return AlloyFurnaceParallelCalculator.calculateStartableParallel(energyParallel, catalystParallel, materialParallel, outputParallel);
    }

    /**
     * 计算催化剂允许的并行数。
     * 优先使用传入的缓存催化剂效果，避免重复解析。
     *
     * @param recipe               配方
     * @param cachedCatalystEffect 已缓存的催化剂效果（可为 null）
     */
    public int calculateCatalystParallel(AdvancedAlloyFurnaceRecipe recipe,
                                         @Nullable ResolvedCatalystEffect cachedCatalystEffect) {
        if (cachedCatalystEffect != null) {
            return clampParallelToInt(cachedCatalystEffect.recipeParallel());
        }
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        return clampParallelToInt(
                CatalystEffectResolver.resolve(recipe, catalystStack, recipe.processTime()).recipeParallel());
    }

    private static int clampParallelToInt(long parallel) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, parallel));
    }

    /**
     * 计算输入材料允许的并行数。
     * 对于每种材料: 可用数量 / 配方需求数量 = 该材料允许的并行数，取所有材料的最小值。
     */
    public int calculateMaterialParallel(AdvancedAlloyFurnaceRecipe recipe) {
        return FurnaceInputPort.calculateMaterialParallel(
                recipe, this.itemHandler, INPUT_SLOTS_START, INPUT_SLOTS_COUNT,
                this.inputFluidTanks, FLUID_TANK_COUNT,
                this.inputChemicalStorage, currentChemicalKeyProvider());
    }

    /**
     * 计算输出空间允许的并行数。
     * 对于每种输出: 可用空间 / 单次产出数量 = 该输出允许的并行数，取所有输出的最小值。
     */
    public int calculateOutputParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int maxParallel = Integer.MAX_VALUE;

        // 计算物品输出空间限制
        for (ItemStack output : recipe.outputs()) {
            long totalSpace = 0;
            int outputCount = output.getCount();

            if (outputCount <= 0) continue;

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                int slotLimit = this.itemHandler.getSlotLimit(i);

                if (slotStack.isEmpty()) {
                    totalSpace += slotLimit;
                } else if (ItemStack.isSameItemSameComponents(slotStack, output)) {
                    totalSpace += (long) slotLimit - slotStack.getCount();
                }
            }

            // 先除: 可用空间 / 单次产出数量 = 该输出允许的并行数
            long parallelLong = totalSpace / outputCount;
            int possibleParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
            maxParallel = Math.min(maxParallel, possibleParallel);

            if (maxParallel <= 0) return 0;
        }

        // 计算流体输出空间限制
        for (FluidStack outputFluid : recipe.outputFluids()) {
            long totalSpace = 0;
            int fluidAmount = outputFluid.getAmount();

            if (fluidAmount <= 0) continue;

            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                int tankCapacity = this.outputFluidTanks[i].getCapacity();

                if (tankFluid.isEmpty()) {
                    totalSpace += tankCapacity;
                } else if (FluidStack.isSameFluidSameComponents(tankFluid, outputFluid)) {
                    totalSpace += (long) tankCapacity - tankFluid.getAmount();
                }
            }

            // 先除: 可用空间 / 单次产出数量 = 该流体允许的并行数
            long parallelLong = totalSpace / fluidAmount;
            int possibleParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
            maxParallel = Math.min(maxParallel, possibleParallel);

            if (maxParallel <= 0) return 0;
        }

        ChemicalKeyProvider provider = currentChemicalKeyProvider();
        for (var outputChemical : recipe.keyOutputs()) {
            if (outputChemical == null || outputChemical.what() == null || outputChemical.amount() <= 0L
                    || !provider.isChemicalKey(outputChemical.what())) {
                continue;
            }
            var view = provider.fromGenericStack(outputChemical);
            if (view == null) return 0;
            long totalSpace = 0L;
            for (int i = 0; i < this.outputChemicalStorage.size(); i++) {
                var stored = this.outputChemicalStorage.getStackInSlot(i);
                if (stored.isEmpty()) {
                    totalSpace = saturatingAdd(totalSpace, this.outputChemicalStorage.capacity(i));
                } else if (stored.isSameType(view)) {
                    long free = this.outputChemicalStorage.capacity(i) - stored.amount();
                    if (free > 0L) totalSpace = saturatingAdd(totalSpace, free);
                }
            }
            long possible = totalSpace / outputChemical.amount();
            maxParallel = Math.min(maxParallel,
                    possible > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) possible);
            if (maxParallel <= 0) return 0;
        }

        return maxParallel;
    }

    private ChemicalKeyProvider currentChemicalKeyProvider() {
        ChemicalKeyProvider registered = ChemicalKeyProviders.get();
        return registered == ChemicalKeyProvider.NONE ? this.chemicalKeyProvider : registered;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
