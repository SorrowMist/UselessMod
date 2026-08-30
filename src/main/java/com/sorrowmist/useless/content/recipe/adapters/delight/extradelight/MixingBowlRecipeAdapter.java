package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.mixingbowl.recipes.MixingBowlRecipe;
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

public final class MixingBowlRecipeAdapter implements IRecipeAdapter<MixingBowlRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "mixing_bowl");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<MixingBowlRecipe> getRecipeClass() {
        return MixingBowlRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MixingBowlRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        MixingBowlRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(
                ExtraDelightRecipeAdapterUtils.withContainer(source.getIngredients(), source.getContainer()));
        List<Ingredient> molds = new ArrayList<>();
        Ingredient mixingBowl = AdapterUtils.toMoldIngredient(getMoldItem());
        if (!mixingBowl.isEmpty()) {
            molds.add(mixingBowl);
        }
        molds.addAll(DelightRecipeAdapterUtils.bakingTrayMolds(source.getContainer()));
        List<LongSizedFluidIngredient> fluids = ExtraDelightRecipeAdapterUtils.fluids(source.getFluids());
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (inputs.isEmpty() && fluids.isEmpty() || output == null || output.isEmpty()) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(source.getStirs());
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, fluids, List.of(), List.of(output.copy()),
                List.of(), List.of(), ExtraDelightRecipeAdapterUtils.energy(time), time,
                Ingredient.EMPTY, 0, molds,
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<MixingBowlRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<MixingBowlRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<MixingBowlRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), MixingBowlRecipe.class)) {
            MixingBowlRecipe source = holder.value();
            List<CountedIngredient> requirements = AdapterUtils.mergeIngredients(
                    ExtraDelightRecipeAdapterUtils.withContainer(source.getIngredients(), source.getContainer()));
            List<LongSizedFluidIngredient> fluids = ExtraDelightRecipeAdapterUtils.fluids(source.getFluids());
            if ((requirements.isEmpty() && fluids.isEmpty())
                    || !DelightRecipeAdapterUtils.matchesItems(requirements, mergedInputs, List.of())
                    || !DelightRecipeAdapterUtils.matchesFluids(fluids, mergedFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }
}
