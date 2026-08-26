package com.sorrowmist.useless.core.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBlacklistMatcherTest {
    @Test
    void exactEntriesOnlyMatchTheSameBlockId() {
        BlockBlacklistMatcher matcher = new BlockBlacklistMatcher(List.of("minecraft:stone"));

        assertTrue(matcher.matches(ResourceLocation.withDefaultNamespace("stone")));
        assertFalse(matcher.matches(ResourceLocation.withDefaultNamespace("stone_bricks")));
    }

    @Test
    void wildcardEntriesMatchTheFullCanonicalId() {
        BlockBlacklistMatcher matcher = new BlockBlacklistMatcher(
                List.of("minecraft:*_ore", "*:raw_*"));

        assertTrue(matcher.matches(ResourceLocation.withDefaultNamespace("iron_ore")));
        assertTrue(matcher.matches(ResourceLocation.fromNamespaceAndPath("create", "raw_zinc")));
        assertFalse(matcher.matches(ResourceLocation.withDefaultNamespace("stone_bricks")));
    }

    @Test
    void blockTagEntriesMatchTaggedBlocks() {
        BlockBlacklistMatcher matcher = new BlockBlacklistMatcher(List.of("#minecraft:logs"),
                (block, tag) -> block == Blocks.OAK_LOG && tag.equals(BlockTags.LOGS));

        assertTrue(matcher.matches(ResourceLocation.withDefaultNamespace("oak_log")));
        assertFalse(matcher.matches(ResourceLocation.withDefaultNamespace("stone")));
    }

    @Test
    void invalidEntriesAreIgnored() {
        BlockBlacklistMatcher matcher = new BlockBlacklistMatcher(
                List.of("ore", "minecraft:stone?", "#minecraft:not_a_real_tag"));

        assertFalse(matcher.matches(ResourceLocation.withDefaultNamespace("stone")));
        assertFalse(matcher.matches(ResourceLocation.withDefaultNamespace("iron_ore")));
    }
}
