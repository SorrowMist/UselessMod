package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import de.ellpeck.naturesaura.recipes.AnimalSpawnerRecipe;
import de.ellpeck.naturesaura.recipes.OfferingRecipe;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 1.20.1 native-shape tests for Nature's Aura 39.4.
 *
 * <p>The unit-test classpath loads the native jar but does not run Forge item registration, so the
 * mod's own block/item stacks are supplied through a narrow static stub. All recipe parsing,
 * counts, NBT predicates and outputs still come from the native 39.4 classes.</p>
 */
class NaturesAura3904NativeForge1201AdapterTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void treeRitualReadsNativeResultAndConsumesSixteenGoldPowder() {
        ItemStack goldPowder = new ItemStack(Items.GOLD_NUGGET);
        ItemStack woodStand = new ItemStack(Items.OAK_FENCE);
        try (MockedStatic<NaturesAuraAdapterUtils> stubs = Mockito.mockStatic(
                NaturesAuraAdapterUtils.class, Mockito.CALLS_REAL_METHODS)) {
            stubs.when(() -> NaturesAuraAdapterUtils.item("gold_powder")).thenReturn(goldPowder);
            stubs.when(() -> NaturesAuraAdapterUtils.item("wood_stand")).thenReturn(woodStand);

            ItemStack output = tagged(Items.DIAMOND, 2, "tree-output");
            TreeRitualRecipe source = new TreeRitualRecipe(
                    id("tree_ritual"),
                    Ingredient.of(Items.OAK_SAPLING),
                    output,
                    120,
                    Ingredient.of(Items.STONE),
                    Ingredient.of(Items.STONE),
                    Ingredient.of(Items.GOLD_INGOT),
                    Ingredient.of(goldPowder),
                    Ingredient.of(goldPowder));

            AdvancedAlloyFurnaceRecipe converted = new TreeRitualRecipeAdapter()
                    .convertAll(holder("tree_ritual", source), null).get(0);

            assertEquals(1L, required(converted, new ItemStack(Items.OAK_SAPLING)));
            assertEquals(2L, required(converted, new ItemStack(Items.STONE)));
            assertEquals(1L, required(converted, new ItemStack(Items.GOLD_INGOT)));
            // Two recipe ingredients plus the sixteen gold-powder blocks in the native multiblock.
            assertEquals(18L, required(converted, goldPowder));
            assertEquals(120, converted.processTime());
            assertEquals(2, converted.outputs().get(0).getCount());
            assertEquals("tree-output", tag(converted.outputs().get(0)).getString("marker"));
            assertTrue(converted.molds().get(0).test(woodStand));
        }
    }

    @Test
    void altarConsumesOneInputKeepsNbtAndUsesCatalystAsMold() {
        ItemStack altar = new ItemStack(Items.ENCHANTING_TABLE);
        ItemStack taggedInput = tagged(Items.DIAMOND, 5, "altar-input");
        try (MockedStatic<NaturesAuraAdapterUtils> stubs = Mockito.mockStatic(
                NaturesAuraAdapterUtils.class, Mockito.CALLS_REAL_METHODS)) {
            stubs.when(() -> NaturesAuraAdapterUtils.item("nature_altar")).thenReturn(altar);

            AltarRecipe normal = new AltarRecipe(
                    id("altar"),
                    DataComponentIngredient.of(true, taggedInput),
                    tagged(Items.EMERALD, 1, "altar-output"),
                    Ingredient.EMPTY,
                    3_000,
                    300);
            AdvancedAlloyFurnaceRecipe converted = new NatureAltarRecipeAdapter()
                    .convertAll(holder("altar", normal), null).get(0);

            assertEquals(1L, required(converted, taggedInput));
            assertEquals(0L, required(converted, new ItemStack(Items.DIAMOND)));
            assertEquals(3_000L, converted.energy());
            assertEquals(300, converted.processTime());
            assertEquals("altar-output", tag(converted.outputs().get(0)).getString("marker"));
            assertTrue(converted.molds().get(0).test(altar));
        }

        AltarRecipe catalyzed = new AltarRecipe(
                id("altar_catalyzed"),
                Ingredient.of(Items.COAL),
                new ItemStack(Items.EMERALD),
                Ingredient.of(Items.BLAZE_ROD),
                30_000,
                250);
        AdvancedAlloyFurnaceRecipe catalyzedConverted = new NatureAltarRecipeAdapter()
                .convertAll(holder("altar_catalyzed", catalyzed), null).get(0);

        assertTrue(catalyzedConverted.molds().get(0).test(new ItemStack(Items.BLAZE_ROD)));
        assertEquals(1L, required(catalyzedConverted, new ItemStack(Items.COAL)));
        // The catalyst selects the mold and is not a consumed input.
        assertEquals(0L, required(catalyzedConverted, new ItemStack(Items.BLAZE_ROD)));
    }

    @Test
    void animalSpawnerUsesNativeIngredientAmountsAndNativeEntityType() {
        ItemStack spawner = new ItemStack(Items.SPAWNER);
        try (MockedStatic<NaturesAuraAdapterUtils> stubs = Mockito.mockStatic(
                NaturesAuraAdapterUtils.class, Mockito.CALLS_REAL_METHODS)) {
            stubs.when(() -> NaturesAuraAdapterUtils.item("animal_spawner")).thenReturn(spawner);

            AnimalSpawnerRecipe source = new AnimalSpawnerRecipe(
                    id("animal_spawner"),
                    EntityType.COW,
                    50_000,
                    60,
                    Ingredient.of(new ItemStack(Items.BEEF, 3)),
                    Ingredient.of(Items.BONE));

            AdvancedAlloyFurnaceRecipe converted = new AnimalSpawnerRecipeAdapter()
                    .convertAll(holder("animal_spawner", source), null).get(0);

            assertEquals(1L, required(converted, new ItemStack(Items.BONE)));
            assertEquals(3L, required(converted, new ItemStack(Items.BEEF)));
            assertEquals(Items.COW_SPAWN_EGG, converted.outputs().get(0).getItem());
            assertEquals(50_000L, converted.energy());
            assertEquals(60, converted.processTime());
            assertTrue(converted.molds().get(0).test(spawner));
        }

        AnimalSpawnerRecipe noSpawnEgg = new AnimalSpawnerRecipe(
                id("animal_spawner_item"),
                EntityType.ITEM,
                1,
                20,
                Ingredient.of(Items.STICK));
        assertTrue(new AnimalSpawnerRecipeAdapter()
                .convertAll(holder("animal_spawner_item", noSpawnEgg), null).isEmpty());
    }

    @Test
    void offeringKeepsFixedBatchCountsAndOutputNbt() {
        ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
        try (MockedStatic<NaturesAuraAdapterUtils> stubs = Mockito.mockStatic(
                NaturesAuraAdapterUtils.class, Mockito.CALLS_REAL_METHODS)) {
            stubs.when(() -> NaturesAuraAdapterUtils.item("offering_table")).thenReturn(table);

            OfferingRecipe source = new OfferingRecipe(
                    id("offering"),
                    Ingredient.of(Items.BLAZE_POWDER),
                    Ingredient.of(Items.BLAZE_POWDER),
                    tagged(Items.DIAMOND, 2, "offering-output"));

            AdvancedAlloyFurnaceRecipe converted = new OfferingRecipeAdapter()
                    .convertAll(holder("offering", source), null).get(0);

            // Sixteen offerings plus the one calling item, merged because they are the same item.
            assertEquals(17L, required(converted, new ItemStack(Items.BLAZE_POWDER)));
            assertEquals(32, converted.outputs().get(0).getCount());
            assertEquals("offering-output", tag(converted.outputs().get(0)).getString("marker"));
            assertTrue(converted.molds().get(0).test(table));
        }
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, ItemStack stack) {
        long total = 0L;
        for (CountedIngredient input : recipe.inputs()) {
            if (input.ingredient().test(stack)) {
                total += input.count();
            }
        }
        return total;
    }

    private static ItemStack tagged(Item item, int count, String marker) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("marker", marker);
        return stack;
    }

    private static CompoundTag tag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        assertFalse(tag == null, "expected preserved NBT on " + stack);
        return tag;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("naturesaura", "test/" + path);
    }

    private static <T extends Recipe<?>> RecipeHolder<T> holder(String path, T recipe) {
        return new RecipeHolder<>(id(path), recipe);
    }
}
