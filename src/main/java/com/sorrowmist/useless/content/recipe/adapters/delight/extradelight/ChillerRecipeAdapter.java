package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.chiller.ChillerRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.core.registries.BuiltInRegistries;
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

/** Converts Extra Delight chiller recipes, including their fluid and container inputs. */
public final class ChillerRecipeAdapter implements IRecipeAdapter<ChillerRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "chiller");
    private static final ResourceLocation BAR_MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "bar_mold");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<ChillerRecipe> getRecipeClass() {
        return ChillerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChillerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        ChillerRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(itemInputs(source));
        FluidStack inputFluid = source.getFluid();
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if ((inputs.isEmpty() && (inputFluid == null || inputFluid.isEmpty()))
                || output == null || output.isEmpty()) {
            return List.of();
        }
        int time = ExtraDelightRecipeAdapterUtils.processTime(source.getCookTime());
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs,
                ExtraDelightRecipeAdapterUtils.fluid(inputFluid), List.of(), List.of(output.copy()),
                List.of(), List.of(), ExtraDelightRecipeAdapterUtils.energy(time), time,
                Ingredient.EMPTY, 0, molds(source),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<ChillerRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<ChillerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ChillerRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), ChillerRecipe.class)) {
            ChillerRecipe source = holder.value();
            List<CountedIngredient> requirements = AdapterUtils.mergeIngredients(
                    itemInputs(source));
            List<com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient> fluids =
                    ExtraDelightRecipeAdapterUtils.fluid(source.getFluid());
            if ((requirements.isEmpty() && fluids.isEmpty())
                    || !DelightRecipeAdapterUtils.matchesItems(requirements, mergedInputs, List.of())
                    || !DelightRecipeAdapterUtils.matchesFluids(fluids, mergedFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    private static List<Ingredient> itemInputs(ChillerRecipe source) {
        if (source == null) {
            return List.of();
        }
        ItemStack container = source.getOutputContainer();
        return isSecondaryMold(container)
                ? source.getIngredients()
                : ExtraDelightRecipeAdapterUtils.withContainer(source.getIngredients(), container);
    }

    private static List<Ingredient> molds(ChillerRecipe source) {
        List<Ingredient> molds = new ArrayList<>();
        Ingredient chiller = AdapterUtils.toMoldIngredient(getMoldStack());
        if (!chiller.isEmpty()) {
            molds.add(chiller);
        }

        if (source != null) {
            ItemStack container = source.getOutputContainer();
            if (isSecondaryMold(container)) {
                for (int i = 0; i < container.getCount(); i++) {
                    molds.add(Ingredient.of(container.copyWithCount(1)));
                }
            }
        }
        return List.copyOf(molds);
    }

    private static ItemStack getMoldStack() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    private static boolean isSecondaryMold(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (DelightRecipeAdapterUtils.isBakingTray(stack)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return BAR_MOLD_ID.equals(id);
    }
}
