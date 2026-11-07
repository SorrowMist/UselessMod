package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerOutput;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One native Forge 2.1.3 cycle; inventory-dependent catalyst parallelism is not assumed. */
public class CrystalCatalyzerRecipeAdapter implements IRecipeAdapter<CrystalCatalyzerRecipe> {
    private static final int BASE_OUTPUT_MULTIPLIER = 1;
    private static final int MATRIX_OUTPUT_MULTIPLIER = 8;

    @Override public Class<CrystalCatalyzerRecipe> getRecipeClass() { return CrystalCatalyzerRecipe.class; }

    @Override public ItemStack getMoldItem() {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("ae2lt", "crystal_catalyzer")));
    }

    @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CrystalCatalyzerRecipe> holder, Level level) {
        if (holder == null || !isSupported(holder.value())) return List.of();
        CrystalCatalyzerRecipe recipe = holder.value();
        var keyInputs = AELightningIngredientHelper.lightningInputs(recipe.lightningTier(), recipe.lightningCost());
        ItemStack baseOutput = resolveOutput(recipe, BASE_OUTPUT_MULTIPLIER);
        if (baseOutput.isEmpty()) return List.of();
        ResourceLocation baseId = AdapterUtils.convertedId(holder.id());
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        result.add(createRecipe(baseId, baseOutput, keyInputs, molds(recipe.catalyst(), false), recipe));
        ItemStack matrixOutput = resolveOutput(recipe, MATRIX_OUTPUT_MULTIPLIER);
        if (!matrixOutput.isEmpty()) {
            result.add(createRecipe(new ResourceLocation(baseId.getNamespace(),
                            baseId.getPath() + "_with_collapse_matrix"),
                    matrixOutput, keyInputs, molds(recipe.catalyst(), true), recipe));
        }
        return List.copyOf(result);
    }

    @Override public List<RecipeHolder<CrystalCatalyzerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty() || !matchesMold(mold)) return List.of();
        List<RecipeHolder<CrystalCatalyzerRecipe>> matches = new ArrayList<>();
        for (var holder : RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(),
                ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())) {
            CrystalCatalyzerRecipe recipe = holder.value();
            if (!isSupported(recipe)) continue;
            if (!FluidIngredientAllocator.matches(
                    List.of(AELightningIngredientHelper.exactFluidInput(recipe.fluidInput())), mergedFluids, 1L)) continue;
            if (!AELightningIngredientHelper.matchesLightning(mergedKeys,
                    recipe.lightningTier(), recipe.lightningCost())) continue;
            if (!resolveOutput(recipe, BASE_OUTPUT_MULTIPLIER).isEmpty()) matches.add(holder);
        }
        return matches;
    }

    private static boolean isSupported(CrystalCatalyzerRecipe recipe) {
        if (recipe.mode() != Mode.CRYSTAL && recipe.mode() != Mode.DUST) return false;
        // Molds preserve predicates, not a non-consumed stack quantity. Requiring one catalyst
        // cannot stand in for a recipe that needs a larger stock or for its maximum parallelism.
        if (recipe.catalyst().isPresent() && recipe.catalystCount() != 1) return false;
        CrystalCatalyzerOutput output = recipe.outputSpec();
        return output instanceof CrystalCatalyzerOutput.OfItem || output instanceof CrystalCatalyzerOutput.OfTag;
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, ItemStack output, List<GenericStack> keyInputs,
            List<Ingredient> molds, CrystalCatalyzerRecipe source) {
        return new AdvancedAlloyFurnaceRecipe(id, List.of(),
                List.of(AELightningIngredientHelper.exactFluidInput(source.fluidInput())), keyInputs,
                List.of(output), List.of(), List.of(), source.energyPerCycle(),
                source.mode().getMinProcessTicks(), Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL);
    }

    private List<Ingredient> molds(Optional<Ingredient> catalyst, boolean withMatrix) {
        List<Ingredient> molds = new ArrayList<>();
        molds.add(AdapterUtils.toMoldIngredient(getMoldItem()));
        catalyst.ifPresent(molds::add);
        // The bonus always requires the matrix, including recipes without a catalyst.
        if (withMatrix) {
            var id = new ResourceLocation("ae2lt", "lightning_collapse_matrix");
            molds.add(Ingredient.of(BuiltInRegistries.ITEM.getOptional(id)
                    .orElseThrow(() -> new IllegalStateException("Missing native AE2LT item: " + id))));
        }
        return List.copyOf(molds);
    }

    private static ItemStack resolveOutput(CrystalCatalyzerRecipe recipe, int multiplier) {
        CrystalCatalyzerOutput spec = recipe.outputSpec();
        ItemStack resolved = spec.resolve();
        if (resolved.isEmpty() || spec.count() <= 0 || spec.count() > Integer.MAX_VALUE / multiplier) {
            return ItemStack.EMPTY;
        }
        return resolved.copyWithCount(spec.count() * multiplier);
    }
}
