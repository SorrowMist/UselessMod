package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdvancedAlloyFurnaceRecipeBuilder {

    private final List<CountedIngredient> inputs = new ArrayList<>();
    private final List<LongSizedFluidIngredient> inputFluids = new ArrayList<>();
    private final List<GenericStack> keyInputs = new ArrayList<>();
    private final List<ItemStack> outputs = new ArrayList<>();
    private final List<FluidStack> outputFluids = new ArrayList<>();
    private final List<GenericStack> keyOutputs = new ArrayList<>();
    private long energy = 2000L;
    private int processTime = 200;
    private Ingredient catalyst = Ingredient.EMPTY;
    private int catalystUses = 0;
    private final List<Ingredient> molds = new ArrayList<>();
    private AlloyFurnaceMode mode = AlloyFurnaceMode.NORMAL;

    public static AdvancedAlloyFurnaceRecipeBuilder create() {
        return new AdvancedAlloyFurnaceRecipeBuilder();
    }

    public AdvancedAlloyFurnaceRecipeBuilder input(Ingredient ingredient, long count) {
        this.inputs.add(new CountedIngredient(Objects.requireNonNull(ingredient, "ingredient"), count));
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder input(ItemLike item, long count) {
        return input(Ingredient.of(Objects.requireNonNull(item, "item")), count);
    }

    public AdvancedAlloyFurnaceRecipeBuilder fluidInput(FluidStack fluid) {
        SizedFluidIngredient converted = AdapterUtils.toSizedFluidIngredient(fluid);
        if (converted != null) this.inputFluids.add(LongSizedFluidIngredient.from(converted));
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder fluidInput(SizedFluidIngredient fluid) {
        if (fluid != null) {
            this.inputFluids.add(LongSizedFluidIngredient.from(fluid));
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder fluidInput(LongSizedFluidIngredient fluid) {
        if (fluid != null) {
            this.inputFluids.add(fluid);
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder keyInput(GenericStack keyInput) {
        if (keyInput != null) {
            this.keyInputs.add(keyInput);
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder output(ItemLike item, int count) {
        this.outputs.add(new ItemStack(Objects.requireNonNull(item, "item"), count));
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder output(ItemStack output) {
        if (output != null && !output.isEmpty()) {
            this.outputs.add(output.copy());
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder fluidOutput(FluidStack output) {
        if (output != null && !output.isEmpty()) {
            this.outputFluids.add(output.copy());
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder keyOutput(GenericStack keyOutput) {
        if (keyOutput != null) {
            this.keyOutputs.add(keyOutput);
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder energy(long energy) {
        this.energy = energy;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder processTime(int ticks) {
        this.processTime = ticks;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder catalyst(Ingredient catalyst, int uses) {
        this.catalyst = Objects.requireNonNull(catalyst, "catalyst");
        this.catalystUses = uses;
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder mold(Ingredient mold) {
        this.molds.clear();
        if (mold != null) {
            this.molds.add(mold);
        }
        return this;
    }

    /** Replaces the complete set of independent mold requirements. */
    public AdvancedAlloyFurnaceRecipeBuilder molds(List<Ingredient> molds) {
        this.molds.clear();
        if (molds != null) {
            this.molds.addAll(molds);
        }
        return this;
    }

    /** Replaces the complete set of independent mold requirements. */
    public AdvancedAlloyFurnaceRecipeBuilder molds(Ingredient... molds) {
        this.molds.clear();
        if (molds != null) {
            for (Ingredient mold : molds) {
                this.molds.add(mold);
            }
        }
        return this;
    }

    /** Adds one independent mold requirement. */
    public AdvancedAlloyFurnaceRecipeBuilder addMold(Ingredient mold) {
        if (mold != null) {
            this.molds.add(mold);
        }
        return this;
    }

    public AdvancedAlloyFurnaceRecipeBuilder mode(AlloyFurnaceMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        return this;
    }

    /** Builds an in-memory recipe for use by an adapter conversion method. */
    public AdvancedAlloyFurnaceRecipe build(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.copyOf(this.inputs),
                List.copyOf(this.inputFluids),
                List.copyOf(this.keyInputs),
                this.outputs.stream().map(ItemStack::copy).toList(),
                this.outputFluids.stream().map(FluidStack::copy).toList(),
                List.copyOf(this.keyOutputs),
                this.energy,
                this.processTime,
                this.catalyst,
                this.catalystUses,
                List.copyOf(this.molds),
                this.mode
        );
    }

    public void save(RecipeOutput output, ResourceLocation id) {
        output.accept(id, build(id), null); // null = no advancement
    }
}
