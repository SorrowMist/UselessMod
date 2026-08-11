package com.sorrowmist.useless.content.recipe.adapters.ae.extendedae;

import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ExtendedAE 水晶装配器配方适配器
 */
public class CrystalAssemblerRecipeAdapter implements IRecipeAdapter<CrystalAssemblerRecipe> {

    @Override
    public Class<CrystalAssemblerRecipe> getRecipeClass() {
        return CrystalAssemblerRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(EAESingletons.CRYSTAL_ASSEMBLER);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CrystalAssemblerRecipe> holder, Level level) {
        if (holder == null) return null;

        CrystalAssemblerRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

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
        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        IngredientStack.Fluid fluidInput = originalRecipe.getFluid();
        if (fluidInput != null && !fluidInput.isEmpty()) {
            // 从 IngredientStack.Fluid 提取 FluidStack
            var fluidIngredient = fluidInput.getIngredient();
            long amount = fluidInput.getAmount();
            if (fluidIngredient != null && !fluidIngredient.isEmpty() && amount > 0
                    && amount <= Integer.MAX_VALUE) {
                inputFluids.add(new SizedFluidIngredient(fluidIngredient, (int) amount));
            }
        }

        // 计算能量消耗：EAE使用AE单位，需要转换为FE（*2）
        int energyCost = AdapterUtils.EXTENDEDAE_ENERGY_MULTIPLIER * AdapterUtils.DEFAULT_PROCESS_TIME * AdapterUtils.AE_TO_FE_CONVERSION;
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                inputFluids,
                outputs,
                List.of(),           // 无流体输出
                energyCost,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,    // 无催化剂
                0,
                // 水晶装配器作为模具
                Ingredient.of(new ItemStack(EAESingletons.CRYSTAL_ASSEMBLER)),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    @Nullable
    public List<RecipeHolder<CrystalAssemblerRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalAssemblerRecipe>> recipes = recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE);

        List<RecipeHolder<CrystalAssemblerRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipes) {
            CrystalAssemblerRecipe recipe = holder.value();
            // 使用全类名
            List<IngredientStack.Item> recipeInputs = recipe.getInputs();

            Map<Ingredient, Long> requiredInputs = new LinkedHashMap<>();
            for (IngredientStack.Item inputStack : recipeInputs) {
                if (inputStack == null || inputStack.isEmpty()) continue;

                Ingredient ingredient = inputStack.getIngredient();
                AdapterUtils.mergeIngredient(requiredInputs, ingredient, inputStack.getAmount());
            }

            boolean fluidMatch = matchesFluidInput(mergedFluids, recipe.getFluid());
            if (AdapterUtils.matchesRequired(mergedInputs, requiredInputs)
                    && !recipeInputs.isEmpty() && fluidMatch) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private boolean matchesFluidInput(Map<FluidStack, Long> mergedFluids, IngredientStack.Fluid fluidInput) {
        if (fluidInput == null || fluidInput.isEmpty()) return true;
        var ingredient = fluidInput.getIngredient();
        long amount = fluidInput.getAmount();
        if (ingredient == null || ingredient.isEmpty() || amount <= 0 || amount > Integer.MAX_VALUE) {
            return false;
        }
        return FluidIngredientAllocator.matches(
                List.of(new SizedFluidIngredient(ingredient, (int) amount)),
                mergedFluids, 1L);
    }
}
