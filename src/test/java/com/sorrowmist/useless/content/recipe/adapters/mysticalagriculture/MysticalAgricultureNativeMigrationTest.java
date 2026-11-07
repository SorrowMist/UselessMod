package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe;
import com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe;
import com.blakebr0.mysticalagriculture.crafting.recipe.InfusionRecipe;
import com.blakebr0.mysticalagriculture.crafting.recipe.AwakeningRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MysticalAgricultureNativeMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void awakeningDoesNotDoubleCountInterleavedEssencesAndKeepsTheirRealCountAndNbt() {
        var ingredients = NonNullList.withSize(5, Ingredient.of(Items.GOLD_INGOT));
        var essence = new ItemStack(Items.DIAMOND, 40); essence.getOrCreateTag().putString("essence", "exact");
        var source = new AwakeningRecipe(new ResourceLocation("test", "awakening_source"), ingredients,
                NonNullList.of(ItemStack.EMPTY, essence, essence.copy(), essence.copy(), essence.copy()),
                new ItemStack(Items.EMERALD, 3), false);
        var converted = new AwakeningRecipeAdapter().convertAll(new RecipeHolder<>(new ResourceLocation("test", "awakening"), source), null).get(0);
        assertEquals(165, converted.inputs().stream().mapToLong(input -> input.count()).sum());
        var gold = converted.inputs().stream().filter(input -> input.ingredient().test(new ItemStack(Items.GOLD_INGOT))).findFirst().orElseThrow();
        assertEquals(5, gold.count()); // altar plus four pedestals, not nine pedestal inputs
        var exact = converted.inputs().stream().filter(input -> input.ingredient().test(essence)).toList();
        assertEquals(160, exact.stream().mapToLong(input -> input.count()).sum());
        assertTrue(exact.stream().noneMatch(input -> input.ingredient().test(new ItemStack(Items.DIAMOND))));
        assertEquals(3, converted.outputs().get(0).getCount());
    }

    @Test void infusionIncludesTheAltarItemExactlyOnceAndCopiesOutputNbt() {
        var ingredients = NonNullList.withSize(9, Ingredient.of(Items.GOLD_INGOT));
        ingredients.set(0, Ingredient.of(Items.DIAMOND));
        var output = new ItemStack(Items.EMERALD, 2); output.getOrCreateTag().putInt("quality", 5);
        var source = new InfusionRecipe(new ResourceLocation("test", "infusion_source"), ingredients, output, false);
        var converted = new InfusionRecipeAdapter().convertAll(new RecipeHolder<>(new ResourceLocation("test", "infusion"), source), null).get(0);
        assertEquals(9, converted.inputs().stream().mapToLong(input -> input.count()).sum());
        assertEquals(1, converted.inputs().stream().filter(input -> input.ingredient().test(new ItemStack(Items.DIAMOND))).findFirst().orElseThrow().count());
        output.getOrCreateTag().putInt("quality", 99);
        assertEquals(5, converted.outputs().get(0).getTag().getInt("quality"));
        assertEquals(2, converted.outputs().get(0).getCount());
    }

    @Test void nativeTransferNbtInfusionCannotLoseTheAltarItemsDataThroughItsDisplayResult() {
        var id = new ResourceLocation("test", "transfer_nbt");
        var source = new InfusionRecipe(id, NonNullList.withSize(9, Ingredient.of(Items.IRON_SWORD)),
                new ItemStack(Items.DIAMOND_SWORD), true);
        var inventory = new net.minecraftforge.items.ItemStackHandler(9);
        var input = new ItemStack(Items.IRON_SWORD); input.getOrCreateTag().putString("owner", "keep-me");
        inventory.setStackInSlot(0, input);
        assertEquals("keep-me", source.assemble(inventory, net.minecraft.core.RegistryAccess.EMPTY).getTag().getString("owner"));
        assertTrue(new InfusionRecipeAdapter().convertAll(new RecipeHolder<>(id, source), null).isEmpty());
    }

    @Test void awakeningTransferNbtAndCustomRemainderTransformersAreNotStaticRecipes() {
        var id = new ResourceLocation("test", "dynamic_awakening");
        var awakening = new AwakeningRecipe(id, NonNullList.withSize(5, Ingredient.of(Items.GOLD_INGOT)),
                NonNullList.withSize(4, new ItemStack(Items.DIAMOND, 40)), new ItemStack(Items.EMERALD), true);
        assertTrue(new AwakeningRecipeAdapter().convertAll(new RecipeHolder<>(id, awakening), null).isEmpty());
        var infusion = new InfusionRecipe(id, NonNullList.withSize(9, Ingredient.of(Items.GOLD_INGOT)),
                new ItemStack(Items.EMERALD), false);
        infusion.setTransformer((slot, stack) -> new ItemStack(Items.BUCKET));
        assertTrue(new InfusionRecipeAdapter().convertAll(new RecipeHolder<>(id, infusion), null).isEmpty());
    }

    @Test void unknownRecipeImplementationsAreNotAssumedToHaveStaticOutputs() {
        var source = mock(IInfusionRecipe.class);
        assertTrue(new InfusionRecipeAdapter().convertAll(new RecipeHolder<>(new ResourceLocation("test", "unknown"), source), null).isEmpty());
    }
}
