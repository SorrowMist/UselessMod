package com.sorrowmist.useless.content.recipe.adapters.ae2;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
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
 * AE2 压印器配方适配器
 * <p>
 * 将压印器配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 压印模板（顶部/底部）→ 模具(mold)
 *   - INSCRIBE 模式：模具不消耗（正常模具行为）
 *   - PRESS 模式：模具被消耗（需要特殊处理）
 * - 中间物品 → 普通输入（总是被消耗）
 * <p>
 * 这样可以正确处理所有AE系列mod（AE2、ExtendedAE、AdvancedAE、MEGA Cells等）的压印器配方
 */
public class InscriberRecipeAdapter implements IRecipeAdapter<InscriberRecipe> {

    // AE2 压印器基础能量消耗
    private static final int AE2_ENERGY_PER_TICK = 10;
    private static final int AE2_PROCESS_TICKS = 20; // 1秒
    private static final int TOTAL_ENERGY = AE2_ENERGY_PER_TICK * AE2_PROCESS_TICKS; // 200

    @Override
    public Class<InscriberRecipe> getRecipeClass() {
        return InscriberRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<InscriberRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        InscriberRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取输入
        Ingredient middleInput = originalRecipe.getMiddleInput();
        Ingredient topInput = originalRecipe.getTopOptional();
        Ingredient bottomInput = originalRecipe.getBottomOptional();
        ItemStack output = originalRecipe.getResultItem();
        InscriberProcessType processType = originalRecipe.getProcessType();

        // 检查中间输入是否有效
        if (middleInput == null || isIngredientEmpty(middleInput) || output.isEmpty()) {
            return result;
        }

        // 构建输入列表
        List<CountedIngredient> countedIngredients = new ArrayList<>();

        // 中间输入（必需，总是被消耗）
        countedIngredients.add(new CountedIngredient(middleInput, 1));

        // 确定模具（压印模板）
        Ingredient moldIngredient;
        boolean moldConsumed = false;

        // 检查顶部或底部是否有压印模板
        if (!isIngredientEmpty(topInput)) {
            moldIngredient = topInput;
            // PRESS 模式下模具被消耗
            moldConsumed = (processType == InscriberProcessType.PRESS);
        } else if (!isIngredientEmpty(bottomInput)) {
            moldIngredient = bottomInput;
            // PRESS 模式下模具被消耗
            moldConsumed = (processType == InscriberProcessType.PRESS);
        } else {
            // 没有压印模板，使用压印器作为模具占位符
            moldIngredient = Ingredient.of(AEBlocks.INSCRIBER.asItem());
        }

        // 如果模具被消耗（PRESS模式），将其加入输入列表
        if (moldConsumed) {
            // 顶部和底部都被消耗
            if (!isIngredientEmpty(topInput)) {
                countedIngredients.add(new CountedIngredient(topInput, 1));
            }
            if (!isIngredientEmpty(bottomInput)) {
                countedIngredients.add(new CountedIngredient(bottomInput, 1));
            }
            // 模具置空，因为已经被当作输入处理了
            moldIngredient = Ingredient.EMPTY;
        }

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                TOTAL_ENERGY,
                AE2_PROCESS_TICKS,
                Ingredient.EMPTY,  // 催化剂（AE2压印器配方不使用催化剂）
                0,
                moldIngredient,    // 模具（压印模板，INSCRIBE模式下不消耗）
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
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<InscriberRecipe> holder, Level level) {
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
    public RecipeHolder<InscriberRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<InscriberRecipe>> recipes = (List<RecipeHolder<InscriberRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                appeng.recipes.AERecipeTypes.INSCRIBER
        );

        for (RecipeHolder<InscriberRecipe> holder : recipes) {
            InscriberRecipe recipe = holder.value();

            Ingredient middleInput = recipe.getMiddleInput();
            Ingredient topInput = recipe.getTopOptional();
            Ingredient bottomInput = recipe.getBottomOptional();
            InscriberProcessType processType = recipe.getProcessType();

            if (middleInput == null || isIngredientEmpty(middleInput)) continue;

            // 检查中间输入是否匹配
            boolean matchesMiddle = false;
            // 顶部是否需要匹配（INSCRIBE模式下不需要，PRESS模式下需要）
            boolean matchesTop = isIngredientEmpty(topInput);
            // 底部是否需要匹配（INSCRIBE模式下不需要，PRESS模式下需要）
            boolean matchesBottom = isIngredientEmpty(bottomInput);

            for (ItemStack stack : inputs) {
                if (stack.isEmpty()) continue;

                if (!matchesMiddle && middleInput.test(stack)) {
                    matchesMiddle = true;
                }

                // PRESS 模式下需要匹配顶部输入
                if (!matchesTop && processType == InscriberProcessType.PRESS && topInput.test(stack)) {
                    matchesTop = true;
                }

                // PRESS 模式下需要匹配底部输入
                if (!matchesBottom && processType == InscriberProcessType.PRESS && bottomInput.test(stack)) {
                    matchesBottom = true;
                }
            }

            if (matchesMiddle && matchesTop && matchesBottom) {
                return holder;
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 70; // 优先级低于 EAE 和 AAE，但高于原版熔炉
    }
}
