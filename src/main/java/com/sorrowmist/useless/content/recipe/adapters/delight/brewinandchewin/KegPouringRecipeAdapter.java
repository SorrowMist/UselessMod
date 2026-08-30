package com.sorrowmist.useless.content.recipe.adapters.delight.brewinandchewin;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import umpaz.brewinandchewin.common.crafting.KegPouringRecipe;
import umpaz.brewinandchewin.common.registry.BnCItems;
import umpaz.brewinandchewin.common.utility.AbstractedFluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Brewin And Chewin keg pouring recipes, including unripe cheese wheels. */
public final class KegPouringRecipeAdapter implements IRecipeAdapter<KegPouringRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.BREWIN_AND_CHEWIN;
    }

    @Override
    public Class<KegPouringRecipe> getRecipeClass() {
        return KegPouringRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(BnCItems.KEG);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<KegPouringRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        KegPouringRecipe source = holder.value();
        FluidStack fluid = toFluidStack(source.getRawFluid());
        ItemStack container = source.getContainer();
        ItemStack output = source.getOutput();
        if (fluid == null || container == null || container.isEmpty()
                || output == null || output.isEmpty()) {
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(Ingredient.of(container.copyWithCount(1)), 1L)),
                List.of(LongSizedFluidIngredient.from(fluid)),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<KegPouringRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || (mergedInputs == null || mergedInputs.isEmpty())
                && (mergedFluids == null || mergedFluids.isEmpty())) {
            return List.of();
        }

        List<RecipeHolder<KegPouringRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<KegPouringRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), KegPouringRecipe.class)) {
            KegPouringRecipe source = holder.value();
            FluidStack fluid = toFluidStack(source.getRawFluid());
            ItemStack container = source.getContainer();
            if (fluid == null || container == null || container.isEmpty()
                    || source.getOutput().isEmpty()) {
                continue;
            }
            List<CountedIngredient> itemRequirements = List.of(
                    new CountedIngredient(Ingredient.of(container.copyWithCount(1)), 1L));
            List<LongSizedFluidIngredient> fluidRequirements = List.of(
                    LongSizedFluidIngredient.from(fluid));
            if (DelightRecipeAdapterUtils.matchesItems(itemRequirements, mergedInputs, List.of())
                    && DelightRecipeAdapterUtils.matchesFluids(fluidRequirements, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static FluidStack toFluidStack(AbstractedFluidStack source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        long amount = source.unit().convertToLoader(source.amount());
        if (amount <= 0L || amount > Integer.MAX_VALUE) {
            return null;
        }
        return new FluidStack(source.fluid().builtInRegistryHolder(), (int) amount,
                source.componentPatch());
    }
}
