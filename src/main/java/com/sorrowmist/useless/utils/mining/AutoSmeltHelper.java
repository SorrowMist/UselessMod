package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 挖掘时的自动熔炼：把掉落物按熔炉配方再炼一遍。
 *
 * <p>只认原版三类烹饪配方（熔炉 / 高炉 / 烟熏炉），与配方转换开关无关——
 * 这里查的是世界当前的 {@code RecipeManager}，所以数据包与其它模组新增的
 * 熔炼配方都能生效。</p>
 *
 * <p>优先级刻意固定为「熔炉 → 高炉 → 烟熏炉」：同一输入可能同时命中多条
 * （例如生铁既能烧也能高炉炼），固定顺序才能保证产物稳定，不会随配方表
 * 的迭代顺序变化。</p>
 */
public final class AutoSmeltHelper {
    /** 查询顺序固定，避免同一输入命中多条配方时产物漂移。 */
    private static final List<RecipeType<? extends AbstractCookingRecipe>> RECIPE_TYPES = List.of(
            RecipeType.SMELTING,
            RecipeType.BLASTING,
            RecipeType.SMOKING);

    private AutoSmeltHelper() {
    }

    /**
     * 对整批掉落物做一次熔炼转换。
     *
     * <p>未开启自动熔炼、世界为空或整批都烧不动时，原样返回入参，
     * 保证调用方拿到的列表语义与不开启时完全一致。</p>
     *
     * @param level 世界（必须是服务端世界，客户端没有完整配方表）
     * @param drops 掉落物列表
     * @param tool  工具（用于读取自动熔炼开关）
     * @return 熔炼后的掉落物列表
     */
    public static List<ItemStack> smeltDrops(Level level, List<ItemStack> drops, ItemStack tool) {
        if (level == null || level.isClientSide() || drops == null || drops.isEmpty()) {
            return drops;
        }
        if (!UComponentUtils.isAutoSmeltEnabled(tool)) {
            return drops;
        }

        List<ItemStack> smelted = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            smelted.add(smeltOne(level, drop));
        }
        return MiningUtils.mergeItemStacks(smelted);
    }

    /**
     * 熔炼单个堆叠：整堆一起换算，产物数量按输入数量等比放大。
     *
     * <p>找不到任何烹饪配方时原样返回（例如圆石、矿物块这类本来就不需要烧的掉落物）。</p>
     */
    private static ItemStack smeltOne(Level level, ItemStack input) {
        if (input.isEmpty()) {
            return input;
        }

        AbstractCookingRecipe recipe = findRecipe(level, input);
        if (recipe == null) {
            return input;
        }

        // 用单个样本取产物，避免把整堆喂进配方（部分模组配方不读 count，会把数量吃掉）
        ItemStack sample = input.copyWithCount(1);
        ItemStack result = recipe.assemble(new SingleRecipeInput(sample), level.registryAccess());
        if (result.isEmpty()) {
            return input;
        }

        // 输入是整堆时，产物按比例放大并封顶到最大堆叠数
        ItemStack output = result.copy();
        int total = Math.min(output.getMaxStackSize(), output.getCount() * input.getCount());
        output.setCount(total);
        return output;
    }

    /** 按固定顺序查第一条命中的烹饪配方。 */
    private static AbstractCookingRecipe findRecipe(Level level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
        for (RecipeType<? extends AbstractCookingRecipe> type : RECIPE_TYPES) {
            AbstractCookingRecipe recipe = findRecipe(level, type, recipeInput);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    private static <T extends AbstractCookingRecipe> T findRecipe(
            Level level, RecipeType<T> type, SingleRecipeInput input) {
        try {
            return level.getRecipeManager()
                    .getRecipeFor(type, input, level)
                    .map(RecipeHolder::value)
                    .orElse(null);
        } catch (Throwable ignored) {
            // 某个模组的配方在匹配阶段抛异常，不能连累整批掉落物
            return null;
        }
    }
}
