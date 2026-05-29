package com.sorrowmist.useless.content.items;

import appeng.api.ids.AEComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class AE2GiftPackageItem extends Item {
    public AE2GiftPackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!level.isClientSide) {
            NonNullList<ItemStack> contents = createPackageContents();
            
            for (ItemStack content : contents) {
                if (!content.isEmpty()) {
                    if (!player.getInventory().add(content)) {
                        player.drop(content, false);
                    }
                }
            }
            
            stack.shrink(1);
            
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.minecraft.commands.CommandSourceStack source = serverPlayer.createCommandSourceStack();
                serverPlayer.getServer().getCommands().performPrefixedCommand(source, "ae2 channelmode infinite");
            }
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private NonNullList<ItemStack> createPackageContents() {
        NonNullList<ItemStack> contents = NonNullList.create();

        contents.add(createItemStack("ae2", "creative_energy_cell", 1));
        contents.add(createItemStack("ae2", "fluix_covered_cable", 64));
        contents.add(createItemStack("ae2", "wireless_access_point", 1));
        contents.add(createItemStack("ae2", "wireless_booster", 64));
        contents.add(createItemStack("ae2", "wireless_crafting_terminal", 1));
        contents.add(createItemStack("ae2", "crafting_terminal", 1));

        if (ModList.get().isLoaded("extendedae_plus")) {
            contents.add(createItemStack("extendedae_plus", "infinity_biginteger_cell", 1));
        } else {
            for (int i = 0; i < 8; i++) {
                contents.add(createItemStack("ae2", "item_storage_cell_256k", 1));
            }
        }

        if (ModList.get().isLoaded("extendedae")) {
            contents.add(createItemStack("extendedae", "ex_drive", 1));
        } else {
            contents.add(createItemStack("ae2", "drive", 1));
        }

        if (ModList.get().isLoaded("ae2wtlib")) {
            contents.add(createItemStack("ae2wtlib", "quantum_bridge_card", 1));
            for (int i = 0; i < 8; i++) {
                contents.add(createItemStack("ae2", "quantum_ring", 1));
            }
            contents.add(createItemStack("ae2", "quantum_link", 1));
        }

        contents.addAll(createEntangledSingularityPair());

        return contents;
    }

    private NonNullList<ItemStack> createEntangledSingularityPair() {
        NonNullList<ItemStack> pair = NonNullList.create();
        Item singularityItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", "quantum_entangled_singularity"));
        
        if (singularityItem != Items.AIR) {
            long frequency = new Date().getTime() * 100 + (System.nanoTime() % 100);
            
            ItemStack singularity1 = new ItemStack(singularityItem, 1);
            singularity1.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
            
            ItemStack singularity2 = new ItemStack(singularityItem, 1);
            singularity2.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
            
            pair.add(singularity1);
            pair.add(singularity2);
        }
        
        return pair;
    }

    private ItemStack createItemStack(String modId, String itemId, int count) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(modId, itemId));
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.ae2_gift_package.description"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}