package com.sorrowmist.useless.content.recipe.adapters.mekanism.generators;

import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FissionRecipeAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void expandsWaterRepresentationsIntoIndependentRecipes() {
        FluidStackIngredient water = IngredientCreatorAccess.fluid().from(
                FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 1);
        FissionRecipeAdapter adapter = new FissionRecipeAdapter();
        List<net.minecraft.world.item.crafting.RecipeHolder<MekanismSyntheticRecipe>> waterRecipes =
                adapter.createWaterRecipes(water, MekanismChemicals.FISSILE_FUEL.asStack(1),
                        MekanismChemicals.STEAM.asStack(1), MekanismChemicals.NUCLEAR_WASTE.asStack(1));

        assertEquals(2, waterRecipes.size());
        assertEquals(waterRecipes.size(), waterRecipes.stream()
                .map(net.minecraft.world.item.crafting.RecipeHolder::id)
                .collect(Collectors.toSet()).size());
        assertEquals(waterRecipes.size(), waterRecipes.stream()
                .map(holder -> holder.value().convertedRecipe().inputFluids().size())
                .filter(size -> size == 1)
                .count());
        assertEquals(1, waterRecipes.stream()
                .map(holder -> holder.value().convertedRecipe().inputFluids().size())
                .distinct()
                .findFirst()
                .orElse(0));
        assertEquals(Set.of(Fluids.WATER, Fluids.LAVA), waterRecipes.stream()
                .map(holder -> holder.value().convertedRecipe().inputFluids().getFirst().getFluid())
                .collect(Collectors.toSet()));
    }
}
