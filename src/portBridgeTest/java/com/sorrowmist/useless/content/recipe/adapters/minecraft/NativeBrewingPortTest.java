package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.common.brewing.VanillaBrewingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeBrewingPortTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static ItemStack potion() { return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD); }

    @Test void vanillaPotionAndContainerMixesUseNativePotionNbtAndThreeBottleBatch() {
        var source = new VanillaBrewingRecipe();
        var input = potion().copyWithCount(3);
        var reagent = new ItemStack(Items.BLAZE_POWDER);
        var recipes = BrewingRecipeAdapter.findRuntimeRecipes(List.of(source), List.of(input, reagent), RegistryAccess.EMPTY);
        assertEquals(1, recipes.size());
        var recipe = recipes.get(0).value().convertedRecipe();
        assertEquals(3, recipe.outputs().get(0).getCount());
        assertEquals(Potions.STRENGTH, PotionUtils.getPotion(recipe.outputs().get(0)));
        assertEquals(400, recipe.processTime());
        assertTrue(ItemIngredientAllocator.matches(recipe.inputs(), List.of(input, reagent), 1));
        assertFalse(ItemIngredientAllocator.matches(recipe.inputs(), List.of(input.copyWithCount(2), reagent), 1));
        assertEquals(Potions.AWKWARD, PotionUtils.getPotion(input));
        var container = BrewingRecipeAdapter.findRuntimeRecipes(List.of(source),
                List.of(input, new ItemStack(Items.GUNPOWDER)), RegistryAccess.EMPTY);
        assertEquals(1, container.size());
        assertTrue(container.get(0).value().convertedRecipe().outputs().get(0).is(Items.SPLASH_POTION));
        assertEquals(Potions.AWKWARD, PotionUtils.getPotion(container.get(0).value().convertedRecipe().outputs().get(0)));
    }

    @Test void forgeSimpleRecipeKeepsExactForeignNbtCountsAndActualConsumption() {
        var input = new ItemStack(Items.GLASS_BOTTLE);
        input.getOrCreateTag().putString("foreign:mixture", "special");
        var reagent = new ItemStack(Items.REDSTONE);
        reagent.getOrCreateTag().putInt("foreign:tier", 5);
        var output = new ItemStack(Items.DIAMOND, 200);
        output.getOrCreateTag().putLong("foreign:progress", 9876543210L);
        var source = new BrewingRecipe(DataComponentIngredient.of(true, input),
                DataComponentIngredient.of(true, reagent), output);
        var recipes = BrewingRecipeAdapter.findRuntimeRecipes(List.of(source),
                List.of(input.copyWithCount(3), reagent), RegistryAccess.EMPTY);
        assertEquals(1, recipes.size());
        var recipe = recipes.get(0).value().convertedRecipe();
        assertEquals(600, recipe.outputs().get(0).getCount());
        assertEquals(output.getTag(), recipe.outputs().get(0).getTag());
        var allocated = ItemIngredientAllocator.allocate(recipe.inputs(), List.of(input.copyWithCount(3), reagent), 1);
        assertNotNull(allocated); assertEquals(3, allocated.consumedFromInput(0)); assertEquals(1, allocated.consumedFromInput(1));
        assertFalse(ItemIngredientAllocator.matches(recipe.inputs(),
                List.of(new ItemStack(Items.GLASS_BOTTLE, 3), reagent), 1));
        recipe.outputs().get(0).getTag().putLong("foreign:progress", 0);
        assertEquals(9876543210L, output.getTag().getLong("foreign:progress"));
    }

    @Test void arbitraryModBrewingIsEvaluatedForActualNbtInsteadOfFlattenedDisplayCandidates() {
        IBrewingRecipe source = new IBrewingRecipe() {
            @Override public boolean isInput(ItemStack stack) { return stack.is(Items.GLASS_BOTTLE) && stack.hasTag(); }
            @Override public boolean isIngredient(ItemStack stack) { return stack.is(Items.REDSTONE); }
            @Override public ItemStack getOutput(ItemStack input, ItemStack reagent) {
                if (!isInput(input) || !isIngredient(reagent)) return ItemStack.EMPTY;
                var output = new ItemStack(Items.EXPERIENCE_BOTTLE);
                output.setTag(input.getTag().copy());
                return output;
            }
        };
        var input = new ItemStack(Items.GLASS_BOTTLE, 3);
        input.getOrCreateTag().putLong("foreign:dynamic", 77);
        var reagent = new ItemStack(Items.REDSTONE);
        var a = BrewingRecipeAdapter.findRuntimeRecipes(List.of(source), List.of(input, reagent), RegistryAccess.EMPTY);
        assertEquals(1, a.size());
        assertEquals(77, a.get(0).value().convertedRecipe().outputs().get(0).getTag().getLong("foreign:dynamic"));
        input.getOrCreateTag().putLong("foreign:dynamic", 88);
        var b = BrewingRecipeAdapter.findRuntimeRecipes(List.of(source), List.of(input, reagent), RegistryAccess.EMPTY);
        assertNotEquals(a.get(0).id(), b.get(0).id());
        assertEquals(88, b.get(0).value().convertedRecipe().outputs().get(0).getTag().getLong("foreign:dynamic"));
        assertTrue(BrewingRecipeAdapter.findRuntimeRecipes(List.of(source),
                List.of(new ItemStack(Items.GLASS_BOTTLE, 3), reagent), RegistryAccess.EMPTY).isEmpty());
    }

    @Test void orderedSnapshotMatchesActualForgeRegistryPriority() throws ReflectiveOperationException {
        var first = new BrewingRecipe(Ingredient.of(Items.GLASS_BOTTLE), Ingredient.of(Items.COAL), new ItemStack(Items.DIAMOND));
        var second = new BrewingRecipe(Ingredient.of(Items.GLASS_BOTTLE), Ingredient.of(Items.COAL), new ItemStack(Items.EMERALD));
        var original = List.copyOf(BrewingRecipeRegistry.getRecipes());
        // Forge deliberately exposes an unmodifiable view. Test-only reflective cleanup is
        // needed because its public API permits registration but has no removal method.
        var field = BrewingRecipeRegistry.class.getDeclaredField("recipes");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var mutable = (List<IBrewingRecipe>) field.get(null);
        // Add and remove only our two real registry entries. The native registry chooses first.
        BrewingRecipeRegistry.addRecipe(first); BrewingRecipeRegistry.addRecipe(second);
        try {
            var nativeResult = BrewingRecipeRegistry.getOutput(new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.COAL));
            var result = BrewingRecipeAdapter.findRuntimeRecipes(BrewingRecipeRegistry.getRecipes(),
                    List.of(new ItemStack(Items.GLASS_BOTTLE, 3), new ItemStack(Items.COAL)), RegistryAccess.EMPTY);
            assertEquals(1, result.size());
            assertEquals(nativeResult.getItem(), result.get(0).value().convertedRecipe().outputs().get(0).getItem());
            assertTrue(nativeResult.is(Items.DIAMOND));
        } finally {
            mutable.remove(first); mutable.remove(second);
            assertEquals(original, BrewingRecipeRegistry.getRecipes());
        }
    }

    @Test void sharedInputAndReagentCannotDoubleSpendOneStackAndOutputOverflowIsRejected() {
        var source = new BrewingRecipe(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND));
        assertTrue(BrewingRecipeAdapter.findRuntimeRecipes(List.of(source), List.of(new ItemStack(Items.IRON_INGOT, 3)), RegistryAccess.EMPTY).isEmpty());
        var result = BrewingRecipeAdapter.findRuntimeRecipes(List.of(source), List.of(new ItemStack(Items.IRON_INGOT, 4)), RegistryAccess.EMPTY);
        assertEquals(1, result.size());
        var consumed = ItemIngredientAllocator.allocate(result.get(0).value().convertedRecipe().inputs(),
                List.of(new ItemStack(Items.IRON_INGOT, 4)), 1);
        assertNotNull(consumed); assertEquals(4, consumed.consumedFromInput(0));
        var tooLarge = new BrewingRecipe(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.COAL), new ItemStack(Items.DIAMOND, Integer.MAX_VALUE));
        assertTrue(BrewingRecipeAdapter.findRuntimeRecipes(List.of(tooLarge),
                List.of(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.COAL)), RegistryAccess.EMPTY).isEmpty());
    }

    @Test void staticCatalogueKeepsOnlyNativeAcceptedPairsAndOutputNbtChangesItsStableId() {
        var source = new BrewingRecipe(Ingredient.of(Items.GLASS_BOTTLE), Ingredient.of(Items.REDSTONE), new ItemStack(Items.DIAMOND));
        var a = BrewingRecipeAdapter.createStaticRecipes(List.of(source), RegistryAccess.EMPTY,
                List.of(new ItemStack(Items.GLASS_BOTTLE)), List.of(new ItemStack(Items.REDSTONE), new ItemStack(Items.COAL)));
        assertEquals(1, a.size());
        var b = BrewingRecipeAdapter.createStaticRecipes(List.of(source), RegistryAccess.EMPTY,
                List.of(new ItemStack(Items.GLASS_BOTTLE)), List.of(new ItemStack(Items.COAL), new ItemStack(Items.REDSTONE)));
        assertEquals(a.get(0).id(), b.get(0).id());
        var output = new ItemStack(Items.DIAMOND); output.getOrCreateTag().putString("foreign:new", "x");
        var changed = new BrewingRecipe(Ingredient.of(Items.GLASS_BOTTLE), Ingredient.of(Items.REDSTONE), output);
        var c = BrewingRecipeAdapter.createStaticRecipes(List.of(changed), RegistryAccess.EMPTY,
                List.of(new ItemStack(Items.GLASS_BOTTLE)), List.of(new ItemStack(Items.REDSTONE)));
        assertNotEquals(a.get(0).id(), c.get(0).id());
    }
}
