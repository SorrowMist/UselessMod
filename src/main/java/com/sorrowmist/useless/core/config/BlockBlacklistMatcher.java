package com.sorrowmist.useless.core.config;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class BlockBlacklistMatcher {
    private final Set<ResourceLocation> exactIds;
    private final List<Pattern> wildcardPatterns;
    private final List<TagKey<Block>> tags;
    private final BiPredicate<Block, TagKey<Block>> tagMatcher;
    private final String listName;

    static BlockBlacklistMatcher empty() {
        return empty("blacklist");
    }

    static BlockBlacklistMatcher empty(String listName) {
        return new BlockBlacklistMatcher(List.of(), listName);
    }

    BlockBlacklistMatcher(List<? extends String> entries) {
        this(entries, (block, tag) -> block.defaultBlockState().is(tag));
    }

    BlockBlacklistMatcher(List<? extends String> entries, String listName) {
        this(entries, listName, (block, tag) -> block.defaultBlockState().is(tag));
    }

    BlockBlacklistMatcher(List<? extends String> entries,
                          BiPredicate<Block, TagKey<Block>> tagMatcher) {
        this(entries, "blacklist", tagMatcher);
    }

    BlockBlacklistMatcher(List<? extends String> entries,
                          String listName,
                          BiPredicate<Block, TagKey<Block>> tagMatcher) {
        Set<ResourceLocation> exact = new HashSet<>();
        List<Pattern> wildcards = new ArrayList<>();
        List<TagKey<Block>> parsedTags = new ArrayList<>();
        this.listName = listName;
        this.tagMatcher = tagMatcher;

        if (entries != null) {
            for (String entry : entries) {
                if (entry == null || entry.isBlank()) {
                    warn(entry, "empty entry");
                    continue;
                }

                if (entry.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                    if (tagId == null) {
                        warn(entry, "invalid block tag id");
                        continue;
                    }
                    TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
                    if (BuiltInRegistries.BLOCK.getTag(tag).isEmpty()) {
                        warn(entry, "unknown block tag");
                    }
                    parsedTags.add(tag);
                    continue;
                }

                if (entry.indexOf('*') >= 0) {
                    if (!isValidWildcard(entry)) {
                        warn(entry, "invalid wildcard pattern");
                        continue;
                    }
                    wildcards.add(compileWildcard(entry));
                    continue;
                }

                ResourceLocation blockId = ResourceLocation.tryParse(entry);
                if (blockId == null) {
                    warn(entry, "invalid block id");
                } else if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
                    warn(entry, "unknown block id");
                } else {
                    exact.add(blockId);
                }
            }
        }

        this.exactIds = Set.copyOf(exact);
        this.wildcardPatterns = List.copyOf(wildcards);
        this.tags = List.copyOf(parsedTags);
    }

    boolean isEmpty() {
        return exactIds.isEmpty() && wildcardPatterns.isEmpty() && tags.isEmpty();
    }

    boolean matches(ResourceLocation blockId) {
        if (blockId == null) return false;
        if (exactIds.contains(blockId)) return true;

        String id = blockId.toString();
        if (wildcardPatterns.stream().anyMatch(pattern -> pattern.matcher(id).matches())) {
            return true;
        }

        Block block = BuiltInRegistries.BLOCK.get(blockId);
        return tags.stream().anyMatch(tag -> tagMatcher.test(block, tag));
    }

    private static boolean isValidWildcard(String value) {
        return ResourceLocation.tryParse(value.replace("*", "wildcard")) != null;
    }

    private static Pattern compileWildcard(String value) {
        String regex = Arrays.stream(value.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return Pattern.compile(regex);
    }

    private void warn(String entry, String reason) {
        UselessMod.LOGGER.warn("Ignoring {} entry '{}': {}", listName, entry, reason);
    }
}
