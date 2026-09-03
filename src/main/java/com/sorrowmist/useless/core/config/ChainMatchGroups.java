package com.sorrowmist.useless.core.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 连锁挖掘等价组集合。
 * 配置列表中的每一条目就是一个独立的等价组，复用 {@link BlockBlacklistMatcher}
 * 解析精确方块ID、{@code #方块标签} 和 {@code *} 通配符。
 */
final class ChainMatchGroups {
    private static final ChainMatchGroups EMPTY = new ChainMatchGroups(List.of());

    private final List<BlockBlacklistMatcher> groups;

    static ChainMatchGroups empty() {
        return EMPTY;
    }

    ChainMatchGroups(List<? extends String> entries) {
        List<BlockBlacklistMatcher> parsed = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                BlockBlacklistMatcher matcher = new BlockBlacklistMatcher(
                        List.of(entry == null ? "" : entry),
                        "chain mining equivalent group '" + entry + "'");
                if (!matcher.isEmpty()) {
                    parsed.add(matcher);
                }
            }
        }
        this.groups = List.copyOf(parsed);
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
