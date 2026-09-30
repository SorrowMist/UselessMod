package com.sorrowmist.useless.core.config;

import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.data.BeefToolLayoutManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连锁挖掘等价组的读取入口。
 *
 * <p>等价组是<b>玩家个人设置</b>，与造化杖布局存在同一份 persistent data 里
 * （见 {@link BeefToolLayout#chainGroups()}），因此随布局一起校验、同步、导入导出。</p>
 *
 * <p>本类同时承担两端的一致性职责：服务端从玩家存档读，客户端从
 * {@code BeefToolLayoutSyncPacket} 下发的快照读（{@link #setClientMirror}）。
 * 右键连锁（剥皮/刮铜/去蜡/耕地）走的是 {@code Item#useOn}，两端都会执行并各自预测，
 * 若客户端拿不到等价组，客户端预测的范围会小于服务端实际破坏的范围。</p>
 *
 * <p>解析结果（正则已编译、TagKey 已建）按玩家缓存，连锁扫描的热路径只做一次哈希查找。</p>
 */
public final class ChainGroupManager {

    private static final Map<UUID, ChainMatchGroups> SERVER_CACHE = new ConcurrentHashMap<>();
    private static volatile ChainMatchGroups clientMirror = ChainMatchGroups.empty();

    private ChainGroupManager() {
    }

    /**
     * 取原点方块对应的等价判定。
     *
     * <p>{@code player} 为 null（或非服务端玩家的服务端上下文）时退回严格同方块匹配，
     * 与「没有任何等价组」的行为一致。</p>
     */
    public static ChainEquivalence equivalenceFor(@Nullable Player player, Block origin) {
        if (player == null) {
            return ChainMatchGroups.empty().forOrigin(origin);
        }
        if (player.level().isClientSide()) {
            return clientMirror.forOrigin(origin);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ChainMatchGroups groups = SERVER_CACHE.computeIfAbsent(serverPlayer.getUUID(),
                    id -> ChainMatchGroups.ofGroups(BeefToolLayoutManager.chainGroups(serverPlayer)));
            return groups.forOrigin(origin);
        }
        return ChainMatchGroups.empty().forOrigin(origin);
    }

    /** 玩家改过等价组后调用，丢弃其解析缓存。 */
    public static void invalidate(UUID playerId) {
        SERVER_CACHE.remove(playerId);
    }

    /** 客户端收到服务端下发的布局快照后刷新镜像。 */
    public static void setClientMirror(List<List<String>> chainGroups) {
        clientMirror = ChainMatchGroups.ofGroups(chainGroups);
    }

    /** 服务端停止（含单人存档退出）时清空，避免跨存档残留。 */
    public static void clearAll() {
        SERVER_CACHE.clear();
        clientMirror = ChainMatchGroups.empty();
    }

    /**
     * 校验等价组：数量上限 + 条目语法。
     *
     * <p>只校验<b>形式</b>是否合法。语法合法但当前解析不到的条目（未知方块 ID、
     * 尚未加载的标签、暂无匹配的通配符）允许保存——与 {@link BlockBlacklistMatcher}
     * 「warn 并忽略」的宽容语义一致，也不会因为某个模组没装就写不进去。</p>
     */
    public static void validateEntries(List<List<String>> chainGroups) throws BeefToolLayout.LayoutException {
        if (chainGroups == null) {
            return;
        }
        if (chainGroups.size() > BeefToolLayout.MAX_CHAIN_GROUPS) {
            throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.LIMIT);
        }

        int total = 0;
        for (List<String> group : chainGroups) {
            if (group == null || group.size() > BeefToolLayout.MAX_CHAIN_ENTRIES_PER_GROUP) {
                throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.LIMIT);
            }
            for (String entry : group) {
                if (!isValidEntrySyntax(entry)) {
                    throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.INVALID_STRUCTURE);
                }
                total++;
            }
        }
        if (total > BeefToolLayout.MAX_TOTAL_CHAIN_ENTRIES) {
            throw new BeefToolLayout.LayoutException(BeefToolLayout.Error.LIMIT);
        }
    }

    /**
     * 条目语法是否合法。
     *
     * <p>与 {@link BlockBlacklistMatcher} 的解析规则保持一致（精确 ID / {@code #标签} /
     * {@code *} 通配符），但<b>不查方块注册表</b>，因此未知方块 ID 也算语法合法。</p>
     */
    public static boolean isValidEntrySyntax(@Nullable String entry) {
        if (entry == null) {
            return false;
        }
        String trimmed = entry.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > BeefToolLayout.MAX_CHAIN_ENTRY_LENGTH
                || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return false;
        }
        if (trimmed.startsWith("#")) {
            return ResourceLocation.tryParse(trimmed.substring(1)) != null;
        }
        if (trimmed.indexOf('*') >= 0) {
            return ResourceLocation.tryParse(trimmed.replace("*", "wildcard")) != null;
        }
        return ResourceLocation.tryParse(trimmed) != null;
    }
}
