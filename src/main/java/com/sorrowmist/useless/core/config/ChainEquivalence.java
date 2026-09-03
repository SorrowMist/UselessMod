package com.sorrowmist.useless.core.config;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 连锁挖掘的"同类方块"判定。
 * 由 {@link ConfigManager#getChainMiningEquivalence(Block)} 在每次扫描前构造一次，扫描结束即丢弃。
 *
 * <p>原点方块命中的等价组决定判定范围：命中多条取并集，一条都不命中时退回严格同方块匹配。
 * 单次扫描内用 memo 缓存每种方块的判定结果——增强连锁一次可扫十几万格，
 * 但其中不同的方块种类只有几十个，热循环因此仍是一次哈希查找。
 * memo 只活在一次扫描内，数据包重载标签后下一次扫描立刻生效。
 */
public final class ChainEquivalence {
    private final Block origin;
    private final List<BlockBlacklistMatcher> hitGroups;
    private final Object2BooleanMap<Block> memo;

    ChainEquivalence(Block origin, List<BlockBlacklistMatcher> hitGroups) {
        this.origin = origin;
        this.hitGroups = hitGroups;
        this.memo = hitGroups.isEmpty() ? null : new Object2BooleanOpenHashMap<>();
    }

    /**
     * 判断目标方块状态是否与原点方块属于同类，可被一起连锁。
     */
    public boolean matches(BlockState state) {
        Block block = state.getBlock();
        if (block == this.origin) return true;
        if (this.hitGroups.isEmpty()) return false;
        if (this.memo.containsKey(block)) {
            return this.memo.getBoolean(block);
        }
        boolean result = testGroups(block);
        this.memo.put(block, result);
        return result;
    }

    private boolean testGroups(Block block) {
        var blockId = BuiltInRegistries.BLOCK.getKey(block);
        return this.hitGroups.stream().anyMatch(group -> group.matches(blockId));
    }
}
