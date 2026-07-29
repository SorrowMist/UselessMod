package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import de.ellpeck.naturesaura.recipes.AnimalSpawnerRecipe;
import de.ellpeck.naturesaura.recipes.OfferingRecipe;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturesAuraRecipeAdapterTest {

    @Test
    void treeRitualConsumesSaplingIngredientsAndSixteenGoldPowder() {
        ItemStack output = named(new ItemStack(Items.DIAMOND), "tree-output");
        TreeRitualRecipe source = new TreeRitualRecipe(
                Ingredient.of(Items.OAK_SAPLING),
                output,
                120,
                List.of(Ingredient.of(Items.STONE), Ingredient.of(Items.STONE), Ingredient.of(Items.GOLD_INGOT))
        );

        TreeRitualRecipeAdapter adapter = new TreeRitualRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(holder("tree_ritual", source), null).getFirst();

        assertEquals(1L, required(converted, new ItemStack(Items.OAK_SAPLING)));
        assertEquals(2L, required(converted, new ItemStack(Items.STONE)));
        assertEquals(1L, required(converted, new ItemStack(Items.GOLD_INGOT)));
        assertEquals(16L, required(converted, NaturesAuraAdapterUtils.item("gold_powder")));
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertEquals(120, converted.processTime());
        assertTrue(converted.mold().test(adapter.getMoldItem()));
        assertFalse(converted.mold().test(new ItemStack(Items.STICK)));
        assertEquals("tree-output", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void altarUsesNatureAltarWithoutCatalystAndCatalystWhenSpecified() {
        NatureAltarRecipeAdapter adapter = new NatureAltarRecipeAdapter();
        AltarRecipe normal = new AltarRecipe(
                Ingredient.of(Items.GOLD_INGOT), named(new ItemStack(Items.DIAMOND), "crimson-output"),
                Ingredient.EMPTY, 3_000, 300
        );
        AdvancedAlloyFurnaceRecipe normalConverted = adapter.convertAll(holder("tainted_gold", normal), null).getFirst();

        assertTrue(normalConverted.mold().test(NaturesAuraAdapterUtils.item("nature_altar")));
        assertFalse(normalConverted.mold().test(new ItemStack(Items.BLAZE_ROD)));
        assertEquals(3_000L, normalConverted.energy());
        assertEquals(300, normalConverted.processTime());
        assertEquals("crimson-output", normalConverted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());

        AltarRecipe catalyzed = new AltarRecipe(
                Ingredient.of(Items.COAL), new ItemStack(Items.EMERALD),
                Ingredient.of(Items.BLAZE_ROD), 30_000, 250
        );
        AdvancedAlloyFurnaceRecipe catalyzedConverted = adapter.convertAll(holder("coal", catalyzed), null).getFirst();

        assertTrue(catalyzedConverted.mold().test(new ItemStack(Items.BLAZE_ROD)));
        assertFalse(catalyzedConverted.mold().test(NaturesAuraAdapterUtils.item("nature_altar")));
        assertEquals(1L, required(catalyzedConverted, new ItemStack(Items.COAL)));
        assertEquals(0L, required(catalyzedConverted, new ItemStack(Items.BLAZE_ROD)));
    }

    @Test
    void animalSpawnerUsesIngredientAmountsAndOutputsSpawnEgg() {
        Ingredient beef = Ingredient.of(new ItemStack(Items.BEEF, 3));
        AnimalSpawnerRecipe source = new AnimalSpawnerRecipe(
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW),
                50_000,
                60,
                List.of(Ingredient.of(Items.BONE), beef)
        );

        AnimalSpawnerRecipeAdapter adapter = new AnimalSpawnerRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(holder("cow", source), null).getFirst();

        assertTrue(converted.mold().test(adapter.getMoldItem()));
        assertFalse(converted.mold().test(new ItemStack(Items.STICK)));
        assertEquals(1L, required(converted, new ItemStack(Items.BONE)));
        assertEquals(3L, required(converted, new ItemStack(Items.BEEF)));
        assertEquals(Items.COW_SPAWN_EGG, converted.outputs().getFirst().getItem());
        assertEquals(50_000L, converted.energy());
        assertEquals(60, converted.processTime());

        AnimalSpawnerRecipe missingEgg = new AnimalSpawnerRecipe(
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ITEM),
                1,
                20,
                List.of(Ingredient.of(Items.STICK))
        );
        assertTrue(adapter.convertAll(holder("item_entity", missingEgg), null).isEmpty());
    }

    @Test
    void offeringUsesOneStartItemForFixedSixteenOperationBatch() {
        ItemStack output = named(new ItemStack(Items.DIAMOND, 2), "offering-output");
        OfferingRecipe source = new OfferingRecipe(
                Ingredient.of(Items.BLAZE_POWDER), Ingredient.of(Items.BLAZE_POWDER), output
        );

        OfferingRecipeAdapter adapter = new OfferingRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(holder("offering", source), null).getFirst();

        // The same item satisfies the sixteen offerings plus the one consumed start item.
        assertEquals(17L, required(converted, new ItemStack(Items.BLAZE_POWDER)));
        assertEquals(32, converted.outputs().getFirst().getCount());
        assertEquals("offering-output", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertEquals(AdapterUtils.DEFAULT_PROCESS_TIME, converted.processTime());
        assertTrue(converted.mold().test(adapter.getMoldItem()));
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, ItemStack stack) {
        return recipe.inputs().stream()
                .filter(input -> input.ingredient().test(stack))
                .mapToLong(input -> input.count())
                .sum();
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> holder(String path, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("naturesaura", "test/" + path), recipe);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
