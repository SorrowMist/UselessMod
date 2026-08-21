package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewingRecipeAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsVanillaPotionMixToThreeBottles() {
        BrewingRecipeAdapter adapter = new BrewingRecipeAdapter();
        AdvancedAlloyFurnaceRecipe recipe = findRecipe(
                vanillaRecipes(),
                potion(Items.POTION, Potions.AWKWARD, 3),
                new ItemStack(Items.GOLDEN_CARROT));

        assertNotNull(recipe);
        assertEquals(3L, recipe.inputs().getFirst().count());
        assertEquals(1L, recipe.inputs().get(1).count());
        assertEquals(3, recipe.outputs().getFirst().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(
                recipe.outputs().getFirst(), potion(Items.POTION, Potions.NIGHT_VISION, 3)));
        assertEquals(AdapterUtils.DEFAULT_ENERGY, recipe.energy());
        assertEquals(PotionBrewing.BREWING_TIME_SECONDS * 20, recipe.processTime());
        assertTrue(adapter.matchesMold(new ItemStack(Items.BREWING_STAND)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.FURNACE)));
    }

    @Test
    void includesVanillaContainerConversionsForAllPotionHolders() {
        List<RecipeHolder<BrewingSyntheticRecipe>> recipes = vanillaRecipes();

        AdvancedAlloyFurnaceRecipe splash = findRecipe(
                recipes,
                potion(Items.POTION, Potions.AWKWARD, 3),
                new ItemStack(Items.GUNPOWDER));
        AdvancedAlloyFurnaceRecipe lingering = findRecipe(
                recipes,
                potion(Items.SPLASH_POTION, Potions.AWKWARD, 3),
                new ItemStack(Items.DRAGON_BREATH));

        assertNotNull(splash);
        assertNotNull(lingering);
        assertTrue(ItemStack.isSameItemSameComponents(
                splash.outputs().getFirst(), potion(Items.SPLASH_POTION, Potions.AWKWARD, 3)));
        assertTrue(ItemStack.isSameItemSameComponents(
                lingering.outputs().getFirst(), potion(Items.LINGERING_POTION, Potions.AWKWARD, 3)));
    }

    @Test
    void includesNewVanillaStartMixes() {
        List<StartMix> startMixes = List.of(
                new StartMix(Items.BREEZE_ROD, Potions.WIND_CHARGED),
                new StartMix(Items.SLIME_BLOCK, Potions.OOZING),
                new StartMix(Items.STONE, Potions.INFESTED),
                new StartMix(Items.COBWEB, Potions.WEAVING));

        for (StartMix startMix : startMixes) {
            AdvancedAlloyFurnaceRecipe recipe = findRecipe(
                    vanillaRecipes(), potion(Items.POTION, Potions.AWKWARD, 3),
                    new ItemStack(startMix.reagent()));
            assertNotNull(recipe, startMix.reagent().toString());
            assertTrue(ItemStack.isSameItemSameComponents(
                    recipe.outputs().getFirst(), potion(Items.POTION, startMix.output(), 3)));
        }
    }

    @Test
    void requiresThreeIdenticalPotionInputsAndExactPotionData() {
        List<RecipeHolder<BrewingSyntheticRecipe>> recipes = vanillaRecipes();

        assertNotNull(findRecipe(recipes,
                potion(Items.POTION, Potions.AWKWARD, 3),
                new ItemStack(Items.GOLDEN_CARROT)));
        assertNullRecipe(findRecipe(recipes,
                potion(Items.POTION, Potions.WATER, 3),
                new ItemStack(Items.GOLDEN_CARROT)));
        assertNullRecipe(findRecipe(recipes,
                potion(Items.POTION, Potions.AWKWARD, 2),
                new ItemStack(Items.GOLDEN_CARROT)));
        assertNullRecipe(findRecipe(recipes,
                List.of(potion(Items.POTION, Potions.AWKWARD, 2),
                        potion(Items.POTION, Potions.WATER, 1),
                        new ItemStack(Items.GOLDEN_CARROT))));
    }

    @Test
    void convertsConcreteBrewingRecipeIntoStaticRecipe() {
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addRecipe(new BrewingRecipe(
                Ingredient.of(Items.POTION), Ingredient.of(Items.QUARTZ), new ItemStack(Items.DIAMOND)));
        PotionBrewing brewing = builder.build();

        AdvancedAlloyFurnaceRecipe recipe = findRecipe(
                BrewingRecipeAdapter.createStaticRecipes(
                        brewing, RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS),
                potion(Items.POTION, Potions.WATER, 3),
                new ItemStack(Items.QUARTZ));

        assertNotNull(recipe);
        assertEquals(Items.DIAMOND, recipe.outputs().getFirst().getItem());
        assertEquals(3, recipe.outputs().getFirst().getCount());
    }

    @Test
    void includesPotionMixesRegisteredOnTheBrewingBuilder() {
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addMix(Potions.WATER, Items.QUARTZ, Potions.NIGHT_VISION);
        PotionBrewing brewing = builder.build();

        AdvancedAlloyFurnaceRecipe recipe = findRecipe(
                BrewingRecipeAdapter.createStaticRecipes(
                        brewing, RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS),
                potion(Items.POTION, Potions.WATER, 3),
                new ItemStack(Items.QUARTZ));

        assertNotNull(recipe);
        assertTrue(ItemStack.isSameItemSameComponents(
                recipe.outputs().getFirst(), potion(Items.POTION, Potions.NIGHT_VISION, 3)));
    }

    @Test
    void runsBlackBoxBrewingRecipeWithoutAddingItToStaticRecipes() {
        IBrewingRecipe blackBox = new IBrewingRecipe() {
            @Override
            public boolean isInput(ItemStack input) {
                return input.is(Items.POTION);
            }

            @Override
            public boolean isIngredient(ItemStack ingredient) {
                return ingredient.is(Items.QUARTZ);
            }

            @Override
            public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                return isInput(input) && isIngredient(ingredient)
                        ? new ItemStack(Items.DIAMOND)
                        : ItemStack.EMPTY;
            }
        };
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addRecipe(blackBox);
        PotionBrewing brewing = builder.build();

        List<RecipeHolder<BrewingSyntheticRecipe>> staticRecipes =
                BrewingRecipeAdapter.createStaticRecipes(
                        brewing, RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS);
        assertTrue(brewing.hasMix(
                potion(Items.POTION, Potions.WATER, 1), new ItemStack(Items.QUARTZ)));
        List<RecipeHolder<BrewingSyntheticRecipe>> runtimeRecipes =
                BrewingRecipeAdapter.findRuntimeRecipes(
                        brewing,
                        List.of(potion(Items.POTION, Potions.WATER, 3),
                                new ItemStack(Items.QUARTZ)),
                        RegistryAccess.EMPTY);

        assertTrue(staticRecipes.stream().noneMatch(holder ->
                holder.value().convertedRecipe().outputs().stream()
                        .anyMatch(output -> output.is(Items.DIAMOND))));
        assertEquals(1, runtimeRecipes.size());
        AdvancedAlloyFurnaceRecipe runtime = runtimeRecipes.getFirst().value().convertedRecipe();
        assertEquals(Items.DIAMOND, runtime.outputs().getFirst().getItem());
        assertEquals(3, runtime.outputs().getFirst().getCount());
        assertEquals(3L, runtime.inputs().getFirst().count());
    }

    @Test
    void keepsEquivalentRecipeIdsStable() {
        List<RecipeHolder<BrewingSyntheticRecipe>> first = vanillaRecipes();
        List<RecipeHolder<BrewingSyntheticRecipe>> second = vanillaRecipes();

        AdvancedAlloyFurnaceRecipe firstRecipe = findRecipe(
                first, potion(Items.POTION, Potions.AWKWARD, 3),
                new ItemStack(Items.GOLDEN_CARROT));
        AdvancedAlloyFurnaceRecipe secondRecipe = findRecipe(
                second, potion(Items.POTION, Potions.AWKWARD, 3),
                new ItemStack(Items.GOLDEN_CARROT));

        assertNotNull(firstRecipe);
        assertNotNull(secondRecipe);
        assertEquals(firstRecipe.id(), secondRecipe.id());
    }

    private static List<RecipeHolder<BrewingSyntheticRecipe>> vanillaRecipes() {
        PotionBrewing brewing = PotionBrewing.bootstrap(
                FeatureFlags.DEFAULT_FLAGS, RegistryAccess.EMPTY);
        return BrewingRecipeAdapter.createStaticRecipes(
                brewing, RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS);
    }

    private static AdvancedAlloyFurnaceRecipe findRecipe(
            List<RecipeHolder<BrewingSyntheticRecipe>> candidates,
            ItemStack input, ItemStack ingredient) {
        return findRecipe(candidates, List.of(input, ingredient));
    }

    private static AdvancedAlloyFurnaceRecipe findRecipe(
            List<RecipeHolder<BrewingSyntheticRecipe>> candidates,
            List<ItemStack> inputs) {
        return candidates.stream()
                .map(holder -> holder.value().convertedRecipe())
                .filter(recipe -> ItemIngredientAllocator.matches(recipe.inputs(), inputs, 1L))
                .findFirst()
                .orElse(null);
    }

    private static ItemStack potion(Item item, Holder<Potion> potion, int count) {
        return PotionContents.createItemStack(item, potion).copyWithCount(count);
    }

    private static void assertNullRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        assertTrue(recipe == null);
    }

    private record StartMix(Item reagent, Holder<Potion> output) {
    }
}
