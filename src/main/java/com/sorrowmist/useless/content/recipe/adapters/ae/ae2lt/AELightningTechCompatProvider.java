package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeConversionUtils;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import static com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE;

/** The only entry point that links optional AE2 Lightning Tech recipe types. */
public final class AELightningTechCompatProvider {
    private AELightningTechCompatProvider() {
    }

    public static List<IRecipeAdapter<?>> createAdapters() {
        return List.of(
                new LightningSimulationRecipeAdapter(),
                new LightningAssemblyRecipeAdapter(),
                new OverloadProcessingRecipeAdapter(),
                new CrystalCatalyzerRecipeAdapter(),
                new FirmamentConversionRecipeAdapter(),
                new SteakLightningRecipeAdapter());
    }

    public static List<AdvancedAlloyFurnaceRecipe> getJeiRecipes(
            RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        addConvertedRecipes(
                recipes, recipeManager, LIGHTNING_SIMULATION_TYPE.get(),
                new LightningSimulationRecipeAdapter(), level);
        addConvertedRecipes(
                recipes, recipeManager, LIGHTNING_ASSEMBLY_TYPE.get(),
                new LightningAssemblyRecipeAdapter(), level);
        addConvertedRecipes(
                recipes, recipeManager, OVERLOAD_PROCESSING_TYPE.get(),
                new OverloadProcessingRecipeAdapter(), level);
        addConvertedRecipes(
                recipes, recipeManager, CRYSTAL_CATALYZER_TYPE.get(),
                new CrystalCatalyzerRecipeAdapter(), level);
        addConvertedRecipes(
                recipes, recipeManager, FIRMAMENT_CONVERSION_TYPE.get(),
                new FirmamentConversionRecipeAdapter(), level);
        recipes.addAll(new SteakLightningRecipeAdapter().getAllRecipes());
        return recipes;
    }

    private static <I extends RecipeInput, T extends Recipe<I>> void addConvertedRecipes(
            List<AdvancedAlloyFurnaceRecipe> convertedRecipes,
            RecipeManager recipeManager,
            RecipeType<T> recipeType,
            IRecipeAdapter<T> adapter,
            Level level) {
        for (RecipeHolder<T> holder : recipeManager.getAllRecipesFor(recipeType)) {
            convertedRecipes.addAll(RecipeConversionUtils.convertAll(adapter, holder, level));
        }
    }
}
