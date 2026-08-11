package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniversalPatternTagInputTest {
    private static final TagKey<Item> TEST_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("useless_mod_test", "omniversal_tag_input"));
    private static Map<TagKey<Item>, List<Holder<Item>>> originalTags;

    @BeforeAll
    static void bootstrapAndBindTestTag() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalTags = BuiltInRegistries.ITEM.getTags().collect(Collectors.toUnmodifiableMap(
                Pair::getFirst,
                pair -> StreamSupport.stream(pair.getSecond().spliterator(), false).toList()));
        BuiltInRegistries.ITEM.bindTags(Map.of(TEST_TAG, List.of(holder(Items.IRON_INGOT), holder(Items.GOLD_INGOT))));
    }

    @AfterAll
    static void restoreItemTags() {
        if (originalTags != null) {
            BuiltInRegistries.ITEM.bindTags(originalTags);
        }
    }

    @Test
    void recipeNetworkRoundTripRetainsTagInputsCatalystAndMold() {
        Ingredient tag = Ingredient.of(TEST_TAG);
        AdvancedAlloyFurnaceRecipe expected = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "tag_network"),
                List.of(new CountedIngredient(tag, 2L)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.NETHER_STAR)),
                List.of(),
                List.of(),
                100L,
                20,
                tag,
                3,
                List.of(tag),
                AlloyFurnaceMode.NORMAL);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.OTHER);

        try {
            AdvancedAlloyFurnaceRecipe.STREAM_CODEC.encode(buffer, expected);
            AdvancedAlloyFurnaceRecipe decoded = AdvancedAlloyFurnaceRecipe.STREAM_CODEC.decode(buffer);

            assertTagIngredient(decoded.inputs().getFirst().ingredient());
            assertTagIngredient(decoded.catalyst());
            assertTagIngredient(decoded.molds().getFirst());
        } finally {
            buffer.release();
        }
    }

    @Test
    void patternDataTagSlotsRoundTripThroughJsonAndNetwork() {
        OmniversalPatternData expected = new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "tag_data"),
                "fingerprint",
                false,
                Optional.empty(),
                List.of(),
                List.of(new OmniversalPatternData.TagInputSlot(0, TEST_TAG)),
                List.of(0),
                List.of());

        var encoded = OmniversalPatternData.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        assertEquals(expected, OmniversalPatternData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.OTHER);
        try {
            OmniversalPatternData.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, OmniversalPatternData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void onlyPureTagIngredientsBecomeTagSlots() {
        AEProcessingPattern source = processingPattern(Items.IRON_INGOT);
        AdvancedAlloyFurnaceRecipe pureTag = recipe(Ingredient.of(TEST_TAG));
        Ingredient mixed = Ingredient.fromValues(Stream.of(
                new Ingredient.TagValue(TEST_TAG),
                new Ingredient.ItemValue(new ItemStack(Items.IRON_INGOT))));

        assertEquals(Map.of(0, List.of(TEST_TAG)),
                OmniversalPatternEncoding.resolveTagInputSlots(pureTag, source));
        assertTrue(OmniversalPatternEncoding.resolveTagInputSlots(recipe(mixed), source).isEmpty());
        assertTrue(OmniversalPatternEncoding.resolveTagInputSlots(
                recipe(Ingredient.of(Items.IRON_INGOT)), source).isEmpty());
    }

    @Test
    void tagInputAcceptsAnotherMemberButRejectsNonMembers() {
        DynamicComponentPatternDetails pattern = tagPattern();
        IPatternDetails.IInput input = pattern.getInputs()[0];

        assertTrue(input.isValid(key(Items.GOLD_INGOT), null));
        assertFalse(input.isValid(key(Items.DIAMOND), null));
    }

    @Test
    void passiveExtractionUsesAnotherMemberFromTheNetwork() {
        DynamicComponentPatternDetails pattern = tagPattern();
        AEItemKey gold = key(Items.GOLD_INGOT);
        TestStorage storage = new TestStorage(gold, 1L);

        PassivePatternInputTransaction.Result result = PassivePatternInputTransaction.extract(
                pattern,
                1L,
                null,
                storage,
                storage::cachedInventory,
                appeng.api.networking.security.IActionSource.empty(),
                (key, amount) -> { });

        assertEquals(PassivePatternInputTransaction.Failure.NONE, result.failure());
        assertEquals(1L, result.inputs()[0].get(gold));
        assertEquals(0L, storage.amount(gold));
    }

    private static DynamicComponentPatternDetails tagPattern() {
        AEProcessingPattern source = processingPattern(Items.IRON_INGOT);
        return new DynamicComponentPatternDetails(
                source,
                List.of(),
                List.of(),
                Map.of(0, List.of(TEST_TAG)),
                Map.of(),
                RegistryAccess.EMPTY);
    }

    private static AEProcessingPattern processingPattern(Item item) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(key(item), 1L)),
                List.of(new GenericStack(key(Items.NETHER_STAR), 1L)));
        return new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(encoded)));
    }

    private static AdvancedAlloyFurnaceRecipe recipe(Ingredient input) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "tag_slot"),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.NETHER_STAR)),
                List.of(),
                List.of(),
                1L,
                20,
                Ingredient.EMPTY,
                0,
                List.of(),
                AlloyFurnaceMode.NORMAL);
    }

    private static AEItemKey key(Item item) {
        return Objects.requireNonNull(AEItemKey.of(new ItemStack(item)));
    }

    private static Holder<Item> holder(Item item) {
        ResourceLocation id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item));
        return BuiltInRegistries.ITEM.getHolderOrThrow(ResourceKey.create(Registries.ITEM, id));
    }

    private static void assertTagIngredient(Ingredient ingredient) {
        assertEquals(1, ingredient.getValues().length);
        assertTrue(ingredient.getValues()[0] instanceof Ingredient.TagValue);
        assertEquals(TEST_TAG, ((Ingredient.TagValue) ingredient.getValues()[0]).tag());
    }

    private static final class TestStorage implements appeng.api.storage.MEStorage {
        private final Map<AEItemKey, Long> contents;

        private TestStorage(AEItemKey key, long amount) {
            contents = new java.util.LinkedHashMap<>();
            contents.put(key, amount);
        }

        @Override
        public long insert(appeng.api.stacks.AEKey what, long amount,
                           appeng.api.config.Actionable mode,
                           appeng.api.networking.security.IActionSource source) {
            if (mode == appeng.api.config.Actionable.MODULATE && what instanceof AEItemKey itemKey) {
                contents.merge(itemKey, amount, Long::sum);
            }
            return amount;
        }

        @Override
        public long extract(appeng.api.stacks.AEKey what, long amount,
                            appeng.api.config.Actionable mode,
                            appeng.api.networking.security.IActionSource source) {
            if (!(what instanceof AEItemKey itemKey)) return 0L;
            long extracted = Math.min(amount, contents.getOrDefault(itemKey, 0L));
            if (mode == appeng.api.config.Actionable.MODULATE) {
                contents.put(itemKey, contents.getOrDefault(itemKey, 0L) - extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(appeng.api.stacks.KeyCounter out) {
            contents.forEach(out::add);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("tag test storage");
        }

        private long amount(AEItemKey key) {
            return contents.getOrDefault(key, 0L);
        }

        private appeng.api.stacks.KeyCounter cachedInventory() {
            appeng.api.stacks.KeyCounter result = new appeng.api.stacks.KeyCounter();
            contents.forEach(result::add);
            return result;
        }
    }
}
