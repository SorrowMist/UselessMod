package com.sorrowmist.useless.content.items;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.items.AEBaseItem;
import appeng.menu.MenuOpener;
import net.minecraft.core.BlockPos;
import appeng.menu.locator.MenuLocators;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.menus.ContainerPatternConverter;
import com.sorrowmist.useless.content.menus.HostPatternConverter;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Converts ordinary AE2 processing patterns using the molds in a linked hub. */
public final class OmniversalPatternConverterItem extends AEBaseItem implements IMenuItem {
    public OmniversalPatternConverterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
        @NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                ItemStack stack = player.getItemInHand(hand);
                if (com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, UComponents.PATTERN_CONVERTER_LINK_TARGET.get()) != null) {
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.remove(stack, UComponents.PATTERN_CONVERTER_LINK_TARGET.get());
                    player.displayClientMessage(Component.translatable(
                            "message.useless_mod.pattern_converter.unlinked"), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.useless_mod.pattern_converter.unbound"), true);
                }
            }
            return new InteractionResultHolder<>(
                    InteractionResult.sidedSuccess(level.isClientSide()), player.getItemInHand(hand));
        }
        if (!level.isClientSide()) {
            MenuOpener.open(ContainerPatternConverter.TYPE, player, MenuLocators.forHand(player, hand));
        }
        return new InteractionResultHolder<>(
                InteractionResult.sidedSuccess(level.isClientSide()), player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;

        BlockEntity targetBlockEntity = level.getBlockEntity(context.getClickedPos());
        if (isLinkableTarget(targetBlockEntity)) {
            if (!level.isClientSide()) {
                GlobalPos target = GlobalPos.of(level.dimension(), context.getClickedPos());
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, UComponents.PATTERN_CONVERTER_LINK_TARGET.get(), target);
                player.displayClientMessage(Component.translatable(
                        "message.useless_mod.pattern_converter.linked",
                        targetBlockEntity.getBlockState().getBlock().getName()), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, UComponents.PATTERN_CONVERTER_LINK_TARGET.get());
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.useless_mod.pattern_converter.unlinked"));
            tooltip.add(Component.translatable("tooltip.useless_mod.pattern_converter.bind_hint"));
        } else {
            tooltip.add(Component.translatable(
                    "tooltip.useless_mod.pattern_converter.linked",
                    target.dimension().location().toString(),
                    target.pos().getX(), target.pos().getY(), target.pos().getZ()));
        }
    }

    private static boolean isLinkableTarget(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof OmniversalMoldHubBlockEntity
                || blockEntity instanceof AdvancedAlloyFurnaceBlockEntity
                || blockEntity instanceof MultiblockAlloyFurnaceCoreBlockEntity;
    }

    @Override
    public @Nullable ItemMenuHost getMenuHost(
            Player player, int slot, ItemStack stack, @Nullable BlockPos pos) {
        return new HostPatternConverter(player, slot, stack);
    }
}
