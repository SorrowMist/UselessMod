package com.sorrowmist.useless.api.recipe;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;

/** Public builder for recipes returned by an alloy-furnace compatibility adapter. */
public final class AlloyFurnaceRecipeBuilder extends AdvancedAlloyFurnaceRecipeBuilder {
    private AlloyFurnaceRecipeBuilder() {
    }

    public static AlloyFurnaceRecipeBuilder create() {
        return new AlloyFurnaceRecipeBuilder();
    }

    @Override
    public AlloyFurnaceRecipeBuilder input(Ingredient ingredient, long count) {
        super.input(ingredient, count);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder input(ItemLike item, long count) {
        super.input(item, count);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder fluidInput(FluidStack fluid) {
        super.fluidInput(fluid);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder fluidInput(SizedFluidIngredient fluid) {
        super.fluidInput(fluid);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder keyInput(GenericStack keyInput) {
        super.keyInput(keyInput);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder output(ItemLike item, int count) {
        super.output(item, count);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder output(ItemStack output) {
        super.output(output);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder fluidOutput(FluidStack output) {
        super.fluidOutput(output);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder keyOutput(GenericStack keyOutput) {
        super.keyOutput(keyOutput);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder energy(long energy) {
        super.energy(energy);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder processTime(int ticks) {
        super.processTime(ticks);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder catalyst(Ingredient catalyst, int uses) {
        super.catalyst(catalyst, uses);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder mold(Ingredient mold) {
        super.mold(mold);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder molds(List<Ingredient> molds) {
        super.molds(molds);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder molds(Ingredient... molds) {
        super.molds(molds);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder addMold(Ingredient mold) {
        super.addMold(mold);
        return this;
    }

    @Override
    public AlloyFurnaceRecipeBuilder mode(AlloyFurnaceMode mode) {
        super.mode(mode);
        return this;
    }

    @Override
    public AdvancedAlloyFurnaceRecipe build(ResourceLocation id) {
        return super.build(id);
    }
}
