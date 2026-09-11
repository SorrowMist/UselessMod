package com.sorrowmist.useless.content.recipe.adapters.hostilenetworks;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
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
        return mold.is(Hostile.Items.SIM_CHAMBER) || mold.is(Hostile.Items.LOOT_FABRICATOR);
    }

    @Override
    public List<RecipeHolder<HostileNetworksSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        List<RecipeHolder<HostileNetworksSyntheticRecipe>> result = new ArrayList<>();
        for (DataModel model : DataModelRegistry.INSTANCE.getValues()) {
            addTrainingRecipe(result, model);
            addInferenceRecipe(result, model);
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
     * One recipe covers the full defective-to-self-aware training path.
     *
     * <p>Simulation training always adds exactly {@code +1} data, so one defective model and
     * that many prediction matrices become one self-aware model. Each simmable training also
     * always yields the model's generalized prediction (overworld/nether/end/etc.), while the
     * matching mob prediction is the expected value of that tier's accuracy, rounded to a
     * single craft.
     */
    private static void addTrainingRecipe(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result, DataModel model) {
        ModelTier maxTier = selfAwareTier();
        if (maxTier == null) return;

        int targetData = model.getRequiredData(maxTier);
        Ingredient matrix = model.input();
        if (targetData <= 0 || AdapterUtils.isIngredientEmpty(matrix)) return;

        TrainingYield training = trainingYield(model, maxTier, targetData);
        if (training == null) return;

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(modelStack(model, targetData));
        if (!addCountedOutput(outputs, model.baseDrop(), training.baseDrops())
                || !addCountedOutput(outputs, model.getPredictionDrop(), training.predictions())) {
            return;
        }

        long energy = trainingEnergy(model.simCost(), targetData, 1);
        int processTime = safeProcessTime(multiply(SIMULATION_TRAINING_TIME, targetData));
        if (energy < 0L || processTime <= 0) return;

        ResourceLocation id = recipeId(model, "training");
        if (id == null) return;

        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(
                        new CountedIngredient(exact(modelStack(model, 0)), 1L),
                        new CountedIngredient(matrix, targetData)),
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
     * Self-aware inference keeps the model as a second mold so it is not consumed.
     * The matching prediction is guaranteed; the base drop is always produced by HNN inference.
     */
    private static void addInferenceRecipe(
            List<RecipeHolder<HostileNetworksSyntheticRecipe>> result, DataModel model) {
        ModelTier maxTier = selfAwareTier();
        if (maxTier == null) return;

        Ingredient matrix = model.input();
        if (AdapterUtils.isIngredientEmpty(matrix)) return;

        ItemStack selfAwareModel = modelStack(model, model.getRequiredData(maxTier));
        List<ItemStack> outputs = new ArrayList<>();
        if (!model.baseDrop().isEmpty()) outputs.add(model.baseDrop().copy());
        ItemStack prediction = model.getPredictionDrop();
        if (!prediction.isEmpty()) outputs.add(prediction.copy());
        if (outputs.isEmpty()) return;

        long energy = multiply(model.simCost(), SIMULATION_INFERENCE_TIME);
        if (energy < 0L) return;

        ResourceLocation id = recipeId(model, "inference");
        if (id == null) return;

        AdvancedAlloyFurnaceRecipe recipe = new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(matrix, 1L)),
                List.of(),
                List.of(),
                outputs,
                List.of(),
                List.of(),
                energy,
                SIMULATION_INFERENCE_TIME,
                Ingredient.EMPTY,
                0,
                List.of(simChamberMold(), exact(selfAwareModel)),
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
     * Generalized predictions are guaranteed on every simmable training. Mob predictions use
     * that tier's accuracy, including values above 100%.
     */
    @Nullable
    private static TrainingYield trainingYield(DataModel model, ModelTier maxTier, int targetData) {
        double expectedPredictions = 0.0;
        long expectedBaseDrops = 0L;
        List<ModelTier> tiers = ModelTierRegistry.getSortedTiers();
        for (int index = 0; index < tiers.size(); index++) {
            ModelTier tier = tiers.get(index);
            if (tier == maxTier || tier.isMax()) break;
            if (!tier.canSim()) continue;

            int tierStart = Math.max(0, model.getRequiredData(tier));
            int tierEnd = index + 1 < tiers.size()
                    ? Math.min(targetData, model.getRequiredData(tiers.get(index + 1)))
                    : targetData;
            int actions = Math.max(0, tierEnd - tierStart);
            if (actions <= 0) continue;

            expectedBaseDrops += actions;
            float accuracy = tier.accuracy();
            if (Float.isFinite(accuracy) && accuracy > 0F) {
                expectedPredictions += actions * (double) accuracy;
            }
        }
        if (expectedBaseDrops < 0L || !Double.isFinite(expectedPredictions) || expectedPredictions < 0.0) {
            return null;
        }
        long predictions = Math.round(expectedPredictions);
        if (predictions < 0L) return null;
        return new TrainingYield(expectedBaseDrops, predictions);
    }

    private static boolean addCountedOutput(List<ItemStack> outputs, ItemStack stack, long count) {
        if (count <= 0L) return true;
        if (stack == null || stack.isEmpty() || count > Integer.MAX_VALUE) return false;
        ItemStack output = stack.copy();
        output.setCount((int) count);
        outputs.add(output);
        return true;
    }

    private record TrainingYield(long baseDrops, long predictions) {
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
    private static ModelTier selfAwareTier() {
        try {
            for (ModelTier tier : ModelTierRegistry.getSortedTiers()) {
                if ("self_aware".equals(tier.name())) return tier;
            }
            return ModelTierRegistry.getMaxTier();
        } catch (RuntimeException exception) {
            return null;
        }
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
