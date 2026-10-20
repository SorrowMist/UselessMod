package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.core.common.recipes.CountedIngredient;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.machines.common.recipe.AlloySmeltingRecipe;
import com.enderio.machines.common.recipe.SagMillingRecipe;
import com.enderio.machines.common.recipe.SlicingRecipe;
import com.enderio.machines.common.recipe.EnchanterRecipe;
import com.enderio.base.common.util.ExperienceUtil;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real 6.2.13 recipe objects, not mocks of a newer Ender IO interface. */
class EnderIOMachinePortTest {
    private static final ResourceLocation ID = new ResourceLocation("kubejs:enderio_port");
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @Test void alloySmeltingPreservesNativeCountsNbtEnergyAndActualConsumption() {
        var metal = Ingredient.of(Items.IRON_INGOT);
        var output = new ItemStack(Items.DIAMOND, 400);
        output.getOrCreateTag().putString("foreign", "retained");
        var source = new AlloySmeltingRecipe(ID,
                List.of(new CountedIngredient(metal, 200), new CountedIngredient(metal, 50)),
                output, 12000, 0.0F);
        var adapter = spy(new AlloySmeltingRecipeAdapter());
        doReturn(new ItemStack(Items.BRICKS)).when(adapter).getMoldItem();
        var result = adapter.convertAll(new RecipeHolder<>(ID, source), null).get(0);
        assertEquals(250, result.inputs().get(0).count());
        assertEquals(12000, result.energy());
        assertEquals(400, result.outputs().get(0).getCount());
        assertEquals("retained", result.outputs().get(0).getTag().getString("foreign"));
        assertNull(ItemIngredientAllocator.allocate(result.inputs(),
                List.of(new ItemStack(Items.IRON_INGOT, 249)), 1));
        var allocation = ItemIngredientAllocator.allocate(result.inputs(),
                List.of(new ItemStack(Items.IRON_INGOT, 250)), 1);
        assertNotNull(allocation); assertEquals(250, allocation.consumedFromInput(0));
        result.outputs().get(0).getTag().putString("foreign", "changed");
        assertEquals("retained", output.getTag().getString("foreign"));
    }

    @Test void sliceAndSpliceMergesRepeatedSlotsButRetainsAlternatives() {
        var either = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
        var exact = Ingredient.of(Items.IRON_INGOT);
        var source = new SlicingRecipe(ID, new ItemStack(Items.COMPARATOR, 3),
                List.of(either, either, exact, Ingredient.EMPTY), 6000);
        var adapter = spy(new SlicingRecipeAdapter());
        doReturn(new ItemStack(Items.BRICKS)).when(adapter).getMoldItem();
        var result = adapter.convertAll(new RecipeHolder<>(ID, source), null).get(0);
        assertEquals(6000, result.energy());
        assertEquals(3, result.outputs().get(0).getCount());
        assertFalse(ItemIngredientAllocator.matches(result.inputs(),
                List.of(new ItemStack(Items.IRON_INGOT, 2)), 1));
        assertTrue(ItemIngredientAllocator.matches(result.inputs(),
                List.of(new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.IRON_INGOT)), 1));
    }

    @Test void sagRetainsChanceOnlyAndOutputMultiplierSemantics() {
        var chance = sag(SagMillingRecipe.BonusType.CHANCE_ONLY, 0.25F);
        var multiplied = sag(SagMillingRecipe.BonusType.MULTIPLY_OUTPUT, 0.25F);
        var multipliers = new SagMillingRecipeAdapter.GrindingBallMultipliers(1.5, 2.0);
        var a = converted(chance, multipliers, 0.9);
        var b = converted(multiplied, multipliers, 0.9);
        assertNotNull(a); assertNotNull(b);
        // CHANCE_ONLY: one roll at 50%; MULTIPLY_OUTPUT: 1.5 rolls at 50%.
        assertEquals(0.5, (double)a.outputs().get(0).getCount() / a.inputs().get(0).count(), 1e-8);
        assertEquals(0.75, (double)b.outputs().get(0).getCount() / b.inputs().get(0).count(), 1e-8);
        assertEquals((int)(101 * 0.9F) * a.inputs().get(0).count(), a.energy());
        assertEquals((int)(101 * 0.9F) * b.inputs().get(0).count(), b.energy());
    }

    @Test void sagNoneIgnoresBonusAndBatchesNativeFractionalOutputs() {
        var result = converted(sag(SagMillingRecipe.BonusType.NONE, 0.1F),
                new SagMillingRecipeAdapter.GrindingBallMultipliers(3, 4), 1);
        assertNotNull(result);
        assertEquals(10, result.inputs().get(0).count());
        assertEquals(1, result.outputs().get(0).getCount());
        assertEquals(1010, result.energy());
    }

    @Test void sagClampsProbabilityAndDoesNotMutateTaggedSourceOutput() {
        var output = new ItemStack(Items.DIAMOND, 2);
        output.getOrCreateTag().putInt("marker", 42);
        var source = new SagMillingRecipe(ID, Ingredient.of(Items.COAL),
                List.of(SagMillingRecipe.OutputItem.of(output, 0.75F, false)),
                101, SagMillingRecipe.BonusType.CHANCE_ONLY);
        var result = converted(source, new SagMillingRecipeAdapter.GrindingBallMultipliers(1, 2), 1);
        assertNotNull(result);
        assertEquals(1, result.inputs().get(0).count());
        assertEquals(2, result.outputs().get(0).getCount());
        assertEquals(42, result.outputs().get(0).getTag().getInt("marker"));
        result.outputs().get(0).getTag().putInt("marker", 99);
        assertEquals(42, output.getTag().getInt("marker"));
    }

    @Test void invalidNativeInputsAndUnsupportedResultKindsAreNotFlattened() {
        assertNull(EnderIOAdapterUtils.counted(List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 0))));
        assertNull(EnderIOAdapterUtils.itemOutputs(List.of(OutputStack.EMPTY)));
        assertNull(converted(sag(SagMillingRecipe.BonusType.NONE, Float.NaN),
                new SagMillingRecipeAdapter.GrindingBallMultipliers(1, 1), 1));
        assertNull(converted(sag(SagMillingRecipe.BonusType.NONE, 0.25F),
                new SagMillingRecipeAdapter.GrindingBallMultipliers(1, 1), Double.POSITIVE_INFINITY));
    }

    @Test void enchanterGeneratesAllLevelsAndConsumesExperienceFluidRatherThanFe() {
        var source = spy(new EnchanterRecipe(ID,
                new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 2), Enchantments.SHARPNESS, 1));
        // Native config is not loaded in a unit JVM. The running pack supplies these through
        // EnchanterRecipe's actual ForgeConfigSpec getters; exercise both costs distinctly.
        doAnswer(invocation -> (int)invocation.getArgument(0) * 3).when(source).getXPCostForLevel(anyInt());
        doAnswer(invocation -> (int)invocation.getArgument(0) * 2).when(source).getLapisForLevel(anyInt());
        var adapter = spy(new EnchanterRecipeAdapter());
        doReturn(new ItemStack(Items.BRICKS)).when(adapter).getMoldItem();
        doAnswer(invocation -> new FluidStack(Fluids.WATER,
                Math.toIntExact(ExperienceUtil.getFluidFromLevel(invocation.getArgument(0)))))
                .when(adapter).experienceFluid(anyInt());
        var recipes = adapter.convertAll(new RecipeHolder<>(ID, source), null);
        assertEquals(5, recipes.size());
        assertEquals(5, recipes.stream().map(r -> r.id()).distinct().count());
        for (int level = 1; level <= 5; level++) {
            var recipe = recipes.get(level - 1);
            assertEquals(2 * level, recipe.inputs().get(1).count());
            assertEquals(2 * level, recipe.inputs().get(2).count());
            assertEquals(0, recipe.energy(), "XP must not be replaced by a trivial FE cost");
            assertEquals(ExperienceUtil.getFluidFromLevel(3 * level), recipe.inputFluids().get(0).amount());
            assertEquals(level, EnchantmentHelper.getEnchantments(recipe.outputs().get(0)).get(Enchantments.SHARPNESS));
            verify(adapter).experienceFluid(3 * level);
        }
    }

    private static SagMillingRecipe sag(SagMillingRecipe.BonusType bonus, float chance) {
        return new SagMillingRecipe(ID, Ingredient.of(Items.COAL),
                List.of(SagMillingRecipe.OutputItem.of(new ItemStack(Items.DIAMOND), chance, false)), 101, bonus);
    }
    private static com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe converted(
            SagMillingRecipe source, SagMillingRecipeAdapter.GrindingBallMultipliers multipliers, double power) {
        return SagMillingRecipeAdapter.convertRecipe(ID, source, multipliers, power,
                List.of(Ingredient.of(Items.BRICKS)));
    }
}
