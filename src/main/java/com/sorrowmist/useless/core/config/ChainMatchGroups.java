package com.sorrowmist.useless.core.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 连锁挖掘等价组集合。
 * 每个「组」是一个条目列表，组内条目复用 {@link BlockBlacklistMatcher} 解析
 * 精确方块ID、{@code #方块标签} 和 {@code *} 通配符；命中同一个组的方块互相连锁。
 *
 * <p>组由玩家在「模式轮盘 → 连锁挖掘 → 齿轮」界面里编辑，存在玩家个人的
 * {@code BeefToolLayout} 里，见 {@link ChainGroupManager}。</p>
 */
final class ChainMatchGroups {
    private static final ChainMatchGroups EMPTY = new ChainMatchGroups(List.of());

    private final List<BlockBlacklistMatcher> groups;

    static ChainMatchGroups empty() {
        return EMPTY;
    }

    private ChainMatchGroups(List<BlockBlacklistMatcher> groups) {
        this.groups = groups;
    }

    /**
     * 由「组 → 条目」的嵌套列表构造。空组与解析后为空的组直接丢弃。
     */
    static ChainMatchGroups ofGroups(List<List<String>> groupEntries) {
        if (groupEntries == null || groupEntries.isEmpty()) {
            return EMPTY;
        }

        List<BlockBlacklistMatcher> parsed = new ArrayList<>();
        for (int i = 0; i < groupEntries.size(); i++) {
            List<String> entries = groupEntries.get(i);
            BlockBlacklistMatcher matcher = new BlockBlacklistMatcher(
                    entries == null ? List.of() : entries,
                    "chain mining equivalent group #" + (i + 1));
            if (!matcher.isEmpty()) {
                parsed.add(matcher);
            }
        }
        return parsed.isEmpty() ? EMPTY : new ChainMatchGroups(List.copyOf(parsed));
    }

    /**
     * 挑出原点方块命中的等价组。一条都不命中时返回的判定退回严格同方块匹配。
     */
    ChainEquivalence forOrigin(Block origin) {
        if (this.groups.isEmpty()) {
            return new ChainEquivalence(origin, List.of());
        }

        ResourceLocation originId = BuiltInRegistries.BLOCK.getKey(origin);
        List<BlockBlacklistMatcher> hit = new ArrayList<>();
        for (BlockBlacklistMatcher group : this.groups) {
            if (group.matches(originId)) {
                hit.add(group);
            }
        }
        return new ChainEquivalence(origin, List.copyOf(hit));
    }
}
