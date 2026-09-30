package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.recipe.barrel.BarrelCompostRecipe;

import java.util.List;

/**
 * 把 Ex Deorum 桶的堆肥配方转换为合金炉配方。
 *
 * <p>堆肥配方描述「投入一件物品可增加多少堆肥量」，当桶内堆肥量累计到
 * {@link ExDeorumRecipeAdapterUtils#COMPOST_FULL_VOLUME} 时转化为一个泥土。
 * 合金炉没有分步累积的中间状态，因此按单次产出折算：凑满一次完整堆肥所需的
 * 物品数量取 {@code ceil(满容量 / 单件体积)}，产物固定为泥土。</p>
 *
 * <p>石桶与木桶共用同一套堆肥配方，模具以 Ex Deorum 的 {@code exdeorum:barrels}
 * 物品标签表示，因此二者无需分别适配。</p>
 */
public final class BarrelCompostRecipeAdapter implements IRecipeAdapter<BarrelCompostRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<BarrelCompostRecipe> getRecipeClass() {
        return BarrelCompostRecipe.class;
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
    public List<RecipeHolder<BarrelCompostRecipe>> findMatchingRecipes(
            Level level, java.util.Map<Ingredient, Long> mergedInputs,
            java.util.Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            java.util.Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<BarrelCompostRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<BarrelCompostRecipe> holder : ExDeorumRecipeAdapterUtils.allOf(
                level.getRecipeManager(), BarrelCompostRecipe.class)) {
            BarrelCompostRecipe recipe = holder.value();
            if (recipe == null || AdapterUtils.isIngredientEmpty(recipe.ingredient)) {
                continue;
            }
            if (AdapterUtils.countMatchingIngredient(mergedInputs, recipe.ingredient) > 0L) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BarrelCompostRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        BarrelCompostRecipe recipe = holder.value();
        int volume = recipe.getVolume();
        if (volume <= 0) {
            return List.of();
        }

        // 凑满一次堆肥所需的物品数量；单件体积超过满容量时按 1 件计。
        long required = Math.max(1L, (long) Math.ceil(
                (double) ExDeorumRecipeAdapterUtils.COMPOST_FULL_VOLUME / volume));

        int processTime = ExDeorumRecipeAdapterUtils.processTimeFor(AdapterUtils.safeInt(required));

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(recipe.ingredient, required)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.DIRT)),
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
