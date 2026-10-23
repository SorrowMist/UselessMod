package com.sorrowmist.useless.content.recipe.adapters.embers;

import com.rekindled.embers.recipe.IAlchemyRecipe;
import com.rekindled.embers.recipe.IMeltingRecipe;
import com.rekindled.embers.recipe.IMixingRecipe;
import com.rekindled.embers.recipe.IStampingRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.BatchRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public final class EmbersRecipeAdapter<T extends Recipe<?>> extends BatchRecipeAdapter<T> {
    private final boolean separator;

    public EmbersRecipeAdapter(Class<T> type, String mold, String prefix, boolean separator) {
        super(type, RecipeSourceIds.EMBERS, mold, prefix, AlloyFurnaceMode.NORMAL);
        this.separator = separator;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<T> holder, Level level) {
        if (holder == null) return List.of();
        List<CountedIngredient> inputs = List.of();
        List<List<LongSizedFluidIngredient>> fluidInputs = List.of(List.of());
        List<ItemStack> outputs = List.of();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        // shortcut: keep the local input/output conversion; revisit aspect puzzles and stamps for native machine parity.
        if (holder.value() instanceof IAlchemyRecipe alchemy) {
            var ingredients = new ArrayList<Ingredient>();
            ingredients.add(alchemy.getCenterInput());
            ingredients.addAll(alchemy.getInputs());
            inputs = items(ingredients, 64L);
            outputs = List.of(scale(alchemy.getResultItem(), 64L));
        } else if (holder.value() instanceof IMeltingRecipe melting) {
            FluidStack output = melting.getDisplayOutput();
            FluidStack bonus = melting.getBonus();
            if (output.isEmpty() || !separator && !bonus.isEmpty()) return List.of();
            inputs = items(List.of(melting.getDisplayInput()), 64L);
            fluidOutputs.add(scale(output, 64L));
            if (separator && !bonus.isEmpty()) fluidOutputs.add(scale(bonus, 64L));
        } else if (holder.value() instanceof IStampingRecipe stamping) {
            inputs = items(List.of(stamping.getDisplayInput()), 64L);
            if (stamping.getDisplayInputFluid() != com.rekindled.embers.recipe.FluidIngredient.EMPTY) {
                fluidInputs = fluids(List.of(stamping.getDisplayInputFluid().serialize()), 64L);
            }
            outputs = List.of(scale(stamping.getResultItem(), 64L));
        } else if (holder.value() instanceof IMixingRecipe mixing) {
            fluidInputs = fluids(mixing.getDisplayInputFluids().stream().map(value -> value.serialize()).toList(), 64L);
            fluidOutputs.add(scale(mixing.getDisplayOutput(), 64L));
        } else {
            return List.of();
        }
        if (outputs.stream().allMatch(ItemStack::isEmpty) && fluidOutputs.stream().allMatch(FluidStack::isEmpty)) {
            return List.of();
        }
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (int index = 0; index < fluidInputs.size(); index++) {
            var id = fluidInputs.size() == 1 ? holder.id()
                    : new ResourceLocation(holder.id().getNamespace(),
                    holder.id().getPath() + "_fluid_variant_" + index);
            result.add(recipe(id, inputs, fluidInputs.get(index), outputs, fluidOutputs, 2000L));
        }
        return List.copyOf(result);
    }
}
