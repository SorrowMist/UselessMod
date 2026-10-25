package com.sorrowmist.useless.content.recipe.adapters.ae.ae2;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2 压印器配方适配器
 * <p>
 * 将压印器配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 压印器机器始终是第一个模具；压印模板（顶部/底部）→ 额外模具(mold)
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
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(AEBlocks.INSCRIBER.asItem());
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(AEBlocks.INSCRIBER.asItem());
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
        List<Ingredient> molds = new ArrayList<>();
        molds.add(Ingredient.of(getMoldItem()));

        if (processType == InscriberProcessType.PRESS) {
            // PRESS 模式：所有物品（顶部、底部、中间）都放入输入栏，以 ae2:inscriber 作为模具
            countedIngredients.add(new CountedIngredient(middleInput, 1));
            if (!isIngredientEmpty(topInput)) {
                countedIngredients.add(new CountedIngredient(topInput, 1));
            }
            if (!isIngredientEmpty(bottomInput)) {
                countedIngredients.add(new CountedIngredient(bottomInput, 1));
            }
        } else {
            // The machine and BOTH non-consumed plates are required (AND), not alternatives.
            // Keeping the machine first also makes every printing recipe discoverable by inscriber.
            countedIngredients.add(new CountedIngredient(middleInput, 1));
            if (!isIngredientEmpty(topInput)) molds.add(topInput);
            if (!isIngredientEmpty(bottomInput)) molds.add(bottomInput);
        }

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                TOTAL_ENERGY,
                AE2_PROCESS_TICKS,
                Ingredient.EMPTY,  // 催化剂（AE2压印器配方不使用催化剂）
                0,
                molds,             // machine + every reusable plate
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
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<InscriberRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<InscriberRecipe>> recipes = (List<RecipeHolder<InscriberRecipe>>) (List<?>) com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(recipeManager,
                AERecipeTypes.INSCRIBER
        );

        List<RecipeHolder<InscriberRecipe>> matches = new java.util.ArrayList<>();

        if (!matchesMold(mold)) return List.of();
        for (RecipeHolder<InscriberRecipe> holder : recipes) {
            for (var converted : convertAll(holder, level)) {
                Map<Ingredient, Long> required = new LinkedHashMap<>();
                for (var input : converted.inputs()) AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
                if (AdapterUtils.matchesRequired(mergedInputs, required)) matches.add(holder);
            }
        }
        // Multi-mold recipes are subsequently checked by the mold hub; a single mold slot
        // cannot satisfy the additional reusable plates.

        return matches;
    }
}
