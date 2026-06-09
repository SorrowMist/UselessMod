package com.sorrowmist.useless.content.blockentities;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.AELightningTechCompat;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.CrystalCatalyzerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.LightningAssemblyRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.LightningSimulationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.OverloadProcessingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.dataenergistics.DataEnergisticsCompat;
import com.sorrowmist.useless.content.recipe.adapters.dataenergistics.DataReassemblerRecipeAdapter;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 多线程合成任务类 - 处理 AE 网络的合成请求
 */
public class CraftingTask {
    private final int taskId;
    private final IPatternDetails pattern;
    private final CraftingTaskContext context;
    private final ReentrantLock taskLock = new ReentrantLock();
    private final List<ItemStack> taskInputItems = new ArrayList<>();
    private final List<FluidStack> taskInputFluids = new ArrayList<>();
    private final List<OutputKey> taskInputKeys = new ArrayList<>();
    private final AtomicInteger craftCount = new AtomicInteger(1);
    private volatile boolean cancelled = false;
    private volatile boolean processingComplete = false;
    private AdvancedAlloyFurnaceBlockEntity.AETaskProgress taskProgressRef = null;

    public CraftingTask(int taskId, IPatternDetails pattern, KeyCounter[] inputHolder, int totalCrafts,
                        CraftingTaskContext context) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
        this.craftCount.set(Math.max(1, totalCrafts));
        this.storeInputMaterials(inputHolder);
    }

    public boolean isSamePattern(IPatternDetails otherPattern) {
        if (this.pattern == null || otherPattern == null) {
            return false;
        }
        var thisOutputs = this.pattern.getOutputs();
        var otherOutputs = otherPattern.getOutputs();
        if (thisOutputs.size() != otherOutputs.size()) {
            return false;
        }
        for (int i = 0; i < thisOutputs.size(); i++) {
            if (!thisOutputs.get(i).what().equals(otherOutputs.get(i).what())) {
                return false;
            }
        }
        return true;
    }

    public boolean isProcessingComplete() {
        return processingComplete;
    }

    public void addMaterials(KeyCounter[] additionalInput) {
        taskLock.lock();
        try {
            if (processingComplete) {
                return;
            }
            craftCount.incrementAndGet();
            storeInputMaterials(additionalInput);
            if (taskProgressRef != null) {
                taskProgressRef.updateCraftCount(craftCount.get());
                context.markChanged();
                context.sendAETaskProgressToClients();
            }
        } finally {
            taskLock.unlock();
        }
    }

    private void storeInputMaterials(KeyCounter[] counters) {
        if (counters == null) return;

        for (KeyCounter counter : counters) {
            if (counter == null) continue;

            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();

                if (key instanceof AEItemKey itemKey) {
                    // 普通物品：直接转换为 ItemStack
                    ItemStack stack = itemKey.toStack((int) amount);
                    taskInputItems.add(stack);
                } else if (key instanceof AEFluidKey fluidKey) {
                    // 流体：转换为 FluidStack
                    FluidStack stack = new FluidStack(fluidKey.getFluid(), (int) amount);
                    taskInputFluids.add(stack);
                } else {
                    ItemStack wrappedStack = GenericStack.wrapInItemStack(key, 1);
                    if (!wrappedStack.isEmpty()) {
                        taskInputItems.add(wrappedStack);
                        taskInputKeys.add(new OutputKey(key, amount));
                    }
                }
            }
        }
    }

    /**
     * 使用本体模具/催化剂统一查找配方，与机器本体匹配逻辑一致
     */
    private AdvancedAlloyFurnaceRecipe findTaskRecipe() {
        if (context.getLevel() == null) return null;

        List<ItemStack> tempInputs = new ArrayList<>(taskInputItems);
        List<FluidStack> tempFluids = new ArrayList<>(taskInputFluids);

        ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
        if (!catalystStack.isEmpty()) {
            tempInputs.add(catalystStack.copy());
        }

        ItemStack moldStack = context.getItemHandler().getStackInSlot(context.getMoldSlot());

        AdvancedAlloyFurnaceRecipe recipe = AlloyFurnaceRecipeManager.getInstance().findRecipe(
                context.getLevel(), tempInputs, tempFluids, moldStack
        );
        if (recipe != null) {
            return recipe;
        }

        recipe = findDataEnergisticsRecipe(tempInputs, tempFluids, moldStack);
        if (recipe != null) {
            return recipe;
        }

        return findAELightningTechRecipe(tempInputs, tempFluids, moldStack);
    }

    private AdvancedAlloyFurnaceRecipe findAELightningTechRecipe(List<ItemStack> inputs, List<FluidStack> fluids, ItemStack moldStack) {
        if (!AELightningTechCompat.isAELightningTechLoaded() || context.getLevel() == null) {
            return null;
        }
        if (moldStack == null || moldStack.isEmpty()) {
            return null;
        }

        ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(moldStack.getItem());
        if (!"ae2lt".equals(moldId.getNamespace())) {
            return null;
        }

        return switch (moldId.getPath()) {
            case "lightning_simulation_room" -> findLightningSimulationRecipe(inputs);
            case "lightning_assembly_chamber" -> findLightningAssemblyRecipe(inputs);
            case "overload_processing_factory" -> findOverloadProcessingRecipe(inputs, fluids);
            default -> findCrystalCatalyzerRecipe(inputs, fluids, moldStack);
        };
    }

    private AdvancedAlloyFurnaceRecipe findLightningSimulationRecipe(List<ItemStack> inputs) {
        LightningSimulationRecipeAdapter adapter = new LightningSimulationRecipeAdapter();
        for (RecipeHolder<LightningSimulationRecipe> holder : context.getLevel().getRecipeManager().getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())) {
            LightningSimulationRecipe recipe = holder.value();
            if (matchesLightningSimulationInputs(recipe.inputs(), inputs)
                    && matchesLightningRequirement(recipe.lightningTier(), recipe.lightningCost())
                    && matchesItemOnlyOutput(recipe.getResultStack())) {
                return adapter.convert(holder, context.getLevel());
            }
        }
        return null;
    }

    private AdvancedAlloyFurnaceRecipe findLightningAssemblyRecipe(List<ItemStack> inputs) {
        LightningAssemblyRecipeAdapter adapter = new LightningAssemblyRecipeAdapter();
        for (RecipeHolder<LightningAssemblyRecipe> holder : context.getLevel().getRecipeManager().getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())) {
            LightningAssemblyRecipe recipe = holder.value();
            if (matchesLightningSimulationInputs(recipe.inputs(), inputs)
                    && matchesLightningRequirement(recipe.lightningTier(), recipe.lightningCost())
                    && matchesItemOnlyOutput(recipe.getResultStack())) {
                return adapter.convert(holder, context.getLevel());
            }
        }
        return null;
    }

    private AdvancedAlloyFurnaceRecipe findOverloadProcessingRecipe(List<ItemStack> inputs, List<FluidStack> fluids) {
        OverloadProcessingRecipeAdapter adapter = new OverloadProcessingRecipeAdapter();
        for (RecipeHolder<OverloadProcessingRecipe> holder : context.getLevel().getRecipeManager().getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())) {
            OverloadProcessingRecipe recipe = holder.value();
            if (matchesOverloadInputs(recipe.itemInputs(), inputs)
                    && matchesFluidInput(recipe.fluidInput(), fluids)
                    && matchesLightningRequirement(recipe.lightningTier(), recipe.lightningCost())
                    && matchesOutputs(recipe.itemResults(), recipe.fluidResult().isEmpty() ? List.of() : List.of(recipe.fluidResult()))) {
                return adapter.convert(holder, context.getLevel());
            }
        }
        return null;
    }

    private AdvancedAlloyFurnaceRecipe findCrystalCatalyzerRecipe(List<ItemStack> inputs, List<FluidStack> fluids, ItemStack moldStack) {
        CrystalCatalyzerRecipeAdapter adapter = new CrystalCatalyzerRecipeAdapter();
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : context.getLevel().getRecipeManager().getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())) {
            CrystalCatalyzerRecipe recipe = holder.value();
            if (recipe.mode() == Mode.CRYSTAL
                    && recipe.catalyst().map(catalyst -> catalyst.test(moldStack)).orElse(false)
                    && matchesFluidInput(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), fluids)
                    && matchesLightningRequirement(recipe.lightningTier(), recipe.lightningCost())
                    && matchesItemOnlyOutput(recipe.getOutputTemplate())) {
                return adapter.convert(holder, context.getLevel());
            }
        }
        return null;
    }

    private boolean matchesLightningSimulationInputs(List<LightningSimulationIngredient> recipeInputs, List<ItemStack> inputs) {
        for (LightningSimulationIngredient ingredient : recipeInputs) {
            if (!matchesIngredient(ingredient.ingredient(), ingredient.count(), inputs)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesOverloadInputs(List<OverloadProcessingIngredient> recipeInputs, List<ItemStack> inputs) {
        for (OverloadProcessingIngredient ingredient : recipeInputs) {
            if (!matchesIngredient(ingredient.ingredient(), ingredient.count(), inputs)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesIngredient(Ingredient ingredient, long amount, List<ItemStack> inputs) {
        long found = 0;
        for (ItemStack input : inputs) {
            if (ingredient.test(input)) {
                found += input.getCount();
            }
        }
        return found >= amount;
    }

    private boolean matchesFluidInput(FluidStack required, List<FluidStack> fluids) {
        if (required.isEmpty()) {
            return true;
        }
        long found = 0;
        for (FluidStack fluid : fluids) {
            if (FluidStack.isSameFluidSameComponents(fluid, required)) {
                found += fluid.getAmount();
            }
        }
        return found >= required.getAmount();
    }

    private boolean matchesLightningRequirement(LightningKey.Tier tier, long amount) {
        LightningKey requiredKey = LightningKey.of(tier);
        for (OutputKey inputKey : taskInputKeys) {
            if (inputKey.key.equals(requiredKey) && inputKey.amount >= amount) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesItemOnlyOutput(ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        return matchesOutputs(List.of(output), List.of());
    }

    private boolean matchesOutputs(List<ItemStack> itemOutputs, List<FluidStack> fluidOutputs) {
        for (var patternOutput : pattern.getOutputs()) {
            boolean matched = false;
            if (patternOutput.what() instanceof AEItemKey itemKey) {
                ItemStack patternStack = itemKey.toStack((int) Math.min(patternOutput.amount(), Integer.MAX_VALUE));
                for (ItemStack output : itemOutputs) {
                    if (ItemStack.isSameItem(patternStack, output)) {
                        matched = true;
                        break;
                    }
                }
            } else if (patternOutput.what() instanceof AEFluidKey fluidKey) {
                for (FluidStack output : fluidOutputs) {
                    if (fluidKey.getFluid().isSame(output.getFluid())) {
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private AdvancedAlloyFurnaceRecipe findDataEnergisticsRecipe(List<ItemStack> inputs, List<FluidStack> fluids, ItemStack moldStack) {
        if (!DataEnergisticsCompat.isDataEnergisticsLoaded() || context.getLevel() == null) {
            return null;
        }
        if (!isDataReassemblerMold(moldStack)) {
            return null;
        }

        DataReassemblerRecipeAdapter adapter = new DataReassemblerRecipeAdapter();
        for (RecipeHolder<DataRipperReassemblerRecipe> holder : context.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get())) {
            DataRipperReassemblerRecipe recipe = holder.value();
            if (matchesDataReassemblerInputs(recipe, inputs, fluids) && matchesDataReassemblerOutputs(recipe)) {
                return adapter.convert(holder, context.getLevel());
            }
        }

        return null;
    }

    private boolean isDataReassemblerMold(ItemStack moldStack) {
        if (moldStack == null || moldStack.isEmpty()) {
            return false;
        }
        ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(moldStack.getItem());
        return "data_energistics".equals(moldId.getNamespace()) && "data_reassembler".equals(moldId.getPath());
    }

    private boolean matchesDataReassemblerInputs(DataRipperReassemblerRecipe recipe, List<ItemStack> inputs, List<FluidStack> fluids) {
        for (DataRipperReassemblerIngredient ingredient : recipe.getItemInputs()) {
            long found = 0;
            for (ItemStack input : inputs) {
                if (ingredient.ingredient().test(input)) {
                    found += input.getCount();
                }
            }
            if (found < ingredient.count()) {
                return false;
            }
        }

        for (GenericStack fluidInput : recipe.getFluidInputs()) {
            if (!(fluidInput.what() instanceof AEFluidKey requiredFluid)) {
                continue;
            }
            long found = 0;
            for (FluidStack input : fluids) {
                AEFluidKey inputKey = AEFluidKey.of(input);
                if (inputKey != null && inputKey.equals(requiredFluid)) {
                    found += input.getAmount();
                }
            }
            if (found < fluidInput.amount()) {
                return false;
            }
        }

        GenericStack keyInput = recipe.getKeyInput();
        if (keyInput == null || keyInput.amount() <= 0) {
            return true;
        }
        if (keyInput.what() instanceof AEItemKey itemKey) {
            long found = 0;
            Item item = itemKey.getItem();
            for (ItemStack input : inputs) {
                if (input.is(item)) {
                    found += input.getCount();
                }
            }
            return found >= keyInput.amount();
        }
        if (keyInput.what() instanceof AEFluidKey fluidKey) {
            long found = 0;
            for (FluidStack input : fluids) {
                AEFluidKey inputKey = AEFluidKey.of(input);
                if (inputKey != null && inputKey.equals(fluidKey)) {
                    found += input.getAmount();
                }
            }
            return found >= keyInput.amount();
        }
        for (OutputKey inputKey : taskInputKeys) {
            if (inputKey.key.equals(keyInput.what()) && inputKey.amount >= keyInput.amount()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDataReassemblerOutputs(DataRipperReassemblerRecipe recipe) {
        for (var patternOutput : pattern.getOutputs()) {
            boolean matched = false;
            if (patternOutput.what() instanceof AEItemKey itemKey) {
                ItemStack patternStack = itemKey.toStack((int) Math.min(patternOutput.amount(), Integer.MAX_VALUE));
                for (ItemStack recipeOutput : recipe.getItemOutputs()) {
                    if (ItemStack.isSameItem(patternStack, recipeOutput)) {
                        matched = true;
                        break;
                    }
                }
            } else if (patternOutput.what() instanceof AEFluidKey fluidKey) {
                for (GenericStack fluidOutput : recipe.getFluidOutputs()) {
                    if (fluidOutput.what() instanceof AEFluidKey outputKey && outputKey.equals(fluidKey)) {
                        matched = true;
                        break;
                    }
                }
            } else {
                GenericStack keyOutput = recipe.getKeyOutput();
                matched = keyOutput != null && keyOutput.what().equals(patternOutput.what()) && keyOutput.amount() >= patternOutput.amount();
            }

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private String getProductName() {
        if (pattern == null || pattern.getOutputs().isEmpty()) {
            return "Unknown";
        }

        var output = pattern.getOutputs().getFirst();
        if (output.what() instanceof AEItemKey itemKey) {
            return itemKey.getItem().getDescriptionId();
        } else if (output.what() instanceof AEFluidKey fluidKey) {
            return fluidKey.toString();
        }

        return "Unknown";
    }

    private boolean validateRecipe() {
        if (context.getLevel() == null || pattern == null) {
            returnMaterialsToAE();
            return false;
        }

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        if (recipe == null) {
            returnMaterialsToAE();
            return false;
        }

        if (pattern.getOutputs().isEmpty()) {
            returnMaterialsToAE();
            return false;
        }

        if (recipe.outputs().isEmpty() && recipe.outputFluids().isEmpty()) {
            returnMaterialsToAE();
            return false;
        }

        for (var patternOutput : pattern.getOutputs()) {
            boolean matched = false;

            if (patternOutput.what() instanceof AEItemKey itemKey) {
                ItemStack patternStack = itemKey.toStack((int) patternOutput.amount());
                for (ItemStack recipeOutput : recipe.outputs()) {
                    if (ItemStack.isSameItem(patternStack, recipeOutput)) {
                        matched = true;
                        break;
                    }
                }
            } else if (patternOutput.what() instanceof AEFluidKey fluidKey) {
                for (FluidStack recipeFluid : recipe.outputFluids()) {
                    if (fluidKey.getFluid().isSame(recipeFluid.getFluid())) {
                        matched = true;
                        break;
                    }
                }
            } else {
                ItemStack patternStack = GenericStack.wrapInItemStack(patternOutput.what(), (int) Math.min(patternOutput.amount(), Integer.MAX_VALUE));
                for (ItemStack recipeOutput : recipe.outputs()) {
                    if (ItemStack.isSameItemSameComponents(patternStack, recipeOutput)) {
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                returnMaterialsToAE();
                return false;
            }
        }

        return true;
    }

    private void returnMaterialsToAE() {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        // 在工作线程中复制需要返回的材料，避免在主线程中访问taskInputItems
        final List<ItemStack> itemsToReturn;
        final List<FluidStack> fluidsToReturn;
        final List<OutputKey> keysToReturn;

        taskLock.lock();
        try {
            itemsToReturn = new ArrayList<>(taskInputItems);
            fluidsToReturn = new ArrayList<>(taskInputFluids);
            keysToReturn = new ArrayList<>(taskInputKeys);
            taskInputItems.clear();
            taskInputFluids.clear();
            taskInputKeys.clear();
        } finally {
            taskLock.unlock();
        }

        context.getLevel().getServer().execute(() -> {
            for (OutputKey keyToReturn : keysToReturn) {
                context.tryOutputKeyToAE(keyToReturn.key, keyToReturn.amount);
            }

            // 将任务输入物品返回给AE网络
            for (ItemStack stack : itemsToReturn) {
                if (isWrappedKeyStack(stack, keysToReturn)) {
                    continue;
                }
                if (!stack.isEmpty()) {
                    // 尝试输出到AE网络
                    long inserted = context.tryOutputToAE(stack);
                    int remainingCount = (int) (stack.getCount() - inserted);

                    // 如果AE网络没存下，尝试放入机器的输入槽
                    if (remainingCount > 0) {
                        ItemStack remainingStack = stack.copy();
                        remainingStack.setCount(remainingCount);

                        // 使用tryLock避免阻塞主线程
                        boolean locked = context.getCraftingLock().tryLock();
                        if (locked) {
                            try {
                                int inputSlotsStart = context.getInputSlotsStart();
                                int inputSlotsCount = context.getInputSlotsCount();
                                for (int i = inputSlotsStart; i < inputSlotsStart + inputSlotsCount; i++) {
                                    ItemStack slotStack = context.getItemHandler().getStackInSlot(i);
                                    if (slotStack.isEmpty()) {
                                        context.getItemHandler().setStackInSlot(i, remainingStack.copy());
                                        break;
                                    } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack) &&
                                            slotStack.getCount() < slotStack.getMaxStackSize()) {
                                        int addAmount = Math.min(remainingCount,
                                                slotStack.getMaxStackSize() - slotStack.getCount()
                                        );
                                        slotStack.grow(addAmount);
                                        remainingCount -= addAmount;
                                        if (remainingCount <= 0) break;
                                    }
                                }
                            } finally {
                                context.getCraftingLock().unlock();
                            }
                        }
                    }
                }
            }

            // 将任务输入流体返回给AE网络
            for (FluidStack fluidStack : fluidsToReturn) {
                if (!fluidStack.isEmpty()) {
                    // 尝试输出到AE网络
                    long inserted = context.tryOutputFluidToAE(fluidStack);
                    int remainingAmount = (int) (fluidStack.getAmount() - inserted);

                    // 如果AE网络没存下，尝试放入机器的输入流体槽
                    if (remainingAmount > 0) {
                        FluidStack remainingFluid = fluidStack.copy();
                        remainingFluid.setAmount(remainingAmount);

                        // 使用tryLock避免阻塞主线程
                        boolean locked = context.getCraftingLock().tryLock();
                        if (locked) {
                            try {
                                int fluidTankCount = context.getFluidTankCount();
                                FluidTank[] inputFluidTanks = context.getInputFluidTanks();
                                for (int i = 0; i < fluidTankCount; i++) {
                                    FluidTank tank = inputFluidTanks[i];
                                    if (tank.isEmpty() || tank.getFluid().getFluid().isSame(remainingFluid.getFluid())) {
                                        int filled = tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                                        remainingAmount -= filled;
                                        if (remainingAmount <= 0) break;
                                    }
                                }
                            } finally {
                                context.getCraftingLock().unlock();
                            }
                        }
                    }
                }
            }
        });
    }

    private int getRecipeProcessTime() {
        if (context.getLevel() == null) return 200;

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        if (recipe != null && recipe.processTime() > 0) {
            ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
            return CatalystParallelManager.calculateProcessTimeWithCatalyst(recipe.processTime(), catalystStack);
        }

        return 200;
    }

    public void run() {
        if (cancelled || context.getLevel() == null || context.getLevel().isClientSide) {
            return;
        }

        // 验证配方：检查输入材料是否能通过有效的配方合成出样板定义的产物
        if (!validateRecipe()) {
            // 找不到有效的配方，取消任务
            processingComplete = true;
            return;
        }

        // 保存基础处理时间用于异常处理
        final int baseProcessTime = getRecipeProcessTime();

        // 获取配方基础能量消耗（每tick），优先使用配方自身能量
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        final int baseEnergyPerTick;
        if (recipe != null && recipe.processTime() > 0) {
            baseEnergyPerTick = Math.max(1, recipe.energy() / recipe.processTime());
        } else {
            baseEnergyPerTick = 200;
        }

        try {
            int currentCraftCount = craftCount.get(); // 获取当前需要合成的次数
            // 使用机器本体催化剂槽位中的催化剂来决定最大并行数
            int maxParallel = context.getCatalystMaxParallel();
            if (maxParallel <= 0) {
                maxParallel = 1; // 至少为1
            }

            // 获取合成产物名称和单次产出数量
            String productName = getProductName();
            int outputCount = 1;
            if (pattern != null && !pattern.getOutputs().isEmpty()) {
                var output = pattern.getOutputs().getFirst();
                outputCount = (int) output.amount();
            }

            ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
            boolean useUsefulIngot = !catalystStack.isEmpty() && CatalystParallelManager.isUsefulIngot(catalystStack);

            // 只有不用有用锭时才用能量限制并行（用有用锭时能量不限制并行）
            if (baseEnergyPerTick > 0 && !useUsefulIngot) {
                int maxEnergyParallel = context.getEnergyManager().getMaxEnergyStored() / baseEnergyPerTick;
                maxParallel = Math.min(maxParallel, Math.max(1, maxEnergyParallel));
            }

            // 总处理时间 = 基础时间 × ceil(合成次数 / 最大并行数)
            int batches = (int) Math.ceil((double) currentCraftCount / maxParallel);
            int processTime = baseProcessTime * batches;
            int lastBatchSize = currentCraftCount - maxParallel * (batches - 1);

            int progress = 0;

            int totalOutputCount = currentCraftCount * outputCount; // 最终产物总数 = 合成次数 × 单次产出数量

            // 创建任务进度信息并添加到地图中
            AdvancedAlloyFurnaceBlockEntity.AETaskProgress taskProgress = new AdvancedAlloyFurnaceBlockEntity.AETaskProgress(
                    productName, processTime, currentCraftCount, totalOutputCount
            );
            context.getAETaskProgressMap().put(taskId, taskProgress);
            // 保存任务进度引用，用于任务合并时更新
            this.taskProgressRef = taskProgress;

            // 更新总进度
            context.getTotalAEMaxProgressAtomic().addAndGet(processTime);
            context.markChanged();

            // 发送初始任务进度到客户端
            context.sendAETaskProgressToClients();

            int progressUpdateCounter = 0;
            boolean energyFailed = false;
            while (progress < processTime && !cancelled) {
                // 先检查是否已取消（使用volatile读取，不需要锁）
                if (cancelled) break;

                // 根据当前批次计算实际并行数（最后一批可能不满maxParallel）
                int batchIndex = baseProcessTime > 0 ? progress / baseProcessTime : 0;
                int actualBatchParallel = (batchIndex < batches - 1) ? maxParallel : lastBatchSize;
                long energyRequiredLong = useUsefulIngot ? (long) baseEnergyPerTick :
                        (long) baseEnergyPerTick * actualBatchParallel;
                int energyRequired =
                        energyRequiredLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) energyRequiredLong;

                // 尝试消耗能量（不需要锁）
                if (!context.getEnergyManager().tryConsumeEnergy(energyRequired)) {
                    energyFailed = true;
                    break;
                }

                // 更新进度（需要锁保护）
                taskLock.lock();
                try {
                    if (cancelled) break;
                    progress++;
                    context.getTotalAEProgressAtomic().incrementAndGet();
                    // 更新单个任务的进度
                    taskProgress.setProgress(progress);
                    context.markChanged();

                    // 每20 ticks发送一次进度更新（大约每秒一次）
                    progressUpdateCounter++;
                    if (progressUpdateCounter >= 20) {
                        context.sendAETaskProgressToClients();
                        progressUpdateCounter = 0;
                    }
                } finally {
                    taskLock.unlock();
                }

                Thread.sleep(50);
            }

            if (energyFailed) {
                returnMaterialsToAE();
                processingComplete = true;
                context.getTotalAEProgressAtomic().addAndGet(-progress);
                context.getTotalAEMaxProgressAtomic().addAndGet(-processTime);
                context.getAETaskProgressMap().remove(taskId);
                context.markChanged();
                return;
            }

            if (!cancelled && progress >= processTime) {
                // 使用最新的 craftCount 值，确保包含所有合并的材料
                completeCrafting(craftCount.get());
            }

            // 标记任务已完成处理（不再接受新材料合并）
            processingComplete = true;

            // 任务完成或取消后更新总进度
            context.getTotalAEProgressAtomic().addAndGet(-progress);
            context.getTotalAEMaxProgressAtomic().addAndGet(-processTime);
            // 移除任务进度信息
            context.getAETaskProgressMap().remove(taskId);
            context.markChanged();

            // 发送任务完成的进度更新到客户端
            context.sendAETaskProgressToClients();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 任务中断后更新总进度
            processingComplete = true;
            int maxParallel = context.getCatalystMaxParallel();
            if (maxParallel <= 0) {
                maxParallel = 1;
            }
            if (baseEnergyPerTick > 0) {
                int maxEnergyParallel = context.getEnergyManager().getMaxEnergyStored() / baseEnergyPerTick;
                maxParallel = Math.min(maxParallel, Math.max(1, maxEnergyParallel));
            }
            int batches = (int) Math.ceil((double) craftCount.get() / maxParallel);
            context.getTotalAEMaxProgressAtomic().addAndGet(-baseProcessTime * batches);
            context.markChanged();
        }
    }

    private void completeCrafting(int craftCount) {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        // 在工作线程中预先准备输出数据，避免在主线程中等待锁
        final List<OutputItem> outputItems = new ArrayList<>();
        final List<OutputFluid> outputFluids = new ArrayList<>();
        final List<OutputKey> outputKeys = new ArrayList<>();

        for (var output : pattern.getOutputs()) {
            if (output.what() instanceof AEItemKey itemKey) {
                ItemStack outputStack = itemKey.toStack((int) (output.amount() * craftCount));
                outputItems.add(new OutputItem(outputStack));
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                FluidStack outputFluid = new FluidStack(fluidKey.getFluid(), (int) (output.amount() * craftCount));
                outputFluids.add(new OutputFluid(outputFluid));
            } else {
                outputKeys.add(new OutputKey(output.what(), output.amount() * craftCount));
            }
        }

        // 清空任务的独立存储空间（在工作线程中完成，不需要锁）
        taskLock.lock();
        try {
            taskInputItems.clear();
            taskInputFluids.clear();
            taskInputKeys.clear();
        } finally {
            taskLock.unlock();
        }

        // 在主线程中执行AE网络输出和槽位操作，使用tryLock避免阻塞
        context.getLevel().getServer().execute(() -> {
            // 处理物品输出
            for (OutputItem outputItem : outputItems) {
                ItemStack outputStack = outputItem.stack;

                // 优先输出到AE网络
                long inserted = context.tryOutputToAE(outputStack);
                int remainingCount = (int) (outputStack.getCount() - inserted);

                // 如果AE网络没存下，输出到自己的输出栏
                if (remainingCount > 0) {
                    ItemStack remainingStack = outputStack.copy();
                    remainingStack.setCount(remainingCount);
                    // 使用tryLock避免阻塞主线程
                    boolean locked = context.getCraftingLock().tryLock();
                    if (locked) {
                        try {
                            int outputSlotsStart = context.getOutputSlotsStart();
                            int outputSlotsCount = context.getOutputSlotsCount();
                            for (int i = outputSlotsStart; i < outputSlotsStart + outputSlotsCount; i++) {
                                ItemStack slotStack = context.getItemHandler().getStackInSlot(i);
                                if (slotStack.isEmpty()) {
                                    context.getItemHandler().setStackInSlot(i, remainingStack.copy());
                                    break;
                                } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack)) {
                                    slotStack.grow(remainingStack.getCount());
                                    break;
                                }
                            }
                        } finally {
                            context.getCraftingLock().unlock();
                        }
                    }
                    // 如果获取不到锁，物品会丢失（但这种情况很少发生，且比卡死游戏好）
                }
            }

            // 处理流体输出
            for (OutputFluid outputFluid : outputFluids) {
                FluidStack fluidStack = outputFluid.stack;

                // 优先输出到AE网络
                long inserted = context.tryOutputFluidToAE(fluidStack);
                int remainingAmount = (int) (fluidStack.getAmount() - inserted);

                // 如果AE网络没存下，输出到自己的流体输出槽
                if (remainingAmount > 0) {
                    FluidStack remainingFluid = new FluidStack(fluidStack.getFluid(), remainingAmount);
                    // 使用tryLock避免阻塞主线程
                    boolean locked = context.getCraftingLock().tryLock();
                    if (locked) {
                        try {
                            int fluidTankCount = context.getFluidTankCount();
                            FluidTank[] outputFluidTanks = context.getOutputFluidTanks();
                            for (int i = 0; i < fluidTankCount; i++) {
                                FluidTank tank = outputFluidTanks[i];
                                if (tank.isEmpty() || tank.getFluid().getFluid().isSame(remainingFluid.getFluid())) {
                                    tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                                    break;
                                }
                            }
                        } finally {
                            context.getCraftingLock().unlock();
                        }
                    }
                }
            }

            for (OutputKey outputKey : outputKeys) {
                context.tryOutputKeyToAE(outputKey.key, outputKey.amount);
            }
            context.markChanged();
        });
    }

    public void cancel() {
        this.cancelled = true;
    }

    // 辅助类用于存储输出数据
    private record OutputItem(ItemStack stack) {
    }

    private record OutputFluid(FluidStack stack) {
    }

    private record OutputKey(AEKey key, long amount) {
    }

    private boolean isWrappedKeyStack(ItemStack stack, List<OutputKey> keys) {
        if (stack.isEmpty()) {
            return false;
        }
        for (OutputKey inputKey : keys) {
            ItemStack wrappedStack = GenericStack.wrapInItemStack(inputKey.key, 1);
            if (ItemStack.isSameItemSameComponents(stack, wrappedStack)) {
                return true;
            }
        }
        return false;
    }
}
