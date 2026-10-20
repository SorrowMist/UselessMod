package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.chemical.ItemStackToChemicalRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Independent item -> chemical adapters for Mekanism's two conversion types. */
public final class ItemToChemicalRecipeAdapter implements IRecipeAdapter<ItemStackToChemicalRecipe<?,?>> {
    private static final long PROCESS_TICKS = 100L;
    private static final long ENERGY_PER_TICK = 200L;

    private final RecipeType<? extends ItemStackToChemicalRecipe<?,?>> recipeType;
    private final long processTicks;
    private final String name;
    private final ItemStack mold;

    public ItemToChemicalRecipeAdapter(RecipeType<? extends ItemStackToChemicalRecipe<?,?>> recipeType,
                                       long processTicks, String name, ItemStack mold) {
        this.recipeType = recipeType;
        this.processTicks = processTicks;
        this.name = name;
        this.mold = mold == null ? ItemStack.EMPTY : mold.copy();
    }

    public static ItemToChemicalRecipeAdapter chemicalConversion() {
        return new ItemToChemicalRecipeAdapter(
                MekanismRecipeType.GAS_CONVERSION.get(),
                PROCESS_TICKS, "chemical_conversion",
                new ItemStack(MekanismBlocks.CHEMICAL_OXIDIZER.getBlock()));
    }

    public static ItemToChemicalRecipeAdapter oxidizing() {
        return new ItemToChemicalRecipeAdapter(
                MekanismRecipeType.OXIDIZING.get(),
                PROCESS_TICKS, "oxidizing",
                new ItemStack(MekanismBlocks.CHEMICAL_OXIDIZER.getBlock()));
    }

    public static ItemToChemicalRecipeAdapter pigmentExtracting() {
        return new ItemToChemicalRecipeAdapter(
                MekanismRecipeType.PIGMENT_EXTRACTING.get(),
                PROCESS_TICKS, "pigment_extracting",
                new ItemStack(MekanismBlocks.PIGMENT_EXTRACTOR.getBlock()));
    }

    @Override
    public Class<ItemStackToChemicalRecipe<?,?>> getRecipeClass() {
        return (Class) ItemStackToChemicalRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return mold.isEmpty() ? null : mold.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackToChemicalRecipe<?,?>> holder, Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null || holder.value().getType() != recipeType) return List.of();
        ItemStackToChemicalRecipe<?,?> original = holder.value();
        ItemStackIngredient itemInput = original.getInput();
        if (MekanismChemicalRecipeSupport.item(itemInput) == null) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack<?> output : original.getOutputDefinition()) {
            GenericStack key = MekanismChemicalRecipeSupport.key(output);
            if (key == null) continue;
            result.add(MekanismChemicalRecipeSupport.recipe(
                    MekanismChemicalRecipeSupport.variantId(holder.id(), name + "_" + id(output)),
                    MekanismChemicalRecipeSupport.items(itemInput), List.of(), List.of(), List.of(), List.of(),
                    List.of(key), AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, processTicks, 1L),
                    AdapterUtils.safeInt(processTicks), getMoldItem()));
        }
        return result;
    }

    @Override
    public List<RecipeHolder<ItemStackToChemicalRecipe<?,?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ItemStackToChemicalRecipe<?,?>>> result = new ArrayList<>();
        for (RecipeHolder<ItemStackToChemicalRecipe<?,?>> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,ItemStackToChemicalRecipe<?,?>>getAllRecipesFor(manager, recipeType)) {
            ItemStackToChemicalRecipe<?,?> recipe = holder.value();
            if (MekanismChemicalRecipeSupport.matchesItem(mergedInputs, recipe.getInput())) result.add(holder);
        }
        return result;
    }

    private static String id(ChemicalStack<?> stack) {
        var id = stack.getTypeRegistryName();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
