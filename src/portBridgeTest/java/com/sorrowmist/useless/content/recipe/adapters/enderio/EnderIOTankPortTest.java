package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.machines.common.recipe.TankRecipe;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnderIOTankPortTest {
    private static final ResourceLocation ID = new ResourceLocation("kubejs:native_tank");
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static TankRecipeAdapter adapter() {
        var adapter = spy(new TankRecipeAdapter());
        doReturn(Ingredient.of(Items.BRICKS, Items.GLASS)).when(adapter).moldIngredient();
        return adapter;
    }
    @Test void fillingConsumesExactFluidQuantityAndNbtAndKeepsLargeOutput() {
        var fluid = new FluidStack(Fluids.WATER, 250);
        fluid.getOrCreateTag().putString("foreign:mixture", "xp");
        var output = new ItemStack(Items.EXPERIENCE_BOTTLE, 200);
        output.getOrCreateTag().putInt("foreign:quality", 17);
        var source = new TankRecipe(ID, Ingredient.of(Items.GLASS_BOTTLE), output, fluid, false);
        var converted = adapter().convertAll(new RecipeHolder<>(ID, source), null).get(0);
        assertNull(adapter().getMoldItem()); // Do not index only the ordinary tank.
        assertEquals(0, converted.energy()); assertEquals(250, converted.inputFluids().get(0).amount());
        assertTrue(converted.outputFluids().isEmpty());
        assertEquals(200, converted.outputs().get(0).getCount());
        assertEquals(output.getTag(), converted.outputs().get(0).getTag());
        assertFalse(FluidIngredientAllocator.matchesLong(converted.inputFluids(), List.of(new FluidStack(Fluids.WATER, 250)), 1));
        assertFalse(FluidIngredientAllocator.matchesLong(converted.inputFluids(), Map.of(fluid, 249L), 1));
        assertTrue(FluidIngredientAllocator.matchesLong(converted.inputFluids(), Map.of(fluid, 250L), 1));
        var allocated = FluidIngredientAllocator.allocateLong(converted.inputFluids(), List.of(fluid.copy()), 1);
        assertNotNull(allocated); assertEquals(250, allocated.consumedFromSupply(0));
        converted.outputs().get(0).getTag().putInt("foreign:quality", 0);
        assertEquals(17, output.getTag().getInt("foreign:quality"));
    }
    @Test void emptyingProducesFluidWithoutRequiringItAndConsumesTheContainer() {
        var input = new ItemStack(Items.WET_SPONGE);
        input.getOrCreateTag().putString("foreign:state", "keep-predicate");
        var fluid = new FluidStack(Fluids.WATER, 1000);
        fluid.getOrCreateTag().putInt("foreign:salinity", 5);
        var source = new TankRecipe(ID, DataComponentIngredient.of(true, input),
                new ItemStack(Items.SPONGE), fluid, true);
        var converted = adapter().convertAll(new RecipeHolder<>(ID, source), null).get(0);
        assertTrue(converted.inputFluids().isEmpty());
        assertEquals(1000, converted.outputFluids().get(0).getAmount());
        assertEquals(fluid.getTag(), converted.outputFluids().get(0).getTag());
        assertFalse(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(Items.WET_SPONGE)), 1));
        var allocation = ItemIngredientAllocator.allocate(converted.inputs(), List.of(input), 1);
        assertNotNull(allocation); assertEquals(1, allocation.consumedFromInput(0));
        converted.outputFluids().get(0).getOrCreateTag().putInt("foreign:salinity", 0);
        assertEquals(5, fluid.getTag().getInt("foreign:salinity"));
    }
    @Test void invalidNativeRecipesAreRejectedInsteadOfCreatingFreeConversions() {
        var adapter = adapter();
        assertTrue(adapter.convertAll(null, null).isEmpty());
        assertTrue(adapter.convertAll(new RecipeHolder<>(ID, new TankRecipe(ID, Ingredient.EMPTY,
                new ItemStack(Items.DIAMOND), new FluidStack(Fluids.WATER, 1), false)), null).isEmpty());
        assertTrue(adapter.convertAll(new RecipeHolder<>(ID, new TankRecipe(ID, Ingredient.of(Items.COAL),
                new ItemStack(Items.DIAMOND), FluidStack.EMPTY, true)), null).isEmpty());
    }
}
