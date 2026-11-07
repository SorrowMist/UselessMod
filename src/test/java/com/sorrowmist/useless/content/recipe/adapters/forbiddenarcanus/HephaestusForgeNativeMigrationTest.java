package com.sorrowmist.useless.content.recipe.adapters.forbiddenarcanus;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forbiddenarcanus.ForbiddenArcanusRecipeCompatLoader;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.stal111.forbidden_arcanus.common.block.HephaestusForgeBlock;
import com.stal111.forbidden_arcanus.common.block.entity.forge.MagicCircle;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualRequirements;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.RitualResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.RitualResultType;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult;
import com.stal111.forbidden_arcanus.common.item.BlacksmithGavelItem;
import com.stal111.forbidden_arcanus.common.item.enhancer.EnhancerDefinition;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Constructs genuine 2.2.6 rituals/results/tools; no mock of the source recipe API. */
class HephaestusForgeNativeMigrationTest {
    private static Item forge;
    private static BlacksmithGavelItem gavel;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        forge = InventoryTestBootstrap.registerItem(new ResourceLocation("forbidden_arcanus", "hephaestus_forge"),
                () -> new Item(new Item.Properties()));
        gavel = InventoryTestBootstrap.registerItem("fa_native_gavel",
                () -> new BlacksmithGavelItem(Tiers.IRON, 1, -2.8F, 25, new Item.Properties()));
    }

    @Test
    void fixedOutputRetainsNativeNbtPredicatesCountsAndAllFourEssenceCosts() {
        ItemStack main = new ItemStack(Items.DIAMOND);
        main.getOrCreateTag().putString("owner", "main");
        ItemStack pedestal = new ItemStack(Items.IRON_INGOT);
        pedestal.getOrCreateTag().putString("grade", "ritual");
        Ingredient mainPredicate = StrictNBTIngredient.of(main);
        Ingredient pedestalPredicate = StrictNBTIngredient.of(pedestal);
        ItemStack output = new ItemStack(Items.EMERALD, 3);
        output.getOrCreateTag().putString("payload", "preserved");
        Ritual ritual = new Ritual(List.of(new RitualInput(pedestalPredicate, 3)), mainPredicate,
                new CreateItemResult(output), new EssencesDefinition(82, 1, 1000, 9), null, MagicCircle.Config.DEFAULT);
        ItemStack tool = new ItemStack(gavel);
        ItemStack untouched = tool.copy();

        AdvancedAlloyFurnaceRecipe converted = convert(ritual, tool, mold(1));
        assertSame(mainPredicate, converted.inputs().get(0).ingredient());
        assertSame(pedestalPredicate, converted.inputs().get(1).ingredient());
        assertEquals(3L, converted.inputs().get(1).count());
        assertFalse(converted.inputs().get(0).ingredient().test(new ItemStack(Items.DIAMOND)));
        assertFalse(converted.inputs().get(1).ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertEquals(2_184_000L, converted.energy());
        assertEquals((int) RitualManager.DEFAULT_RITUAL_TIME, converted.processTime());
        assertEquals(3, converted.outputs().get(0).getCount());
        output.getOrCreateTag().putString("payload", "changed");
        assertEquals("preserved", converted.outputs().get(0).getTag().getString("payload"));
        assertEquals(untouched.getTag(), tool.getTag(), "even getRemainingUses must not mutate the input");
    }

    @Test
    void gavelUsesAreDecrementedInReturnedNbtWithoutLosingDamageNameOrOtherTags() {
        ItemStack tool = new ItemStack(gavel);
        tool.setDamageValue(13);
        tool.setHoverName(Component.literal("Keep me"));
        tool.getOrCreateTag().putString("owner", "test-owner");
        gavel.setRemainingUses(tool, 7);
        CompoundTag before = tool.getTag().copy();
        var converted = convert(simple(), tool, mold(1));
        var toolInput = converted.inputs().get(converted.inputs().size() - 1);
        assertEquals(1L, toolInput.count());
        assertTrue(toolInput.consumed());
        assertTrue(toolInput.ingredient().test(tool));
        ItemStack differentUses = tool.copy();
        gavel.setRemainingUses(differentUses, 8);
        assertFalse(toolInput.ingredient().test(differentUses));
        var returned = converted.outputs().get(1);
        assertEquals(6, gavel.getRemainingUses(returned));
        assertEquals(13, returned.getDamageValue(), "ritual uses are not vanilla durability damage");
        assertEquals(tool.getHoverName(), returned.getHoverName());
        assertEquals("test-owner", returned.getTag().getString("owner"));
        assertEquals(before, tool.getTag());
        returned.getOrCreateTag().putString("owner", "changed output");
        assertEquals(before, tool.getTag());
    }

    @Test
    void finalUseReturnsAnExhaustedGavelButDoesNotDestroyItOrPermitFurtherRituals() {
        ItemStack tool = new ItemStack(gavel);
        gavel.setRemainingUses(tool, 1);
        ItemStack returned = convert(simple(), tool, mold(1)).outputs().get(1);
        assertEquals(gavel, returned.getItem());
        assertEquals(0, gavel.getRemainingUses(returned));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), simple(), List.of(returned), mold(1)).isEmpty());
        gavel.setRemainingUses(returned, -1);
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), simple(), List.of(returned), mold(1)).isEmpty());
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), simple(), List.of(), mold(1)).isEmpty());
    }

    @Test
    void nativeTierMoldsAreStrictAndKeepTheMinimumTierInsteadOfUsingTierOne() {
        var source = ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), new EssencesDefinition(1, 0, 0, 0),
                new RitualRequirements(3, List.of()), List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(new ItemStack(gavel)), mold(2)).isEmpty());
        var converted = convert(source, new ItemStack(gavel), mold(3));
        assertFalse(converted.mold().test(mold(1)));
        assertFalse(converted.mold().test(mold(2)));
        assertTrue(converted.mold().test(mold(3)));
        assertFalse(converted.mold().test(mold(4)), "a separate exact variant handles the higher tier");
        var variants = HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(new ItemStack(gavel)), null);
        assertEquals(3, variants.size());
        assertEquals(Set.of(3, 4, 5), variants.stream()
                .map(r -> HephaestusForgeBlock.getTier(r.mold().getItems()[0])).collect(java.util.stream.Collectors.toSet()));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(new ItemStack(gavel)), mold(6)).isEmpty());
    }

    @Test
    void essenceCapacityStillRestrictsNativeForgeTiersAfterTheFeMigration() {
        // Tier one stores only 10 souls; tier two stores 50. No fake default price or tier bypass.
        var source = ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), new EssencesDefinition(0, 11, 0, 0),
                null, List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(new ItemStack(gavel)), mold(1)).isEmpty());
        assertEquals(22_000L, convert(source, new ItemStack(gavel), mold(2)).energy());
    }

    @Test
    void enhancerRequirementsAreRejectedRatherThanConsumedOrIgnored() {
        var enhancer = new EnhancerDefinition(Items.EMERALD, Map.of(), List.of());
        var source = ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), new EssencesDefinition(1, 0, 0, 0),
                new RitualRequirements(1, List.of(Holder.direct(enhancer))),
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(new ItemStack(gavel)), mold(5)).isEmpty());
    }

    @Test
    void upgradesKeepTheirWorldAndExactRequiredTierConditionsByBeingRejected() {
        UpgradeTierResult upgrade = new UpgradeTierResult(2, 3);
        assertEquals(2, upgrade.getRequiredTier());
        assertEquals(3, upgrade.getUpgradedTier());
        var source = ritual(upgrade, new EssencesDefinition(1, 0, 0, 0), null,
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(new ItemStack(gavel)), mold(2)).isEmpty());
    }

    @Test
    void dynamicCreateSubclassesAndUnknownOrTransmuteLikeActionsAreNotStaticItemResults() {
        RitualResult dynamicCreate = new CreateItemResult(new ItemStack(Items.EMERALD)) {
            @Override public boolean checkConditions(ItemStack input, Level level, BlockPos pos) { return false; }
            @Override public void apply(RitualManager.MainIngredientAccessor accessor, Level level, BlockPos pos) {
                ItemStack actual = accessor.get().copy();
                actual.getOrCreateTag().putBoolean("dynamic", true);
                accessor.set(actual);
            }
        };
        RitualResult transmuteLike = new RitualResult() {
            @Override public void apply(RitualManager.MainIngredientAccessor accessor, Level level, BlockPos pos) {
                ItemStack actual = accessor.get();
                ItemStack result = new ItemStack(Items.EMERALD);
                result.setTag(actual.getTag() == null ? null : actual.getTag().copy());
                accessor.set(result);
            }
            @Override public RitualResultType<?> getType() { return null; }
        };
        for (var action : List.of(dynamicCreate, transmuteLike)) {
            assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), ritual(action,
                    new EssencesDefinition(1, 0, 0, 0), null,
                    List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2))),
                    List.of(new ItemStack(gavel)), mold(1)).isEmpty());
        }
    }

    @Test
    void missingOrNegativeCostsAreRejectedAndLargeCostsUseLongArithmetic() {
        assertEquals(2000L, HephaestusForgeRecipeAdapter.energyFor(new EssencesDefinition(0, 0, 0, 0)));
        assertEquals(17_179_869_176_000L, HephaestusForgeRecipeAdapter.energyFor(
                new EssencesDefinition(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> HephaestusForgeRecipeAdapter.energyFor(null));
        assertThrows(IllegalArgumentException.class,
                () -> HephaestusForgeRecipeAdapter.energyFor(new EssencesDefinition(-1, 0, 0, 0)));
        var missing = ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), null, null,
                List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)));
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), missing, List.of(new ItemStack(gavel)), mold(1)).isEmpty());
    }

    @Test
    void malformedInputsAndNonNativeToolSubclassesFailClosed() {
        for (int amount : List.of(-1, 0, 9, Integer.MAX_VALUE)) {
            var invalid = ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), new EssencesDefinition(1, 0, 0, 0),
                    null, List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), amount)));
            assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), invalid, List.of(new ItemStack(gavel)), mold(1)).isEmpty());
        }
        var custom = InventoryTestBootstrap.registerItem("fa_dynamic_gavel", () ->
                new BlacksmithGavelItem(Tiers.IRON, 1, -2.8F, 25, new Item.Properties()) {
                    @Override public void setRemainingUses(ItemStack tool, int remaining) {
                        throw new AssertionError("unknown tool semantics must not be invoked");
                    }
                });
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), simple(), List.of(new ItemStack(custom)), mold(1)).isEmpty());
        assertTrue(HephaestusForgeRecipeAdapter.convertRitual(id(), ritual(new CreateItemResult(ItemStack.EMPTY),
                new EssencesDefinition(1, 0, 0, 0), null, List.of()), List.of(new ItemStack(gavel)), mold(1)).isEmpty());
    }

    @Test
    void noPedestalRitualIsSupportedAndOverlappingInputsCannotReuseTheSameSupply() {
        var noPedestals = ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), new EssencesDefinition(1, 0, 0, 0),
                null, List.of());
        assertEquals(2, convert(noPedestals, new ItemStack(gavel), mold(1)).inputs().size());
        Ingredient same = Ingredient.of(Items.DIAMOND);
        var overlap = new Ritual(List.of(new RitualInput(same, 2)), same, new CreateItemResult(new ItemStack(Items.EMERALD)),
                new EssencesDefinition(1, 0, 0, 0), null, MagicCircle.Config.DEFAULT);
        var recipe = convert(overlap, new ItemStack(gavel), mold(1));
        assertFalse(ItemIngredientAllocator.matches(recipe.inputs(), List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(gavel)), 1L));
        assertTrue(ItemIngredientAllocator.matches(recipe.inputs(), List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(gavel)), 1L));
    }

    @Test
    void catalogueAndConcreteCallerOverloadUseTheNativeRegistryAndDoNotReuseToolNbt() {
        var adapter = new HephaestusForgeRecipeAdapter();
        Level level = level(simple());
        assertEquals(new ResourceLocation("forbidden_arcanus", "hephaestus_forge/ritual"), FARegistries.RITUAL.location());
        assertEquals(forge, adapter.getMoldItem().getItem());
        assertTrue(adapter.matchesMold(mold(3)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.ANVIL)));
        assertFalse(adapter.getGeneratedRecipes(level).isEmpty());

        ItemStack usedTool = new ItemStack(gavel);
        usedTool.getOrCreateTag().putString("owner", "runtime");
        gavel.setRemainingUses(usedTool, 6);
        List<ItemStack> supplies = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT, 2), usedTool);
        CompoundTag before = usedTool.getTag().copy();
        var matches = adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(), mold(1), supplies);
        assertEquals(1, matches.size());
        RecipeHolder<ForbiddenArcanusSyntheticRecipe> holder = matches.get(0);
        // This is the same convertAll(holder, level, actualInputs) default called by the manager
        // after its concrete-input lookup, not a catalogue-only helper.
        var result = adapter.convertAll(holder, level, supplies).get(0);
        assertEquals(5, gavel.getRemainingUses(result.outputs().get(1)));
        assertEquals("runtime", result.outputs().get(1).getTag().getString("owner"));
        assertEquals(before, usedTool.getTag());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(), mold(1), supplies.subList(0, 2)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(gavel), 1L), Map.of(), mold(1)).isEmpty());
    }

    @Test
    void concreteAeItemKeysKeepTheGavelNbtInsteadOfUsingIngredientDisplayStacks() {
        var adapter = new HephaestusForgeRecipeAdapter();
        ItemStack tool = new ItemStack(gavel);
        gavel.setRemainingUses(tool, 9);
        tool.getOrCreateTag().putString("owner", "ae");
        Map<AEKey, Long> keys = Map.of(AEItemKey.of(tool), 1L,
                AEItemKey.of(new ItemStack(Items.DIAMOND)), 1L,
                AEItemKey.of(new ItemStack(Items.IRON_INGOT)), 2L);
        var matches = adapter.findMatchingRecipes(level(simple()), Map.of(), Map.of(), keys, mold(1), List.of());
        assertEquals(1, matches.size());
        var returned = matches.get(0).value().convertedRecipe().outputs().get(1);
        assertEquals(8, gavel.getRemainingUses(returned));
        assertEquals("ae", returned.getTag().getString("owner"));
    }

    @Test
    void stableVariantIdsDistinguishRemainingUsesOwnerAndNativeMoldTier() {
        ItemStack tool = new ItemStack(gavel);
        gavel.setRemainingUses(tool, 3);
        var first = convert(simple(), tool, mold(1));
        assertEquals(first.id(), convert(simple(), tool.copy(), mold(1)).id());
        gavel.setRemainingUses(tool, 2);
        assertNotEquals(first.id(), convert(simple(), tool, mold(1)).id());
        gavel.setRemainingUses(tool, 3);
        tool.getOrCreateTag().putString("owner", "other");
        assertNotEquals(first.id(), convert(simple(), tool, mold(1)).id());
        assertNotEquals(first.id(), convert(simple(), new ItemStack(gavel), mold(2)).id());
        var duplicates = HephaestusForgeRecipeAdapter.convertRitual(id(), simple(), List.of(tool, tool.copy()), mold(1));
        assertEquals(1, duplicates.size());
    }

    @Test
    void published226RitualDataSupportsOnlyTheTwoUnconstrainedFixedResults() throws Exception {
        // Read the pinned official jar's actual data, but construct its native recipe types:
        // ordinary unit tests have no datapack registry event that binds ritual result codecs.
        for (String name : List.of("eternal_stella", "ferrognetic_mixture", "sea_prism", "smelter_prism",
                "terrastomp_prism", "whirlwind_prism", "upgrade_tier_2", "upgrade_tier_3", "upgrade_tier_4", "upgrade_tier_5")) {
            String path = "/data/forbidden_arcanus/forbidden_arcanus/hephaestus_forge/ritual/" + name + ".json";
            JsonObject json;
            try (var stream = Ritual.class.getResourceAsStream(path)) {
                assertNotNull(stream, "official 2.2.6 data must be on the test runtime classpath: " + name);
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
            registerJsonItems(json);
            List<RitualInput> inputs = new ArrayList<>();
            for (var element : json.getAsJsonArray("inputs")) {
                var row = element.getAsJsonObject();
                inputs.add(new RitualInput(Ingredient.fromJson(row.get("ingredient")), row.get("amount").getAsInt()));
            }
            var fees = json.getAsJsonObject("essences");
            var essences = new EssencesDefinition(fees.get("aureal").getAsInt(), fees.get("souls").getAsInt(),
                    fees.get("blood").getAsInt(), fees.get("experience").getAsInt());
            RitualRequirements requirements = null;
            if (json.has("additional_requirements")) {
                var extra = json.getAsJsonObject("additional_requirements");
                // A nonempty list of genuine definitions must be rejected before effects can be
                // flattened into consumed item inputs or an unconditional output recipe.
                List<Holder<EnhancerDefinition>> enhancers = extra.has("enhancers")
                        ? List.of(Holder.direct(new EnhancerDefinition(Items.EMERALD, Map.of(), List.of()))) : List.of();
                requirements = new RitualRequirements(extra.has("forge_tier") ? extra.get("forge_tier").getAsInt() : 1, enhancers);
            }
            JsonObject action = json.getAsJsonObject("result");
            RitualResult result = switch (action.get("type").getAsString()) {
                case "forbidden_arcanus:create_item" -> new CreateItemResult(ItemStack.CODEC.parse(
                        JsonOps.INSTANCE, action.get("result_item")).getOrThrow(false, message -> fail(message)));
                case "forbidden_arcanus:upgrade_tier" -> new UpgradeTierResult(
                        action.get("required_tier").getAsInt(), action.get("upgraded_tier").getAsInt());
                default -> throw new AssertionError("Unexpected official action: " + action);
            };
            var source = new Ritual(inputs, Ingredient.fromJson(json.get("main_ingredient")), result,
                    essences, requirements, MagicCircle.Config.DEFAULT);
            var variants = HephaestusForgeRecipeAdapter.convertRitual(new ResourceLocation("forbidden_arcanus", name),
                    source, List.of(new ItemStack(gavel)), null);
            if (name.equals("eternal_stella") || name.equals("ferrognetic_mixture")) {
                assertEquals(name.equals("eternal_stella") ? 3 : 5, variants.size());
                assertEquals(name.equals("eternal_stella") ? 2_166_000L : 2_704_000L, variants.get(0).energy());
                assertEquals(new ResourceLocation("forbidden_arcanus", name),
                        BuiltInRegistries.ITEM.getKey(variants.get(0).outputs().get(0).getItem()));
                assertEquals(1, variants.get(0).outputs().get(0).getCount());
            } else {
                assertTrue(variants.isEmpty(), name + " must not lose its enhancer or upgrade requirements");
            }
        }
    }

    private static void registerJsonItems(com.google.gson.JsonElement json) {
        if (json.isJsonArray()) {
            for (var child : json.getAsJsonArray()) registerJsonItems(child);
        } else if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if ((entry.getKey().equals("item") || entry.getKey().equals("id")) && entry.getValue().isJsonPrimitive()) {
                    var itemId = new ResourceLocation(entry.getValue().getAsString());
                    if (BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
                        InventoryTestBootstrap.registerItem(itemId, () -> new Item(new Item.Properties()));
                    }
                } else {
                    registerJsonItems(entry.getValue());
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void optionalLoaderAndRealManagerLookupKeepNbtSeparatedInBothCaches() throws Exception {
        // A private instance avoids leaving extra adapters in the singleton used by other tests.
        var constructor = AlloyFurnaceRecipeManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        try (var singleton = mockStatic(AlloyFurnaceRecipeManager.class, CALLS_REAL_METHODS)) {
            singleton.when(AlloyFurnaceRecipeManager::getInstance).thenReturn(manager);
            ForbiddenArcanusRecipeCompatLoader.register();
        }
        assertEquals(1, manager.getRegisteredAdapters().size());
        assertEquals(RecipeSourceIds.FORBIDDEN_ARCANUS,
                manager.getAdapterSourceId(manager.getRegisteredAdapters().get(0)));
        Level level = level(simple());
        RecipeManager recipeManager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        // No public bootstrap/ModRecipeTypes is installed in this test. Supply only an empty
        // native recipe index; the real registration, adapter lookup, conversion caches and
        // final item/mold allocation are all exercised, without mocking their implementations.
        var emptyIndex = AlloyFurnaceRecipeManager.class.getDeclaredMethod("emptyRecipeIndex");
        emptyIndex.setAccessible(true);
        var indexes = AlloyFurnaceRecipeManager.class.getDeclaredField("recipeIndexes");
        indexes.setAccessible(true);
        ((Map<Object, Object>) indexes.get(manager)).put(recipeManager, emptyIndex.invoke(null));

        ItemStack tool = new ItemStack(gavel);
        gavel.setRemainingUses(tool, 8);
        tool.getOrCreateTag().putString("owner", "manager");
        var materials = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT, 2), tool);
        var first = manager.findRecipe(level, materials, List.of(), mold(1));
        assertNotNull(first);
        assertEquals(7, gavel.getRemainingUses(first.outputs().get(1)));
        gavel.setRemainingUses(tool, 4);
        var second = manager.findRecipe(level, materials, List.of(), mold(1));
        assertNotNull(second);
        assertNotEquals(first.id(), second.id());
        assertEquals(3, gavel.getRemainingUses(second.outputs().get(1)));
        assertEquals("manager", second.outputs().get(1).getTag().getString("owner"));
        gavel.setRemainingUses(tool, 8);
        assertSame(first, manager.findRecipe(level, materials, List.of(), mold(1)));
        gavel.setRemainingUses(tool, 0);
        assertNull(manager.findRecipe(level, materials, List.of(), mold(1)));
    }

    @Test
    void networkRoundTripKeepsExactToolTierAndOutputTags() {
        ItemStack tool = new ItemStack(gavel);
        gavel.setRemainingUses(tool, 11);
        tool.getOrCreateTag().putString("owner", "wire");
        var original = convert(simple(), tool, mold(3));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AdvancedAlloyFurnaceRecipe.STREAM_CODEC.encode(buffer, original);
            var decoded = AdvancedAlloyFurnaceRecipe.STREAM_CODEC.decode(buffer);
            assertEquals(original.id(), decoded.id());
            assertEquals(original.energy(), decoded.energy());
            assertTrue(decoded.mold().test(mold(3)));
            assertFalse(decoded.mold().test(mold(1)));
            assertTrue(decoded.inputs().get(2).ingredient().test(tool));
            ItemStack other = tool.copy();
            gavel.setRemainingUses(other, 10);
            assertFalse(decoded.inputs().get(2).ingredient().test(other));
            assertEquals(10, gavel.getRemainingUses(decoded.outputs().get(1)));
            assertEquals("wire", decoded.outputs().get(1).getTag().getString("owner"));
        } finally {
            buffer.release();
        }
    }

    @Test
    void registryApiFailuresPropagateInsteadOfGeneratingCheapDefaults() {
        Level level = mock(Level.class);
        when(level.registryAccess()).thenThrow(new IllegalStateException("registry API failure"));
        assertThrows(IllegalStateException.class, () -> new HephaestusForgeRecipeAdapter().getGeneratedRecipes(level));
        assertEquals(2000, AdapterUtils.DEFAULT_ENERGY);
    }

    private static Ritual simple() {
        return ritual(new CreateItemResult(new ItemStack(Items.EMERALD)), new EssencesDefinition(1, 0, 0, 0),
                null, List.of(new RitualInput(Ingredient.of(Items.IRON_INGOT), 2)));
    }

    private static Ritual ritual(RitualResult result, EssencesDefinition essences,
                                 RitualRequirements requirements, List<RitualInput> inputs) {
        return new Ritual(inputs, Ingredient.of(Items.DIAMOND), result, essences, requirements, MagicCircle.Config.DEFAULT);
    }

    private static AdvancedAlloyFurnaceRecipe convert(Ritual source, ItemStack tool, ItemStack mold) {
        var converted = HephaestusForgeRecipeAdapter.convertRitual(id(), source, List.of(tool), mold);
        assertEquals(1, converted.size());
        return converted.get(0);
    }

    private static ResourceLocation id() { return new ResourceLocation("test", "native_fa_ritual"); }

    private static ItemStack mold(int tier) {
        return HephaestusForgeBlock.setTierOnStack(new ItemStack(forge), tier);
    }

    @SuppressWarnings("unchecked")
    private static Level level(Ritual ritual) {
        Level level = mock(Level.class);
        RegistryAccess access = mock(RegistryAccess.class);
        Registry<Ritual> registry = mock(Registry.class);
        when(level.registryAccess()).thenReturn(access);
        when(access.registryOrThrow(FARegistries.RITUAL)).thenReturn(registry);
        when(registry.entrySet()).thenReturn(Set.of(Map.entry(ResourceKey.create(FARegistries.RITUAL, id()), ritual)));
        return level;
    }
}
