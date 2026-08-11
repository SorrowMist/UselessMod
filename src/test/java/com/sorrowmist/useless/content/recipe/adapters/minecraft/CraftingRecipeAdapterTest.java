package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRecipeAdapterTest {
    private static final TagKey<Item> BOUND_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("useless_mod_test", "bound_crafting_input"));
    private static final TagKey<Item> REUSABLE_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("useless_mod_test", "reusable_crafting_input"));
    private static Map<TagKey<Item>, List<Holder<Item>>> originalTags;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalTags = BuiltInRegistries.ITEM.getTags().collect(Collectors.toUnmodifiableMap(
                com.mojang.datafixers.util.Pair::getFirst,
                pair -> StreamSupport.stream(pair.getSecond().spliterator(), false).toList()));
        BuiltInRegistries.ITEM.bindTags(Map.of(
                BOUND_TAG, List.of(holder(Items.IRON_INGOT), holder(Items.GOLD_INGOT)),
                REUSABLE_TAG, List.of(holder(Items.DIAMOND), holder(Items.IRON_PICKAXE))));
    }

    @AfterAll
    static void restoreItemTags() {
        if (originalTags != null) {
            BuiltInRegistries.ITEM.bindTags(originalTags);
        }
    }

    @Test
    void convertsShapedRecipeWithMergedInputsAndDefaults() {
        ShapedRecipe source = new ShapedRecipe(
                "",
                CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.IRON_INGOT)), "##", " #"),
                new ItemStack(Items.IRON_PICKAXE)
        );

        AdvancedAlloyFurnaceRecipe converted = convert("shaped", source);

        assertEquals(1, converted.inputs().size());
        assertEquals(3L, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(converted.inputFluids().isEmpty());
        assertEquals(1, itemCount(converted.outputs(), Items.IRON_PICKAXE));
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertEquals(AdapterUtils.DEFAULT_PROCESS_TIME, converted.processTime());
        assertTrue(converted.catalyst().isEmpty());
        assertEquals(0, converted.catalystUses());
        assertTrue(converted.mold().test(new ItemStack(Items.CRAFTING_TABLE)));
        assertEquals(AlloyFurnaceMode.NORMAL, converted.mode());
        assertEquals("shaped_converted", converted.id().getPath());
    }

    @Test
    void convertsCraftingRecipeWithUnresolvedTagInput() {
        TagKey<Item> tag = TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "unresolved_crafting_input"));
        ShapedRecipe source = new ShapedRecipe(
                "",
                CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('#', Ingredient.of(tag)), "#"),
                new ItemStack(Items.DIAMOND)
        );

        AdvancedAlloyFurnaceRecipe converted = convert("unresolved_tag", source);

        assertEquals(1, converted.inputs().size());
        assertEquals(1L, converted.inputs().getFirst().count());
        assertEquals(1, converted.inputs().getFirst().ingredient().getValues().length);
        assertTrue(converted.inputs().getFirst().ingredient().getValues()[0] instanceof Ingredient.TagValue);
    }

    @Test
    void convertsCraftingRecipeWithBoundTagInput() {
        ShapedRecipe source = new ShapedRecipe(
                "",
                CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('#', Ingredient.of(BOUND_TAG)), "#"),
                new ItemStack(Items.DIAMOND)
        );

        AdvancedAlloyFurnaceRecipe converted = convert("bound_tag", source);

        Ingredient input = converted.inputs().getFirst().ingredient();
        assertEquals(1, input.getValues().length);
        assertTrue(input.getValues()[0] instanceof Ingredient.TagValue);
        assertTrue(input.test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(input.test(new ItemStack(Items.GOLD_INGOT)));
    }

    @Test
    void convertsLargeTagWithoutCartesianProduct() {
        Ingredient largeTagLike = Ingredient.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.DIAMOND,
                Items.EMERALD, Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.QUARTZ, Items.AMETHYST_SHARD, Items.OAK_LOG, Items.BIRCH_LOG,
                Items.SPRUCE_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
                Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.CRIMSON_STEM, Items.WARPED_STEM,
                Items.STONE, Items.COBBLESTONE, Items.DEEPSLATE, Items.NETHERRACK);
        ShapedRecipe source = new ShapedRecipe(
                "",
                CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('#', largeTagLike), "##"),
                new ItemStack(Items.STICK, 4));

        AdvancedAlloyFurnaceRecipe converted = convert("large_tag", source);

        assertEquals(1, converted.inputs().size());
        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(24, converted.inputs().getFirst().ingredient().getItems().length);
        assertEquals(4, itemCount(converted.outputs(), Items.STICK));
    }

    @Test
    void convertsCraftingSubclassWithLargeTagWithoutCartesianProduct() {
        Ingredient largeTagLike = Ingredient.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.DIAMOND,
                Items.EMERALD, Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.QUARTZ, Items.AMETHYST_SHARD, Items.OAK_LOG, Items.BIRCH_LOG,
                Items.SPRUCE_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
                Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.CRIMSON_STEM, Items.WARPED_STEM,
                Items.STONE, Items.COBBLESTONE, Items.DEEPSLATE, Items.NETHERRACK);
        ReusableShapedRecipe source = new ReusableShapedRecipe(
                ShapedRecipePattern.of(Map.of('#', largeTagLike, 'X', largeTagLike), "#X", "##"),
                new ItemStack(Items.EMERALD), 1);

        AdvancedAlloyFurnaceRecipe converted = convert("large_subclass_tag", source);

        assertEquals(1, converted.inputs().size());
        assertEquals(3L, converted.inputs().getFirst().count());
        assertEquals(2, converted.molds().size());
        assertEquals(24, converted.molds().get(1).getItems().length);
    }

    @Test
    void keepsMixedReusableTagAsOneMold() {
        ReusableShapedRecipe source = new ReusableShapedRecipe(
                ShapedRecipePattern.of(
                        Map.of('#', Ingredient.of(Items.IRON_INGOT), 'X', Ingredient.of(REUSABLE_TAG)),
                        "#X"),
                new ItemStack(Items.EMERALD), 1);

        AdvancedAlloyFurnaceRecipe converted = convert("reusable_tag_mold", source);

        assertEquals(1, converted.inputs().size());
        assertEquals(1L, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertEquals(2, converted.molds().size());
        Ingredient reusableMold = converted.molds().get(1);
        assertTrue(reusableMold.test(new ItemStack(Items.DIAMOND)));
        assertTrue(reusableMold.test(new ItemStack(Items.IRON_PICKAXE)));
        assertFalse(converted.inputs().stream()
                .anyMatch(input -> input.ingredient().test(new ItemStack(Items.DIAMOND))));
    }

    @Test
    void rejectsTagWithMixedReusableAndConsumedCandidates() {
        ConditionalRemainderRecipe source = new ConditionalRemainderRecipe();

        assertTrue(new CraftingRecipeAdapter()
                .convertAll(holder("mixed_reusable_remainder", source), null)
                .isEmpty());
    }

    @Test
    void convertsShapelessRecipeWithoutLosingIngredientComponents() {
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("exact-input"));
        Ingredient exact = DataComponentIngredient.of(true, namedIron.copy());
        ShapelessRecipe source = shapeless(
                new ItemStack(Items.DIAMOND, 2), exact, exact);

        AdvancedAlloyFurnaceRecipe converted = convert("shapeless", source);

        assertEquals(1, converted.inputs().size());
        assertEquals(2L, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(namedIron));
        assertFalse(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertEquals(2, itemCount(converted.outputs(), Items.DIAMOND));
    }

    @Test
    void substitutesDeterministicWaterAndLavaBucketsWithMergedFluids() {
        ShapelessRecipe source = shapeless(
                new ItemStack(Items.OBSIDIAN),
                Ingredient.of(Items.WATER_BUCKET),
                Ingredient.of(Items.LAVA_BUCKET),
                Ingredient.of(Items.WATER_BUCKET)
        );

        AdvancedAlloyFurnaceRecipe converted = convert("bucket_fluids", source);

        assertTrue(converted.inputs().isEmpty());
        assertEquals(2_000, fluidAmount(converted.inputFluids(), Fluids.WATER));
        assertEquals(1_000, fluidAmount(converted.inputFluids(), Fluids.LAVA));
        assertEquals(0, itemCount(converted.outputs(), Items.BUCKET));
        assertEquals(1, itemCount(converted.outputs(), Items.OBSIDIAN));
        assertEquals(4, converted.molds().size());
        assertEquals(3, converted.molds().stream()
                .filter(mold -> mold.test(new ItemStack(Items.BUCKET)))
                .count());
    }

    @Test
    void movesStableNonBucketRemainderToOutputs() {
        ShapelessRecipe source = shapeless(
                new ItemStack(Items.SUGAR), Ingredient.of(Items.HONEY_BOTTLE));

        AdvancedAlloyFurnaceRecipe converted = convert("stable_remainder", source);

        assertEquals(1L, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.HONEY_BOTTLE)));
        assertEquals(1, itemCount(converted.outputs(), Items.GLASS_BOTTLE));
        assertEquals(1, converted.molds().size());
        assertTrue(converted.mold().test(new ItemStack(Items.CRAFTING_TABLE)));
    }

    @Test
    void mergesRepeatedNonBucketRemaindersIntoOutputs() {
        ShapelessRecipe source = shapeless(
                new ItemStack(Items.SUGAR),
                Ingredient.of(Items.HONEY_BOTTLE), Ingredient.of(Items.HONEY_BOTTLE));

        AdvancedAlloyFurnaceRecipe converted = convert("repeated_remainders", source);

        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(2, itemCount(converted.outputs(), Items.GLASS_BOTTLE));
        assertEquals(1, converted.molds().size());
    }

    @Test
    void rejectsIngredientWithInputDependentRemainder() {
        ShapelessRecipe source = shapeless(
                new ItemStack(Items.DIAMOND),
                Ingredient.of(Items.WATER_BUCKET, Items.IRON_INGOT));

        assertTrue(new CraftingRecipeAdapter().convertAll(holder("ambiguous_remainder", source), null).isEmpty());
    }

    @Test
    void convertsCraftingRecipeSubclassesAndRejectsWrongMolds() {
        ShapelessRecipe source = new ShapelessRecipe(
                "",
                CraftingBookCategory.MISC,
                new ItemStack(Items.DIAMOND),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT))) {
        };
        CraftingRecipeAdapter adapter = new CraftingRecipeAdapter();

        assertEquals(1, adapter.convertAll(holder("dynamic", source), null).size());
        assertTrue(adapter.matchesMold(new ItemStack(Items.CRAFTING_TABLE)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.FURNACE)));
        assertFalse(adapter.matchesMold(ItemStack.EMPTY));
    }

    @Test
    void assemblesDynamicCraftingSubclassWithActualInputsAndRemainders() {
        DynamicShapelessRecipe source = new DynamicShapelessRecipe();
        RecipeHolder<CraftingRecipe> holder = holder("dynamic_runtime", source);
        CraftingRecipeAdapter adapter = new CraftingRecipeAdapter();

        assertTrue(adapter.convertAll(holder, null).isEmpty());

        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(
                holder, null, List.of(new ItemStack(Items.GOLD_INGOT))).getFirst();

        assertEquals(1, itemCount(converted.outputs(), Items.EMERALD));
        assertEquals(1, itemCount(converted.outputs(), Items.GLASS_BOTTLE));
        assertEquals(1, converted.molds().size());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.GOLD_INGOT)));
    }

    private static ShapelessRecipe shapeless(ItemStack output, Ingredient... ingredients) {
        NonNullList<Ingredient> inputs = NonNullList.create();
        inputs.addAll(List.of(ingredients));
        return new ShapelessRecipe("", CraftingBookCategory.MISC, output, inputs);
    }

    private static final class DynamicShapelessRecipe extends ShapelessRecipe {
        private DynamicShapelessRecipe() {
            super("", CraftingBookCategory.MISC, ItemStack.EMPTY,
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT)));
        }

        @Override
        public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack assemble(net.minecraft.world.item.crafting.CraftingInput input,
                                  net.minecraft.core.HolderLookup.Provider registries) {
            return input.getItem(0).is(Items.GOLD_INGOT)
                    ? new ItemStack(Items.EMERALD)
                    : new ItemStack(Items.DIAMOND);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(
                net.minecraft.world.item.crafting.CraftingInput input) {
            NonNullList<ItemStack> remainder = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            remainder.set(0, new ItemStack(Items.GLASS_BOTTLE));
            return remainder;
        }
    }

    private static final class ReusableShapedRecipe extends ShapedRecipe {
        private final int reusableSlot;

        private ReusableShapedRecipe(
                ShapedRecipePattern pattern, ItemStack result, int reusableSlot) {
            super("", CraftingBookCategory.MISC, pattern, result);
            this.reusableSlot = reusableSlot;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(
                net.minecraft.world.item.crafting.CraftingInput input) {
            NonNullList<ItemStack> remaining = super.getRemainingItems(input);
            ItemStack original = input.getItem(reusableSlot);
            if (original.isEmpty()) {
                return remaining;
            }

            ItemStack reusable = original.copy();
            if (reusable.isDamageableItem()) {
                int damage = reusable.getDamageValue() + 1;
                if (damage < reusable.getMaxDamage()) {
                    reusable.setDamageValue(damage);
                    remaining.set(reusableSlot, reusable);
                }
            } else {
                remaining.set(reusableSlot, reusable);
            }
            return remaining;
        }
    }

    private static final class ConditionalRemainderRecipe extends ShapelessRecipe {
        private ConditionalRemainderRecipe() {
            super("", CraftingBookCategory.MISC, new ItemStack(Items.EMERALD),
                    NonNullList.of(Ingredient.EMPTY,
                            Ingredient.of(Items.DIAMOND, Items.IRON_INGOT)));
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(
                net.minecraft.world.item.crafting.CraftingInput input) {
            NonNullList<ItemStack> remaining = super.getRemainingItems(input);
            if (input.getItem(0).is(Items.DIAMOND)) {
                remaining.set(0, input.getItem(0).copy());
            }
            return remaining;
        }
    }

    private static AdvancedAlloyFurnaceRecipe convert(String path, CraftingRecipe source) {
        return new CraftingRecipeAdapter().convertAll(holder(path, source), null).getFirst();
    }

    private static RecipeHolder<CraftingRecipe> holder(String path, CraftingRecipe source) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", path), source);
    }

    private static Holder<Item> holder(Item item) {
        ResourceLocation id = java.util.Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item));
        return BuiltInRegistries.ITEM.getHolderOrThrow(ResourceKey.create(Registries.ITEM, id));
    }

    private static int itemCount(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static int fluidAmount(
            List<SizedFluidIngredient> stacks, net.minecraft.world.level.material.Fluid fluid) {
        return stacks.stream()
                .filter(stack -> stack.ingredient().test(new FluidStack(fluid, 1)))
                .mapToInt(SizedFluidIngredient::amount)
                .sum();
    }
}
