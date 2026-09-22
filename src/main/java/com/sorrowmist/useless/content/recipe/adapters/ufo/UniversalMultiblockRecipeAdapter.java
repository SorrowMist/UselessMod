package com.sorrowmist.useless.content.recipe.adapters.ufo;

import com.raishxn.ufo.block.MultiblockBlocks;
import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.UniversalMultiblockMachineKind;
import com.raishxn.ufo.recipe.UniversalMultiblockRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Converts one machine kind from UFO's universal multiblock recipe type. */
public final class UniversalMultiblockRecipeAdapter
        extends UfoRecipeAdapter<UniversalMultiblockRecipe> {
    private final UniversalMultiblockMachineKind machine;

    public UniversalMultiblockRecipeAdapter(UniversalMultiblockMachineKind machine) {
        this.machine = machine;
    }

    @Override
    public Class<UniversalMultiblockRecipe> getRecipeClass() {
        return UniversalMultiblockRecipe.class;
    }

    @Override
    protected RecipeType<UniversalMultiblockRecipe> recipeType() {
        return ModRecipes.UNIVERSAL_MULTIBLOCK_TYPE.get();
    }

    @Override
    protected boolean accepts(UniversalMultiblockRecipe recipe) {
        return recipe != null && recipe.getMachine() == this.machine;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return switch (this.machine) {
            case QMF -> new ItemStack(MultiblockBlocks.QUANTUM_MATTER_FABRICATOR_CONTROLLER.get());
            case QUANTUM_CRYOFORGE -> new ItemStack(MultiblockBlocks.QUANTUM_CRYOFORGE_CONTROLLER.get());
            default -> null;
        };
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<UniversalMultiblockRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || !accepts(holder.value())) {
            return List.of();
        }
        UniversalMultiblockRecipe source = holder.value();
        if (source.getTime() <= 0) {
            return List.of();
        }

        List<CountedIngredient> items = UfoRecipeAdapterSupport.itemInputs(
                source.getItemInputs(), UniversalMultiblockRecipe.ItemRequirement::ingredient,
                UniversalMultiblockRecipe.ItemRequirement::amount);
        List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> fluids =
                UfoRecipeAdapterSupport.concreteFluidInputs(
                        source.getFluidInputs(), UniversalMultiblockRecipe.FluidRequirement::fluid,
                        UniversalMultiblockRecipe.FluidRequirement::amount);
        if (fluids == null) {
            return List.of();
        }
        List<appeng.api.stacks.GenericStack> chemicals = UfoRecipeAdapterSupport.chemicalInputs(
                source.getChemicalInputs(), UniversalMultiblockRecipe.ChemicalRequirement::chemicalId,
                UniversalMultiblockRecipe.ChemicalRequirement::amount);
        if (chemicals == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = new ArrayList<>();
        List<appeng.api.stacks.GenericStack> keyOutputs = new ArrayList<>();
        UfoRecipeAdapterSupport.addItemOutput(outputs, keyOutputs, source.getItemOutput(),
                source.getItemOutputAmount());
        UfoRecipeAdapterSupport.addFluidOutput(outputFluids, keyOutputs, source.getFluidOutput(),
                source.getFluidOutputAmount());
        if ((items.isEmpty() && fluids.isEmpty() && chemicals.isEmpty())
                || (outputs.isEmpty() && outputFluids.isEmpty() && keyOutputs.isEmpty())) {
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), items, fluids, chemicals,
                outputs, outputFluids, keyOutputs,
                UfoRecipeAdapterSupport.energy(source.getEnergy()), source.getTime(),
                Ingredient.EMPTY, 0, AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL));
    }
}
