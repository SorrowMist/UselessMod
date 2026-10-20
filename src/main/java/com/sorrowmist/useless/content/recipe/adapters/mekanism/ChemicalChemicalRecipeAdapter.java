package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.chemical.ChemicalChemicalToChemicalRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chemical + chemical -> chemical adapters for the Chemical Infuser and Pigment Mixer. */
public final class ChemicalChemicalRecipeAdapter implements IRecipeAdapter<ChemicalChemicalToChemicalRecipe<?,?,?>> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 200L;

    private final RecipeType<? extends ChemicalChemicalToChemicalRecipe<?,?,?>> recipeType;
    private final ItemStack mold;
    private final String name;

    public ChemicalChemicalRecipeAdapter(RecipeType<? extends ChemicalChemicalToChemicalRecipe<?,?,?>> recipeType,
                                         ItemStack mold, String name) {
        this.recipeType = recipeType;
        this.mold = mold == null ? ItemStack.EMPTY : mold.copy();
        this.name = name;
    }

    public static ChemicalChemicalRecipeAdapter chemicalInfuser() {
        return new ChemicalChemicalRecipeAdapter(
                MekanismRecipeType.CHEMICAL_INFUSING.get(),
                new ItemStack(MekanismBlocks.CHEMICAL_INFUSER.getBlock()), "chemical_infuser");
    }

    public static ChemicalChemicalRecipeAdapter pigmentMixer() {
        return new ChemicalChemicalRecipeAdapter(
                MekanismRecipeType.PIGMENT_MIXING.get(),
                new ItemStack(MekanismBlocks.PIGMENT_MIXER.getBlock()), "pigment_mixer");
    }

    @Override
    public Class<ChemicalChemicalToChemicalRecipe<?,?,?>> getRecipeClass() {
        return (Class) ChemicalChemicalToChemicalRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return mold.isEmpty() ? null : mold.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChemicalChemicalToChemicalRecipe<?,?,?>> holder,
                                                       Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null || holder.value().getType() != recipeType) return List.of();
        ChemicalChemicalToChemicalRecipe<?,?,?> original = holder.value();
        ChemicalStackIngredient<?,?> left = original.getLeftInput();
        ChemicalStackIngredient<?,?> right = original.getRightInput();
        if (left == null || right == null || left.hasNoMatchingInstances() || right.hasNoMatchingInstances()) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack<?> leftStack : left.getRepresentations()) {
            GenericStack leftKey = MekanismChemicalRecipeSupport.key(leftStack);
            if (leftKey == null) continue;
            for (ChemicalStack<?> rightStack : right.getRepresentations()) {
                GenericStack rightKey = MekanismChemicalRecipeSupport.key(rightStack);
                if (rightKey == null) continue;
                for (ChemicalStack<?> outputStack : original.getOutputDefinition()) {
                    GenericStack outputKey = MekanismChemicalRecipeSupport.key(outputStack);
                    if (outputKey == null) continue;
                    String suffix = name + "_" + id(leftStack) + "_" + id(rightStack) + "_out_" + id(outputStack);
                    result.add(MekanismChemicalRecipeSupport.recipe(
                            MekanismChemicalRecipeSupport.variantId(holder.id(), suffix), List.of(), List.of(),
                            List.of(leftKey, rightKey), List.of(), List.of(), List.of(outputKey),
                            AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                            AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
                }
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<ChemicalChemicalToChemicalRecipe<?,?,?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ChemicalChemicalToChemicalRecipe<?,?,?>>> result = new ArrayList<>();
        for (RecipeHolder<ChemicalChemicalToChemicalRecipe<?,?,?>> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,ChemicalChemicalToChemicalRecipe<?,?,?>>getAllRecipesFor(manager, recipeType)) {
            ChemicalChemicalToChemicalRecipe<?,?,?> recipe = holder.value();
            if (matchesPair(mergedKeys, recipe.getLeftInput(), recipe.getRightInput())) result.add(holder);
        }
        return result;
    }

    private static boolean matchesPair(Map<AEKey, Long> available, ChemicalStackIngredient<?,?> left,
                                       ChemicalStackIngredient<?,?> right) {
        for (ChemicalStack<?> leftStack : left.getRepresentations()) {
            GenericStack leftKey = MekanismChemicalRecipeSupport.key(leftStack);
            if (leftKey == null) continue;
            for (ChemicalStack<?> rightStack : right.getRepresentations()) {
                GenericStack rightKey = MekanismChemicalRecipeSupport.key(rightStack);
                if (rightKey == null) continue;
                long leftAmount = leftKey.amount();
                long rightAmount = rightKey.amount();
                if (leftKey.what().equals(rightKey.what())) {
                    if (available.getOrDefault(leftKey.what(), 0L)
                            >= MekanismChemicalRecipeSupport.saturatingAdd(leftAmount, rightAmount)) return true;
                } else if (available.getOrDefault(leftKey.what(), 0L) >= leftAmount
                        && available.getOrDefault(rightKey.what(), 0L) >= rightAmount) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String id(ChemicalStack<?> stack) {
        ResourceLocation id = stack.getTypeRegistryName();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
