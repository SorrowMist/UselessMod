package com.sorrowmist.useless.compat.lychee;

import com.google.gson.JsonElement;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import snownee.lychee.mixin.BlockPredicateAccess;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

/** Reads the complete native 1.20.1 predicate, not Lychee's viewer/showcase block lists. */
public final class LycheeBlockPredicates {
    private LycheeBlockPredicates() {
    }

    /** The anvil mold cannot represent a landing position, block state or block entity. */
    public static boolean isUnrestricted(@Nullable BlockPredicate predicate) {
        Constraints constraints = itemIndependentConstraints(predicate);
        return constraints != null && constraints.blocks() == null && constraints.tag() == null;
    }

    /**
     * Preserve the fixed regular-anvil workstation projection without widening the falling filter.
     * Both a block list and a tag are conjunctive; tags are resolved against the current registry
     * each time, rather than assuming that even minecraft:anvil still contains the regular anvil.
     * State/NBT requirements are refused even if one showcase/default state happens to match.
     */
    public static boolean acceptsRegularAnvil(@Nullable BlockPredicate predicate) {
        Constraints constraints = itemIndependentConstraints(predicate);
        return constraints != null
                && (constraints.blocks() == null || constraints.blocks().contains(Blocks.ANVIL))
                && (constraints.tag() == null || Blocks.ANVIL.defaultBlockState().is(constraints.tag()));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Constraints itemIndependentConstraints(@Nullable BlockPredicate predicate) {
        if (predicate == null || predicate.getClass() != BlockPredicate.class) {
            return null;
        }
        TagKey<Block> tag;
        Set<Block> blocks;
        StatePropertiesPredicate properties;
        NbtPredicate nbt;
        if (predicate instanceof BlockPredicateAccess access) {
            // This accessor is shipped by the pinned Lychee 5.2.1 jar.
            tag = access.getTag();
            blocks = access.getBlocks();
            properties = access.getProperties();
            nbt = access.getNbt();
        } else {
            // Plain native unit tests have no mixin transformer. Read the same four native fields
            // by unique field TYPE, not dev/obfuscated names, without initializing any registries.
            // If the layout/access policy changes, refuse rather than falling back to a showcase.
            try {
                tag = nativeField(predicate, TagKey.class);
                blocks = nativeField(predicate, Set.class);
                properties = nativeField(predicate, StatePropertiesPredicate.class);
                nbt = nativeField(predicate, NbtPredicate.class);
            } catch (ReflectiveOperationException | RuntimeException inaccessible) {
                return null;
            }
        }
        // Even nbt="{}" requires block entity data. Also, new NbtPredicate(null) serializes to
        // JSON null like ANY but is NOT ANY: Lychee still requires a non-null block-entity tag.
        if (nbt != NbtPredicate.ANY || properties == null
                || properties.getClass() != StatePropertiesPredicate.class) {
            return null;
        }
        JsonElement state = properties.serializeToJson();
        if (!state.isJsonNull() && (!state.isJsonObject() || !state.getAsJsonObject().entrySet().isEmpty())) {
            return null;
        }
        return new Constraints(tag, blocks);
    }

    @Nullable
    private static <T> T nativeField(BlockPredicate predicate, Class<T> type) throws ReflectiveOperationException {
        Field found = null;
        for (Field field : BlockPredicate.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                if (found != null) {
                    throw new ReflectiveOperationException("Ambiguous native BlockPredicate field type: " + type);
                }
                found = field;
            }
        }
        if (found == null || !found.trySetAccessible()) {
            throw new ReflectiveOperationException("Inaccessible native BlockPredicate field type: " + type);
        }
        return type.cast(found.get(predicate));
    }

    private record Constraints(@Nullable TagKey<Block> tag, @Nullable Set<Block> blocks) {
    }
}
