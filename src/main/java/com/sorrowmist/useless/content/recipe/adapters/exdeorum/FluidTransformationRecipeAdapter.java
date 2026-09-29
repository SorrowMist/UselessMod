package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.recipe.barrel.FluidTransformationRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 Ex Deorum 桶的流体转化配方转换为合金炉配方。
 *
 * <p>此类配方描述「满桶的基础流体在下方方块作为催化剂时转化为另一种流体」。</p>
 *
 * <p>催化剂是配方查表的必要组成部分，其判据为方块谓词而非物品，与合金炉的模具槽语义不兼容，
 * 因此本适配器不引入催化剂模具：只要基础流体与桶模具同时满足即可转换。该取舍会放宽
 * Ex Deorum 对催化剂位置的约束，属于转换到合金炉后的固有差异。</p>
 *
 * <p>配方自带的 {@code duration} 表示催化方块数量为 1 时的推进总时长，直接作为处理时间沿用。</p>
 */
public final class FluidTransformationRecipeAdapter implements IRecipeAdapter<FluidTransformationRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<FluidTransformationRecipe> getRecipeClass() {
        return FluidTransformationRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return ExDeorumRecipeAdapterUtils.isBarrel(mold);
    }

    @Override
    public List<RecipeHolder<FluidTransformationRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedFluids == null || mergedFluids.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<FluidTransformationRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<FluidTransformationRecipe> holder : ExDeorumRecipeAdapterUtils.allOf(
                level.getRecipeManager(), FluidTransformationRecipe.class)) {
            FluidTransformationRecipe recipe = holder.value();
            if (recipe == null || recipe.baseFluid() == null) {
                continue;
            }
            // 转化以满桶为触发条件，故基础流体按整桶计量。
            LongSizedFluidIngredient base = new LongSizedFluidIngredient(
                    recipe.baseFluid(), ExDeorumRecipeAdapterUtils.BUCKET_VOLUME);
            if (!ExDeorumRecipeAdapterUtils.matchesFluids(List.of(base), mergedFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<FluidTransformationRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        FluidTransformationRecipe recipe = holder.value();
        if (recipe.baseFluid() == null || recipe.resultFluid() == null) {
            return List.of();
        }

        int duration = recipe.duration();
        int processTime = duration > 0
                ? AdapterUtils.safeInt(duration)
                : ExDeorumRecipeAdapterUtils.processTimeFor(1);

        LongSizedFluidIngredient base = new LongSizedFluidIngredient(
                recipe.baseFluid(), ExDeorumRecipeAdapterUtils.BUCKET_VOLUME);

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(),
                List.of(base),
                List.of(),
                List.of(),
                List.of(new FluidStack(recipe.resultFluid(), ExDeorumRecipeAdapterUtils.BUCKET_VOLUME)),
                List.of(),
                ExDeorumRecipeAdapterUtils.energyFor(processTime),
                processTime,
                Ingredient.EMPTY,
                0,
                ExDeorumRecipeAdapterUtils.barrelMolds(),
                AlloyFurnaceMode.NORMAL));
    }
}
