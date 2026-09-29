package com.sorrowmist.useless.core.config;

import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.data.BeefToolLayoutManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生物保护名单的读取入口。
 *
 * <p>保护名单是<b>玩家个人设置</b>，与连锁挖掘等价组、造化杖布局存在同一份 persistent data 里
 * （见 {@link BeefToolLayout#protectedTypes()} / {@link BeefToolLayout#protectedEntities()}），
 * 因此随布局一起校验、同步、导入导出。</p>
 *
 * <p>本类同时承担两端的一致性职责：服务端从玩家存档读（按 UUID 缓存解析结果），
 * 客户端从 {@code BeefToolLayoutSyncPacket} 下发的快照读（{@link #setClientMirror}），
 * 后者只用于 tooltip 里的「已保护 N 种 / N 只」计数。</p>
 */
public final class BeefToolProtectionManager {

    /** 解析后的名单：生物种类 ID 集合 + 生物个体 UUID 集合。 */
    public record Protection(Set<String> types, Set<UUID> entities) {
        public static final Protection EMPTY = new Protection(Set.of(), Set.of());

        public boolean isEmpty() {
            return types.isEmpty() && entities.isEmpty();
        }
    }

    private static final Map<UUID, Protection> SERVER_CACHE = new ConcurrentHashMap<>();
    private static volatile Protection clientMirror = Protection.EMPTY;

    private BeefToolProtectionManager() {
    }

    /**
     * 判定用入口：客户端返回镜像，服务端返回按玩家 UUID 缓存的解析结果。
     *
     * <p>存档缺失或解析失败一律返回空名单（等价于「没有任何手动保护」），
     * 与 {@link BeefToolLayoutManager#load} 的只读语义一致，不在读路径上产生写副作用。</p>
     */
    public static Protection protectionFor(@Nullable Player player) {
        if (player == null) {
            return Protection.EMPTY;
        }
        if (player.level().isClientSide()) {
            return clientMirror;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return SERVER_CACHE.computeIfAbsent(serverPlayer.getUUID(), id -> {
                BeefToolLayout layout = BeefToolLayoutManager.load(serverPlayer);
                return layout == null
                        ? Protection.EMPTY
                        : ofLists(layout.protectedTypes(), layout.protectedEntities());
            });
        }
        return Protection.EMPTY;
    }

    /** tooltip 用：客户端镜像（只读）。 */
    public static Protection clientProtection() {
        return clientMirror;
    }

    /** 玩家改过名单或改过布局后调用，丢弃其解析缓存。 */
    public static void invalidate(UUID playerId) {
        SERVER_CACHE.remove(playerId);
    }

    /** 客户端收到服务端下发的布局快照后刷新镜像。 */
    public static void setClientMirror(List<String> types, List<String> entities) {
        clientMirror = ofLists(types, entities);
    }

    /** 服务端停止 / 客户端登出时清空，避免跨存档残留。 */
    public static void clearAll() {
        SERVER_CACHE.clear();
        clientMirror = Protection.EMPTY;
    }

    private static Protection ofLists(List<String> types, List<String> entities) {
        Set<String> typeSet = new HashSet<>();
        if (types != null) {
            for (String type : types) {
                if (type != null && !type.isBlank()) {
                    typeSet.add(type);
                }
            }
        }
        Set<UUID> entitySet = new HashSet<>();
        if (entities != null) {
            for (String raw : entities) {
                if (raw == null) {
                    continue;
                }
                try {
                    entitySet.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // 存档里出现不可解析的 UUID 时静默忽略，不影响其它条目
                }
            }
        }
        return new Protection(Set.copyOf(typeSet), Set.copyOf(entitySet));
    }

    /**
     * 保存前语义校验：生物种类 ID 需是合法的 {@link ResourceLocation} 语法
     * （<b>不查实体注册表</b>，与 {@link ChainGroupManager#validateEntries} 的宽容语义一致），
     * 个体条目需是可解析的 UUID。
     */
    public static void validateEntries(List<String> types, List<String> entities)
            throws BeefToolLayout.LayoutException {
        if (types != null) {
            if (types.size() > BeefToolLayout.MAX_PROTECTED_TYPES) {
                throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.LIMIT);
            }
            for (String type : types) {
                if (type == null || ResourceLocation.tryParse(type) == null) {
                    throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.INVALID_STRUCTURE);
                }
            }
        }
        if (entities != null) {
            if (entities.size() > BeefToolLayout.MAX_PROTECTED_ENTITIES) {
                throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.LIMIT);
            }
            for (String raw : entities) {
                if (raw == null) {
                    throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.INVALID_STRUCTURE);
                }
                try {
                    UUID.fromString(raw);
                } catch (IllegalArgumentException exception) {
                    throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.INVALID_STRUCTURE);
                }
            }
        }
    }
}
