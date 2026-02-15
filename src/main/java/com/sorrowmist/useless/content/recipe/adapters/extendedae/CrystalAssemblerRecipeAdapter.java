package com.sorrowmist.useless.content.recipe.adapters.extendedae;

import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtendedAE 水晶装配器配方适配器
 */
public class CrystalAssemblerRecipeAdapter implements IRecipeAdapter<CrystalAssemblerRecipe> {

    // AE 到 FE 的转换系数
    private static final int AE_TO_FE = 2;
    // 水晶装配器能量乘数
    private static final int ENERGY_MULTIPLIER = 10;
    // 处理时间基础值（ticks）
    private static final int BASE_PROCESS_TIME = 20;
    // 总能量消耗 = ENERGY_MULTIPLIER * BASE_PROCESS_TIME * AE_TO_FE

    @Override
    public Class<CrystalAssemblerRecipe> getRecipeClass() {
        return CrystalAssemblerRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CrystalAssemblerRecipe> holder, Level level) {
        if (holder == null) return null;

        CrystalAssemblerRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取输入材料
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

        // 获取输出物品
        ItemStack result = originalRecipe.output;
        List<ItemStack> outputs = List.of(result.copy());

        // 获取流体输入
        List<FluidStack> inputFluids = new ArrayList<>();
        IngredientStack.Fluid fluidInput = originalRecipe.getFluid();
        if (fluidInput != null && !fluidInput.isEmpty()) {
            // 从 IngredientStack.Fluid 提取 FluidStack
            var fluidIngredient = fluidInput.getIngredient();
            long amount = fluidInput.getAmount();
            if (fluidIngredient != null) {
                var fluids = fluidIngredient.getStacks();
                if (fluids.length > 0) {
                    inputFluids.add(new FluidStack(fluids[0].getFluid(), (int) amount));
                }
            }
        }

        // 计算能量消耗：EAE使用AE单位，需要转换为FE（*2）
        int energyCost = ENERGY_MULTIPLIER * BASE_PROCESS_TIME * AE_TO_FE;
        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                inputFluids,
                outputs,
                List.of(),           // 无流体输出
                energyCost,
                BASE_PROCESS_TIME,
                Ingredient.EMPTY,    // 无催化剂
                0,
                // 水晶装配器作为模具
                Ingredient.of(new ItemStack(EAESingletons.CRYSTAL_ASSEMBLER)),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<CrystalAssemblerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalAssemblerRecipe>> recipes = recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE);

        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipes) {
            CrystalAssemblerRecipe recipe = holder.value();
            // 使用全类名
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
        return 85; // 优先级略低于电路切片器
    }
}
