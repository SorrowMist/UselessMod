package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Optional chemical integration owned by a compatibility module. */
public interface ChemicalCompatProvider {
    ChemicalCompatProvider NONE = new ChemicalCompatProvider() {
        @Override
        public FurnaceChemicalStorage createStorage(long capacity, Runnable onChanged) {
            return FurnaceChemicalStorage.DISABLED;
        }

        @Override
        public @Nullable ChemicalHandlerView getAdjacentHandler(Level level, BlockPos pos, BlockState state,
                                                                BlockEntity entity, @Nullable Direction side) {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    };

    FurnaceChemicalStorage createStorage(long capacity, Runnable onChanged);

    @Nullable
    ChemicalHandlerView getAdjacentHandler(Level level, BlockPos pos, BlockState state,
                                           BlockEntity entity, @Nullable Direction side);

    /**
     * 读「物品里装的化学品」——无线物流用它把化学品罐当成过滤标记物。
     *
     * <p>没装任何化学品（或本环境不支持化学品）时返回 {@code null}。</p>
     */
    @Nullable
    default ChemicalStackView chemicalInItem(ItemStack stack) {
        return null;
    }

    /**
     * JEI 拖来的化学品原料 → 一个能当过滤标记的容器物品（装满该化学品的储罐）。
     *
     * <p>参数是 JEI 的原始原料对象，只有化学品集成自己认得它；转换不了时返回空栈。</p>
     */
    default ItemStack markerForChemical(Object chemicalIngredient) {
        return ItemStack.EMPTY;
    }

    /**
     * 把一个 {@link ChemicalStackView#typeKey() typeKey} 翻成注册表 id，供过滤器的通配符匹配用。
     *
     * <p>{@code typeKey()} 是个不透明对象（Mekanism 那边是它自己的 {@code Chemical}），
     * 常驻代码拿不到显示名或 id，所以只能由集成自己翻译。返回 {@code null} 表示
     * 「这一端给不出 id」：此时过滤器里的通配符对化学品恒不匹配——宁可「什么都不搬」
     * （可观察、玩家能改），也不要因为拿不到 id 就当「不限制」而把整张网络灌出去。</p>
     *
     * <p>注意化学品<b>没有</b> {@code #tag} 形式：Mekanism 的化学品注册表没有跨模组的
     * 通用 TagKey，界面会明确提示这一点。</p>
     */
    @Nullable
    default net.minecraft.resources.ResourceLocation chemicalIdOf(Object typeKey) {
        return null;
    }

    default boolean isAvailable() {
        return true;
    }
}
