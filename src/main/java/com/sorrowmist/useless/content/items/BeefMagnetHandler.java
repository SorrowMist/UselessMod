package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 击杀实体后的范围磁力吸附。
 *
 * <p>把击杀点周围配置范围内的掉落物和经验球全部收给玩家。掉落物走
 * {@link MiningUtils#handleDrops(Player, List, ItemStack)}，因此自动继承工具上的
 * "AE存储优先" 开关：绑定了无线接入点且开关打开时先存入 AE 网络，塞不下才进背包。</p>
 *
 * <p>吸附不依赖 {@code LivingDeathEvent}：强制击杀的 {@code forceDie} 分支和非生物实体的击杀
 * 都不会触发该事件，所以调用方在击杀后主动调用 {@link #scheduleSweep} 登记一次扫描。同一 tick 内
 * 的多次登记会合并成一个包围盒，AoE 一次打死几十只怪也只扫一遍。</p>
 */
public final class BeefMagnetHandler {
    /** 每个玩家最多有一个待执行的扫描，键为玩家 UUID。仅在服务端主线程访问。 */
    private static final Map<UUID, PendingSweep> PENDING = new ConcurrentHashMap<>();

    private BeefMagnetHandler() {
    }

    public static boolean isEnabled(ItemStack tool) {
        return tool.getOrDefault(UComponents.BeefMagnetEnabledComponent, false);
    }

    /**
     * 实体被击杀时调用，用配置的磁力半径扫描死亡点周围。
     *
     * @param level    实体所在世界
     * @param player   击杀者
     * @param tool     击杀所用的工具
     * @param deathPos 死亡位置（范围中心）
     */
    public static void onEntityKilled(ServerLevel level, Player player, ItemStack tool, Vec3 deathPos) {
        scheduleSweep(level, player, tool, deathPos,
                ConfigManager.getBeefMagnetRangeX(),
                ConfigManager.getBeefMagnetRangeY(),
                ConfigManager.getBeefMagnetRangeZ());
    }

    /**
     * 登记一次范围吸附。
     *
     * <p>掉落物与经验球是在 {@code LivingEntity#die} 里、死亡事件之后才生成的，所以这里延后一 tick
     * 再扫描。同一玩家、同一 tick 的重复登记会把包围盒并起来而不是排队多次扫描。</p>
     *
     * @param center 范围中心
     * @param rangeX X 轴半径
     * @param rangeY Y 轴半径
     * @param rangeZ Z 轴半径
     */
    public static void scheduleSweep(ServerLevel level, Player player, ItemStack tool, Vec3 center,
                                     double rangeX, double rangeY, double rangeZ) {
        if (!isEnabled(tool)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        AABB box = AABB.ofSize(center, rangeX * 2 + 1, rangeY * 2 + 1, rangeZ * 2 + 1);
        int targetTick = server.getTickCount() + 1;
        UUID playerId = player.getUUID();

        PendingSweep pending = PENDING.get(playerId);
        if (pending != null && pending.tick == targetTick && pending.level == level) {
            // 同一 tick 内的其它击杀：并入已登记的范围，避免重复扫描
            pending.box = pending.box.minmax(box);
            return;
        }

        PendingSweep scheduled = new PendingSweep(targetTick, level, box);
        PENDING.put(playerId, scheduled);
        server.tell(new TickTask(targetTick, () -> {
            PENDING.remove(playerId, scheduled);
            sweep(scheduled.level, player, tool, scheduled.box);
        }));
    }

    private static void sweep(ServerLevel level, Player player, ItemStack tool, AABB area) {
        if (player.isRemoved() || !player.isAlive()) {
            return;
        }

        // 1. 掉落物
        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!itemEntity.isAlive() || itemEntity.getItem().isEmpty() || isThrownByPlayer(itemEntity, player)) {
                continue;
            }
            drops.add(itemEntity.getItem().copy());
            itemEntity.discard();
        }
        if (!drops.isEmpty()) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(drops), tool);
        }

        // 2. 经验球：每次清零拾取冷却，绕过原版"每2 tick 只能吃一个"的限制，
        //    同时保留经验修补等原版行为
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {
            if (!orb.isAlive()) {
                continue;
            }
            player.takeXpDelay = 0;
            orb.playerTouch(player);
        }
    }

    /**
     * 判断掉落物是不是玩家自己刚扔出来的，避免把主动丢弃的东西吸回背包。
     */
    private static boolean isThrownByPlayer(ItemEntity itemEntity, Player player) {
        return itemEntity.hasPickUpDelay() && itemEntity.getOwner() == player;
    }

    /** 一次待执行的扫描：目标 tick、世界和累积的包围盒。 */
    private static final class PendingSweep {
        private final int tick;
        private final ServerLevel level;
        private AABB box;

        private PendingSweep(int tick, ServerLevel level, AABB box) {
            this.tick = tick;
            this.level = level;
            this.box = box;
        }
    }
}
