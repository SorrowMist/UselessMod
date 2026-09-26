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

    default boolean isAvailable() {
        return true;
    }
}
