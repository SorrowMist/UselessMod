package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.ItemStackToChemicalRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mekanism 冶金灌注机配方适配器
 * <p>
 * 将冶金灌注配方转换为高级合金熔炉配方。
 * 由于高级熔炉不支持化学品输入，需要将化学品转换为对应的物品输入。
 * <p>
 * 转换逻辑：
 * - 通过物品转化学品配方查找化学品对应的物品来源
 * - 使用最小公倍数计算批量配方（例如：1红石粉=10mb，1富集红石=80mb，
 *   配方需要10mb红石化学品，则转换为1红石粉 或 8铜锭+1富集红石出8个产物）
 */
public class MetallurgicInfuserRecipeAdapter implements IRecipeAdapter<ItemStackChemicalToItemStackRecipe> {

    // 基础能量消耗
    private static final int BASE_ENERGY = 3000;
    // 处理时间基础值（ticks）
    private static final int BASE_PROCESS_TIME = 40;

    // 缓存化学品到物品来源的映射（按化学品类型）
    private final Map<ResourceLocation, List<ChemicalSource>> chemicalSourceCache = new HashMap<>();

    @Override
    public Class<ItemStackChemicalToItemStackRecipe> getRecipeClass() {
        return ItemStackChemicalToItemStackRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackChemicalToItemStackRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        if (holder == null || level == null) return recipes;

        ItemStackChemicalToItemStackRecipe originalRecipe = holder.value();

        // 只处理冶金灌注配方
        if (!originalRecipe.getType().equals(MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value())) {
            return recipes;
        }

        ResourceLocation originalId = holder.id();

        // 获取物品输入
        var itemInput = originalRecipe.getItemInput();
        if (itemInput == null || itemInput.hasNoMatchingInstances()) {
            return recipes;
        }

        // 获取化学品输入
        var chemicalInput = originalRecipe.getChemicalInput();
        if (chemicalInput == null) {
            return recipes;
        }

        // 获取输出
        List<ItemStack> outputs = originalRecipe.getOutputDefinition();
        if (outputs.isEmpty()) {
            return recipes;
        }

        // 获取化学品来源信息
        List<ChemicalSource> sources = findChemicalSources(level, chemicalInput);
        if (sources.isEmpty()) {
            return recipes;
        }

        // 为每个化学品来源创建配方
        for (ChemicalSource source : sources) {
            AdvancedAlloyFurnaceRecipe recipe = createRecipe(
                    originalId, itemInput, chemicalInput, source, outputs, originalRecipe.perTickUsage()
            );
            if (recipe != null) {
                // 检查是否已存在相同输入输出的配方，避免重复
                AdvancedAlloyFurnaceRecipe existingRecipe = findRecipeWithSameInputsOutputs(recipes, recipe);
                if (existingRecipe == null) {
                    recipes.add(recipe);
                }
                // 如果存在相同输入输出的配方，跳过（已存在）
            }
        }

        return recipes;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<ItemStackChemicalToItemStackRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    /**
     * 查找化学品的物品来源（带缓存）
     */
    private List<ChemicalSource> findChemicalSources(Level level, ChemicalStackIngredient chemicalInput) {
        List<ChemicalSource> sources = new ArrayList<>();
        if (level == null) return sources;

        // 获取化学品表示以确定缓存键
        var chemicalReps = chemicalInput.getRepresentations();
        if (chemicalReps.isEmpty()) return sources;

        // 使用第一个化学品的ID作为缓存键 (getChemicalHolder().getKey().location() 替代已弃用的 getTypeRegistryName())
        ResourceLocation chemicalId = chemicalReps.get(0).getChemicalHolder().getKey().location();

        // 检查缓存
        if (chemicalSourceCache.containsKey(chemicalId)) {
            return chemicalSourceCache.get(chemicalId);
        }

        // 缓存未命中，从配方管理器查找
        RecipeManager recipeManager = level.getRecipeManager();

        // 获取所有物品转化学品配方（化学品转换和氧化）
        List<RecipeHolder<ItemStackToChemicalRecipe>> conversionRecipes = new ArrayList<>();
        conversionRecipes.addAll(recipeManager.getAllRecipesFor(MekanismRecipeTypes.TYPE_CHEMICAL_CONVERSION.value()));
        conversionRecipes.addAll(recipeManager.getAllRecipesFor(MekanismRecipeTypes.TYPE_OXIDIZING.value()));

        // 使用Set跟踪已处理的配方ID，避免重复处理同一个配方
        Set<ResourceLocation> processedRecipeIds = new HashSet<>();

        for (RecipeHolder<ItemStackToChemicalRecipe> holder : conversionRecipes) {
            // 跳过已处理的配方
            if (processedRecipeIds.contains(holder.id())) {
                continue;
            }

            ItemStackToChemicalRecipe recipe = holder.value();
            List<ChemicalStack> outputDefinitions = recipe.getOutputDefinition();

            for (ChemicalStack chemicalOutput : outputDefinitions) {
                ResourceLocation outputChemicalId = chemicalOutput.getChemicalHolder().getKey().location();

                // 确保该化学品ID的缓存列表存在
                if (!chemicalSourceCache.containsKey(outputChemicalId)) {
                    chemicalSourceCache.put(outputChemicalId, new ArrayList<>());
                }

                var itemInput = recipe.getInput();
                if (itemInput != null && !itemInput.hasNoMatchingInstances()) {
                    long amount = chemicalOutput.getAmount();
                    List<ChemicalSource> cachedSources = chemicalSourceCache.get(outputChemicalId);

                    // 避免重复添加
                    boolean existsInCache = cachedSources.stream()
                            .anyMatch(s -> s.recipeId().equals(holder.id()));
                    if (!existsInCache) {
                        ChemicalSource source = new ChemicalSource(itemInput, amount, holder.id());
                        cachedSources.add(source);

                        // 如果这是当前查找的化学品，也添加到返回列表
                        if (chemicalInput.test(chemicalOutput)) {
                            sources.add(source);
                            // 标记该配方已处理，避免重复添加
                            processedRecipeIds.add(holder.id());
                        }
                    }
                }
            }
        }

        return sources;
    }

    /**
     * 清除缓存（当配方重新加载时调用）
     */
    public void clearCache() {
        chemicalSourceCache.clear();
    }

    /**
     * 创建高级熔炉配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation originalId,
            mekanism.api.recipes.ingredients.ItemStackIngredient itemInput,
            ChemicalStackIngredient chemicalInput,
            ChemicalSource chemicalSource,
            List<ItemStack> outputs,
            boolean perTickUsage) {

        // 获取化学品需求量
        long requiredChemicalAmount = getRequiredChemicalAmount(chemicalInput);
        if (requiredChemicalAmount <= 0) {
            return null;
        }

        // 计算转换比例
        // sourceAmount: 一个物品能产生的化学品数量（如1富集红石=80mb）
        // requiredChemicalAmount: 配方需要的化学品数量（如10mb）
        // 比例 = sourceAmount / requiredChemicalAmount（如80/10=8，表示1个富集红石可以做8份配方）
        long sourceAmount = chemicalSource.amount();
        long gcd = gcd(requiredChemicalAmount, sourceAmount);
        // 主物品倍数 = sourceAmount / gcd（如80/10=8，需要8个铜锭）
        long multiplier = sourceAmount / gcd;
        // 化学品物品数量 = requiredChemicalAmount / gcd（如10/10=1，需要1个富集红石）
        long itemCount = requiredChemicalAmount / gcd;

        // 构建配方ID
        String suffix = "_converted_" + chemicalSource.recipeId().getPath().replace('/', '_');
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + suffix
        );

        // 构建输入列表
        List<CountedIngredient> countedIngredients = new ArrayList<>();

        // 添加主物品输入
        var itemRepresentations = itemInput.getRepresentations();
        if (!itemRepresentations.isEmpty()) {
            Ingredient mainIngredient = Ingredient.of(itemRepresentations.stream());
            // 获取原配方中主物品的数量（如青铜配方中的3个铜锭）
            int baseItemCount = itemRepresentations.get(0).getCount();
            // 计算最终需要的物品数量 = 原配方数量 × 倍数
            long finalItemCount = baseItemCount * multiplier;
            countedIngredients.add(new CountedIngredient(mainIngredient, finalItemCount));
        }

        // 添加化学品对应的物品输入
        var chemItemRepresentations = chemicalSource.ingredient().getRepresentations();
        if (!chemItemRepresentations.isEmpty()) {
            Ingredient chemIngredient = Ingredient.of(chemItemRepresentations.stream());
            countedIngredients.add(new CountedIngredient(chemIngredient, itemCount));
        }

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 计算输出（按比例放大）
        List<ItemStack> scaledOutputs = new ArrayList<>();
        for (ItemStack output : outputs) {
            ItemStack scaled = output.copy();
            scaled.setCount((int) (output.getCount() * multiplier));
            scaledOutputs.add(scaled);
        }

        // 创建冶金灌注机模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(MekanismBlocks.METALLURGIC_INFUSER.get()));

        // 计算能量和时间（随批量增加）
        int energy = BASE_ENERGY * (int) multiplier;
        int processTime = BASE_PROCESS_TIME * (int) multiplier;

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),           // 无流体输入
                scaledOutputs,
                List.of(),           // 无流体输出
                energy,
                processTime,
                Ingredient.EMPTY,    // 无催化剂
                0,
                moldIngredient,      // 冶金灌注机作为模具
                AlloyFurnaceMode.NORMAL
        );
    }

    /**
     * 获取化学品需求量
     */
    private long getRequiredChemicalAmount(ChemicalStackIngredient chemicalInput) {
        // 尝试从化学品输入定义中获取数量
        var representations = chemicalInput.getRepresentations();
        if (!representations.isEmpty()) {
            return representations.get(0).getAmount();
        }
        return 0;
    }

    /**
     * 计算最大公约数
     */
    private long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<ItemStackChemicalToItemStackRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ItemStackChemicalToItemStackRecipe>> recipes = recipeManager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value()
        );

        for (RecipeHolder<ItemStackChemicalToItemStackRecipe> holder : recipes) {
            ItemStackChemicalToItemStackRecipe recipe = holder.value();

            // 确保是冶金灌注配方
            if (!recipe.getType().equals(MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value())) {
                continue;
            }

            var itemInput = recipe.getItemInput();
            if (itemInput == null || itemInput.hasNoMatchingInstances()) continue;

            var chemicalInput = recipe.getChemicalInput();
            if (chemicalInput == null) continue;

            // 获取化学品来源
            List<ChemicalSource> sources = findChemicalSources(level, chemicalInput);
            if (sources.isEmpty()) continue;

            // 检查是否有输入物品匹配主物品
            boolean hasMainItem = false;
            for (ItemStack stack : inputs) {
                if (!stack.isEmpty() && itemInput.test(stack)) {
                    hasMainItem = true;
                    break;
                }
            }
            if (!hasMainItem) continue;

            // 检查是否有输入物品匹配化学品来源
            boolean hasChemicalItem = false;
            for (ChemicalSource source : sources) {
                for (ItemStack stack : inputs) {
                    if (!stack.isEmpty() && source.ingredient().test(stack)) {
                        hasChemicalItem = true;
                        break;
                    }
                }
                if (hasChemicalItem) break;
            }
            if (!hasChemicalItem) continue;

            // 主物品和化学品物品都匹配
            return holder;
        }

        return null;
    }

    /**
     * 查找列表中是否有相同输入输出的配方
     * 用于合并重复的配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe findRecipeWithSameInputsOutputs(
            List<AdvancedAlloyFurnaceRecipe> recipes,
            AdvancedAlloyFurnaceRecipe newRecipe) {
        for (AdvancedAlloyFurnaceRecipe existing : recipes) {
            if (hasSameInputsOutputs(existing, newRecipe)) {
                return existing;
            }
        }
        return null;
    }

    /**
     * 比较两个配方是否有相同的输入和输出
     */
    private boolean hasSameInputsOutputs(AdvancedAlloyFurnaceRecipe a, AdvancedAlloyFurnaceRecipe b) {
        // 比较输入
        if (a.inputs().size() != b.inputs().size()) {
            return false;
        }
        for (int i = 0; i < a.inputs().size(); i++) {
            CountedIngredient inputA = a.inputs().get(i);
            CountedIngredient inputB = b.inputs().get(i);
            if (inputA.count() != inputB.count()) {
                return false;
            }
            // 比较Ingredient的内容（使用ItemStack比较）
            if (!ingredientsEqual(inputA.ingredient(), inputB.ingredient())) {
                return false;
            }
        }

        // 比较输出
        if (a.outputs().size() != b.outputs().size()) {
            return false;
        }
        for (int i = 0; i < a.outputs().size(); i++) {
            ItemStack outputA = a.outputs().get(i);
            ItemStack outputB = b.outputs().get(i);
            if (!ItemStack.isSameItemSameComponents(outputA, outputB)) {
                return false;
            }
            if (outputA.getCount() != outputB.getCount()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 比较两个Ingredient是否相等（基于它们匹配的物品）
     */
    private boolean ingredientsEqual(Ingredient a, Ingredient b) {
        ItemStack[] stacksA = a.getItems();
        ItemStack[] stacksB = b.getItems();
        if (stacksA.length != stacksB.length) {
            return false;
        }
        for (int i = 0; i < stacksA.length; i++) {
            if (!ItemStack.isSameItemSameComponents(stacksA[i], stacksB[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getPriority() {
        return 80; // 优先级高于富集仓
    }

    /**
     * 化学品来源记录
     */
    private record ChemicalSource(
            mekanism.api.recipes.ingredients.ItemStackIngredient ingredient,
            long amount,
            ResourceLocation recipeId
    ) {}
}
