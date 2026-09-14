package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.core.component.MultiblockPartData;
import com.sorrowmist.useless.core.component.PassiveHatchSettings;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.world.inventory.ExternalInventoryStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PassiveCraftingHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public PassiveCraftingHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassiveCraftingHatchBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickerLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof PassiveCraftingHatchBlockEntity hatch) {
                hatch.serverTickStandalone();
            }
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof PassiveCraftingHatchBlockEntity hatch) {
            if (!level.isClientSide) {
                hatch.prepareForRemoval();
                hatch.releaseExternalInventory();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof PassiveCraftingHatchBlockEntity hatch) {
            hatch.prepareForRemoval();
            MultiblockPartData itemData = hatch.createSettingsItemData();
            ExternalInventoryReference reference = hatch.getExternalInventoryReference();
            List<PassiveHatchSettings.SlotMultiplier> slotMultipliers = hatch.slotMultiplierSettings();
            boolean customInterval = itemData.intervalTicks()
                    != PassiveCraftingHatchBlockEntity.DEFAULT_INTERVAL_TICKS;
            boolean customMultiplier = itemData.multiplier() != 1L;
            boolean hasCustomData = reference != null || customInterval || customMultiplier
                    || !slotMultipliers.isEmpty();
            if (hasCustomData) {
                for (ItemStack drop : drops) {
                    if (drop.is(asItem())) {
                        if (reference != null) {
                            drop.set(UComponents.EXTERNAL_INVENTORY_REFERENCE.get(), reference);
                        }
                        if (customInterval || customMultiplier || !slotMultipliers.isEmpty()) {
                            drop.set(UComponents.PASSIVE_HATCH_SETTINGS.get(),
                                    new PassiveHatchSettings(itemData.intervalTicks(),
                                            itemData.multiplier(), slotMultipliers));
                        }
                        break;
                    }
                }
            }
        }
        return drops;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof PassiveCraftingHatchBlockEntity hatch) {
            ExternalInventoryReference reference = stack.get(UComponents.EXTERNAL_INVENTORY_REFERENCE.get());
            PassiveHatchSettings settings = stack.get(UComponents.PASSIVE_HATCH_SETTINGS.get());
            MultiblockPartData itemData = stack.get(UComponents.MULTIBLOCK_PART_DATA.get());
            hatch.bindExternalInventory(reference,
                    itemData == null ? null : itemData.inventory(), level.registryAccess());
            if (settings != null) {
                hatch.restoreSettings(settings);
            } else {
                hatch.restoreSettings(itemData);
            }
            ExternalInventoryStore.clearLegacyComponent(hatch, ExternalInventoryKind.PASSIVE_HATCH);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
