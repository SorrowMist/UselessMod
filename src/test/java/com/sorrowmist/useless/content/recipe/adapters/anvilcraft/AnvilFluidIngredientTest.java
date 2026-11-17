package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilFluidIngredientTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void emptyComponentRequirementDoesNotAcceptComponentBearingFluid() {
        FluidStack plain = new FluidStack(Fluids.WATER, 1000);
        FluidStack marked = plain.copy();
        marked.set(DataComponents.CUSTOM_NAME, Component.literal("marked"));

        var ingredient = CauldronRecipeAdapter.fluidInput(plain, true);
        assertTrue(ingredient.ingredient().test(plain));
        assertFalse(ingredient.ingredient().test(marked));
    }

    @Test
    void componentFreePredicateKeepsOrdinaryFluidMatching() {
        FluidStack plain = new FluidStack(Fluids.WATER, 1000);
        FluidStack marked = plain.copy();
        marked.set(DataComponents.CUSTOM_NAME, Component.literal("marked"));

        var ingredient = CauldronRecipeAdapter.fluidInput(plain, false);
        assertTrue(ingredient.ingredient().test(plain));
        assertTrue(ingredient.ingredient().test(marked));
    }
}
