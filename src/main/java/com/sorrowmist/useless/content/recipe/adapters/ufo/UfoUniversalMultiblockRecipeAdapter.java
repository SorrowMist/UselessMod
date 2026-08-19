package com.sorrowmist.useless.content.recipe.adapters.ufo;

import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.UniversalMultiblockMachineKind;
import com.raishxn.ufo.recipe.UniversalMultiblockRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts one UFO Future universal-multiblock machine kind's recipes into alloy-furnace recipes. */
public final class UfoUniversalMultiblockRecipeAdapter implements IRecipeAdapter<UniversalMultiblockRecipe> {

    private final UniversalMultiblockMachineKind machineKind;
    private final ItemStack moldItem;

    public UfoUniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind machineKind, String moldBlockPath) {
        this.machineKind = machineKind;
        this.moldItem = UfoAdapterUtils.item(moldBlockPath);
    }

    @Override
    public Class<UniversalMultiblockRecipe> getRecipeClass() {
        return UniversalMultiblockRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return moldItem;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<UniversalMultiblockRecipe> holder, Level level) {
        UniversalMultiblockRecipe source = holder == null ? null : holder.value();
        if (source == null || !isOwnKind(source)) {
            return List.of();
        }

        ItemStack itemOutput = source.getDisplayedItemOutput();
        FluidStack fluidOutput = source.getFluidOutput();
        if (source.getFluidOutputAmount() > 0 && !fluidOutput.isEmpty()) {
            fluidOutput = fluidOutput.copyWithAmount((int) Math.min(Integer.MAX_VALUE, source.getFluidOutputAmount()));
        } else {
            fluidOutput = FluidStack.EMPTY;
        }
        if (itemOutput.isEmpty() && fluidOutput.isEmpty()) {
            return List.of();
        }

        Map<Ingredient, Long> merged = new LinkedHashMap<>();
        for (UniversalMultiblockRecipe.ItemRequirement input : source.getItemInputs()) {
            if (input != null && input.ingredient() != null && !input.ingredient().isEmpty() && input.amount() > 0) {
                AdapterUtils.mergeIngredient(merged, input.ingredient(), input.amount());
            }
        }
        List<CountedIngredient> inputs = new ArrayList<>(merged.size());
        for (Map.Entry<Ingredient, Long> entry : merged.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        for (UniversalMultiblockRecipe.FluidRequirement input : source.getFluidInputs()) {
            if (input != null && input.fluid() != null && !input.fluid().isEmpty() && input.amount() > 0) {
                SizedFluidIngredient ingredient = AdapterUtils.toSizedFluidIngredient(
                        new FluidStack(input.fluid().getFluid(), (int) Math.min(Integer.MAX_VALUE, input.amount())));
                if (ingredient != null) {
                    inputFluids.add(ingredient);
                }
            }
        }

        int processTime = Math.max(1, source.getTime());
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, source.getEnergy());

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                inputFluids,
                itemOutput.isEmpty() ? List.of() : List.of(itemOutput.copy()),
                fluidOutput.isEmpty() ? List.of() : List.of(fluidOutput),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<UniversalMultiblockRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        boolean hasItems = mergedInputs != null && !mergedInputs.isEmpty();
        boolean hasFluids = mergedFluids != null && !mergedFluids.isEmpty();
        if (!hasItems && !hasFluids) {
            return List.of();
        }

        List<RecipeHolder<UniversalMultiblockRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<UniversalMultiblockRecipe> holder : manager.getAllRecipesFor(ModRecipes.UNIVERSAL_MULTIBLOCK_TYPE.get())) {
            UniversalMultiblockRecipe source = holder.value();
            if (source == null || !isOwnKind(source)) {
                continue;
            }
            Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
            for (UniversalMultiblockRecipe.ItemRequirement input : source.getItemInputs()) {
                if (input != null && input.ingredient() != null && !input.ingredient().isEmpty() && input.amount() > 0) {
                    AdapterUtils.mergeIngredient(requiredItems, input.ingredient(), input.amount());
                }
            }
            if (!requiredItems.isEmpty() && !AdapterUtils.matchesRequired(mergedInputs, requiredItems)) {
                continue;
            }
            List<SizedFluidIngredient> requiredFluids = new ArrayList<>();
            for (UniversalMultiblockRecipe.FluidRequirement input : source.getFluidInputs()) {
                if (input != null && input.fluid() != null && !input.fluid().isEmpty() && input.amount() > 0) {
                    SizedFluidIngredient ingredient = AdapterUtils.toSizedFluidIngredient(
                            new FluidStack(input.fluid().getFluid(), (int) Math.min(Integer.MAX_VALUE, input.amount())));
                    if (ingredient != null) {
                        requiredFluids.add(ingredient);
                    }
                }
            }
            if (AdapterUtils.matchesFluidIngredients(mergedFluids, requiredFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private boolean isOwnKind(UniversalMultiblockRecipe source) {
        return source.getMachine() == this.machineKind;
    }
}
