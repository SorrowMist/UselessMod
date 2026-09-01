package com.sorrowmist.useless.content.items;

import appeng.api.ids.AEComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
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
            
            if (ModList.get().isLoaded("ae2")
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && serverPlayer.getServer() != null) {
                net.minecraft.commands.CommandSourceStack source = serverPlayer.createCommandSourceStack();
                serverPlayer.getServer().getCommands().performPrefixedCommand(source, "ae2 channelmode infinite");
            }
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private NonNullList<ItemStack> createPackageContents() {
        NonNullList<ItemStack> contents = NonNullList.create();
        if (!ModList.get().isLoaded("ae2")) {
            return contents;
        }

        for (String entry : ConfigManager.getAE2GiftPackageItems()) {
            addConfiguredEntry(contents, entry);
        }

        return contents;
    }

    private void addConfiguredEntry(NonNullList<ItemStack> contents, String entry) {
        if (entry == null) {
            return;
        }

        String[] parts = entry.split(",", -1);
        if (parts.length != 2) {
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(parts[0].trim());
        if (itemId == null) {
            return;
        }

        int count;
        try {
            count = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
            return;
        }
        if (count <= 0) {
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return;
        }

        if (itemId.equals(ResourceLocation.fromNamespaceAndPath("ae2", "quantum_entangled_singularity"))) {
            while (count >= 2) {
                contents.addAll(createEntangledSingularityPair(item));
                count -= 2;
            }
        }

        addItemStacks(contents, item, count);
    }

    private void addItemStacks(NonNullList<ItemStack> contents, Item item, int count) {
        int maxStackSize = Math.max(1, item.getDefaultMaxStackSize());
        while (count > 0) {
            int stackSize = Math.min(count, maxStackSize);
            contents.add(new ItemStack(item, stackSize));
            count -= stackSize;
        }
    }

    private NonNullList<ItemStack> createEntangledSingularityPair(Item singularityItem) {
        NonNullList<ItemStack> pair = NonNullList.create();
        long frequency = new Date().getTime() * 100 + (System.nanoTime() % 100);

        ItemStack singularity1 = new ItemStack(singularityItem, 1);
        singularity1.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);

        ItemStack singularity2 = new ItemStack(singularityItem, 1);
        singularity2.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);

        pair.add(singularity1);
        pair.add(singularity2);
        return pair;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.ae2_gift_package.description"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
