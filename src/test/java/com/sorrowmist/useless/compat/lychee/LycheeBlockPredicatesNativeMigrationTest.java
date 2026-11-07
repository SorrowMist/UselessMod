package com.sorrowmist.useless.compat.lychee;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Native vanilla predicates work without Lychee's BlockPredicateAccess mixin in plain JUnit. */
class LycheeBlockPredicatesNativeMigrationTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void wildcardAndEmptyPredicatesAreUnrestrictedButAnEmptyBlockListIsNot() {
        for (BlockPredicate predicate : List.of(BlockPredicate.ANY, parse("{}"), parse("{\"state\":{}}"))) {
            assertTrue(LycheeBlockPredicates.isUnrestricted(predicate));
            assertTrue(LycheeBlockPredicates.acceptsRegularAnvil(predicate));
        }
        assertFalse(LycheeBlockPredicates.isUnrestricted(parse("{\"blocks\":[]}")));
        assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(parse("{\"blocks\":[]}")));
        assertFalse(LycheeBlockPredicates.isUnrestricted(null));
        assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(null));
    }

    @Test
    void onlyFallingPredicatesAdmittingTheRegularAnvilFitTheFixedWorkstation() {
        assertTrue(LycheeBlockPredicates.acceptsRegularAnvil(parse("{\"blocks\":[\"minecraft:anvil\"]}")));
        assertTrue(LycheeBlockPredicates.acceptsRegularAnvil(
                parse("{\"blocks\":[\"minecraft:sand\",\"minecraft:anvil\"]}")));
        for (String block : List.of("sand", "chipped_anvil", "damaged_anvil")) {
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(parse("{\"blocks\":[\"minecraft:" + block + "\"]}")));
        }
        assertFalse(LycheeBlockPredicates.isUnrestricted(parse("{\"blocks\":[\"minecraft:anvil\"]}")));
    }

    @Test
    void allStateAndNbtConstraintsIncludingEmptyNbtAreRefusedRatherThanDefaultStateProbed() {
        for (String json : List.of(
                "{\"blocks\":[\"minecraft:anvil\"],\"state\":{\"facing\":\"north\"}}",
                "{\"state\":{\"age\":{\"min\":\"0\",\"max\":\"3\"}}}",
                "{\"nbt\":\"{}\"}", "{\"nbt\":\"{owner:1b}\"}")) {
            BlockPredicate predicate = parse(json);
            assertFalse(LycheeBlockPredicates.isUnrestricted(predicate), json);
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(predicate), json);
        }
    }

    @Test
    void nbtPredicateIdentityCannotBeRecoveredFromItsNullJsonRepresentation() {
        NbtPredicate exists = new NbtPredicate(null);
        assertNotSame(NbtPredicate.ANY, exists);
        assertTrue(exists.serializeToJson().isJsonNull());
        assertFalse(exists.matches((net.minecraft.nbt.Tag) null));
        BlockPredicate predicate = new BlockPredicate(null, null, StatePropertiesPredicate.ANY, exists);
        assertTrue(predicate.serializeToJson().getAsJsonObject().get("nbt").isJsonNull());
        assertFalse(LycheeBlockPredicates.isUnrestricted(predicate));
        assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(predicate));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blockListAndTagAreConjunctiveAndCurrentTagMembershipIsNeverAssumedOrCached() {
        Map<TagKey<Block>, List<Holder<Block>>> original = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.getTags().forEach(pair ->
                original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        Map<TagKey<Block>, List<Holder<Block>>> bound = new LinkedHashMap<>(original);
        BlockPredicate tagged = BlockPredicate.Builder.block().of(BlockTags.ANVIL).build();
        BlockPredicate intersection = parse("{\"blocks\":[\"minecraft:anvil\"],\"tag\":\"minecraft:anvil\"}");
        BlockPredicate disjoint = parse("{\"blocks\":[\"minecraft:sand\"],\"tag\":\"minecraft:anvil\"}");
        try {
            bound.put(BlockTags.ANVIL, List.of(Blocks.ANVIL.builtInRegistryHolder()));
            BuiltInRegistries.BLOCK.bindTags(bound);
            assertTrue(LycheeBlockPredicates.acceptsRegularAnvil(tagged));
            assertTrue(LycheeBlockPredicates.acceptsRegularAnvil(intersection));
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(disjoint));
            assertFalse(LycheeBlockPredicates.isUnrestricted(tagged));
            TagKey<Block> wrongRegistry = (TagKey<Block>) (TagKey<?>) TagKey.create(Registries.ITEM, BlockTags.ANVIL.location());
            BlockPredicate wrongKey = new BlockPredicate(wrongRegistry, null, StatePropertiesPredicate.ANY, NbtPredicate.ANY);
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(wrongKey),
                    "rebuilding a tag from only its serialized location would widen this predicate");

            bound.put(BlockTags.ANVIL, List.of(Blocks.CHIPPED_ANVIL.builtInRegistryHolder()));
            BuiltInRegistries.BLOCK.bindTags(bound);
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(tagged));
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(intersection));
            assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(parse("{\"tag\":\"useless_mod_test:missing\"}")));
        } finally {
            // bindTags does not clear previously bound tags that are absent from its argument.
            // Explicitly empty our fixture tag before restoring the caller's complete bindings.
            if (!original.containsKey(BlockTags.ANVIL)) original.put(BlockTags.ANVIL, List.of());
            BuiltInRegistries.BLOCK.bindTags(original);
        }
    }

    @Test
    void aCustomPredicateCannotAdvertiseAnUnconstrainedSerializationAndBypassChecks() {
        BlockPredicate custom = new BlockPredicate(null, null, StatePropertiesPredicate.ANY, NbtPredicate.ANY) { };
        assertFalse(LycheeBlockPredicates.isUnrestricted(custom));
        assertFalse(LycheeBlockPredicates.acceptsRegularAnvil(custom));
    }

    private static BlockPredicate parse(String json) {
        return BlockPredicate.fromJson(JsonParser.parseString(json));
    }
}
