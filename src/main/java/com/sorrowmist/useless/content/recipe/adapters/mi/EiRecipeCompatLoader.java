package com.sorrowmist.useless.content.recipe.adapters.mi;

import net.minecraft.resources.ResourceLocation;
import net.swedz.extended_industrialization.EIMachines;

import java.util.List;

/**
 * Builds the alloy-furnace adapters for Extended Industrialization's machines.
 *
 * <p>Extended Industrialization is a Modern Industrialization addon and reuses MI's
 * {@code MachineRecipe}/{@code MachineRecipeType}, so the shared MI adapter handles it directly.
 * Its blocks live under the {@code extended_industrialization} namespace.</p>
 */
public final class EiRecipeCompatLoader {

    private static final String MOD_ID = "extended_industrialization";

    private EiRecipeCompatLoader() {
    }

    public static List<MiMachineRecipeAdapter> createAdapters() {
        return List.of(
                new MiMachineRecipeAdapter(EIMachines.RecipeTypes.ALLOY_SMELTER, id("electric_alloy_smelter"), MOD_ID),
                new MiMachineRecipeAdapter(EIMachines.RecipeTypes.BENDING_MACHINE, id("bronze_bending_machine"), MOD_ID),
                new MiMachineRecipeAdapter(EIMachines.RecipeTypes.CANNING_MACHINE, id("steel_canning_machine"), MOD_ID),
                new MiMachineRecipeAdapter(EIMachines.RecipeTypes.COMPOSTER, id("bronze_composter"), MOD_ID),
                new MiMachineRecipeAdapter(EIMachines.RecipeTypes.BREWERY, id("steel_brewery"), MOD_ID)
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
