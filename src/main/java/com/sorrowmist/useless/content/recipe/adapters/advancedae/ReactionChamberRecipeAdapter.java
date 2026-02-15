package com.sorrowmist.useless.content.recipe.adapters.advancedae;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.pedroksl.advanced_ae.common.definitions.AAEBlocks;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * AdvancedAE 反应仓配方适配器
 */
public class ReactionChamberRecipeAdapter implements IRecipeAdapter<ReactionChamberRecipe> {

    // AE 到 FE 的转换系数
    private static final int AE_TO_FE = 2;

    @Override
    public Class<ReactionChamberRecipe> getRecipeClass() {
        return ReactionChamberRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<ReactionChamberRecipe> holder, Level level) {
        if (holder == null) return null;

        ReactionChamberRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取输入材料 - 使用全类名避免与 glodium 的 IngredientStack 冲突
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        List<IngredientStack.Item> inputs = originalRecipe.getInputs();

        for (IngredientStack.Item inputStack : inputs) {
            if (inputStack != null && !inputStack.isEmpty()) {
                Ingredient ingredient = inputStack.getIngredient();
                long count = inputStack.getAmount();
                if (ingredient != null && !ingredient.isEmpty()) {
                    countedIngredients.add(new CountedIngredient(ingredient, count));
                }
            }
        }

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 获取输出物品或流体
        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = new ArrayList<>();

        var output = originalRecipe.output;
        if (output != null && output.what() != null) {
            if (output.what() instanceof AEItemKey key) {
                outputs.add(key.toStack((int) output.amount()));
            } else if (output.what() instanceof AEFluidKey key) {
                outputFluids.add(key.toStack((int) output.amount()));
            }
        }

        // 获取流体输入（如果有）- 使用全类名
        List<FluidStack> inputFluids = new ArrayList<>();
        IngredientStack.Fluid fluidInput = originalRecipe.getFluid();
        if (fluidInput != null && !fluidInput.isEmpty()) {
            var fluidIngredient = fluidInput.getIngredient();
            long amount = fluidInput.getAmount();
            if (fluidIngredient != null) {
                var fluids = fluidIngredient.getStacks();
                if (fluids.length > 0) {
                    inputFluids.add(new FluidStack(fluids[0].getFluid(), (int) amount));
                }
            }
        }

        // 能量消耗：AAE使用AE单位，需要转换为FE（*2）
        int energyCost = originalRecipe.getEnergy() * AE_TO_FE;
        // 处理时间
        int processTime = 20;

        // 创建反应仓模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(AAEBlocks.REACTION_CHAMBER.block()));

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                inputFluids,
                outputs,
                outputFluids,
                energyCost,
                processTime,
                Ingredient.EMPTY,    // 无催化剂
                0,
                moldIngredient,      // 反应仓作为模具
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<ReactionChamberRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ReactionChamberRecipe>> recipes = recipeManager.getAllRecipesFor(ReactionChamberRecipe.TYPE);

        for (RecipeHolder<ReactionChamberRecipe> holder : recipes) {
            ReactionChamberRecipe recipe = holder.value();
            List<IngredientStack.Item> recipeInputs = recipe.getInputs();

            // 检查是否所有输入都匹配
            boolean allMatch = true;
            for (IngredientStack.Item inputStack : recipeInputs) {
                if (inputStack == null || inputStack.isEmpty()) continue;

                Ingredient ingredient = inputStack.getIngredient();
                boolean found = false;

                for (ItemStack stack : inputs) {
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    allMatch = false;
                    break;
                }
            }

            if (allMatch && !recipeInputs.isEmpty()) {
                return holder;
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 80; // 优先级略低于EAE配方
    }
}
