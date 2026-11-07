package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import cy.jdkdigital.productivebees.common.recipe.AdvancedBeehiveRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe;
import cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe;
import cy.jdkdigital.productivebees.compat.jei.ingredients.BeeIngredient;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Genuine 12.6.0 constructor signatures, including native Lazy<JEI BeeIngredient> parameters. */
class UnsupportedBeeNativeMigrationTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void breedingRejectsNativeParentListsAndDoesNotSynthesizeFreeSelfBreedingEggs() {
        var id = id("breeding");
        var source = new BeeBreedingRecipe(id,
                List.of(bee("productivebees:iron"), bee("productivebees:gold")), bee("productivebees:diamond"));
        assertEquals(2, source.ingredients.size());
        assertRejected(new BeeBreedingRecipeAdapter(), new RecipeHolder<>(id, source));
        var self = new BeeBreedingRecipe(id, List.of(bee("productivebees:iron"), bee("productivebees:iron")),
                bee("productivebees:iron"));
        assertRejected(new BeeBreedingRecipeAdapter(), new RecipeHolder<>(id, self));
    }

    @Test
    void conversionRejectsBothProbabilisticAndGuaranteedNativeEntityInteractions() {
        ItemStack food = new ItemStack(Items.HONEY_BOTTLE, 7);
        food.getOrCreateTag().putString("grade", "required");
        Ingredient predicate = StrictNBTIngredient.of(food);
        for (int chance : List.of(0, 25, 100)) {
            var id = id("conversion_" + chance);
            var source = new BeeConversionRecipe(id, bee("minecraft:bee"), bee("productivebees:iron"), predicate, chance);
            assertSame(predicate, source.item);
            assertEquals(chance, source.chance);
            assertRejected(new BeeConversionRecipeAdapter(), new RecipeHolder<>(id, source));
        }
        assertEquals("required", food.getTag().getString("grade"));
        assertEquals(7, food.getCount());
    }

    @Test
    void productionRejectsTheNativeHiveTableInsteadOfUsingItAsAFreeWaterAndEggMoldRecipe() {
        var id = id("produce");
        ItemStack output = new ItemStack(Items.HONEYCOMB, 13);
        output.getOrCreateTag().putString("bee_type", "productivebees:iron");
        var source = new AdvancedBeehiveRecipe(id, bee("productivebees:iron"),
                Map.of(StrictNBTIngredient.of(output), new IntArrayTag(new int[]{2, 3, 25})));
        assertRejected(new BeeProduceRecipeAdapter(), new RecipeHolder<>(id, source));
        assertEquals(13, output.getCount());
        assertEquals("productivebees:iron", output.getTag().getString("bee_type"));
    }

    @Test
    void fishingRejectsBiomeBoundAndUnrestrictedRecipesRegardlessOfChanceAndTool() {
        for (List<String> biomes : List.of(List.of("minecraft:ocean"), List.<String>of())) {
            for (double chance : List.of(0.001, 0.5, 1.0)) {
                var id = id("fishing");
                var source = new BeeFishingRecipe(id, bee("productivebees:water"), biomes, chance);
                assertEquals(biomes, source.biomes);
                assertEquals(chance, source.chance);
                assertRejected(new BeeFishingRecipeAdapter(), new RecipeHolder<>(id, source));
            }
        }
    }

    @Test
    void rejectionNeverResolvesLazyBeeEntitiesOrDisplayOutputsEvenForConcreteCallerOverloads() {
        AtomicInteger resolutions = new AtomicInteger();
        Lazy<BeeIngredient> dangerous = Lazy.of(() -> {
            resolutions.incrementAndGet();
            throw new AssertionError("No entity/display fallback is permitted for an unsupported recipe");
        });
        var id = id("unresolved");
        assertRejected(new BeeBreedingRecipeAdapter(), new RecipeHolder<>(id,
                new BeeBreedingRecipe(id, List.of(dangerous, dangerous), dangerous)));
        assertRejected(new BeeConversionRecipeAdapter(), new RecipeHolder<>(id,
                new BeeConversionRecipe(id, dangerous, dangerous, Ingredient.of(Items.HONEY_BOTTLE), 100)));
        assertRejected(new BeeFishingRecipeAdapter(), new RecipeHolder<>(id,
                new BeeFishingRecipe(id, dangerous, List.of(), 1.0)));
        assertRejected(new BeeProduceRecipeAdapter(), new RecipeHolder<>(id,
                new AdvancedBeehiveRecipe(id, dangerous, Map.of(Ingredient.of(Items.HONEYCOMB),
                        new IntArrayTag(new int[]{1, 1, 100})))));
        assertEquals(0, resolutions.get());
    }

    private static Lazy<BeeIngredient> bee(String type) {
        // Construct real native ingredients too; no entity creation, client renderer or JEI list.
        BeeIngredient ingredient = new BeeIngredient(EntityType.BEE, new ResourceLocation(type), true);
        return Lazy.of(() -> ingredient);
    }

    private static <T extends Recipe<?>> void assertRejected(IRecipeAdapter<T> adapter, RecipeHolder<T> holder) {
        Level level = mock(Level.class);
        ItemStack mold = new ItemStack(Items.BEE_SPAWN_EGG);
        mold.getOrCreateTagElement("EntityTag").putString("type", "productivebees:iron");
        ItemStack food = new ItemStack(Items.HONEY_BOTTLE, 64);
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.setDamageValue(17);
        rod.getOrCreateTag().putString("tool_state", "must not be ignored");
        var before = rod.getTag().copy();
        List<ItemStack> actual = List.of(food, mold, mold.copy(), rod);
        Map<Ingredient, Long> inputs = Map.of(Ingredient.of(food), 64L, Ingredient.of(mold), 2L);
        Map<FluidStack, Long> fluids = Map.of(new FluidStack(Fluids.WATER, 1), 1_000_000L);
        Map<AEKey, Long> keys = Map.of(AEItemKey.of(food), 64L, AEItemKey.of(mold), 2L);

        assertEquals(RecipeSourceIds.PRODUCTIVE_BEES, adapter.sourceId());
        assertTrue(adapter.getRecipeClass().isInstance(holder.value()));
        assertTrue(adapter.convertAll(holder, level).isEmpty());
        assertTrue(adapter.convertAll(holder, level, actual).isEmpty());
        assertTrue(adapter.convertAll(holder, null).isEmpty());
        assertTrue(adapter.convertAll(null, level).isEmpty());
        assertNull(adapter.convert(holder, level));
        assertTrue(adapter.getGeneratedRecipes(level).isEmpty());
        assertTrue(adapter.getGeneratedRecipes(null).isEmpty());
        assertNull(adapter.getMoldItem());
        for (ItemStack attemptedMold : List.of(mold, rod, food)) {
            assertFalse(adapter.matchesMold(attemptedMold));
            assertTrue(adapter.findMatchingRecipes(level, inputs, fluids, attemptedMold).isEmpty());
            assertTrue(adapter.findMatchingRecipes(level, inputs, fluids, keys, attemptedMold).isEmpty());
            assertTrue(adapter.findMatchingRecipes(level, inputs, fluids, keys, attemptedMold, actual).isEmpty());
            assertNull(adapter.findMatchingRecipe(level, inputs, fluids, attemptedMold));
            assertNull(adapter.findMatchingRecipe(level, inputs, fluids, keys, attemptedMold));
        }
        assertFalse(adapter.matchesMold(null));
        assertTrue(adapter.findMatchingRecipes(null, null, null, null).isEmpty());
        assertEquals(before, rod.getTag());
        verifyNoInteractions(level);
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("test", "pb_" + path); }
}
