package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 配方适配器接口
 * <p>
 * 用于将其他 Mod 或原版机器配方转换为高级合金炉可识别的配方，并在运行时根据合并后的输入查找原始配方。
 * <p>
 * Manager 层负责收集机器输入、合并物品/流体/AEKey，并按模具选择适配器；适配器只负责解释对应配方类型的输入语义。
 *
 * @param <T> 原始配方类型
 */
public interface IRecipeAdapter<T extends Recipe<?>> {

    /**
     * 获取适配器支持的配方类型
     * <p>
     * 该类型用于从 {@link net.minecraft.world.item.crafting.RecipeManager} 中筛选原始配方，也用于注册表判断适配器负责的配方来源。
     *
     * @return 原始配方的 Class 对象
     */
    Class<T> getRecipeClass();

    /**
     * 将一个原始配方转换为单个高级合金炉配方
     * <p>
     * 这是单结果转换的便捷入口。默认实现会调用 {@link #convertAll(RecipeHolder, Level)}，并返回列表中的第一个结果。
     * 如果某种原始配方可能展开为多个合金炉配方，适配器应优先覆写 {@code convertAll}。
     *
     * @param holder  配方持有者（包含ID和配方）
     * @param level   世界
     * @return 转换后的高级合金炉配方，如果无法转换则返回 null
     */
    @Nullable
    default AdvancedAlloyFurnaceRecipe convert(RecipeHolder<T> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.getFirst();
    }

    /**
     * 将一个原始配方转换为多个高级合金炉配方
     * <p>
     * 这是批量转换入口，适合一个原始配方需要按不同模具、不同模式或不同输出形式展开的情况。
     * 默认实现会调用 {@link #convert(RecipeHolder, Level)}，并把非空结果包装成单元素列表。
     *
     * @param holder  配方持有者（包含ID和配方）
     * @param level   世界
     * @return 转换后的高级合金炉配方列表，如果无法转换则返回空列表
     */
    default List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<T> holder, Level level) {
        AdvancedAlloyFurnaceRecipe recipe = convert(holder, level);
        return recipe == null ? List.of() : List.of(recipe);
    }

    /**
     * 获取此适配器对应的模具物品。
     * <p>
     * 用于 AlloyFurnaceRecipeManager 按模具预筛选适配器，快速定位配方类型。
     * 固定模具适配器应返回对应物品；动态模具适配器应返回 null，并通过 {@link #matchesMold(ItemStack)} 自行判断。
     *
     * @return 模具物品，如果此适配器无固定模具则返回 null
     */
    @Nullable
    ItemStack getMoldItem();

    /**
     * 判断当前机器模具是否可能由此适配器处理
     * <p>
     * 默认实现：如果 {@link #getMoldItem()} 返回非 null，则比较模具物品；
     * 如果返回 null（无固定模具），则返回 true（由后续配方匹配逻辑处理）。
     * <p>
     * 动态模具适配器可以覆写此方法，例如根据物品接口、标签或组件判断是否属于本适配器。
     *
     * @param mold 当前机器中的模具物品，可为空
     * @return 如果该模具有可能属于此适配器则返回 true
     */
    default boolean matchesMold(@Nullable ItemStack mold) {
        ItemStack myMold = getMoldItem();
        if (myMold == null || myMold.isEmpty()) {
            return true; // 无固定模具，接受所有
        }
        if (mold == null || mold.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItem(myMold, mold);
    }

    /**
     * 查找并返回匹配的原始配方持有者（不含 AEKey 输入）
     * <p>
     * 这是不需要 AEKey 的适配器兼容入口。覆写此方法即可让五参主入口自动回退到普通物品/流体匹配。
     * 默认返回 null，表示接口本身不提供通用匹配逻辑，避免四参默认调用五参、五参默认再调用四参造成递归循环。
     *
     * @param level        世界
     * @param mergedInputs 已按 Ingredient 合并的物品输入
     * @param mergedFluids 已按流体类型合并的流体输入
     * @param mold         当前模具（可为空）
     * @return 匹配的配方持有者，如果没有或适配器未覆写则返回 null
     */
    @Nullable
    default RecipeHolder<T> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return null;
    }

    /**
     * 查找并返回匹配的原始配方持有者
     * <p>
     * 这是当前适配器的主匹配入口。Manager 层会先统一合并物品、流体和 AEKey，再调用此方法。
     * 适配器应直接基于 Map 做数量匹配，避免把 Ingredient 还原成代表 ItemStack 导致标签、多候选输入或组件信息丢失。
     * <p>
     * 默认实现会回退到四参版本，用于兼容不需要 AEKey 的旧适配器；需要 AEKey 的适配器必须覆写此方法。
     *
     * @param level        世界
     * @param mergedInputs 已按 Ingredient 合并的物品输入
     * @param mergedFluids 已按流体类型合并的流体输入
     * @param mergedKeys   已按 AEKey 合并的非物品/非流体输入
     * @param mold         当前模具（可为空）
     * @return 匹配的配方持有者，如果没有则返回 null
     */
    @Nullable
    default RecipeHolder<T> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        return findMatchingRecipe(level, mergedInputs, mergedFluids, mold);
    }
}
