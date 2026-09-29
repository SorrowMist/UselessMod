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
import thedarkcolour.exdeorum.recipe.barrel.BarrelFluidMixingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 Ex Deorum 桶的双流体混合配方转换为合金炉配方。
 *
 * <p>此类配方由桶内基础流体与相邻方块提供的添加流体共同作用，产出物品。转换后：</p>
 * <ul>
 *     <li>基础流体按配方声明的定量作为流体输入；</li>
 *     <li>添加流体本身不声明用量，按一整桶（{@link ExDeorumRecipeAdapterUtils#BUCKET_VOLUME} mB）折算；</li>
 *     <li>{@code consumes_additive} 为假时不消耗添加流体，对应 Ex Deorum 中「世界里的添加源不被消耗」
 *     的设定，此时该流体仅作为模具参与配方判定。</li>
 * </ul>
 */
public final class BarrelFluidMixingRecipeAdapter implements IRecipeAdapter<BarrelFluidMixingRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<BarrelFluidMixingRecipe> getRecipeClass() {
        return BarrelFluidMixingRecipe.class;
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
    public List<RecipeHolder<BarrelFluidMixingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedFluids == null || mergedFluids.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<BarrelFluidMixingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BarrelFluidMixingRecipe> holder : ExDeorumRecipeAdapterUtils.allOf(
                level.getRecipeManager(), BarrelFluidMixingRecipe.class)) {
            BarrelFluidMixingRecipe recipe = holder.value();
            if (recipe == null) {
                continue;
            }
            if (!ExDeorumRecipeAdapterUtils.matchesFluids(requiredFluids(recipe), mergedFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BarrelFluidMixingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        BarrelFluidMixingRecipe recipe = holder.value();
        ItemStack result = recipe.result();
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        int processTime = ExDeorumRecipeAdapterUtils.processTimeFor(1);

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(),
                requiredFluids(recipe),
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

    /**
     * 汇总配方要求的流体输入。
     *
     * <p>基础流体与添加流体都下发为输入。添加流体不声明用量，按一整桶折算；其
     * {@code consumes_additive} 标志不影响输入构成，因为合金炉没有「提供该流体但不消耗」的
     * 表达方式，省略该输入只会使配方无法匹配。</p>
     */
    private static List<LongSizedFluidIngredient> requiredFluids(BarrelFluidMixingRecipe recipe) {
        List<LongSizedFluidIngredient> fluids = new ArrayList<>(2);
        if (recipe.baseFluid() != null) {
            fluids.add(LongSizedFluidIngredient.from(recipe.baseFluid()));
        }
        if (recipe.additiveFluid() != null) {
            fluids.add(new LongSizedFluidIngredient(
                    recipe.additiveFluid(), ExDeorumRecipeAdapterUtils.BUCKET_VOLUME));
        }
        return List.copyOf(fluids);
    }
}
