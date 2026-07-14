package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AE2 Lightning Tech 水晶催化器配方适配器
 * <p>
 * 将水晶催化器配方（仅 CRYSTAL 模式）转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 仅转换 CRYSTAL 模式的配方
 * - 催化剂 → 模具（不消耗）
 * - 无物品输入
 * - 输出固定 256 个
 */
public class CrystalCatalyzerRecipeAdapter implements IRecipeAdapter<CrystalCatalyzerRecipe> {

    private static final int OUTPUT_COUNT = 1024;
    private static final int BASE_PROCESS_TIME = 200;

    @Override
    public Class<CrystalCatalyzerRecipe> getRecipeClass() {
        return CrystalCatalyzerRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null; // 催化剂作为模具，无固定模具物品
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrystalCatalyzerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        CrystalCatalyzerRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        if (recipe.mode() != Mode.CRYSTAL) {
            return result;
        }

        Optional<Ingredient> catalyst = recipe.catalyst();
        ItemStack outputTemplate = recipe.getOutputTemplate();

        if (outputTemplate.isEmpty()) {
            return result;
        }

        ItemStack output = outputTemplate.copyWithCount(OUTPUT_COUNT);

        int energy = recipe.energyPerCycle();

        Ingredient moldIngredient = catalyst.orElse(Ingredient.EMPTY);

        FluidStack waterInput = new FluidStack(Fluids.WATER, 1000);
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        var keyInputs = List.of(AELightningIngredientHelper.createLightningKeyInput(recipe.lightningTier(), (long) recipe.lightningCost()));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(waterInput),
                keyInputs,
                List.of(output),
                List.of(),
                List.of(),
                energy,
                BASE_PROCESS_TIME,
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
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<CrystalCatalyzerRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null) return List.of();
        if (mold == null || mold.isEmpty()) return List.of();

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalCatalyzerRecipe>> recipes = (List<RecipeHolder<CrystalCatalyzerRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get()
        );

        List<RecipeHolder<CrystalCatalyzerRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : recipes) {
            CrystalCatalyzerRecipe recipe = holder.value();

            if (recipe.mode() != Mode.CRYSTAL) continue;

            Optional<Ingredient> catalyst = recipe.catalyst();
            if (catalyst.isEmpty()) continue;

            if (catalyst.get().test(mold)
                    && AELightningIngredientHelper.matchesLightning(mergedKeys, recipe.lightningTier(), (long) recipe.lightningCost())) {
                matches.add(holder);
            }
        }
        return matches;
    }

}
