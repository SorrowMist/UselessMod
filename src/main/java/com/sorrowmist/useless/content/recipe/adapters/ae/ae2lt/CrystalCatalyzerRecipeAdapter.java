package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerOutput;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.resources.ResourceLocation;
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

    private static final int BASE_OUTPUT_MULTIPLIER = 256;
    private static final int MATRIX_OUTPUT_MULTIPLIER = 1024;
    private static final int BASE_PROCESS_TIME = 200;
    private static final int WATER_PER_CYCLE = 1000;

    @Override
    public Class<CrystalCatalyzerRecipe> getRecipeClass() {
        return CrystalCatalyzerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrystalCatalyzerRecipe> holder, Level level) {
        if (holder == null) return List.of();

        CrystalCatalyzerRecipe recipe = holder.value();
        if (!supportsMode(recipe.mode())) return List.of();

        Optional<Ingredient> catalyst = recipe.catalyst();
        if (catalyst.isPresent() && recipe.catalystCount() <= 0) return List.of();

        FluidStack waterInput = new FluidStack(Fluids.WATER, WATER_PER_CYCLE);
        var keyInputs = List.of(AELightningIngredientHelper.createLightningKeyInput(
                recipe.lightningTier(), (long) recipe.lightningCost()));

        List<Ingredient> baseMolds = molds(catalyst, false);
        ItemStack baseOutput = resolveOutput(recipe, BASE_OUTPUT_MULTIPLIER);
        if (baseOutput.isEmpty()) return List.of();

        ResourceLocation baseId = AdapterUtils.convertedId(holder.id());
        AdvancedAlloyFurnaceRecipe base = createRecipe(
                baseId, baseOutput, waterInput, keyInputs, baseMolds, recipe);
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        result.add(base);
        List<Ingredient> matrixMolds = molds(catalyst, true);
        ItemStack matrixOutput = resolveOutput(recipe, MATRIX_OUTPUT_MULTIPLIER);
        if (matrixOutput.isEmpty()) return List.of();
        result.add(createRecipe(
                ResourceLocation.fromNamespaceAndPath(
                        baseId.getNamespace(), baseId.getPath() + "_with_collapse_matrix"),
                matrixOutput, waterInput, keyInputs, matrixMolds, recipe));
        return List.copyOf(result);
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
        if (level == null || mold == null || mold.isEmpty() || !matchesMold(mold)) return List.of();

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalCatalyzerRecipe>> recipes =
                (List<RecipeHolder<CrystalCatalyzerRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                        ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());

        List<RecipeHolder<CrystalCatalyzerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : recipes) {
            CrystalCatalyzerRecipe recipe = holder.value();

            if (!supportsMode(recipe.mode())) continue;

            Optional<Ingredient> catalyst = recipe.catalyst();
            if (catalyst.isPresent() && recipe.catalystCount() <= 0) continue;
            if (!matchesFluid(mergedFluids, WATER_PER_CYCLE)) continue;
            if (!AELightningIngredientHelper.matchesLightning(
                    mergedKeys == null ? Map.of() : mergedKeys,
                    recipe.lightningTier(), (long) recipe.lightningCost())) continue;
            if (resolveOutput(recipe, BASE_OUTPUT_MULTIPLIER).isEmpty()) continue;

            matches.add(holder);
        }
        return matches;
    }

    private static boolean supportsMode(Mode mode) {
        return mode == Mode.CRYSTAL || mode == Mode.DUST;
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id,
            ItemStack output,
            FluidStack waterInput,
            List<appeng.api.stacks.GenericStack> keyInputs,
            List<Ingredient> molds,
            CrystalCatalyzerRecipe source) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(),
                List.of(waterInput.copy()),
                keyInputs,
                List.of(output),
                List.of(),
                List.of(),
                Math.max(1, source.energyPerCycle()),
                BASE_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    private static List<Ingredient> molds(Optional<Ingredient> catalyst, boolean withMatrix) {
        List<Ingredient> molds = new ArrayList<>();
        molds.add(AdapterUtils.toMoldIngredient(new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get())));
        catalyst.ifPresent(molds::add);
        // A source recipe without a catalyst already uses the crystal catalyzer as its
        // only mold. Keep the matrix variant selectable without inventing a new mold
        // requirement for that recipe shape.
        if (withMatrix && catalyst.isPresent()) {
            molds.add(AdapterUtils.toMoldIngredient(
                    new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())));
        }
        return List.copyOf(molds);
    }

    private static ItemStack resolveOutput(CrystalCatalyzerRecipe recipe, int multiplier) {
        CrystalCatalyzerOutput outputSpec = recipe.outputSpec();
        ItemStack resolved = outputSpec.resolve();
        if (resolved.isEmpty() || outputSpec.count() <= 0
                || outputSpec.count() > Integer.MAX_VALUE / multiplier) {
            return ItemStack.EMPTY;
        }
        return resolved.copyWithCount(outputSpec.count() * multiplier);
    }

    private static boolean matchesFluid(Map<FluidStack, Long> mergedFluids, int amount) {
        if (mergedFluids == null || mergedFluids.isEmpty()) return false;
        FluidStack required = new FluidStack(Fluids.WATER, amount);
        return mergedFluids.entrySet().stream()
                .anyMatch(entry -> entry.getValue() >= amount
                        && FluidStack.isSameFluidSameComponents(entry.getKey(), required));
    }
}
