package com.sorrowmist.useless.compat.kubejs;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.CustomObjectRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.FluidIngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * KubeJS schema for the JSON consumed by {@code AdvancedAlloyFurnaceRecipe.CODEC}.
 *
 * The custom ingredient component keeps the convenient KubeJS shape
 * {@code { ingredient: 'minecraft:iron_ingot', count: 4 }} while producing the
 * counted ingredient object expected by the recipe serializer.
 */
public final class AdvancedAlloyFurnaceRecipeSchema {
    private static final RecipeComponent<List<CustomObjectRecipeComponent.Value>> COUNTED_INGREDIENT =
            RecipeComponent.builder(
                    new CustomObjectRecipeComponent.Key(
                            "ingredient", IngredientComponent.INGREDIENT.instance()),
                    new CustomObjectRecipeComponent.Key(
                            "count", NumberComponent.LONG, true)
            );
    private static final RecipeComponent<List<CustomObjectRecipeComponent.Value>> SIZED_FLUID_INGREDIENT =
            RecipeComponent.builder(
                    new CustomObjectRecipeComponent.Key(
                            "ingredient", FluidIngredientComponent.FLUID_INGREDIENT.instance()),
                    new CustomObjectRecipeComponent.Key(
                            "amount", NumberComponent.LONG)
            );

    private static final RecipeComponent<List<ItemStack>> ITEM_STACK_LIST =
            ItemStackComponent.ITEM_STACK.instance().asList();
    private static final RecipeComponent<List<FluidStack>> FLUID_STACK_LIST =
            FluidStackComponent.FLUID_STACK.instance().asList();
    private static final RecipeComponent<List<Ingredient>> INGREDIENT_LIST =
            IngredientComponent.OPTIONAL_INGREDIENT.instance().asList();

    public static final RecipeKey<String> ID =
            StringComponent.STRING.inputKey("id");
    public static final RecipeKey<List<List<CustomObjectRecipeComponent.Value>>> INGREDIENTS =
            COUNTED_INGREDIENT.asList().inputKey("ingredients");
    public static final RecipeKey<List<List<CustomObjectRecipeComponent.Value>>> INPUT_FLUIDS =
            SIZED_FLUID_INGREDIENT.asList().inputKey("input_fluids").optional(List.of());
    public static final RecipeKey<List<ItemStack>> OUTPUTS =
            ITEM_STACK_LIST.outputKey("outputs");
    public static final RecipeKey<List<FluidStack>> OUTPUT_FLUIDS =
            FLUID_STACK_LIST.outputKey("output_fluids").optional(List.of());
    public static final RecipeKey<Ingredient> MOLD =
            IngredientComponent.OPTIONAL_INGREDIENT.instance()
                    .inputKey("mold").optional(Ingredient.EMPTY);
    public static final RecipeKey<List<Ingredient>> MOLDS =
            INGREDIENT_LIST.inputKey("molds").optional(List.of());
    public static final RecipeKey<Integer> TIER =
            NumberComponent.INT.inputKey("tier")
                    .optional(AdvancedAlloyFurnaceRecipe.NO_EXPLICIT_TIER);

    public static final RecipeKey<Long> ENERGY =
            NumberComponent.LONG.inputKey("energy").optional(2000L);
    public static final RecipeKey<Integer> PROCESS_TIME =
            NumberComponent.INT.inputKey("process_time").optional(200);
    public static final RecipeKey<Ingredient> CATALYST =
            IngredientComponent.OPTIONAL_INGREDIENT.instance()
                    .inputKey("catalyst").optional(Ingredient.EMPTY);
    public static final RecipeKey<Integer> CATALYST_USES =
            NumberComponent.INT.inputKey("catalyst_uses").optional(0);
    public static final RecipeKey<String> MODE =
            StringComponent.STRING.inputKey("mode").optional("normal");

    private AdvancedAlloyFurnaceRecipeSchema() {
    }

    public static final RecipeSchema SCHEMA = new RecipeSchema(
            ID, INGREDIENTS, INPUT_FLUIDS, OUTPUTS, OUTPUT_FLUIDS, MOLD, MOLDS, TIER,
            ENERGY, PROCESS_TIME, CATALYST, CATALYST_USES, MODE
    )
            .constructor(ID, INGREDIENTS, OUTPUTS)
            .constructor(ID, INGREDIENTS, OUTPUTS, MOLD)
            .constructor(ID, INGREDIENTS, OUTPUTS, MOLD, TIER)
            .constructor(ID, INGREDIENTS, OUTPUTS, MOLD, MOLDS, TIER);
}
