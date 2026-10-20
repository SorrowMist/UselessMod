package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.machines.common.init.MachineBlocks;
import com.enderio.machines.common.init.MachineRecipes;
import com.enderio.machines.common.recipe.TankRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ender IO 6's actual tank recipes: filling consumes fluid, emptying produces it. */
public final class TankRecipeAdapter implements IRecipeAdapter<TankRecipe> {
    @Override public Class<TankRecipe> getRecipeClass() { return TankRecipe.class; }
    // Both tank blocks implement this unpowered recipe type. Register as a dynamic mold
    // so the manager does not pre-index only one of them.
    @Override public ItemStack getMoldItem() { return null; }
    Ingredient moldIngredient() {
        return Ingredient.of(MachineBlocks.FLUID_TANK.asItem(), MachineBlocks.PRESSURIZED_FLUID_TANK.asItem());
    }
    @Override public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty()
                && (mold.is(MachineBlocks.FLUID_TANK.asItem())
                    || mold.is(MachineBlocks.PRESSURIZED_FLUID_TANK.asItem()));
    }

    @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<TankRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        var source = holder.value();
        if (source.getInput() == null || source.getInput().isEmpty()
                || source.getOutput() == null || source.getOutput().isEmpty()
                || source.getFluid() == null || source.getFluid().isEmpty()) return List.of();
        var fluid = source.getFluid().copy();
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.getInput(), 1L)),
                source.isEmptying() ? List.of() : List.of(SizedFluidIngredient.of(fluid)),
                List.of(source.getOutput().copy()),
                source.isEmptying() ? List.of(fluid) : List.of(),
                0, AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                moldIngredient(), AlloyFurnaceMode.NORMAL));
    }

    @Override public List<RecipeHolder<TankRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedFluids == null || !matchesMold(mold)) return List.of();
        var matches = new ArrayList<RecipeHolder<TankRecipe>>();
        for (var source : RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(), MachineRecipes.TANK.type().get())) {
            for (var converted : convertAll(source, level)) {
                if (AdapterUtils.matchesRequired(mergedInputs, EnderIOAdapterUtils.requirements(converted.inputs()))
                        && FluidIngredientAllocator.matchesLong(converted.inputFluids(), mergedFluids, 1L)) {
                    matches.add(source);
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }
}
