package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

/**
 * 一次推送代表 N 次工作台合成的 AE2 合成样板包装。
 *
 * <p>输入倍率与产物由 {@link ScaledProcessingPattern} 统一处理；本类只保留分子装配器需要的
 * 合成接口，因此它仍然可以作为 {@link ScaledProcessingPattern} 返回给 NeoECOAE。</p>
 */
public final class ScaledCraftingPattern extends ScaledProcessingPattern
        implements IMolecularAssemblerSupportedPattern {

    public ScaledCraftingPattern(IMolecularAssemblerSupportedPattern pattern, long operationsPerPush) {
        super(pattern, operationsPerPush);
        if (!(getOriginal() instanceof IMolecularAssemblerSupportedPattern)) {
            throw new IllegalArgumentException("Scaled crafting pattern requires a crafting pattern base");
        }
    }

    /** @return 未放大的原始合成样板 */
    public IMolecularAssemblerSupportedPattern getOriginalCraftingPattern() {
        return originalCraftingPattern();
    }

    /** 只装配一次；倍率由接收方在放大产物时体现。 */
    @Override
    public ItemStack assemble(CraftingInput input, Level level) {
        return originalCraftingPattern().assemble(input, level);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return originalCraftingPattern().getRemainingItems(input);
    }

    @Override
    public boolean isItemValid(int slot, AEItemKey key, Level level) {
        return originalCraftingPattern().isItemValid(slot, key, level);
    }

    @Override
    public boolean isSlotEnabled(int slot) {
        return originalCraftingPattern().isSlotEnabled(slot);
    }

    /**
     * 只按一份合成铺料：它会从计数器里各扣掉一份合成所需的量，
     * 剩下的 (N-1) 份由接收方在执行时按倍率一并消化。
     */
    @Override
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor gridAccessor) {
        originalCraftingPattern().fillCraftingGrid(table, gridAccessor);
    }

    private IMolecularAssemblerSupportedPattern originalCraftingPattern() {
        return (IMolecularAssemblerSupportedPattern) getOriginal();
    }
}
