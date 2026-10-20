package com.sorrowmist.useless.content.blocks.multiblock;

import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import com.glodblock.github.extendedae.container.ContainerRenamer;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.core.component.MultiblockPartData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.world.inventory.ExternalInventoryStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MePatternAssemblyBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public MePatternAssemblyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MePatternAssemblyBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly) {
            assembly.releaseExternalInventory();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof MePatternAssemblyBlockEntity assembly) {
            ExternalInventoryReference reference = assembly.getExternalInventoryReference();
            Component customName = assembly.getCustomName();
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    if (reference != null) {
                        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(drop, UComponents.EXTERNAL_INVENTORY_REFERENCE.get(), reference);
                    }
                    if (customName != null) {
                        drop.setHoverName(customName);
                    }
                    break;
                }
            }
        }
        return drops;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly) {
            ExternalInventoryReference reference = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, UComponents.EXTERNAL_INVENTORY_REFERENCE.get());
            MultiblockPartData itemData = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, UComponents.MULTIBLOCK_PART_DATA.get());
            assembly.bindExternalInventory(reference,
                    itemData == null ? null : itemData.inventory(), level.registryAccess());
            ExternalInventoryStore.clearLegacyComponent(assembly, ExternalInventoryKind.PATTERN_ASSEMBLY);
            Component customName = stack.hasCustomHoverName() ? stack.getHoverName() : null;
            if (customName != null) {
                assembly.setName(customName.getString());
            }
        }
    }

    @Override
    public @NotNull InteractionResult use( @NotNull BlockState state,
                                                        Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull InteractionHand hand,
                                                        @NotNull BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly
                && !InteractionUtil.isInAlternateUseMode(player)
                && stack.getItem() instanceof QuartzCuttingKnifeItem) {
            if (!level.isClientSide) {
                MenuOpener.open(ContainerRenamer.TYPE, player, MenuLocators.forBlockEntity(assembly));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, provider, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
