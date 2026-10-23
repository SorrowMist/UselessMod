package com.sorrowmist.useless.content.recipe.adapters.thermal;

import cofh.thermal.lib.util.recipes.ThermalRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.BatchRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class ThermalRecipeAdapter<T extends ThermalRecipe> extends BatchRecipeAdapter<T> {
    public ThermalRecipeAdapter(Class<T> type, String machine, String prefix, AlloyFurnaceMode mode) {
        super(type, RecipeSourceIds.THERMAL, machine, prefix, mode);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<T> holder, Level level) {
        if (holder == null) return List.of();
        T source = holder.value();
        long batch = mode == AlloyFurnaceMode.INSOLATOR ? 1L : 64L;
        var inputs = NativeThermalIngredients.items(source.getInputItems(), batch);
        if (mode == AlloyFurnaceMode.INSOLATOR) {
            inputs = inputs.stream().map(input -> new CountedIngredient(
                    input.ingredient(), input.count(), false)).toList();
        }
        if (mode == AlloyFurnaceMode.PRESS && inputs.size() > 1) {
            var retained = new ArrayList<>(inputs);
            var mold = NativeThermalIngredients.items(List.of(source.getInputItems().get(1)), 1L).get(0);
            retained.set(1, new CountedIngredient(
                    mold.ingredient(), mold.count(), false));
            inputs = List.copyOf(retained);
        }
        List<ItemStack> outputs = new ArrayList<>();
        for (int index = 0; index < source.getOutputItems().size(); index++) {
            ItemStack output = source.getOutputItems().get(index);
            if (mode == AlloyFurnaceMode.INSOLATOR) {
                float chance = index < source.getOutputItemChances().size()
                        ? source.getOutputItemChances().get(index) : 1.0F;
                if (!Float.isFinite(chance) || chance < 0.0F) throw new IllegalArgumentException("Invalid Thermal chance");
                // shortcut: retain the local deterministic yield; revisit if native chance semantics are requested.
                output = output.copyWithCount((int) Math.ceil(chance));
            } else {
                output = scale(output, batch);
            }
            if (!output.isEmpty()) outputs.add(output);
        }
        var fluids = fluids(source.getInputFluids().stream().map(value -> value.toJson()).toList(), batch);
        var outputFluids = source.getOutputFluids().stream().map(value -> scale(value, batch)).toList();
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (int index = 0; index < fluids.size(); index++) {
            var id = fluids.size() == 1 ? holder.id()
                    : new ResourceLocation(holder.id().getNamespace(),
                    holder.id().getPath() + "_fluid_variant_" + index);
            result.add(recipe(id, inputs, fluids.get(index), outputs, outputFluids,
                    Math.multiplyExact(source.getEnergy(), batch)));
        }
        return List.copyOf(result);
    }
}
