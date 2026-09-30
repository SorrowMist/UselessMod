package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.recipe.crucible.CrucibleRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 Ex Deorum 坩埚的熔化配方转换为合金炉配方。
 *
 * <p>坩埚配方描述「投入一件物品，熔化后产出定量流体」，其中熔岩坩埚与水源坩埚共用同一套
 * 配方结构（{@link CrucibleRecipe.Lava} 与 {@link CrucibleRecipe.Water}），仅设备与配方类型不同，
 * 因此两者由本适配器一并处理。</p>
 *
 * <p>坩埚没有对应的物品标签，模具改由 Ex Deorum 公开的 {@code CompatUtil} 枚举当前可用的坩埚物品，
 * 判定与模具列表都据此生成。</p>
 *
 * <p>配方本身不声明耗时，实际熔化速率由设备决定，故统一按单次操作的基准耗时换算处理时间与能量。</p>
 */
public final class CrucibleRecipeAdapter implements IRecipeAdapter<CrucibleRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<CrucibleRecipe> getRecipeClass() {
        return CrucibleRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return ExDeorumRecipeAdapterUtils.isLavaCrucible(mold)
                || ExDeorumRecipeAdapterUtils.isWaterCrucible(mold);
    }

    @Override
    public List<RecipeHolder<CrucibleRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<CrucibleRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CrucibleRecipe> holder : ExDeorumRecipeAdapterUtils.allOf(
                level.getRecipeManager(), CrucibleRecipe.class)) {
            CrucibleRecipe recipe = holder.value();
            if (recipe == null || AdapterUtils.isIngredientEmpty(recipe.ingredient())) {
                continue;
            }
            if (AdapterUtils.countMatchingIngredient(mergedInputs, recipe.ingredient()) > 0L) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrucibleRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        CrucibleRecipe recipe = holder.value();
        Ingredient ingredient = recipe.ingredient();
        FluidStack result = recipe.getResult();
        if (AdapterUtils.isIngredientEmpty(ingredient) || result == null || result.isEmpty()) {
            return List.of();
        }

        List<Ingredient> molds = moldsFor(recipe);
        if (molds.isEmpty()) {
            // 坩埚物品集合为空说明材料注册表尚未就绪，此时放弃转换而不是产出无模具配方。
            return List.of();
        }

        int processTime = ExDeorumRecipeAdapterUtils.processTimeFor(1);

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(ingredient, 1L)),
                List.of(),
                List.of(),
                List.of(),
                List.of(result.copy()),
                List.of(),
                ExDeorumRecipeAdapterUtils.energyFor(processTime),
                processTime,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL));
    }

    /** 按配方所属的坩埚类别选取对应模具，避免熔岩坩埚配方出现水源坩埚模具。 */
    private static List<Ingredient> moldsFor(CrucibleRecipe recipe) {
        if (recipe instanceof CrucibleRecipe.Lava) {
            return ExDeorumRecipeAdapterUtils.lavaCrucibleMolds();
        }
        if (recipe instanceof CrucibleRecipe.Water) {
            return ExDeorumRecipeAdapterUtils.waterCrucibleMolds();
        }
        return List.of();
    }
}
