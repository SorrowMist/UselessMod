package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmeltingRecipeAdapterTest {
    private static Unsafe unsafe;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
    }

    @Test
    void convertsSmeltingBlastingAndSmokingRecipesWithDedicatedMolds()
            throws ReflectiveOperationException {
        assertConverted(
                new SmeltingRecipeAdapter(RecipeType.SMELTING, Items.FURNACE),
                holder("smelting", smelting(200, 1)), 200, Items.FURNACE);
        assertConverted(
                new SmeltingRecipeAdapter(RecipeType.BLASTING, Items.BLAST_FURNACE),
                holder("blasting", blasting(100, 2)), 100, Items.BLAST_FURNACE);
        assertConverted(
                new SmeltingRecipeAdapter(RecipeType.SMOKING, Items.SMOKER),
                holder("smoking", smoking(100, 3)), 100, Items.SMOKER);
    }

    @Test
    void rejectsWrongCookingTypeAndMold() throws ReflectiveOperationException {
        RecipeHolder<AbstractCookingRecipe> smelting = holder("smelting", smelting(200, 1));
        SmeltingRecipeAdapter blasting = new SmeltingRecipeAdapter(
                RecipeType.BLASTING, Items.BLAST_FURNACE);

        assertTrue(blasting.convertAll(smelting, testLevel()).isEmpty());
        assertFalse(blasting.matchesMold(new ItemStack(Items.FURNACE)));
        assertFalse(blasting.matchesMold(new ItemStack(Items.SMOKER)));
        assertFalse(blasting.matchesMold(ItemStack.EMPTY));
        assertTrue(blasting.matchesMold(new ItemStack(Items.BLAST_FURNACE)));
    }

    @Test
    void runtimeLookupUsesCookingTypeAndDedicatedMold() throws ReflectiveOperationException {
        RecipeHolder<AbstractCookingRecipe> smelting = holder("smelting", smelting(200, 1));
        RecipeHolder<AbstractCookingRecipe> blasting = holder("blasting", blasting(100, 2));
        RecipeHolder<AbstractCookingRecipe> smoking = holder("smoking", smoking(100, 3));
        Level level = levelWithRecipes(List.of(smelting, blasting, smoking));
        Map<Ingredient, Long> inputs = AdapterUtils.mergeInputs(List.of(new ItemStack(Items.IRON_ORE)));

        assertRuntimeMatch(new SmeltingRecipeAdapter(RecipeType.SMELTING, Items.FURNACE),
                level, inputs, smelting, Items.FURNACE);
        assertRuntimeMatch(new SmeltingRecipeAdapter(RecipeType.BLASTING, Items.BLAST_FURNACE),
                level, inputs, blasting, Items.BLAST_FURNACE);
        assertRuntimeMatch(new SmeltingRecipeAdapter(RecipeType.SMOKING, Items.SMOKER),
                level, inputs, smoking, Items.SMOKER);

        SmeltingRecipeAdapter adapter = new SmeltingRecipeAdapter(RecipeType.BLASTING, Items.BLAST_FURNACE);
        assertTrue(adapter.findMatchingRecipes(
                level, inputs, Map.of(), new ItemStack(Items.FURNACE)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(
                level, AdapterUtils.mergeInputs(List.of(new ItemStack(Items.GOLD_ORE))),
                Map.of(), new ItemStack(Items.BLAST_FURNACE)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(null, inputs, Map.of(), new ItemStack(Items.BLAST_FURNACE)).isEmpty());
    }

    @Test
    void catalogIncludesAllVanillaCookingTypes() throws ReflectiveOperationException {
        RecipeHolder<AbstractCookingRecipe> smelting = holder("catalog_smelting", smelting(200, 1));
        RecipeHolder<AbstractCookingRecipe> blasting = holder("catalog_blasting", blasting(100, 2));
        RecipeHolder<AbstractCookingRecipe> smoking = holder("catalog_smoking", smoking(100, 3));
        Level level = levelWithRecipes(List.of(smelting, blasting, smoking));

        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new SmeltingRecipeAdapter(RecipeType.SMELTING, Items.FURNACE));
        manager.registerAdapter(new SmeltingRecipeAdapter(RecipeType.BLASTING, Items.BLAST_FURNACE));
        manager.registerAdapter(new SmeltingRecipeAdapter(RecipeType.SMOKING, Items.SMOKER));
        AlloyFurnaceRecipeCatalog.invalidate();

        List<AlloyFurnaceRecipeCatalog.Entry> entries =
                AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.MINECRAFT);

        assertTrue(entries.stream().anyMatch(entry ->
                entry.recipe().id().equals(AdapterUtils.convertedId(smelting.id()))));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.recipe().id().equals(AdapterUtils.convertedId(blasting.id()))));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.recipe().id().equals(AdapterUtils.convertedId(smoking.id()))));
    }

    private static void assertConverted(
            SmeltingRecipeAdapter adapter,
            RecipeHolder<AbstractCookingRecipe> holder,
            int processTime,
            Item mold) throws ReflectiveOperationException {
        AdvancedAlloyFurnaceRecipe converted = adapter.convert(holder, testLevel());

        assertNotNull(converted);
        assertEquals(1, converted.inputs().size());
        assertEquals(1L, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.IRON_ORE)));
        assertEquals(holder.value().getResultItem(RegistryAccess.EMPTY).getCount(),
                converted.outputs().getFirst().getCount());
        assertEquals(processTime, converted.processTime());
        assertEquals((long) processTime * AdapterUtils.DEFAULT_ENERGY / 200, converted.energy());
        assertTrue(converted.mold().test(new ItemStack(mold)));
        assertEquals(mold, adapter.getMoldItem().getItem());
    }

    private static void assertRuntimeMatch(
            SmeltingRecipeAdapter adapter,
            Level level,
            Map<Ingredient, Long> inputs,
            RecipeHolder<AbstractCookingRecipe> expected,
            Item mold) {
        List<RecipeHolder<AbstractCookingRecipe>> matches = adapter.findMatchingRecipes(
                level, inputs, Map.of(), new ItemStack(mold));

        assertEquals(List.of(expected.id()), matches.stream().map(RecipeHolder::id).toList());
    }

    private static RecipeHolder<AbstractCookingRecipe> holder(String path, AbstractCookingRecipe recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("minecraft_test", path), recipe);
    }

    private static SmeltingRecipe smelting(int cookingTime, int outputCount) {
        return new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE),
                new ItemStack(Items.IRON_INGOT, outputCount), 0, cookingTime);
    }

    private static BlastingRecipe blasting(int cookingTime, int outputCount) {
        return new BlastingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE),
                new ItemStack(Items.GOLD_INGOT, outputCount), 0, cookingTime);
    }

    private static SmokingRecipe smoking(int cookingTime, int outputCount) {
        return new SmokingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE),
                new ItemStack(Items.COPPER_INGOT, outputCount), 0, cookingTime);
    }

    private static Level testLevel() throws ReflectiveOperationException {
        return (Level) unsafe.allocateInstance(TestServerLevel.class);
    }

    private static Level levelWithRecipes(List<RecipeHolder<AbstractCookingRecipe>> recipes)
            throws ReflectiveOperationException {
        RecipeManager recipeManager = new RecipeManager(null);
        recipeManager.replaceRecipes(new ArrayList<RecipeHolder<?>>(recipes));
        TestServerLevel.recipeManager = recipeManager;
        return testLevel();
    }

    private static final class TestServerLevel extends ServerLevel {
        private static RecipeManager recipeManager;

        private TestServerLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null);
        }

        @Override
        public RecipeManager getRecipeManager() {
            return recipeManager;
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.EMPTY;
        }
    }
}
