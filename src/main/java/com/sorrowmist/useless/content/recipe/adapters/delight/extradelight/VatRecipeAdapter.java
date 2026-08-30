package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.vat.recipes.VatRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VatRecipeAdapter implements IRecipeAdapter<VatRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "vat");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<VatRecipe> getRecipeClass() {
        return VatRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<VatRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        VatRecipe source = holder.value();
        List<Ingredient> sourceIngredients = new ArrayList<>(source.getIngredients());
        long totalTime = 0L;
        for (VatRecipe.StageIngredient stage : source.getStageIngredients()) {
            if (stage == null) {
                continue;
            }
            if (stage.ingredient != null && !stage.ingredient.isEmpty()) {
                sourceIngredients.add(stage.ingredient);
            }
            totalTime = Math.min(Integer.MAX_VALUE, totalTime + Math.max(0, (long) stage.time));
        }
        sourceIngredients = new ArrayList<>(ExtraDelightRecipeAdapterUtils.withContainer(
                sourceIngredients, source.getUsedItem()));
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(sourceIngredients);
        List<LongSizedFluidIngredient> fluids = source.getFluid() == null
                ? List.of() : ExtraDelightRecipeAdapterUtils.fluids(List.of(source.getFluid()));
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (inputs.isEmpty() && fluids.isEmpty() || output == null || output.isEmpty()) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(
                totalTime <= 0L ? AdapterUtils.DEFAULT_PROCESS_TIME : (int) totalTime);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, fluids, List.of(), List.of(output.copy()),
                List.of(), List.of(), ExtraDelightRecipeAdapterUtils.energy(time), time,
                Ingredient.EMPTY, 0, molds(source),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<VatRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<VatRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<VatRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), VatRecipe.class)) {
            VatRecipe source = holder.value();
            List<Ingredient> sourceIngredients = new ArrayList<>(source.getIngredients());
            for (VatRecipe.StageIngredient stage : source.getStageIngredients()) {
                if (stage != null && stage.ingredient != null && !stage.ingredient.isEmpty()) {
                    sourceIngredients.add(stage.ingredient);
                }
            }
            List<CountedIngredient> requirements = AdapterUtils.mergeIngredients(
                    ExtraDelightRecipeAdapterUtils.withContainer(sourceIngredients, source.getUsedItem()));
            List<LongSizedFluidIngredient> fluids = source.getFluid() == null
                    ? List.of() : ExtraDelightRecipeAdapterUtils.fluids(List.of(source.getFluid()));
            if ((requirements.isEmpty() && fluids.isEmpty())
                    || !DelightRecipeAdapterUtils.matchesItems(requirements, mergedInputs, List.of())
                    || !DelightRecipeAdapterUtils.matchesFluids(fluids, mergedFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    private static List<Ingredient> molds(VatRecipe source) {
        List<Ingredient> molds = new ArrayList<>();
        Ingredient vat = AdapterUtils.toMoldIngredient(getMoldStack());
        if (!vat.isEmpty()) {
            molds.add(vat);
        }
        molds.addAll(DelightRecipeAdapterUtils.bakingTrayMolds(
                source == null ? ItemStack.EMPTY : source.getUsedItem()));
        return List.copyOf(molds);
    }

    private static ItemStack getMoldStack() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }
}
