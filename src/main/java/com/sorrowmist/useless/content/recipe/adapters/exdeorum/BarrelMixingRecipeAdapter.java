package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.recipe.barrel.BarrelMixingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 Ex Deorum 桶的物品混合配方转换为合金炉配方。
 *
 * <p>此类配方要求桶内同时具备一件指定物品与定量流体，两者在桶内发生反应后
 * 直接替换为产物物品。转换后物品与流体都成为合金炉的常规输入，产物为配方结果。</p>
 *
 * <p>物品输入按配方声明原样下发，即使其本身是装满流体的桶也不折算为流体：Ex Deorum 对
 * 同一产物同时存在物品混合与流体混合两条配方（例如牛奶桶与水、水与牛奶流体），把桶折算为
 * 流体会使两条配方的输入输出完全一致，在配方目录中产生重复条目。</p>
 *
 * <p>石桶与木桶共用同一套混合配方，模具以 {@code exdeorum:barrels} 物品标签表示。</p>
 */
public final class BarrelMixingRecipeAdapter implements IRecipeAdapter<BarrelMixingRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<BarrelMixingRecipe> getRecipeClass() {
        return BarrelMixingRecipe.class;
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
    public List<RecipeHolder<BarrelMixingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<BarrelMixingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BarrelMixingRecipe> holder : ExDeorumRecipeAdapterUtils.allOf(
                level.getRecipeManager(), BarrelMixingRecipe.class)) {
            BarrelMixingRecipe recipe = holder.value();
            if (recipe == null || AdapterUtils.isIngredientEmpty(recipe.ingredient)) {
                continue;
            }
            if (AdapterUtils.countMatchingIngredient(mergedInputs, recipe.ingredient) <= 0L) {
                continue;
            }
            if (!ExDeorumRecipeAdapterUtils.matchesFluids(
                    List.of(LongSizedFluidIngredient.from(recipe.fluid)), mergedFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BarrelMixingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        BarrelMixingRecipe recipe = holder.value();
        ItemStack result = recipe.getResult();
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        int processTime = ExDeorumRecipeAdapterUtils.processTimeFor(1);

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(recipe.ingredient, 1L)),
                List.of(LongSizedFluidIngredient.from(recipe.fluid)),
                List.of(),
                List.of(result.copy()),
                List.of(),
                List.of(),
                ExDeorumRecipeAdapterUtils.energyFor(processTime),
                processTime,
                Ingredient.EMPTY,
                0,
                ExDeorumRecipeAdapterUtils.barrelMolds(),
                AlloyFurnaceMode.NORMAL));
    }
}
