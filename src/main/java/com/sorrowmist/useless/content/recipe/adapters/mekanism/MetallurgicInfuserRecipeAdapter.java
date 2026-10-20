package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.chemical.ItemStackChemicalToItemStackRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Metallurgic Infuser and Osmium Compressor recipes to direct AE chemical keys. */
public class MetallurgicInfuserRecipeAdapter implements IRecipeAdapter<ItemStackChemicalToItemStackRecipe<?,?,?>> {
    protected static final long PROCESS_TICKS = AdapterUtils.MEKANISM_METALLURGIC_INFUSER_TICKS_REQUIRED;
    protected static final long ENERGY_PER_TICK = AdapterUtils.MEKANISM_METALLURGIC_INFUSER_ENERGY_PER_TICK;

    @Override
    public Class<ItemStackChemicalToItemStackRecipe<?,?,?>> getRecipeClass() {
        return (Class) ItemStackChemicalToItemStackRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.METALLURGIC_INFUSER.getBlock());
    }

    protected RecipeType<? extends ItemStackChemicalToItemStackRecipe<?,?,?>> getMekanismRecipeType() {
        return MekanismRecipeType.METALLURGIC_INFUSING.get();
    }

    protected long getEnergyPerTick() {
        return ENERGY_PER_TICK;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackChemicalToItemStackRecipe<?,?,?>> holder,
                                                       Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null) return List.of();
        ItemStackChemicalToItemStackRecipe<?,?,?> original = holder.value();
        if (original == null || original.getType() != getMekanismRecipeType()) return List.of();

        var itemInput = original.getItemInput();
        ChemicalStackIngredient<?,?> chemicalInput = original.getChemicalInput();
        if (MekanismChemicalRecipeSupport.item(itemInput) == null
                || chemicalInput == null || chemicalInput.hasNoMatchingInstances()
                || original.getOutputDefinition().isEmpty()) {
            return List.of();
        }

        long chemicalAmountMultiplier = MekanismChemicalRecipeSupport.perTickUsage(original) ? PROCESS_TICKS : 1L;
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ChemicalStack<?> representation : chemicalInput.getRepresentations()) {
            GenericStack key = MekanismChemicalRecipeSupport.key(
                    mekanism.common.util.ChemicalUtil.copyWithAmount(representation,MekanismChemicalRecipeSupport.saturatingMultiply(
                            representation.getAmount(), chemicalAmountMultiplier)));
            if (key == null || key.amount() <= 0L) continue;

            ResourceLocation chemicalId = representation.getTypeRegistryName();
            recipes.add(MekanismChemicalRecipeSupport.recipe(
                    MekanismChemicalRecipeSupport.variantId(holder.id(), "chemical_"
                            + chemicalId.getNamespace() + "_" + chemicalId.getPath()),
                    MekanismChemicalRecipeSupport.items(itemInput), List.of(), List.of(key),
                    copyItems(original.getOutputDefinition()), List.of(), List.of(),
                    AdapterUtils.mekanismEnergyCost(getEnergyPerTick(), PROCESS_TICKS, 1L),
                    AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
        }
        return recipes;
    }

    @Override
    @Nullable
    public List<RecipeHolder<ItemStackChemicalToItemStackRecipe<?,?,?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || mergedKeys.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ItemStackChemicalToItemStackRecipe<?,?,?>>> matches = new ArrayList<>();
        for (RecipeHolder<ItemStackChemicalToItemStackRecipe<?,?,?>> holder
                : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,ItemStackChemicalToItemStackRecipe<?,?,?>>getAllRecipesFor(recipeManager, getMekanismRecipeType())) {
            ItemStackChemicalToItemStackRecipe<?,?,?> recipe = holder.value();
            var itemInput = recipe.getItemInput();
            ChemicalStackIngredient<?,?> chemicalInput = recipe.getChemicalInput();
            if (itemInput == null || chemicalInput == null
                    || !MekanismChemicalRecipeSupport.matchesItem(mergedInputs, itemInput)) continue;

            long multiplier = MekanismChemicalRecipeSupport.perTickUsage(recipe) ? PROCESS_TICKS : 1L;
            boolean chemicalMatch = false;
            for (ChemicalStack<?> representation : chemicalInput.getRepresentations()) {
                GenericStack key = MekanismChemicalRecipeSupport.key(mekanism.common.util.ChemicalUtil.copyWithAmount(representation,
                        MekanismChemicalRecipeSupport.saturatingMultiply(representation.getAmount(), multiplier)));
                if (key != null && mergedKeys.getOrDefault(key.what(), 0L) >= key.amount()) {
                    chemicalMatch = true;
                    break;
                }
            }
            if (chemicalMatch) matches.add(holder);
        }
        return matches;
    }

    private static List<ItemStack> copyItems(List<ItemStack> outputs) {
        return outputs.stream().map(ItemStack::copy).toList();
    }
}
