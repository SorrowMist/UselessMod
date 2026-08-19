package com.sorrowmist.useless.content.recipe.adapters.oritech;

import rearth.oritech.init.recipes.RecipeContent;

import java.util.List;

/** Builds the alloy-furnace adapters for Oritech's item-processing machines. */
public final class OritechRecipeCompatLoader {

    private OritechRecipeCompatLoader() {
    }

    public static List<OritechMachineRecipeAdapter> createAdapters() {
        return List.of(
                new OritechMachineRecipeAdapter(RecipeContent.PULVERIZER, "pulverizer_block"),
                new OritechMachineRecipeAdapter(RecipeContent.GRINDER, "fragment_forge_block"),
                new OritechMachineRecipeAdapter(RecipeContent.ASSEMBLER, "assembler_block"),
                new OritechMachineRecipeAdapter(RecipeContent.FOUNDRY, "foundry_block"),
                new OritechMachineRecipeAdapter(RecipeContent.CENTRIFUGE, "centrifuge_block"),
                new OritechMachineRecipeAdapter(RecipeContent.CENTRIFUGE_FLUID, "centrifuge_block"),
                new OritechMachineRecipeAdapter(RecipeContent.ATOMIC_FORGE, "atomic_forge_block"),
                new OritechMachineRecipeAdapter(RecipeContent.PARTICLE_COLLISION, "accelerator_controller"),
                new OritechMachineRecipeAdapter(RecipeContent.LASER, "laser_arm_block"),
                new OritechMachineRecipeAdapter(RecipeContent.COOLER, "cooler_block"),
                new OritechMachineRecipeAdapter(RecipeContent.REFINERY, "refinery_block")
        );
    }
}
