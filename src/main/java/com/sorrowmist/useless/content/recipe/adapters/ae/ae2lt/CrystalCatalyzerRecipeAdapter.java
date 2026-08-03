package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerOutput;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts AE2 Lightning Tech 2.0 crystal-catalyzer recipes. */
public class CrystalCatalyzerRecipeAdapter implements IRecipeAdapter<CrystalCatalyzerRecipe> {

    private static final int OUTPUT_MULTIPLIER = 1024;
    private static final int BASE_PROCESS_TIME = 200;
    private static final int WATER_PER_CYCLE = 1000;

    @Override
    public Class<CrystalCatalyzerRecipe> getRecipeClass() {
        return CrystalCatalyzerRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrystalCatalyzerRecipe> holder, Level level) {
        if (holder == null) return List.of();

        CrystalCatalyzerRecipe recipe = holder.value();
        if (!supportsMode(recipe.mode())) return List.of();

        Optional<Ingredient> catalyst = recipe.catalyst();
        if (catalyst.isPresent() && recipe.catalystCount() <= 0) return List.of();

        // Resolve through the 2.0 output abstraction so item and tag outputs use the same path.
        ItemStack output = resolveOutput(recipe);
        if (output.isEmpty()) return List.of();

        Ingredient moldIngredient = catalyst.orElse(Ingredient.EMPTY);
        FluidStack waterInput = new FluidStack(Fluids.WATER, WATER_PER_CYCLE);
        var keyInputs = List.of(AELightningIngredientHelper.createLightningKeyInput(
                recipe.lightningTier(), (long) recipe.lightningCost()));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(),
                List.of(waterInput),
                keyInputs,
                List.of(output),
                List.of(),
                List.of(),
                Math.max(1, recipe.energyPerCycle()),
                BASE_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );

        return List.of(convertedRecipe);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<CrystalCatalyzerRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalCatalyzerRecipe>> recipes =
                (List<RecipeHolder<CrystalCatalyzerRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                        ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());

        List<RecipeHolder<CrystalCatalyzerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : recipes) {
            CrystalCatalyzerRecipe recipe = holder.value();

            if (!supportsMode(recipe.mode())) continue;

            Optional<Ingredient> catalyst = recipe.catalyst();
            if (catalyst.isEmpty() || recipe.catalystCount() <= 0) continue;
            if (mold.getCount() < recipe.catalystCount() || !catalyst.get().test(mold)) continue;
            if (!matchesFluid(mergedFluids, WATER_PER_CYCLE)) continue;
            if (!AELightningIngredientHelper.matchesLightning(
                    mergedKeys == null ? Map.of() : mergedKeys,
                    recipe.lightningTier(), (long) recipe.lightningCost())) continue;
            if (resolveOutput(recipe).isEmpty()) continue;

            matches.add(holder);
        }
        return matches;
    }

    private static boolean supportsMode(Mode mode) {
        return mode == Mode.CRYSTAL || mode == Mode.DUST;
    }

    private static ItemStack resolveOutput(CrystalCatalyzerRecipe recipe) {
        CrystalCatalyzerOutput outputSpec = recipe.outputSpec();
        ItemStack resolved = outputSpec.resolve();
        if (resolved.isEmpty() || outputSpec.count() <= 0
                || outputSpec.count() > Integer.MAX_VALUE / OUTPUT_MULTIPLIER) {
            return ItemStack.EMPTY;
        }
        return resolved.copyWithCount(outputSpec.count() * OUTPUT_MULTIPLIER);
    }

    private static boolean matchesFluid(Map<FluidStack, Long> mergedFluids, int amount) {
        if (mergedFluids == null || mergedFluids.isEmpty()) return false;
        FluidStack required = new FluidStack(Fluids.WATER, amount);
        return mergedFluids.entrySet().stream()
                .anyMatch(entry -> entry.getValue() >= amount
                        && FluidStack.isSameFluidSameComponents(entry.getKey(), required));
    }
}
