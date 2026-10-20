package com.sorrowmist.useless.data;

import com.sorrowmist.useless.core.config.BeefToolProtectionManager;
import com.sorrowmist.useless.core.config.ChainGroupManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Loads and stores the beef tool layout in a player's persistent save data. */
public final class BeefToolLayoutManager {
    private static final String ROOT_TAG = "useless_mod:beef_tool_layout";
    private static final String VANILLA_GROUP_MIGRATION_TAG = "useless_mod:beef_tool_vanilla_group";
    private static final String EXDEORUM_GROUP_MIGRATION_TAG = "useless_mod:beef_tool_exdeorum_group";

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
        migrateVanillaModulesOnce(player, layout);
        migrateExDeorumModulesOnce(player, layout);
        BeefToolModuleRegistry.addMissingAvailableModules(layout, findTarget(player));
        if (!layout.pages().isEmpty()) {
            layout.setSelectedPage(Math.max(0,
                    Math.min(layout.selectedPage(), layout.pages().size() - 1)));
        }
        return layout;
    }

    /**
     * 首次加载既有存档时，把归在其它分组的原版工具动作迁入「原版」分组。
     *
     * <p>迁移结果写入持久标记，每个玩家只执行一次，
     * 以免覆盖玩家此后在模式配置界面中对分组的调整。
     */
    private static void migrateVanillaModulesOnce(ServerPlayer player, BeefToolLayout layout) {
        CompoundTag playerData = playerData(player);
        if (playerData != null && playerData.getBoolean(VANILLA_GROUP_MIGRATION_TAG)) {
            return;
        }
        BeefToolModuleRegistry.migrateVanillaModules(layout);
        setFlag(player, VANILLA_GROUP_MIGRATION_TAG);
    }

    /**
     * 首次加载既有存档时，把 Ex Deorum 工具模式迁入其专属分组。
     *
     * <p>三项在旧版本中归入「挖掘」分组并已随存档落盘，补缺逻辑只处理布局中
     * 尚不存在的模块，因此需要显式迁移；迁移结果同样写入持久标记，每个玩家只执行一次。</p>
     */
    private static void migrateExDeorumModulesOnce(ServerPlayer player, BeefToolLayout layout) {
        CompoundTag playerData = playerData(player);
        if (playerData != null && playerData.getBoolean(EXDEORUM_GROUP_MIGRATION_TAG)) {
            return;
        }
        BeefToolModuleRegistry.migrateExDeorumModules(layout);
        setFlag(player, EXDEORUM_GROUP_MIGRATION_TAG);
    }

    public static void validateForSave(BeefToolLayout layout) throws BeefToolLayout.LayoutException {
        layout.validate();
        validateKnownModules(layout);
        ChainGroupManager.validateEntries(layout.chainGroups());
        BeefToolProtectionManager.validateEntries(layout.protectedTypes(), layout.protectedEntities());
    }

    /**
     * 读取玩家已有的布局；不存在或解析失败返回 {@code null}（不创建、不写回）。
     */
    public static BeefToolLayout load(ServerPlayer player) {
        CompoundTag root = get(player);
        if (root == null) {
            return null;
        }
        try {
            return BeefToolLayout.fromNbt(root);
        } catch (BeefToolLayout.LayoutException exception) {
            return null;
        }
    }

    /**
     * 只读地取出玩家的连锁等价组：不 normalize、不写回。
     *
     * <p>供连锁判定的缓存未命中路径使用，避免在读路径上产生写副作用。
     * 存档缺失或解析失败一律返回空列表（等价于「没有任何等价组」）。</p>
     */
    public static List<List<String>> chainGroups(ServerPlayer player) {
        BeefToolLayout layout = load(player);
        return layout == null ? List.of() : layout.chainGroups();
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

    private static CompoundTag playerData(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return persistentData.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag get(ServerPlayer player) {
        CompoundTag playerData = playerData(player);
        return playerData != null && playerData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? playerData.getCompound(ROOT_TAG)
                : null;
    }

    private static void setFlag(ServerPlayer player, String key) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        persistentData.getCompound(Player.PERSISTED_NBT_TAG).putBoolean(key, true);
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
