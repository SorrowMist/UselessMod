package com.sorrowmist.useless.content.recipe.adapters.extendedcrafting;

import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeTableRemainderTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static ShapedTableRecipe recipe(int width,int height,Ingredient ingredient) {
        return new ShapedTableRecipe(new ResourceLocation("test:table"),width,height,
                NonNullList.withSize(width*height,ingredient),new ItemStack(Items.DIAMOND));
    }
    // Official Extended Crafting ends at tier 4. The pack's unofficial build adds tier 5
    // with the same native remainder contract; mock only that version-specific tier answer.
    private static ShapedTableRecipe epicRecipe(Ingredient ingredient) {
        var recipe = spy(recipe(11, 11, ingredient));
        doReturn(5).when(recipe).getTier();
        return recipe;
    }
    @Test void repeatedTagAlternativesDoNotMultiplyUnrelatedSlots() {
        var recipe=recipe(9,9,Ingredient.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT));
        assertEquals(List.of(),ExtendedCraftingTableRecipeAdapter.tableRemainders(recipe,recipe.getIngredients()).orElseThrow());
    }
    @Test void epicTableSupportsAll121SlotsWithTagAlternatives() {
        var recipe=epicRecipe(Ingredient.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT));
        assertEquals(5,recipe.getTier());
        assertEquals(121,ExtendedCraftingTableRecipeAdapter.tableCapacity(recipe.getTier()));
        assertTrue(recipe.getIngredients().size() <= ExtendedCraftingTableRecipeAdapter.tableCapacity(recipe.getTier()));
        assertEquals(List.of(),ExtendedCraftingTableRecipeAdapter.tableRemainders(recipe,recipe.getIngredients()).orElseThrow());
    }
    @Test void epicTableTransformerPreservesElevenColumnPositions() {
        var recipe=epicRecipe(Ingredient.of(Items.IRON_INGOT));
        recipe.setTransformer((x,y,stack) -> new ItemStack(Items.GOLD_NUGGET,y+1));
        var outputs=ExtendedCraftingTableRecipeAdapter.tableRemainders(recipe,recipe.getIngredients()).orElseThrow();
        assertEquals(726,outputs.stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(outputs.stream().allMatch(s -> s.is(Items.GOLD_NUGGET)));
    }
    @Test void nativeBucketsReturnOneContainerPerConsumedSlot() {
        var recipe=recipe(5,5,Ingredient.of(Items.WATER_BUCKET,Items.LAVA_BUCKET));
        var outputs=ExtendedCraftingTableRecipeAdapter.tableRemainders(recipe,recipe.getIngredients()).orElseThrow();
        assertEquals(1,outputs.size());assertTrue(outputs.get(0).is(Items.BUCKET));assertEquals(25,outputs.get(0).getCount());
    }
    @Test void candidateDependentContainerIsRejected() {
        var recipe=recipe(3,3,Ingredient.of(Items.WATER_BUCKET,Items.IRON_INGOT));
        assertTrue(ExtendedCraftingTableRecipeAdapter.tableRemainders(recipe,recipe.getIngredients()).isEmpty());
    }
    @Test void rectangularTransformerUsesRealSquareTableGrid() {
        var recipe=recipe(2,3,Ingredient.of(Items.IRON_INGOT));
        recipe.setTransformer((x,y,stack) -> new ItemStack(Items.GOLD_NUGGET,y+1));
        var outputs=ExtendedCraftingTableRecipeAdapter.tableRemainders(recipe,recipe.getIngredients()).orElseThrow();
        assertEquals(1,outputs.size());assertEquals(12,outputs.get(0).getCount());
    }
    @Test void customCoupledResolverStillRejectsUnboundedCombinations() {
        var slots=Collections.nCopies(9,Ingredient.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT));
        assertTrue(ExtendedCraftingAdapterUtils.deterministicRemainders(slots,Set.of(),
                values -> new net.minecraft.world.SimpleContainer(values.toArray(ItemStack[]::new)),
                values -> List.of()).isEmpty());
    }
}
