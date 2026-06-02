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
        Ingredient moldIngredient;

        if (processType == InscriberProcessType.PRESS) {
            // PRESS 模式：所有物品（顶部、底部、中间）都放入输入栏，以 ae2:inscriber 作为模具
            countedIngredients.add(new CountedIngredient(middleInput, 1));
            if (!isIngredientEmpty(topInput)) {
                countedIngredients.add(new CountedIngredient(topInput, 1));
            }
            if (!isIngredientEmpty(bottomInput)) {
                countedIngredients.add(new CountedIngredient(bottomInput, 1));
            }
            moldIngredient = Ingredient.of(AEBlocks.INSCRIBER.asItem());
        } else {
            // INSCRIBE 模式：以顶部或底部物品作为模具（不消耗），中间物品作为输入
            countedIngredients.add(new CountedIngredient(middleInput, 1));
            if (!isIngredientEmpty(topInput)) {
                moldIngredient = topInput;
            } else if (!isIngredientEmpty(bottomInput)) {
                moldIngredient = bottomInput;
            } else {
                moldIngredient = Ingredient.of(AEBlocks.INSCRIBER.asItem());
            }
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
                moldIngredient,    // 模具
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    /**
     * 检查 Ingredient 是否为空
     * <p>
     * 使用 Ingredient.isEmpty() 而非 getItems().length，
     * 因为 AE2 等模组可能使用自定义 Ingredient 类型，
     * 其 getItems() 返回空数组但 isEmpty() 正确返回 false。
     */
    private boolean isIngredientEmpty(Ingredient ingredient) {
        if (ingredient == null) return true;
        return ingredient.isEmpty();
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
            
            if (processType == InscriberProcessType.PRESS) {
                // PRESS 模式：需要匹配所有输入物品（中间、顶部、底部）
                boolean matchesTop = isIngredientEmpty(topInput);
                boolean matchesBottom = isIngredientEmpty(bottomInput);

                for (ItemStack stack : inputs) {
                    if (stack.isEmpty()) continue;

                    if (!matchesMiddle && middleInput.test(stack)) {
                        matchesMiddle = true;
                    } else if (!matchesTop && !isIngredientEmpty(topInput) && topInput.test(stack)) {
                        matchesTop = true;
                    } else if (!matchesBottom && !isIngredientEmpty(bottomInput) && bottomInput.test(stack)) {
                        matchesBottom = true;
                    }
                }

                if (matchesMiddle && matchesTop && matchesBottom) {
                    return holder;
                }
            } else {
                // INSCRIBE 模式：只需要匹配中间输入
                for (ItemStack stack : inputs) {
                    if (stack.isEmpty()) continue;

                    if (middleInput.test(stack)) {
                        matchesMiddle = true;
                        break;
                    }
                }

                if (matchesMiddle) {
                    return holder;
                }
            }
        }

        return null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<InscriberRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<InscriberRecipe>> recipes = (List<RecipeHolder<InscriberRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                appeng.recipes.AERecipeTypes.INSCRIBER
        );

        RecipeHolder<InscriberRecipe> bestHolder = null;
        int bestScore = -1;

        boolean hasMold = mold != null && !mold.isEmpty();

        for (RecipeHolder<InscriberRecipe> holder : recipes) {
            InscriberRecipe recipe = holder.value();

            Ingredient middleInput = recipe.getMiddleInput();
            Ingredient topInput = recipe.getTopOptional();
            Ingredient bottomInput = recipe.getBottomOptional();
            InscriberProcessType processType = recipe.getProcessType();

            if (middleInput == null || isIngredientEmpty(middleInput)) continue;

            boolean matchesMiddle = false;
            for (ItemStack stack : inputs) {
                if (!stack.isEmpty() && middleInput.test(stack)) {
                    matchesMiddle = true;
                    break;
                }
            }
            if (!matchesMiddle) continue;

            boolean hasTop = !isIngredientEmpty(topInput);
            boolean hasBottom = !isIngredientEmpty(bottomInput);

            if (processType == InscriberProcessType.PRESS) {
                boolean topSatisfied = !hasTop;
                boolean bottomSatisfied = !hasBottom;

                for (ItemStack stack : inputs) {
                    if (stack.isEmpty()) continue;
                    if (!topSatisfied && topInput.test(stack)) topSatisfied = true;
                    if (!bottomSatisfied && bottomInput.test(stack)) bottomSatisfied = true;
                }

                if (hasMold) {
                    if (!topSatisfied && topInput.test(mold)) topSatisfied = true;
                    if (!bottomSatisfied && bottomInput.test(mold)) bottomSatisfied = true;
                }

                if (!topSatisfied || !bottomSatisfied) continue;

                boolean moldMatchesTop = hasTop && hasMold && topInput.test(mold);
                boolean moldMatchesBottom = hasBottom && hasMold && bottomInput.test(mold);
                int matchScore = moldMatchesTop || moldMatchesBottom ? 2 : 1;

                if (matchScore > bestScore) {
                    bestScore = matchScore;
                    bestHolder = holder;
                }
            } else {
                boolean moldMatchesTop = hasTop && hasMold && topInput.test(mold);
                boolean moldMatchesBottom = hasBottom && hasMold && bottomInput.test(mold);

                if ((hasTop || hasBottom) && hasMold && !moldMatchesTop && !moldMatchesBottom) {
                    continue;
                }

                int matchScore;
                if (moldMatchesTop || moldMatchesBottom) {
                    matchScore = 3;
                } else if (!hasTop && !hasBottom) {
                    matchScore = 1;
                } else {
                    matchScore = 2;
                }

                if (matchScore > bestScore) {
                    bestScore = matchScore;
                    bestHolder = holder;
                }
            }
        }

        return bestHolder;
    }

    @Override
    public int getPriority() {
        return 70; // 优先级低于 EAE 和 AAE，但高于原版熔炉
    }
}
