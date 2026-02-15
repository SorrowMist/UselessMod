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

    // Industrial Foregoing 溶解成型机基础能量消耗参考
    private static final int IF_ENERGY_PER_TICK = 100;

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

        // 构建物品输入列表
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        if (itemInputs != null) {
            for (Ingredient ingredient : itemInputs) {
                if (ingredient == null || isIngredientEmpty(ingredient)) continue;
                countedIngredients.add(new CountedIngredient(ingredient, 1));
            }
        }

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

        // 计算总能量消耗
        int processingTime = originalRecipe.processingTime;
        int totalEnergy = 20;

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

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<DissolutionChamberRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
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

            // 检查所有物品输入是否匹配
            boolean[] matched = new boolean[itemInputs.size()];
            int matchedCount = 0;

            for (ItemStack stack : inputs) {
                if (stack.isEmpty()) continue;

                for (int i = 0; i < itemInputs.size(); i++) {
                    if (!matched[i] && itemInputs.get(i).test(stack)) {
                        matched[i] = true;
                        matchedCount++;
                        break;
                    }
                }
            }

            // 所有输入都匹配
            if (matchedCount == itemInputs.size()) {
                return holder;
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 65; // 优先级低于 AE2、EAE 和 AAE，但高于原版熔炉
    }
}
