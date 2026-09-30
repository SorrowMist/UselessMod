package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.recipe.hammer.CompressedHammerRecipe;
import thedarkcolour.exdeorum.recipe.hammer.HammerRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 把 Ex Deorum 的锤子与压缩锤配方转换为合金炉配方。
 *
 * <p>锤子配方描述「锤击一件方块，产出定量物品」，压缩锤配方是其子类，两者结构相同而模具不同：
 * 锤子以钻石锤为模具，压缩锤以钻石压缩锤为模具，二者互不重叠，因此同一输入在两类设备下可
 * 对应不同产出，转换时按配方实际类型分别指定模具。模具槽位只有一个，与单模具配方语义一致。</p>
 *
 * <p>模具固定为钻石品质而不按材质区分：锤击行为与手持锤子的材质无关，若改用整个
 * {@code exdeorum:hammers} 标签，同一配方会因可选模具过多而在模具仓中产生歧义匹配。</p>
 *
 * <p>产出语义：产出量由数量提供者描述，其随机性表达在数量本身而非独立的触发概率上，例如
 * {@code uniform(1, 6)} 表示产出 1 至 6 个。转换时先按 Ex Deorum 的期望值规则把提供者折算为
 * 期望产出量，再经 {@link ExpectedOutputScaler} 折算为最小确定性批次，输入数量、能耗与处理
 * 时间按同一批次倍数同步放大，避免把概率产出错误地当作必出。</p>
 *
 * <p>动态读取：配方来自 RecipeManager，在运行期解析，因此数据包或脚本新增的锤子配方会一并
 * 生效；模具为固定的钻石锤与钻石压缩锤。</p>
 *
 * <p>已知取舍：锤击速度受效率附魔影响，幸运附魔提供额外产出机会，二者均为运行时随机行为，
 * 无法在不引入随机的前提下表达为确定性配方，故此处按未附魔的基准折算。</p>
 */
public final class HammerRecipeAdapter implements IRecipeAdapter<HammerSyntheticRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<HammerSyntheticRecipe> getRecipeClass() {
        return HammerSyntheticRecipe.class;
    }

    /** 模具由配方类型决定，无法在注册阶段固定，因此以动态模具方式登记。 */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return ExDeorumRecipeAdapterUtils.isHammer(mold)
                || ExDeorumRecipeAdapterUtils.isCompressedHammer(mold);
    }

    @Override
    public List<RecipeHolder<HammerSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<HammerRecipe>> sources;
        try {
            // 压缩锤配方是锤子配方的子类，按基类枚举即可同时覆盖两类配方。
            sources = ExDeorumRecipeAdapterUtils.allOf(level.getRecipeManager(), HammerRecipe.class);
        }
        catch (RuntimeException exception) {
            // Ex Deorum 未安装或配方类型未注册时静默跳过，不影响其余来源的转换。
            return List.of();
        }
        if (sources.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<HammerSyntheticRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<HammerRecipe> holder : sources) {
            AdvancedAlloyFurnaceRecipe converted = convertHammer(holder);
            if (converted != null) {
                recipes.add(new RecipeHolder<>(converted.id(), new HammerSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(recipes);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<HammerSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    /**
     * 转换单条锤子配方。
     *
     * @param holder 锤子配方持有者
     * @return 转换后的合金炉配方，无法转换时返回 {@code null}
     */
    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertHammer(RecipeHolder<HammerRecipe> holder) {
        if (holder == null || holder.value() == null) {
            return null;
        }

        HammerRecipe recipe = holder.value();
        if (AdapterUtils.isIngredientEmpty(recipe.ingredient())
                || recipe.result == null || recipe.result.isEmpty()) {
            return null;
        }

        OptionalDouble expected = ExDeorumRecipeAdapterUtils.expectedAmount(recipe.resultAmount());
        if (expected.isEmpty() || expected.getAsDouble() <= 0.0D) {
            return null;
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        ExDeorumRecipeAdapterUtils.appendExpectedOutput(
                weightedOutputs, recipe.result, expected.getAsDouble());

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled =
                ExpectedOutputScaler.scale(weightedOutputs);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
            return null;
        }
        ExpectedOutputScaler.ScaledOutputs scaledOutputs = scaled.get();
        int operations = scaledOutputs.operations();

        // 压缩锤与锤子分属不同标签，按配方实际类型选取模具，避免产出无法在对应设备上复现。
        List<Ingredient> molds = recipe instanceof CompressedHammerRecipe
                ? ExDeorumRecipeAdapterUtils.compressedHammerMolds()
                : ExDeorumRecipeAdapterUtils.hammerMolds();

        int processTime = ExDeorumRecipeAdapterUtils.processTimeFor(operations);

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(recipe.ingredient(), Math.max(1L, operations))),
                List.of(),
                List.of(),
                scaledOutputs.outputs(),
                List.of(),
                List.of(),
                ExDeorumRecipeAdapterUtils.energyFor(processTime),
                processTime,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }
}
