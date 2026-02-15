package com.sorrowmist.useless.content.recipe.adapters.extendedae;

import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtendedAE 电路切片器配方适配器
 */
public class CircuitCutterRecipeAdapter implements IRecipeAdapter<CircuitCutterRecipe> {

    // AE 到 FE 的转换系数
    private static final int AE_TO_FE = 2;
    // 电路切片器能量乘数
    private static final int ENERGY_MULTIPLIER = 10;
    // 处理时间基础值（ticks）
    private static final int BASE_PROCESS_TIME = 20;
    // 总能量消耗 = ENERGY_MULTIPLIER * BASE_PROCESS_TIME * AE_TO_FE

    @Override
    public Class<CircuitCutterRecipe> getRecipeClass() {
        return CircuitCutterRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CircuitCutterRecipe> holder, Level level) {
        if (holder == null) return null;

        CircuitCutterRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取输入材料
        IngredientStack.Item inputStack = originalRecipe.getInput();
        List<CountedIngredient> countedIngredients = new ArrayList<>();

        if (inputStack != null && !inputStack.isEmpty()) {
            Ingredient ingredient = inputStack.getIngredient();
            long count = inputStack.getAmount();
            if (ingredient != null && !ingredient.isEmpty()) {
                countedIngredients.add(new CountedIngredient(ingredient, count));
            }
        }

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 获取输出物品
        ItemStack result = originalRecipe.output;
        List<ItemStack> outputs = List.of(result.copy());

        // 计算能量消耗：EAE使用AE单位，需要转换为FE（*2）
        int energyCost = ENERGY_MULTIPLIER * BASE_PROCESS_TIME * AE_TO_FE;
        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),           // 无流体输入
                outputs,
                List.of(),           // 无流体输出
                energyCost,
                BASE_PROCESS_TIME,
                Ingredient.EMPTY,    // 无催化剂
                0,
                // 电路切片器作为模具
                Ingredient.of(new ItemStack(EAESingletons.CIRCUIT_CUTTER)),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<CircuitCutterRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CircuitCutterRecipe>> recipes = recipeManager.getAllRecipesFor(CircuitCutterRecipe.TYPE);

        for (RecipeHolder<CircuitCutterRecipe> holder : recipes) {
            CircuitCutterRecipe recipe = holder.value();
            IngredientStack.Item inputStack = recipe.getInput();

            if (inputStack == null || inputStack.isEmpty()) continue;

            Ingredient ingredient = inputStack.getIngredient();

            for (ItemStack stack : inputs) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    return holder;
                }
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 90; // 优先级略低于原版熔炉
    }
}
