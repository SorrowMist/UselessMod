package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import appeng.api.stacks.AEKey;
import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorPotionRecipe;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorRecipe;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Athanor recipes, including fixed chance outputs and potion effects. */
public final class AthanorRecipeAdapter implements IRecipeAdapter<AthanorRecipe> {
    private static final ItemStack MOLD = new ItemStack(NVBlocks.ATHANOR_BLOCK.asItem());

    @Override
    public Class<AthanorRecipe> getRecipeClass() {
        return AthanorRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MOLD.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AthanorRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AthanorRecipe source = holder.value();
        ItemStack potionInput = source instanceof AthanorPotionRecipe
                ? NeoVitaeAdapterUtils.representative(source.getInputs().isEmpty()
                ? Ingredient.EMPTY : source.getInputs().get(0)) : null;
        return List.of(convert(holder.id(), source, potionInput));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AthanorRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null || actualInputs == null) return List.of();
        AthanorRecipe source = holder.value();
        if (!(source instanceof AthanorPotionRecipe)) return convertAll(holder, level);

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        Ingredient potionIngredient = source.getInputs().isEmpty()
                ? Ingredient.EMPTY : source.getInputs().get(0);
        for (ItemStack input : NeoVitaeAdapterUtils.distinctMatches(actualInputs, potionIngredient)) {
            result.add(convert(holder.id(), source, input));
        }
        return result;
    }

    @Override
    public List<RecipeHolder<AthanorRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, null);
    }

    @Override
    public List<RecipeHolder<AthanorRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<AthanorRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<AthanorRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(), NVRecipes.ATHANOR_TYPE.get())) {
            AthanorRecipe source = holder.value();
            if (source == null) continue;
            List<CountedIngredient> requirements = NeoVitaeAdapterUtils.counted(source.getInputs());
            boolean itemMatch = actualInputs == null
                    ? NeoVitaeAdapterUtils.matchesItems(mergedInputs, requirements)
                    : NeoVitaeAdapterUtils.matchesItems(requirements, actualInputs);
            if (!itemMatch) continue;

            Optional<SizedFluidIngredient> inputFluid = source.getInputFluid();
            if (inputFluid.isPresent() && !FluidIngredientAllocator.matches(
                    List.of(inputFluid.get()), mergedFluids, 1L)) {
                continue;
            }
            matches.add(holder);
        }
        return matches;
    }

    private static AdvancedAlloyFurnaceRecipe convert(
            net.minecraft.resources.ResourceLocation id, AthanorRecipe source,
            @Nullable ItemStack potionInput) {
        List<Ingredient> molds = new ArrayList<>();
        molds.add(Ingredient.of(MOLD));
        if (source.getTool() != null && !source.getTool().isEmpty()) {
            molds.add(source.getTool());
        }

        return NeoVitaeAdapterUtils.recipe(
                id,
                NeoVitaeAdapterUtils.counted(source.getInputs()),
                NeoVitaeAdapterUtils.inputFluid(source.getInputFluid().orElse(null)),
                fixedOutputs(source, potionInput),
                NeoVitaeAdapterUtils.outputFluid(source.getOutputFluid().orElse(null)),
                NeoVitaeAdapterUtils.energyFor(
                        NeoVitaeAdapterUtils.sumCosts(source.getSpiritusCosts().values())),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                molds);
    }

    private static List<ItemStack> fixedOutputs(AthanorRecipe source, @Nullable ItemStack potionInput) {
        List<ItemStack> outputs = new ArrayList<>();
        PotionContents potion = potionInput == null
                ? null : potionInput.get(DataComponents.POTION_CONTENTS);
        for (ItemStack output : source.getGuaranteedOutput()) {
            outputs.add(source instanceof AthanorPotionRecipe
                    ? potionOutput(output, potion) : output.copy());
        }
        for (Pair<ItemStack, Double> output : source.getChanceOutput()) {
            if (output != null && output.get(0) != null) {
                outputs.add(source instanceof AthanorPotionRecipe
                        ? potionOutput(output.get(0), potion) : output.get(0).copy());
            }
        }
        return outputs;
    }

    private static ItemStack potionOutput(ItemStack output, @Nullable PotionContents potion) {
        ItemStack result = output.copy();
        if (potion == null || !potion.hasEffects()) return result;
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : potion.getAllEffects()) {
            effects.add(new MobEffectInstance(effect));
        }
        result.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), effects));
        return result;
    }
}
