package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismItems;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeavyWaterRecipeAdapterTest {
    private static Level level;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(ServerLevel.class);
    }

    @Test
    void generatesScaledHeavyWaterRecipeWithFilterUpgradeMold() {
        HeavyWaterRecipeAdapter adapter = new HeavyWaterRecipeAdapter();
        List<net.minecraft.world.item.crafting.RecipeHolder<MekanismSyntheticRecipe>> generated =
                adapter.getGeneratedRecipes(level);

        assertEquals(1, generated.size());
        assertEquals(ResourceLocation.fromNamespaceAndPath(
                "useless_mod", "mekanism/heavy_water_filter_upgrade"), generated.getFirst().id());

        var recipe = generated.getFirst().value().convertedRecipe();
        assertTrue(recipe.inputFluids().getFirst().ingredient()
                .test(new FluidStack(Fluids.WATER, 1)));
        assertEquals(1_000, recipe.inputFluids().getFirst().amount());
        assertEquals(MekanismFluids.HEAVY_WATER.get(), recipe.outputFluids().getFirst().getFluid());
        assertEquals(1_000, recipe.outputFluids().getFirst().getAmount());
        assertEquals(200, recipe.processTime());
        assertEquals(10_000L, recipe.energy());
        assertEquals(MekanismItems.FILTER_UPGRADE.get(), adapter.getMoldItem().getItem());
    }

    @Test
    void requiresFilterUpgradeForRuntimeMatching() {
        HeavyWaterRecipeAdapter adapter = new HeavyWaterRecipeAdapter();
        ItemStack filterUpgrade = new ItemStack(MekanismItems.FILTER_UPGRADE.get());
        Map<FluidStack, Long> water = Map.of(new FluidStack(Fluids.WATER, 1_000), 1_000L);

        assertTrue(adapter.matchesMold(filterUpgrade));
        assertFalse(adapter.matchesMold(ItemStack.EMPTY));
        assertFalse(adapter.matchesMold(new ItemStack(Items.STICK)));
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), water, Map.of(), filterUpgrade).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), water, Map.of(), new ItemStack(Items.STICK)).isEmpty());
    }
}
