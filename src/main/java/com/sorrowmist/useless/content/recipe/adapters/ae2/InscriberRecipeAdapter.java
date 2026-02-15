package com.sorrowmist.useless.content.recipe.adapters.ae2;

import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import appeng.recipes.handlers.InscriberRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
 * 使用压印模板的配方会将模板作为模具，其他配方使用压印器作为模具
 */
public class InscriberRecipeAdapter implements IRecipeAdapter<InscriberRecipe> {

    // AE2 压印器基础能量消耗
    private static final int AE2_ENERGY_PER_TICK = 10;
    private static final int AE2_PROCESS_TICKS = 20; // 1秒
    private static final int TOTAL_ENERGY = AE2_ENERGY_PER_TICK * AE2_PROCESS_TICKS; // 200

    // 压印模板标签
    private static final TagKey<Item> INSCRIBER_PRESSES = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            AppEng.makeId("inscriber_presses")
    );

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

        // 检查中间输入是否有效
        if (middleInput == null || isIngredientEmpty(middleInput) || output.isEmpty()) {
            return result;
        }

        // 判断是否是使用压印模板的配方
        Ingredient moldIngredient;

        // 检查顶部或底部是否有压印模板
        if (isPressTemplate(topInput)) {
            moldIngredient = topInput;
        } else if (isPressTemplate(bottomInput)) {
            moldIngredient = bottomInput;
        } else {
            // 没有使用压印模板，使用压印器作为模具
            moldIngredient = Ingredient.of(AEBlocks.INSCRIBER.asItem());
        }

        // 构建输入列表
        List<CountedIngredient> countedIngredients = new ArrayList<>();

        // 中间输入（必需）
        countedIngredients.add(new CountedIngredient(middleInput, 1));

        // 顶部输入（如果不是压印模板）
        if (!isIngredientEmpty(topInput) && !isPressTemplate(topInput)) {
            countedIngredients.add(new CountedIngredient(topInput, 1));
        }

        // 底部输入（如果不是压印模板）
        if (!isIngredientEmpty(bottomInput) && !isPressTemplate(bottomInput)) {
            countedIngredients.add(new CountedIngredient(bottomInput, 1));
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
     * 检查是否为压印模板
     */
    private boolean isPressTemplate(Ingredient ingredient) {
        if (ingredient == null || isIngredientEmpty(ingredient)) {
            return false;
        }

        // 检查是否匹配压印模板标签
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.is(INSCRIBER_PRESSES)) {
                return true;
            }
        }
        return false;
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

            if (middleInput == null || isIngredientEmpty(middleInput)) continue;

            // 判断顶部和底部是否是压印模板
            boolean topIsPressTemplate = isPressTemplate(topInput);
            boolean bottomIsPressTemplate = isPressTemplate(bottomInput);

            // 检查中间输入是否匹配
            boolean matchesMiddle = false;
            // 如果顶部是压印模板，则不需要在输入中匹配；否则需要匹配
            boolean matchesTop = isIngredientEmpty(topInput) || topIsPressTemplate;
            // 如果底部是压印模板，则不需要在输入中匹配；否则需要匹配
            boolean matchesBottom = isIngredientEmpty(bottomInput) || bottomIsPressTemplate;

            for (ItemStack stack : inputs) {
                if (stack.isEmpty()) continue;

                if (!matchesMiddle && middleInput.test(stack)) {
                    matchesMiddle = true;
                }
                // 只有顶部不是压印模板时才需要匹配
                if (!matchesTop && !topIsPressTemplate && topInput.test(stack)) {
                    matchesTop = true;
                }
                // 只有底部不是压印模板时才需要匹配
                if (!matchesBottom && !bottomIsPressTemplate && bottomInput.test(stack)) {
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
