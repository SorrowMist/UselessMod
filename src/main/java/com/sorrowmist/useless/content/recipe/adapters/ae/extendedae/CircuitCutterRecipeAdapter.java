package com.sorrowmist.useless.content.recipe.adapters.ae.extendedae;

import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ExtendedAE 电路切片器配方适配器
 */
public class CircuitCutterRecipeAdapter implements IRecipeAdapter<CircuitCutterRecipe> {

    @Override
    public Class<CircuitCutterRecipe> getRecipeClass() {
        return CircuitCutterRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(EAESingletons.CIRCUIT_CUTTER);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CircuitCutterRecipe> holder, Level level) {
        if (holder == null) return null;

        CircuitCutterRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

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
        int energyCost = AdapterUtils.EXTENDEDAE_ENERGY_MULTIPLIER * AdapterUtils.DEFAULT_PROCESS_TIME * AdapterUtils.AE_TO_FE_CONVERSION;
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),           // 无流体输入
                outputs,
                List.of(),           // 无流体输出
                energyCost,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,    // 无催化剂
                0,
                // 电路切片器作为模具
                Ingredient.of(new ItemStack(EAESingletons.CIRCUIT_CUTTER)),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    @Nullable
    public List<RecipeHolder<CircuitCutterRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CircuitCutterRecipe>> recipes = recipeManager.getAllRecipesFor(CircuitCutterRecipe.TYPE);

        List<RecipeHolder<CircuitCutterRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<CircuitCutterRecipe> holder : recipes) {
            CircuitCutterRecipe recipe = holder.value();
            IngredientStack.Item inputStack = recipe.getInput();

            if (inputStack == null || inputStack.isEmpty()) continue;

            Ingredient ingredient = inputStack.getIngredient();
            if (AdapterUtils.hasMatchingIngredient(mergedInputs, ingredient, inputStack.getAmount())) {
                matches.add(holder);
            }
        }
        return matches;
    }

}
