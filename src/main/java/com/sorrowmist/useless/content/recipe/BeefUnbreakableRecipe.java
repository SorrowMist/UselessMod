package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 造化杖 + 任意物品 → 该物品的「不可破坏」版本。
 * <p>
 * 一个 {@link CustomRecipe}（特殊配方，{@code isSpecial() == true}，不占配方书）：
 * <ul>
 *   <li>合成格里恰好有 <b>1 个</b>非造化杖物品，且<b>至少 1 个</b>造化杖变体；</li>
 *   <li>该物品<b>尚未</b>带 {@code DataComponents.UNBREAKABLE}（已不可破坏的不再参与）；</li>
 *   <li>2×2 与 3×3 都可用（{@link #canCraftInDimensions}）。</li>
 * </ul>
 * 产出 = 该物品本体（保留 NBT / 组件），额外打上 {@code UNBREAKABLE}。
 * <p>
 * <b>消耗语义</b>：目标物品被正常消耗；造化杖不消耗 —— 走 {@link EndlessBeafItem} 覆写的
 * {@code hasCraftingRemainingItem / getCraftingRemainingItem}，由 {@code Recipe} 的默认
 * {@code getRemainingItems} 自动返还（本类刻意<b>不</b>覆写它）。
 */
public class BeefUnbreakableRecipe extends CustomRecipe {
    public BeefUnbreakableRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findTarget(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack target = findTarget(input);
        if (target == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = target.copyWithCount(1);
        result.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        // 2×2 与 3×3 都支持
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.sorrowmist.useless.init.ModRecipeSerializers.BEEF_UNBREAKABLE_SERIALIZER.get();
    }

    /**
     * 找出「唯一的目标物品」：格子内恰好一个非造化杖物品，且至少一个造化杖。
     * 不满足返回 null。
     */
    private static ItemStack findTarget(CraftingInput input) {
        ItemStack target = null;
        boolean hasStaff = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof EndlessBeafItem) {
                hasStaff = true;
                continue;
            }
            // 第二个非杖物品 → 不是本配方的形状
            if (target != null) {
                return null;
            }
            target = stack;
        }
        if (!hasStaff || target == null) {
            return null;
        }
        // 已不可破坏的目标不再参与
        if (target.has(DataComponents.UNBREAKABLE)) {
            return null;
        }
        return target;
    }
}
