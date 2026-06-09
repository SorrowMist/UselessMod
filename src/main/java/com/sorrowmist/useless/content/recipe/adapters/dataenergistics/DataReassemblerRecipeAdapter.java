package com.sorrowmist.useless.content.recipe.adapters.dataenergistics;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataEnergistics 数据重组器配方适配器
 * <p>
 * 将数据重组器配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 物品输入 → 普通输入（合并相同物品）
 * - 流体输入 → 流体输入
 * - 物品产物 → 输出
 * - 流体产物 → 流体输出
 * - keyInput → 转换为物品输入（使用 GenericStack.wrapInItemStack 包装 AE2 特殊数据类型）
 * - keyOutput → 转换为物品输出（使用 GenericStack.wrapInItemStack 包装 AE2 特殊数据类型）
 * - data_energistics:data_reassembler → 模具（不消耗）
 */
public class DataReassemblerRecipeAdapter implements IRecipeAdapter<DataRipperReassemblerRecipe> {

    private static final int BASE_PROCESS_TIME = 200;
    private static final int BASE_ENERGY = 2000;

    @Override
    public Class<DataRipperReassemblerRecipe> getRecipeClass() {
        return DataRipperReassemblerRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<DataRipperReassemblerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        DataRipperReassemblerRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取输入
        List<DataRipperReassemblerIngredient> itemInputs = recipe.getItemInputs();
        List<GenericStack> fluidInputs = recipe.getFluidInputs();
        GenericStack keyInput = recipe.getKeyInput();

        // 获取输出
        List<ItemStack> itemOutputs = recipe.getItemOutputs();
        List<GenericStack> fluidOutputs = recipe.getFluidOutputs();
        GenericStack keyOutput = recipe.getKeyOutput();

        boolean hasItemInputs = !itemInputs.isEmpty();
        boolean hasFluidInputs = !fluidInputs.isEmpty();
        boolean hasKeyInput = keyInput != null && keyInput.amount() > 0;
        boolean hasItemOutputs = !itemOutputs.isEmpty();
        boolean hasFluidOutputs = !fluidOutputs.isEmpty();
        boolean hasKeyOutput = keyOutput != null && keyOutput.amount() > 0;

        // 只要有输入并且有输出就可以转换
        if (!hasItemInputs && !hasFluidInputs && !hasKeyInput) {
            return result;
        }
        if (!hasItemOutputs && !hasFluidOutputs && !hasKeyOutput) {
            return result;
        }

        // 合并物品输入
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (DataRipperReassemblerIngredient input : itemInputs) {
            mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        // 处理 keyInput（转换为物品输入）
        if (hasKeyInput) {
            ItemStack keyInputStack = convertKeyToItemStack(keyInput);
            if (!keyInputStack.isEmpty()) {
                Ingredient keyInputIngredient = Ingredient.of(keyInputStack);
                mergeIngredient(ingredientCounts, keyInputIngredient, getRecipeIngredientCount(keyInput));
            }
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        // 转换流体输入
        List<FluidStack> inputFluids = new ArrayList<>();
        for (GenericStack fluidInput : fluidInputs) {
            if (fluidInput.what() instanceof AEFluidKey fluidKey && fluidInput.amount() > 0) {
                int amount = fluidInput.amount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fluidInput.amount();
                inputFluids.add(fluidKey.toStack(amount));
            }
        }

        // 转换物品输出
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack itemOutput : itemOutputs) {
            if (!itemOutput.isEmpty()) {
                outputs.add(itemOutput.copy());
            }
        }

        // 处理 keyOutput（转换为物品输出）
        if (hasKeyOutput) {
            ItemStack keyOutputStack = convertKeyToItemStack(keyOutput);
            if (!keyOutputStack.isEmpty()) {
                outputs.add(keyOutputStack);
            }
        }

        // 转换流体输出
        List<FluidStack> outputFluids = new ArrayList<>();
        for (GenericStack fluidOutput : fluidOutputs) {
            if (fluidOutput.what() instanceof AEFluidKey fluidKey && fluidOutput.amount() > 0) {
                int amount = fluidOutput.amount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fluidOutput.amount();
                outputFluids.add(fluidKey.toStack(amount));
            }
        }

        int processTime = recipe.getProcessTicks();
        int energy = BASE_ENERGY + (itemInputs.size() + fluidInputs.size()) * 200;
        if (hasKeyInput) energy += 500;
        if (hasKeyOutput) energy += 500;

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 使用数据重组器本身作为模具
        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("data_energistics", "data_reassembler")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                inputFluids,
                outputs,
                outputFluids,
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    /**
     * 将 AE2 的 GenericStack（keyInput/keyOutput）转换为 ItemStack
     * <p>
     * 处理逻辑：
     * - AEItemKey → 直接使用 toStack 方法
     * - AEFluidKey → 不处理（流体应该通过流体槽处理）
     * - 其他类型（DataFlowKey、DataKey、LightningKey 等）→ 使用 wrapInItemStack 包装
     */
    private ItemStack convertKeyToItemStack(GenericStack keyStack) {
        if (keyStack == null || keyStack.what() == null || keyStack.amount() <= 0) {
            return ItemStack.EMPTY;
        }

        AEKey key = keyStack.what();
        long amount = keyStack.amount();

        // 如果是普通物品，直接转换
        if (key instanceof AEItemKey itemKey) {
            int itemAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
            return itemKey.toStack(itemAmount);
        }

        // 如果是流体，不处理（流体应该通过流体槽处理）
        if (key instanceof AEFluidKey) {
            return ItemStack.EMPTY;
        }

        // 其他类型（DataFlowKey、DataKey、LightningKey 等）使用 wrapInItemStack 包装
        int itemAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        return GenericStack.wrapInItemStack(key, itemAmount);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<DataRipperReassemblerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<DataRipperReassemblerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<DataRipperReassemblerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        return findMatchingRecipe(level, inputs, List.of(), mold);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<DataRipperReassemblerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, List<FluidStack> fluidInputs, @Nullable ItemStack mold) {
        if (level == null) {
            return null;
        }

        // 检查模具是否匹配数据重组器
        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"data_energistics".equals(moldId.getNamespace()) || !"data_reassembler".equals(moldId.getPath())) {
                return null;
            }
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<DataRipperReassemblerRecipe>> recipes = (List<RecipeHolder<DataRipperReassemblerRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()
        );

        RecipeHolder<DataRipperReassemblerRecipe> bestHolder = null;
        int bestScore = -1;

        for (RecipeHolder<DataRipperReassemblerRecipe> holder : recipes) {
            DataRipperReassemblerRecipe recipe = holder.value();

            List<DataRipperReassemblerIngredient> recipeItemInputs = recipe.getItemInputs();
            List<GenericStack> recipeFluidInputs = recipe.getFluidInputs();
            GenericStack keyInput = recipe.getKeyInput();

            // 构建 requiredCounts，包含物品输入和 keyInput
            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            for (DataRipperReassemblerIngredient input : recipeItemInputs) {
                mergeIngredient(requiredCounts, input.ingredient(), input.count());
            }

            // 将 keyInput 转换为物品输入进行匹配
            boolean hasKeyInput = keyInput != null && keyInput.amount() > 0;
            if (hasKeyInput) {
                ItemStack keyInputStack = convertKeyToItemStack(keyInput);
                if (!keyInputStack.isEmpty()) {
                    Ingredient keyInputIngredient = Ingredient.of(keyInputStack);
                    mergeIngredient(requiredCounts, keyInputIngredient, getRecipeIngredientCount(keyInput));
                }
            }

            // 检查物品输入匹配（包括 keyInput）
            boolean itemsMatch = true;
            if (!requiredCounts.isEmpty()) {
                itemsMatch = matchesCountedIngredients(inputs, requiredCounts);
            }

            // 检查流体输入匹配
            boolean fluidsMatch = true;
            if (!recipeFluidInputs.isEmpty()) {
                fluidsMatch = matchesFluidInputs(fluidInputs, recipeFluidInputs);
            }

            // 只要物品或流体有一个满足，就可以匹配
            if ((requiredCounts.isEmpty() || itemsMatch) && (recipeFluidInputs.isEmpty() || fluidsMatch)) {
                int score = calculateScore(recipeItemInputs, recipeFluidInputs, hasKeyInput, itemsMatch, fluidsMatch);
                if (score > bestScore) {
                    bestScore = score;
                    bestHolder = holder;
                }
            }
        }

        return bestHolder;
    }

    private int calculateScore(List<DataRipperReassemblerIngredient> itemInputs, List<GenericStack> fluidInputs,
                               boolean hasKeyInput, boolean itemsMatch, boolean fluidsMatch) {
        int score = 0;
        if (itemsMatch && (!itemInputs.isEmpty() || hasKeyInput)) {
            score += 2;
            if (hasKeyInput) score += 1; // keyInput 匹配额外加分
        }
        if (fluidsMatch && !fluidInputs.isEmpty()) {
            score += 1;
        }
        return score;
    }

    private boolean matchesFluidInputs(List<FluidStack> fluidInputs, List<GenericStack> requiredFluids) {
        if (fluidInputs == null || fluidInputs.isEmpty()) {
            return false;
        }

        for (GenericStack required : requiredFluids) {
            if (!(required.what() instanceof AEFluidKey requiredKey)) {
                continue;
            }

            long foundAmount = 0;
            for (FluidStack input : fluidInputs) {
                AEFluidKey inputKey = AEFluidKey.of(input);
                if (inputKey != null && inputKey.equals(requiredKey)) {
                    foundAmount += input.getAmount();
                }
            }

            if (foundAmount < required.amount()) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesCountedIngredients(List<ItemStack> inputs, Map<Ingredient, Long> requiredCounts) {
        Map<Ingredient, Long> inputMatchCounts = new LinkedHashMap<>();

        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) continue;
            int stackCount = stack.getCount();

            for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
                if (entry.getKey().test(stack)) {
                    inputMatchCounts.merge(entry.getKey(), (long) stackCount, Long::sum);
                    break;
                }
            }
        }

        for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
            long required = entry.getValue();
            long actual = inputMatchCounts.getOrDefault(entry.getKey(), 0L);
            if (actual < required) return false;
        }
        return true;
    }

    private long getRecipeIngredientCount(GenericStack keyStack) {
        if (keyStack.what() instanceof AEItemKey) {
            return keyStack.amount();
        }
        return 1;
    }

    private void mergeIngredient(Map<Ingredient, Long> ingredientCounts, Ingredient ingredient, long count) {
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            if (areIngredientsEqual(entry.getKey(), ingredient)) {
                ingredientCounts.put(entry.getKey(), entry.getValue() + count);
                return;
            }
        }
        ingredientCounts.put(ingredient, count);
    }

    private boolean areIngredientsEqual(Ingredient a, Ingredient b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        ItemStack[] stacksA = a.getItems();
        ItemStack[] stacksB = b.getItems();

        if (stacksA.length != stacksB.length) return false;

        for (ItemStack stackA : stacksA) {
            boolean found = false;
            for (ItemStack stackB : stacksB) {
                if (ItemStack.isSameItem(stackA, stackB) && ItemStack.isSameItemSameComponents(stackA, stackB)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    @Override
    public int getPriority() {
        return 55; // 优先级适中
    }
}
