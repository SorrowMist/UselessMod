package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceRecipeBuilder {

    private final List<CountedIngredient> inputs = new ArrayList<>();
    private final List<FluidStack> inputFluids = new ArrayList<>();
    private final List<ItemStack> outputs = new ArrayList<>();
    private int energy = 2000;
    private int processTime = 200;
    private Ingredient catalyst = Ingredient.EMPTY;
    private int catalystUses = 0;
    private Ingredient mold = Ingredient.EMPTY;
    private AlloyFurnaceMode mode = AlloyFurnaceMode.NORMAL;

    public static AdvancedAlloyFurnaceRecipeBuilder create() {
        return new AdvancedAlloyFurnaceRecipeBuilder();
    }

    public AdvancedAlloyFurnaceRecipeBuilder input(Ingredient ingredient, long count) {
        this.inputs.add(new CountedIngredient(ingredient, count));
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder fluidInput(FluidStack fluid) {
        this.inputFluids.add(fluid.copy());
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder output(ItemLike item, int count) {
        this.outputs.add(new ItemStack(item, count));
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder energy(int energy) {
        this.energy = energy;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder processTime(int ticks) {
        this.processTime = ticks;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder catalyst(Ingredient catalyst, int uses) {
        this.catalyst = catalyst;
        this.catalystUses = uses;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder mold(Ingredient mold) {
        this.mold = mold;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder mode(AlloyFurnaceMode mode) {
        this.mode = mode;
        return this;
    }

    public void save(RecipeOutput output, ResourceLocation id) {
        var recipe = new AdvancedAlloyFurnaceRecipe(
                id,
                List.copyOf(this.inputs),
                List.copyOf(this.inputFluids),
                List.copyOf(this.outputs),
                List.of(), // outputFluids 如果不需要可以留空
                this.energy,
                this.processTime,
                this.catalyst,
                this.catalystUses,
                this.mold,
                this.mode
        );
        output.accept(id, recipe, null); // null = 无 advancement
    }
}