package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import com.moakiee.ae2lt.registry.ModItems;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeConversionUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

import static com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE;

/** The only entry point that links optional AE2 Lightning Tech recipe types. */
public final class AELightningTechCompatProvider {
    private static final ResourceLocation ANNIHILATION_PLANE_ID =
            ResourceLocation.fromNamespaceAndPath("ae2", "annihilation_plane");
    private static final ResourceLocation OVERLOAD_TNT_ID =
            ResourceLocation.fromNamespaceAndPath("ae2lt", "overload_tnt");
    private static final int MATERIAL_WATER_AMOUNT = 1;
    private static final long MATERIAL_ENERGY = 2000L;
    private static final int MATERIAL_PROCESS_TIME = 200;

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
        recipes.addAll(createMaterialRecipes());
        return recipes;
    }

    private static List<AdvancedAlloyFurnaceRecipe> createMaterialRecipes() {
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        Item annihilationPlane = BuiltInRegistries.ITEM.getOptional(ANNIHILATION_PLANE_ID).orElse(null);
        if (annihilationPlane != null && annihilationPlane != Items.AIR) {
            List<Ingredient> firmamentDustMolds = List.of(
                    Ingredient.of(Items.END_STONE),
                    Ingredient.of(annihilationPlane));
            List<Ingredient> floatingMatterMolds = List.of(
                    Ingredient.of(Items.SHULKER_SPAWN_EGG),
                    Ingredient.of(annihilationPlane));

            recipes.add(createMaterialRecipe(
                    ResourceLocation.fromNamespaceAndPath(
                            "ae2lt", "firmament_dust_from_end_stone"),
                    new ItemStack(ModItems.FIRMAMENT_DUST.get()),
                    firmamentDustMolds));
            recipes.add(createMaterialRecipe(
                    ResourceLocation.fromNamespaceAndPath(
                            "ae2lt", "floating_matter_from_shulker_spawn_egg"),
                    new ItemStack(ModItems.FLOATING_MATTER.get()),
                    floatingMatterMolds));
        }

        Item overloadTnt = BuiltInRegistries.ITEM.getOptional(OVERLOAD_TNT_ID).orElse(null);
        if (overloadTnt != null && overloadTnt != Items.AIR) {
            recipes.add(createItemRecipe(
                    ResourceLocation.fromNamespaceAndPath(
                            "ae2lt", "research_note_from_lightning_collapse_matrix"),
                    List.of(
                            new CountedIngredient(
                                    Ingredient.of(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()), 1000L),
                            new CountedIngredient(Ingredient.of(overloadTnt), 1L)),
                    new ItemStack(ModItems.RESEARCH_NOTE.get())));
        }

        return List.copyOf(recipes);
    }

    private static AdvancedAlloyFurnaceRecipe createMaterialRecipe(
            ResourceLocation id, ItemStack output, List<Ingredient> molds) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(),
                List.of(new FluidStack(Fluids.WATER, MATERIAL_WATER_AMOUNT)),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                MATERIAL_ENERGY,
                MATERIAL_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    private static AdvancedAlloyFurnaceRecipe createItemRecipe(
            ResourceLocation id, List<CountedIngredient> inputs,
            ItemStack output) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                MATERIAL_ENERGY,
                MATERIAL_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(),
                AlloyFurnaceMode.NORMAL);
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
