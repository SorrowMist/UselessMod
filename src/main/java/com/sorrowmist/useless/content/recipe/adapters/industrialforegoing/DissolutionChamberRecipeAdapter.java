package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.recipe.DissolutionChamberRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Industrial Foregoing 溶解成型机配方适配器
 * <p>
 * 将溶解成型机配方（物品+流体→物品/流体）转换为高级合金熔炉配方
 * 支持流体输入和流体输出
 */
public class DissolutionChamberRecipeAdapter implements IRecipeAdapter<DissolutionChamberRecipe> {

    // Industrial Foregoing 溶解成型机基础能量消耗参考 (90 FE/tick)
    private static final int IF_ENERGY_PER_TICK = 90;
    // 能量倍率 - 使转换后的配方消耗更多能量
    private static final int ENERGY_MULTIPLIER = 4;

    @Override
    public Class<DissolutionChamberRecipe> getRecipeClass() {
        return DissolutionChamberRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<DissolutionChamberRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        DissolutionChamberRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取输入物品列表
        List<Ingredient> itemInputs = originalRecipe.input;

        // 获取输出
        ItemStack itemOutput = originalRecipe.output.orElse(ItemStack.EMPTY);
        FluidStack fluidOutput = originalRecipe.outputFluid.orElse(FluidStack.EMPTY);

        // 如果没有物品输出且没有流体输出，则不转换
        if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
            return result;
        }

        // 构建物品输入列表 - 合并同类型的输入
        List<CountedIngredient> countedIngredients = mergeIngredients(itemInputs);

        // 构建流体输入列表
        List<FluidStack> inputFluids = new ArrayList<>();
        SizedFluidIngredient inputFluidIngredient = originalRecipe.inputFluid;
        if (inputFluidIngredient != null) {
            // 从 SizedFluidIngredient 获取流体和数量
            FluidStack[] fluids = inputFluidIngredient.getFluids();
            if (fluids != null && fluids.length > 0) {
                FluidStack inputFluid = fluids[0];
                if (inputFluid != null && !inputFluid.isEmpty()) {
                    inputFluids.add(inputFluid.copy());
                }
            }
        }

        // 如果没有物品输入且没有流体输入，则不转换
        if (countedIngredients.isEmpty() && inputFluids.isEmpty()) {
            return result;
        }

        // 创建溶解成型机模具要求
        Item dissolutionChamberItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("industrialforegoing", "dissolution_chamber"));
        Ingredient moldIngredient = dissolutionChamberItem != null ?
                Ingredient.of(dissolutionChamberItem) :
                Ingredient.EMPTY;

        // 计算总能量消耗 (Industrial Foregoing: 90 FE/tick * processingTime * 倍率)
        int processingTime = originalRecipe.processingTime;
        int totalEnergy = IF_ENERGY_PER_TICK * processingTime * ENERGY_MULTIPLIER;

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 构建物品输出列表
        List<ItemStack> outputs = new ArrayList<>();
        if (!itemOutput.isEmpty()) {
            outputs.add(itemOutput.copy());
        }

        // 构建流体输出列表
        List<FluidStack> outputFluids = new ArrayList<>();
        if (!fluidOutput.isEmpty()) {
            outputFluids.add(fluidOutput.copy());
        }

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                inputFluids,
                outputs,
                outputFluids,
                totalEnergy,
                processingTime,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    /**
     * 检查 Ingredient 是否为空
     */
    private boolean isIngredientEmpty(Ingredient ingredient) {
        if (ingredient == null) return true;
        ItemStack[] items = ingredient.getItems();
        return items == null || items.length == 0;
    }

    /**
     * 合并同类型的 Ingredient，统计数量
     */
    private List<CountedIngredient> mergeIngredients(List<Ingredient> itemInputs) {
        List<CountedIngredient> result = new ArrayList<>();
        if (itemInputs == null) return result;

        // 使用列表来存储合并后的结果，通过比较 ingredient 的内容来判断是否相同
        List<Ingredient> uniqueIngredients = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        for (Ingredient ingredient : itemInputs) {
            if (ingredient == null || isIngredientEmpty(ingredient)) continue;

            // 查找是否已有相同的 ingredient
            int existingIndex = -1;
            for (int i = 0; i < uniqueIngredients.size(); i++) {
                if (ingredientsEqual(uniqueIngredients.get(i), ingredient)) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                // 已存在，增加数量
                counts.set(existingIndex, counts.get(existingIndex) + 1);
            } else {
                // 新类型，添加到列表
                uniqueIngredients.add(ingredient);
                counts.add(1);
            }
        }

        // 构建 CountedIngredient 列表
        for (int i = 0; i < uniqueIngredients.size(); i++) {
            result.add(new CountedIngredient(uniqueIngredients.get(i), counts.get(i)));
        }

        return result;
    }

    /**
     * 比较两个 Ingredient 是否相等（通过比较它们包含的物品）
     */
    private boolean ingredientsEqual(Ingredient a, Ingredient b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        ItemStack[] itemsA = a.getItems();
        ItemStack[] itemsB = b.getItems();

        if (itemsA.length != itemsB.length) return false;

        // 比较每个物品堆叠
        for (ItemStack stackA : itemsA) {
            boolean found = false;
            for (ItemStack stackB : itemsB) {
                if (ItemStack.isSameItem(stackA, stackB)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        return true;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<DissolutionChamberRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.getFirst();
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings({"unchecked"})
    public RecipeHolder<DissolutionChamberRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        RecipeType<DissolutionChamberRecipe> recipeType = (RecipeType<DissolutionChamberRecipe>) ModuleCore.DISSOLUTION_TYPE.get();

        for (RecipeHolder<DissolutionChamberRecipe> holder : recipeManager.getAllRecipesFor(recipeType)) {
            DissolutionChamberRecipe recipe = holder.value();

            List<Ingredient> itemInputs = recipe.input;
            if (itemInputs == null || itemInputs.isEmpty()) continue;

            // 合并同类型的输入物品并统计数量
            List<CountedIngredient> countedIngredients = mergeIngredients(itemInputs);
            if (countedIngredients.isEmpty()) continue;

            // 统计每个输入槽位中各类型物品的总数量
            List<ItemStack> consolidatedInputs = consolidateInputs(inputs);

            // 检查所有物品输入是否匹配（包括数量）
            boolean[] matched = new boolean[countedIngredients.size()];
            int matchedCount = 0;

            for (ItemStack inputStack : consolidatedInputs) {
                if (inputStack.isEmpty()) continue;

                for (int i = 0; i < countedIngredients.size(); i++) {
                    if (!matched[i]) {
                        CountedIngredient counted = countedIngredients.get(i);
                        if (counted.ingredient().test(inputStack) && inputStack.getCount() >= counted.count()) {
                            matched[i] = true;
                            matchedCount++;
                            break;
                        }
                    }
                }
            }

            // 所有输入都匹配
            if (matchedCount == countedIngredients.size()) {
                return holder;
            }
        }

        return null;
    }

    /**
     * 合并输入物品列表，将同类型物品堆叠到一起统计总数量
     */
    private List<ItemStack> consolidateInputs(List<ItemStack> inputs) {
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) continue;

            // 查找是否已有相同的物品
            boolean found = false;
            for (ItemStack existing : result) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    found = true;
                    break;
                }
            }

            if (!found) {
                result.add(stack.copy());
            }
        }

        return result;
    }

    @Override
    public int getPriority() {
        return 65; // 优先级低于 AE2、EAE 和 AAE，但高于原版熔炉
    }
}
