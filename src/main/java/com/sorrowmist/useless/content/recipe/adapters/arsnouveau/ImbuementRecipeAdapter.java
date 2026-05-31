package com.sorrowmist.useless.content.recipe.adapters.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
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
 * Ars Nouveau 灌魔室配方适配器
 * <p>
 * 将灌魔室配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 中心物品(input) → 普通输入（被消耗）
 * - 产物(output) → 模具（不消耗）
 * - 基座物品(pedestalItems) → 忽略
 * - 魔力消耗(source) → 能量消耗
 */
public class ImbuementRecipeAdapter implements IRecipeAdapter<ImbuementRecipe> {

    private static final int BASE_PROCESS_TIME = 20;

    @Override
    public Class<ImbuementRecipe> getRecipeClass() {
        return ImbuementRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ImbuementRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        ImbuementRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient input = originalRecipe.getInput();
        ItemStack output = originalRecipe.getOutput();
        int source = originalRecipe.getSource();

        if (input.isEmpty() || output.isEmpty()) {
            return result;
        }

        int energy = Math.max(source * 10, 500);
        int processTime = BASE_PROCESS_TIME;

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 产物作为模具
        Ingredient moldIngredient = Ingredient.of(output.copy());

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                List.of(new CountedIngredient(input, 1)),
                List.of(),
                List.of(output.copy()),
                List.of(),
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

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<ImbuementRecipe> holder, Level level) {
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
    public RecipeHolder<ImbuementRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<ImbuementRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ImbuementRecipe>> recipes = (List<RecipeHolder<ImbuementRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                RecipeRegistry.IMBUEMENT_TYPE.get()
        );

        for (RecipeHolder<ImbuementRecipe> holder : recipes) {
            ImbuementRecipe recipe = holder.value();

            Ingredient input = recipe.getInput();
            ItemStack output = recipe.getOutput();

            if (input.isEmpty() || output.isEmpty()) continue;

            // 检查中心物品是否匹配
            boolean matchesInput = false;
            for (ItemStack stack : inputs) {
                if (!stack.isEmpty() && input.test(stack)) {
                    matchesInput = true;
                    break;
                }
            }

            if (!matchesInput) continue;

            // 如果有模具，检查模具是否与产物匹配
            if (mold != null && !mold.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(mold, output)) {
                    continue;
                }
            }

            return holder;
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 40;
    }
}
