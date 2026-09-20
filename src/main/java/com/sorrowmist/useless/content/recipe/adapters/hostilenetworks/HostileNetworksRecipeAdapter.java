package com.sorrowmist.useless.content.recipe.adapters.hostilenetworks;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.shadowsoffire.hostilenetworks.Hostile;
import dev.shadowsoffire.hostilenetworks.HostileConfig;
import dev.shadowsoffire.hostilenetworks.data.DataModel;
import dev.shadowsoffire.hostilenetworks.data.DataModelRegistry;
import dev.shadowsoffire.hostilenetworks.data.ModelTier;
import dev.shadowsoffire.hostilenetworks.data.ModelTierRegistry;
import dev.shadowsoffire.hostilenetworks.item.DataModelItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Hostile Neural Networks simulation and fabricator operations. */
public final class HostileNetworksRecipeAdapter
        implements IRecipeAdapter<HostileNetworksSyntheticRecipe> {
    private static final String MOD_ID = RecipeSourceIds.HOSTILE_NETWORKS;
    private static final int SIMULATION_TRAINING_TIME = 300;
    private static final int SIMULATION_INFERENCE_TIME = 240;
    private static final int FABRICATOR_TIME = 60;
    private static final float TRAINING_COST_MULTIPLIER = 1.2F;

    @Override
    public String sourceId() {
        return RecipeSourceIds.HOSTILE_NETWORKS;
    }

    @Override
    public Class<HostileNetworksSyntheticRecipe> getRecipeClass() {
        return HostileNetworksSyntheticRecipe.class;
    }

    /** The adapter covers two machines, so it uses the fallback mold path. */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        // 数据模型整体作为模具参与匹配，具体品级交给模具原料的数据区间判定。
        return mold.is(Hostile.Items.SIM_CHAMBER)
                || mold.is(Hostile.Items.LOOT_FABRICATOR)
                || mold.is(Hostile.Items.DATA_MODEL);
    }

    @Override
    public List<RecipeHolder<HostileNetworksSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        List<RecipeHolder<HostileNetworksSyntheticRecipe>> result = new ArrayList<>();
        for (DataModel model : DataModelRegistry.INSTANCE.getValues()) {
            addTrainingRecipes(result, model);
            addInferenceRecipes(result, model);
            addFabricatorRecipes(result, model);
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<HostileNetworksSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<HostileNetworksSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<HostileNetworksSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<HostileNetworksSyntheticRecipe>> result = new ArrayList<>();
        for (RecipeHolder<HostileNetworksSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe == null || recipe.molds().size() != 1 || !matchesMold(recipe, mold)) continue;

            boolean matches = actualInputs != null && !actualInputs.isEmpty()
                    ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, mergedKeyInputs(mergedKeys), 1L)
                    : matchesMergedInputs(recipe.inputs(), mergedInputs);
            if (matches) result.add(holder);
        }
        return List.copyOf(result);
    }

    /**
     * 按品级为模型生成升级配方。
     *
     * <p>每个品级本身就是一个数据区间 {@code [req(T), req(next(T)) - 1]}，与
     * {@code ModelTierRegistry.getByData} 的判定保持一致。升级到下一品级消耗的矩阵数固定为
     * 该品级的跨度 {@code req(next(T)) - req(T)}，产出的模型数据正好落在下一品级起点。
     *
     * <p>这样任意数据值的模型都能被唯一一条升级配方命中，不再要求数据精确等于某个阈值。
     */
    private static void addTrainingRecipes(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result, DataModel model) {
        Ingredient matrix = model.input();
        if (AdapterUtils.isIngredientEmpty(matrix)) return;

        List<ModelTier> tiers = ModelTierRegistry.getSortedTiers();
        for (int index = 0; index + 1 < tiers.size(); index++) {
            addTrainingRecipe(result, model, tiers.get(index), tiers.get(index + 1), matrix);
        }
    }

    /** 生成由 {@code tier} 升到 {@code nextTier} 的单条配方。 */
    private static void addTrainingRecipe(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result, DataModel model,
            ModelTier tier, ModelTier nextTier, Ingredient matrix) {
        // 真实模拟室拒绝无法模拟的品级，这些品级不存在升级配方。
        if (tier == null || nextTier == null || !tier.canSim()) return;

        int fromData = Math.max(0, model.getRequiredData(tier));
        int targetData = model.getRequiredData(nextTier);
        int span = targetData - fromData;
        if (span <= 0) return;

        Ingredient inputModel = DataModelRangeIngredient.of(model, tier);
        if (inputModel == null) return;

        // 每次模拟必定产出一个基础掉落，预测掉落按该品级准确率的期望值取整。
        long predictions = 0L;
        float accuracy = tier.accuracy();
        if (Float.isFinite(accuracy) && accuracy > 0F) {
            predictions = Math.round(span * (double) accuracy);
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(modelStack(model, targetData));
        if (!addOptionalOutput(outputs, model.baseDrop(), span)
                || !addOptionalOutput(outputs, model.getPredictionDrop(), predictions)) {
            return;
        }

        long energy = trainingEnergy(model.simCost(), span, 1);
        int processTime = safeProcessTime(multiply(SIMULATION_TRAINING_TIME, span));
        if (energy < 0L || processTime <= 0) return;

        ResourceLocation id = recipeId(model, "training/" + tier.name());
        if (id == null) return;

        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(
                        new CountedIngredient(inputModel, 1L),
                        new CountedIngredient(matrix, span)),
                List.of(),
                List.of(),
                outputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(simChamberMold()),
                AlloyFurnaceMode.NORMAL);
        result.add(holder(recipe));
    }

    /**
     * 为每个可模拟品级生成一条推理配方。
     *
     * <p>模型放在模具槽中不被消耗，数据也保持不变，因此每个品级对应一条以该品级数据区间
     * 为模具的配方。这样处于品级区间内任意数据值的模型都能推理，不再要求数据精确等于
     * 品级起点。
     */
    private static void addInferenceRecipes(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result, DataModel model) {
        for (ModelTier tier : ModelTierRegistry.getSortedTiers()) {
            addInferenceRecipe(result, model, tier);
        }
    }

    private static void addInferenceRecipe(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result,
            DataModel model, ModelTier tier) {
        // 真实模拟室拒绝故障模型和不可模拟的自定义品级。
        if (tier == null || !tier.canSim()) return;

        Ingredient matrix = model.input();
        float accuracy = tier.accuracy();
        if (AdapterUtils.isIngredientEmpty(matrix)
                || !Float.isFinite(accuracy) || accuracy <= 0F) {
            // 零准确率品级没有有限的确定性批量能产出预测，因此不存在推理配方。
            return;
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = inferenceOutputs(model, accuracy);
        if (scaled.isEmpty()) return;

        ExpectedOutputScaler.ScaledOutputs outputsFor = scaled.get();
        int operations = outputsFor.operations();
        List<ItemStack> outputs = new ArrayList<>();
        if (!addAllOutputs(outputs, outputsFor.outputs()) || outputs.isEmpty()) return;

        Ingredient mold = DataModelRangeIngredient.of(model, tier);
        if (mold == null) return;

        long perRunEnergy = multiply(model.simCost(), SIMULATION_INFERENCE_TIME);
        long energy = multiply(perRunEnergy, operations);
        int processTime = safeProcessTime(multiply(SIMULATION_INFERENCE_TIME, operations));
        if (energy < 0L || processTime <= 0) return;

        ResourceLocation id = recipeId(model, "inference/" + tier.name());
        if (id == null) return;

        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(matrix, operations)),
                List.of(),
                List.of(),
                outputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(mold),
                AlloyFurnaceMode.NORMAL);
        result.add(holder(recipe));
    }

    private static void addFabricatorRecipes(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result, DataModel model) {
        ItemStack prediction = model.getPredictionDrop();
        if (prediction.isEmpty()) return;

        List<ItemStack> drops = model.fabDrops();
        for (int index = 0; index < drops.size(); index++) {
            ItemStack drop = drops.get(index);
            if (drop == null || drop.isEmpty()) continue;

            long energy = multiply(Math.max(0, HostileConfig.fabPowerCost), FABRICATOR_TIME);
            if (energy < 0L) continue;

            ResourceLocation id = recipeId(model, "fabricator/" + index);
            if (id == null) continue;

            AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                    id,
                    List.of(new CountedIngredient(exact(prediction), 1L)),
                    List.of(),
                    List.of(),
                    List.of(drop.copy()),
                    List.of(),
                    List.of(),
                    energy,
                    FABRICATOR_TIME,
                    Ingredient.EMPTY,
                    0,
                    List.of(lootFabricatorMold()),
                    AlloyFurnaceMode.NORMAL);
            result.add(holder(recipe));
        }
    }

    /**
     * Calculates the smallest deterministic inference batch with the same expected outputs as
     * the Simulation Chamber. A base drop is guaranteed for each operation; prediction drops
     * use the tier accuracy, including accuracies above 100%.
     */
    private static Optional<ExpectedOutputScaler.ScaledOutputs> inferenceOutputs(
            DataModel model, float accuracy) {
        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        addGuaranteedWeightedOutput(weightedOutputs, model.baseDrop());

        ItemStack prediction = model.getPredictionDrop();
        if (!prediction.isEmpty()) {
            int guaranteed = (int) Math.floor(accuracy);
            double fractional = accuracy - guaranteed;
            if (guaranteed > 0) {
                weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        prediction.copyWithCount(1), guaranteed, guaranteed, 1.0D));
            }
            if (fractional > 1.0E-9D) {
                weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        prediction.copyWithCount(1), 1, 1, fractional));
            }
        }
        return ExpectedOutputScaler.scale(weightedOutputs);
    }

    private static void addGuaranteedWeightedOutput(
            List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) return;
        int count = stack.getCount();
        weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                stack.copyWithCount(1), count, count, 1.0D));
    }

    private static boolean addAllOutputs(List<ItemStack> outputs, List<ItemStack> additions) {
        if (additions == null) return true;
        for (ItemStack addition : additions) {
            if (addition == null || addition.isEmpty()) continue;
            // The scaler has already materialized the complete batch count into this stack.
            if (!addCountedOutput(outputs, addition, 1L)) return false;
        }
        return true;
    }

    /**
     * 追加一个可选产物：数量为零或产物为空时静默跳过，不会导致整条配方失效。
     *
     * <p>部分模型没有预测掉落或基础掉落，这类模型仍应保留升级与推理配方，
     * 只是缺少对应产物，因此这里与 {@link #addCountedOutput} 的失败语义区分开。
     *
     * @return 仅在数值溢出等无法继续的情况下返回 false
     */
    private static boolean addOptionalOutput(List<ItemStack> outputs, ItemStack stack, long count) {
        if (count <= 0L || stack == null || stack.isEmpty()) return true;
        return addCountedOutput(outputs, stack, count);
    }

    private static boolean addCountedOutput(List<ItemStack> outputs, ItemStack stack, long count) {
        if (count <= 0L) return true;
        if (stack == null || stack.isEmpty() || count > Integer.MAX_VALUE) return false;
        long stackCount = Math.max(1L, stack.getCount());
        long total;
        try {
            total = Math.multiplyExact(stackCount, count);
        } catch (ArithmeticException exception) {
            return false;
        }
        if (total > Integer.MAX_VALUE) return false;
        ItemStack output = stack.copy();
        output.setCount((int) total);
        outputs.add(output);
        return true;
    }

    private static RecipeHolder<HostileNetworksSyntheticRecipe> holder(AdvancedAlloyFurnaceRecipe recipe) {
        return new RecipeHolder<>(recipe.id(), new HostileNetworksSyntheticRecipe(recipe));
    }

    private static ItemStack modelStack(DataModel model, int data) {
        ItemStack stack = new ItemStack(Hostile.Items.DATA_MODEL);
        DataModelItem.setStoredModel(stack, model);
        DataModelItem.setData(stack, data);
        return stack;
    }

    private static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(false, stack.copyWithCount(1));
    }

    private static Ingredient simChamberMold() {
        return AdapterUtils.toMoldIngredient(Hostile.Items.SIM_CHAMBER.value().getDefaultInstance());
    }

    private static Ingredient lootFabricatorMold() {
        return AdapterUtils.toMoldIngredient(Hostile.Items.LOOT_FABRICATOR.value().getDefaultInstance());
    }

    private static boolean matchesMold(AdvancedAlloyFurnaceRecipe recipe, ItemStack mold) {
        return AdapterUtils.matchesMold(recipe.mold(), mold);
    }

    private static boolean matchesMergedInputs(
            List<CountedIngredient> requirements, Map<Ingredient, Long> available) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient requirement : requirements) {
            AdapterUtils.mergeIngredient(required, requirement.ingredient(), requirement.count());
        }
        return ItemIngredientAllocator.matches(available, required);
    }

    private static List<appeng.api.stacks.GenericStack> mergedKeyInputs(
            Map<appeng.api.stacks.AEKey, Long> mergedKeys) {
        if (mergedKeys == null || mergedKeys.isEmpty()) return List.of();
        List<appeng.api.stacks.GenericStack> result = new ArrayList<>();
        for (Map.Entry<appeng.api.stacks.AEKey, Long> entry : mergedKeys.entrySet()) {
            long amount = Math.max(0L, entry.getValue());
            if (amount > 0L) result.add(new appeng.api.stacks.GenericStack(entry.getKey(), amount));
        }
        return result;
    }

    @Nullable
    private static ResourceLocation recipeId(DataModel model, String operation) {
        ResourceLocation modelId = DataModelRegistry.INSTANCE.getKey(model);
        if (modelId == null || operation == null || operation.isBlank()) return null;
        String path = (modelId.getNamespace() + "_" + modelId.getPath()).replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, "compat/" + operation + "/" + path);
    }

    private static long trainingEnergy(int simCost, int trainings, int operations) {
        long perTick = Math.max(0, Mth.ceil(simCost * TRAINING_COST_MULTIPLIER));
        long perTraining = multiply(perTick, SIMULATION_TRAINING_TIME);
        if (perTraining < 0L) return -1L;
        long allTrainings = multiply(trainings, operations);
        if (allTrainings < 0L) return -1L;
        return multiply(perTraining, allTrainings);
    }

    private static long multiply(long left, long right) {
        try {
            return Math.multiplyExact(Math.max(0L, left), Math.max(0L, right));
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    private static int safeProcessTime(long value) {
        return value <= 0L || value > Integer.MAX_VALUE ? 0 : (int) value;
    }
}
