package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OreFluidTagNamespaceTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }

    @Test void neoForgeRawMaterialTagsNeverReachForgeDustValidation() {
        assertFalse(assertDoesNotThrow(() -> IndustrialForegoingRecipeAdapterUtils
                .isValidRawMaterialTag(new ResourceLocation("c","raw_materials/raw_voidmetal"))));
        assertFalse(assertDoesNotThrow(() -> IndustrialForegoingRecipeAdapterUtils
                .isValidRawMaterialTag(new ResourceLocation("c","raw_materials/iron"))));
    }

    @Test void unrelatedTagsAreRejectedWithoutNativeRegistryAccess() {
        assertFalse(IndustrialForegoingRecipeAdapterUtils.isValidRawMaterialTag(null));
        assertFalse(IndustrialForegoingRecipeAdapterUtils
                .isValidRawMaterialTag(new ResourceLocation("forge","dusts/iron")));
        assertFalse(IndustrialForegoingRecipeAdapterUtils
                .isValidRawMaterialTag(new ResourceLocation("minecraft","raw_materials/iron")));
    }
}
