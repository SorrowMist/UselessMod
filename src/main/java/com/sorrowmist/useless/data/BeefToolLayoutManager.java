package com.sorrowmist.useless.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Loads and stores the beef tool layout in a player's persistent save data. */
public final class BeefToolLayoutManager {
    private static final String ROOT_TAG = "useless_mod:beef_tool_layout";

    private BeefToolLayoutManager() {
    }

    public static BeefToolLayout loadOrCreate(ServerPlayer player) {
        CompoundTag root = get(player);
        if (root == null) {
            BeefToolLayout layout = BeefToolModuleRegistry.defaultLayout();
            normalizeForPlayer(player, layout);
            save(player, layout);
            return layout;
        }

        try {
            BeefToolLayout layout = BeefToolLayout.fromNbt(root);
            validateKnownModules(layout);
            normalizeForPlayer(player, layout);
            save(player, layout);
            return layout;
        } catch (BeefToolLayout.LayoutException exception) {
            BeefToolLayout layout = BeefToolModuleRegistry.defaultLayout();
            normalizeForPlayer(player, layout);
            save(player, layout);
            return layout;
        }
    }

    public static BeefToolLayout normalizeForPlayer(ServerPlayer player, BeefToolLayout layout) {
        BeefToolModuleRegistry.addMissingAvailableModules(layout, findTarget(player));
        if (!layout.pages().isEmpty()) {
            layout.setSelectedPage(Math.max(0,
                    Math.min(layout.selectedPage(), layout.pages().size() - 1)));
        }
        return layout;
    }

    public static void validateForSave(BeefToolLayout layout) throws BeefToolLayout.LayoutException {
        layout.validate();
        validateKnownModules(layout);
    }

    public static void save(ServerPlayer player, BeefToolLayout layout) {
        try {
            validateForSave(layout);
        } catch (BeefToolLayout.LayoutException exception) {
            return;
        }

        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        persistentData.getCompound(Player.PERSISTED_NBT_TAG).put(ROOT_TAG, layout.toNbt());
    }

    private static CompoundTag get(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag playerData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        return playerData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? playerData.getCompound(ROOT_TAG)
                : null;
    }

    private static void validateKnownModules(BeefToolLayout layout) throws BeefToolLayout.LayoutException {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                for (String module : group.modules()) {
                    validateKnownModule(module);
                }
            }
        }
        for (String module : layout.unassignedModules()) {
            validateKnownModule(module);
        }
    }

    private static void validateKnownModule(String module) throws BeefToolLayout.LayoutException {
        if (!BeefToolModuleRegistry.isKnown(module)) {
            throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.UNKNOWN_MODULE);
        }
    }

    private static ItemStack findTarget(ServerPlayer player) {
        return com.sorrowmist.useless.utils.UselessItemUtils.findTargetToolInHands(player)
                .map(java.util.AbstractMap.SimpleImmutableEntry::getKey)
                .orElse(ItemStack.EMPTY);
    }
}
