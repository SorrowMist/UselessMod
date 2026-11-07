package com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import io.github.lounode.ae2cs.common.item.CrystalSeedItem;
import io.github.lounode.ae2cs.common.recipe.SizedIngredient;
import io.github.lounode.ae2cs.common.recipe.circuit_etcher.CircuitEtcherRecipe;
import io.github.lounode.ae2cs.common.recipe.crystal_aggregator.CrystalAggregatorRecipe;
import io.github.lounode.ae2cs.common.recipe.crystal_pulverizer.CrystalPulverizerRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AECrystalScienceNativeMigrationTest {
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        for (String machine : List.of("circuit_etcher", "crystal_aggregator",
                "crystal_pulverizer", "crystal_growth_chamber")) {
            InventoryTestBootstrap.registerItem(new ResourceLocation("ae2cs", machine),
                    () -> new Item(new Item.Properties()));
        }
    }

    @Test void etcherUsesTheNativeSizedIngredientAndKeepsLongCountsAndEnergy() {
        var id = new ResourceLocation("test", "etcher");
        var input = new SizedIngredient(Ingredient.of(Items.GOLD_INGOT), Integer.MAX_VALUE);
        var output = result();
        var nativeRecipe = new CircuitEtcherRecipe(id, input, input, null, output, 2_000_000_000);
        var converted = new CircuitEtcherRecipeAdapter().convert(new RecipeHolder<>(id, nativeRecipe), null);
        assertEquals(4_294_967_294L, converted.inputs().get(0).count());
        assertEquals(4_000_000_000L, converted.energy());
        assertOutputCopyAndMold(converted, output, "circuit_etcher");
    }

    @Test void aggregatorRetainsTheExactNativeNbtPredicateAndOutput() {
        var id = new ResourceLocation("test", "aggregator");
        var gold = new ItemStack(Items.GOLD_INGOT);
        gold.getOrCreateTag().putString("grade", "exact");
        var predicate = DataComponentIngredient.of(true, gold);
        var output = result();
        var source = new CrystalAggregatorRecipe(id, new SizedIngredient(predicate, 7), null,
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 2), output, 1000);
        var converted = new CrystalAggregatorRecipeAdapter().convertAll(new RecipeHolder<>(id, source), null).get(0);
        var row = converted.inputs().stream().filter(r -> r.ingredient() == predicate).findFirst().orElseThrow();
        assertEquals(7L, row.count());
        assertTrue(row.ingredient().test(gold));
        assertFalse(row.ingredient().test(new ItemStack(Items.GOLD_INGOT)));
        assertEquals(9L, converted.inputs().stream().mapToLong(r -> r.count()).sum());
        assertOutputCopyAndMold(converted, output, "crystal_aggregator");
    }

    @Test void pulverizerPreservesCountsAndDoesNotFlattenDynamicSubclasses() {
        var id = new ResourceLocation("test", "pulverizer");
        var input = new SizedIngredient(Ingredient.of(Items.QUARTZ), 11);
        var output = result();
        var source = new CrystalPulverizerRecipe(id, input, output, 2_000_000_000);
        var adapter = new CrystalPulverizerRecipeAdapter();
        var converted = adapter.convert(new RecipeHolder<>(id, source), null);
        assertEquals(11L, converted.inputs().get(0).count());
        assertEquals(4_000_000_000L, converted.energy());
        assertOutputCopyAndMold(converted, output, "crystal_pulverizer");
        var dynamic = new CrystalPulverizerRecipe(id, input, output, 1000) {};
        assertTrue(adapter.convertAll(new RecipeHolder<>(id, dynamic), null).isEmpty());
        assertTrue(new CircuitEtcherRecipeAdapter().convertAll(new RecipeHolder<>(id,
                new CircuitEtcherRecipe(id, input, null, null, output, 1000) {}), null).isEmpty());
        assertTrue(new CrystalAggregatorRecipeAdapter().convertAll(new RecipeHolder<>(id,
                new CrystalAggregatorRecipe(id, input, null, null, output, 1000) {}), null).isEmpty());
    }

    @Test void growthUsesNativeSeedResultsAndKeepsBothExistingMoldVariants() {
        var seed = InventoryTestBootstrap.registerItem("ae2cs_seed",
                () -> new CrystalSeedItem(new Item.Properties(), () -> Items.QUARTZ, 1_000_000_000));
        var source = new ItemStack(seed, 3);
        source.getOrCreateTag().putString("owner", "not transferred by native growth");
        var nativeResult = CrystalSeedItem.grow(source, 1_000_000_000);
        assertEquals(Items.QUARTZ, nativeResult.getItem());
        assertEquals(3, nativeResult.getCount());
        assertFalse(nativeResult.hasTag());
        var adapter = new CrystalGrowthRecipeAdapter(List.of(seed));
        var recipes = adapter.getAllRecipes();
        assertEquals(2, recipes.size());
        var chamber = recipes.get(0);
        var water = recipes.get(1);
        assertEquals("growth_ae2cs_seed", chamber.id().getPath());
        assertEquals("growth_ae2cs_seed_water_bucket", water.id().getPath());
        assertEquals(5_000_000_000L, chamber.energy());
        assertEquals(1L, chamber.inputs().get(0).count());
        assertEquals(Items.QUARTZ, chamber.outputs().get(0).getItem());
        assertTrue(chamber.mold().test(machine("crystal_growth_chamber")));
        assertFalse(chamber.mold().test(new ItemStack(Items.WATER_BUCKET)));
        assertTrue(water.mold().test(new ItemStack(Items.WATER_BUCKET)));
        assertTrue(adapter.matchesMold(machine("crystal_growth_chamber")));
        assertTrue(adapter.matchesMold(new ItemStack(Items.WATER_BUCKET)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.BUCKET)));
    }

    private static ItemStack result() {
        var stack = new ItemStack(Items.DIAMOND, 3);
        stack.getOrCreateTag().putString("result", "preserved");
        return stack;
    }

    private static ItemStack machine(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("ae2cs", path)));
    }

    private static void assertOutputCopyAndMold(AdvancedAlloyFurnaceRecipe recipe,
                                               ItemStack source, String mold) {
        assertEquals(3, recipe.outputs().get(0).getCount());
        assertEquals("preserved", recipe.outputs().get(0).getTag().getString("result"));
        assertTrue(recipe.mold().test(machine(mold)));
        recipe.outputs().get(0).getTag().putString("result", "changed");
        assertEquals("preserved", source.getTag().getString("result"));
    }
}
