package com.sorrowmist.useless.content.recipe.adapters.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
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

import java.util.List;
import java.util.Map;

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

    @Override
    public Class<ImbuementRecipe> getRecipeClass() {
        return ImbuementRecipe.class;
    }

    @Nullable
    @Override
    public ItemStack getMoldItem() {
        return null; // 产物作为模具，无固定模具物品
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        // Imbuement 的模具是配方产物本身
        // 因为接口没有父 RecipeHolder 上下文，无法直接判断；
        // 返回 true 让 findMatchingRecipe 自行做产物匹配
        return true;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ImbuementRecipe> holder, Level level) {
        if (holder == null) return List.of();

        ImbuementRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient input = originalRecipe.getInput();
        ItemStack output = originalRecipe.getOutput();
        int source = originalRecipe.getSource();

        if (input.isEmpty() || output.isEmpty()) {
            return List.of();
        }

        int energy = Math.max(source * 10, 500);
        int processTime = AdapterUtils.DEFAULT_PROCESS_TIME;

        // 产物作为模具
        Ingredient moldIngredient = Ingredient.of(output.copy());

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
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

        return List.of(convertedRecipe);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<ImbuementRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
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
            if (!AdapterUtils.hasMatchingIngredient(mergedInputs, input)) continue;

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
}
