package com.sorrowmist.useless.content.recipe.adapters.astralsorcery;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.lightwell.LightwellRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将聚星缸（Astral Sorcery 的 Lightwell 流明井配方）转换为高级合金炉配方。
 *
 * <p>聚星缸以催化物品为输入、以流体为唯一产出。源配方本身只声明产出流体与生产倍率，
 * 不含确定的产出量：实际产量由方块按水晶石阶数计算后再乘以该倍率。本适配器以固定基准容量
 * 乘以生产倍率的方式还原产出量，使转换结果具备稳定的流体输出语义。</p>
 *
 * <p>聚星缸配方不消耗流体，因此转换结果的流体输入列表恒为空。催化物品的星辉属性组件
 * 不参与匹配，源配方本身也只按物品标签判定，因此同一标签下的不同组件物品共用一条转换配方。</p>
 */
public final class LightwellRecipeAdapter implements IRecipeAdapter<LightwellRecipe> {

    /** 生产倍率折算基准容量（mB）。 */
    private static final int PRODUCTION_BASE_AMOUNT = 1000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.ASTRAL_SORCERY;
    }

    @Override
    public Class<LightwellRecipe> getRecipeClass() {
        return LightwellRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(BlocksAS.LIGHTWELL.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<LightwellRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        LightwellRecipe recipe = holder.value();
        Ingredient input = recipe.getInput();
        if (AdapterUtils.isIngredientEmpty(input)) return List.of();

        List<FluidStack> outputFluids = buildOutputFluids(recipe);
        // 无有效流体产出的配方在合金炉中不产生任何结果，直接跳过。
        if (outputFluids.isEmpty()) return List.of();

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(),
                outputFluids,
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<LightwellRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        if (mergedInputs == null || mergedInputs.isEmpty()) return List.of();

        List<RecipeHolder<LightwellRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<LightwellRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(RecipeTypesAS.LIGHTWELL_TYPE.get())) {
            LightwellRecipe recipe = holder.value();
            if (recipe == null || AdapterUtils.isIngredientEmpty(recipe.getInput())) continue;
            if (!AdapterUtils.matchesRequired(mergedInputs, Map.of(recipe.getInput(), 1L))) continue;
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    /**
     * 构造配方产出的流体。
     *
     * <p>产出量按基准容量与生产倍率的乘积向上取整；倍率非正或结果不足 1mB 时视为无产出。</p>
     *
     * @param recipe 源聚星缸配方
     * @return 流体产出列表；无有效产出时返回空列表
     */
    private static List<FluidStack> buildOutputFluids(LightwellRecipe recipe) {
        Fluid fluid = recipe.getGeneratedFluid();
        if (fluid == null) return List.of();

        float multiplier = recipe.getProductionMultiplier();
        if (multiplier <= 0F) return List.of();

        int amount = (int) Math.ceil(PRODUCTION_BASE_AMOUNT * multiplier);
        if (amount <= 0) return List.of();

        return List.of(new FluidStack(fluid, amount));
    }
}
