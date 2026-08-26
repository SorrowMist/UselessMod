package com.sorrowmist.useless.compat.itemobliterator;

import elocindev.item_obliterator.neoforge.ItemObliterator;
import elocindev.item_obliterator.neoforge.config.ConfigEntries;
import elocindev.item_obliterator.neoforge.utils.Utils;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemObliteratorRuntimeTest {
    @Test
    void protectedItemsBypassEveryItemObliteratorUtilityBlacklist() {
        ConfigEntries config = ItemObliterator.Config;
        ConfigSnapshot snapshot = ConfigSnapshot.capture(config);

        try {
            config.use_hashmap_optimizations = false;
            config.blacklisted_items.add("useless_mod:item");
            config.blacklisted_nbt.add("blocked");
            config.only_disable_recipes.add("useless_mod:item");
            config.only_disable_interactions.add("useless_mod:item");
            config.only_disable_attacks.add("useless_mod:item");

            assertFalse(Utils.isDisabled("useless_mod:item"));
            assertFalse(Utils.shouldRecipeBeDisabled("useless_mod:item"));
            assertFalse(Utils.isDisabledInteract("useless_mod:item"));
            assertFalse(Utils.isDisabledAttack("useless_mod:item"));

            ItemStack stack = new ItemStack(ModItems.USELESS_INGOT_TIER_1.get());
            CompoundTag customData = new CompoundTag();
            customData.putString("blocked", "true");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
            assertFalse(Utils.isDisabled(stack));

            MerchantOffer offer = new MerchantOffer(
                    new ItemCost(Items.PAPER), stack.copy(), 1, 1, 0.05F);
            assertFalse(Utils.isDisabled(offer));
        } finally {
            snapshot.restore(config);
            ItemObliterator.reloadConfigHashsets();
        }
    }

    @Test
    void nonProtectedItemsStillFollowTheBlacklist() {
        ConfigEntries config = ItemObliterator.Config;
        ConfigSnapshot snapshot = ConfigSnapshot.capture(config);

        try {
            config.use_hashmap_optimizations = false;
            config.blacklisted_items.add("minecraft:paper");

            assertTrue(Utils.isDisabled("minecraft:paper"));
            assertFalse(Utils.isDisabled("useless_mod:item"));
        } finally {
            snapshot.restore(config);
            ItemObliterator.reloadConfigHashsets();
        }
    }

    @Test
    void containerCleanupKeepsProtectedItemsButStillClearsOtherItems() throws Exception {
        ConfigEntries config = ItemObliterator.Config;
        ConfigSnapshot snapshot = ConfigSnapshot.capture(config);

        try {
            config.blacklisted_items.add("useless_mod:useless_ingot_tier_1");
            config.blacklisted_items.add("minecraft:paper");

            ItemStack protectedStack = new ItemStack(ModItems.USELESS_INGOT_TIER_1.get());
            ItemStack ordinaryStack = new ItemStack(Items.PAPER);
            AbstractContainerMenu menu = new TestMenu(protectedStack, ordinaryStack);

            ItemObliterator handler = allocateHandler();
            handler.onPlayerContainer(new PlayerContainerEvent(null, menu));

            assertEquals(1, protectedStack.getCount());
            assertTrue(ordinaryStack.isEmpty());
        } finally {
            snapshot.restore(config);
            ItemObliterator.reloadConfigHashsets();
        }
    }

    private static ItemObliterator allocateHandler() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (ItemObliterator) ((Unsafe) field.get(null))
                .allocateInstance(ItemObliterator.class);
    }

    private static final class TestMenu extends AbstractContainerMenu {
        private TestMenu(ItemStack protectedStack, ItemStack ordinaryStack) {
            super(null, 0);
            SimpleContainer container = new SimpleContainer(2);
            container.setItem(0, protectedStack);
            container.setItem(1, ordinaryStack);
            addSlot(new Slot(container, 0, 0, 0));
            addSlot(new Slot(container, 1, 0, 0));
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private record ConfigSnapshot(
            boolean useHashmapOptimizations,
            List<String> blacklistedItems,
            List<String> blacklistedNbt,
            List<String> onlyDisableInteractions,
            List<String> onlyDisableAttacks,
            List<String> onlyDisableRecipes) {
        private static ConfigSnapshot capture(ConfigEntries config) {
            return new ConfigSnapshot(
                    config.use_hashmap_optimizations,
                    new ArrayList<>(config.blacklisted_items),
                    new ArrayList<>(config.blacklisted_nbt),
                    new ArrayList<>(config.only_disable_interactions),
                    new ArrayList<>(config.only_disable_attacks),
                    new ArrayList<>(config.only_disable_recipes));
        }

        private void restore(ConfigEntries config) {
            config.use_hashmap_optimizations = useHashmapOptimizations;
            replace(config.blacklisted_items, blacklistedItems);
            replace(config.blacklisted_nbt, blacklistedNbt);
            replace(config.only_disable_interactions, onlyDisableInteractions);
            replace(config.only_disable_attacks, onlyDisableAttacks);
            replace(config.only_disable_recipes, onlyDisableRecipes);
        }

        private static void replace(List<String> target, List<String> values) {
            target.clear();
            target.addAll(values);
        }
    }
}
