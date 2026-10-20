package com.sorrowmist.useless.core.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 造化杖飞行授予标记。
 * <p>
 * 标记写在玩家的持久化数据里，而不是内存集合里。原因：{@code Abilities.mayfly} 本身会被写进
 * 玩家 NBT。若「该飞行由模组授予」只记录在内存中，则服务器重启或单人存档重载后该标记即丢失，
 * 玩家离线期间保留的 mayfly 无法回收，表现为未持有造化杖仍可永久创造飞行。
 */
public final class FlyEffectedHolder {
    private static final String ROOT_TAG = "useless_mod:beef_tool_flight";
    private static final String GRANTED_TAG = "granted";

    private FlyEffectedHolder() {} // 防止实例化

    /** 标记该玩家的飞行由造化杖授予，返回是否为新标记。 */
    public static boolean add(Player player) {
        CompoundTag data = getOrCreate(player);
        if (data.getBoolean(GRANTED_TAG)) {
            return false;
        }
        data.putBoolean(GRANTED_TAG, true);
        return true;
    }

    /** 该玩家的飞行当前是否由造化杖授予（跨存档重载有效）。 */
    public static boolean contains(Player player) {
        return get(player).getBoolean(GRANTED_TAG);
    }

    /** 清除授予标记，返回此前是否处于「由造化杖授予」状态。 */
    public static boolean remove(Player player) {
        CompoundTag data = get(player);
        if (!data.getBoolean(GRANTED_TAG)) {
            return false;
        }
        data.remove(GRANTED_TAG);
        return true;
    }

    private static CompoundTag get(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        CompoundTag playerData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        return playerData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? playerData.getCompound(ROOT_TAG)
                : new CompoundTag();
    }

    private static CompoundTag getOrCreate(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }

        CompoundTag playerData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!playerData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            playerData.put(ROOT_TAG, new CompoundTag());
        }
        return playerData.getCompound(ROOT_TAG);
    }
}
