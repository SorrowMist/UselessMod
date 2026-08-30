package com.sorrowmist.useless.content.recipe.adapters.delight.brewinandchewin;

import com.mojang.datafixers.util.Either;
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
import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;
import umpaz.brewinandchewin.common.crafting.FluidIngredientWithAmount;
import umpaz.brewinandchewin.common.crafting.KegFermentingRecipe;
import umpaz.brewinandchewin.common.crafting.KegPouringRecipe;
import umpaz.brewinandchewin.common.registry.BnCItems;
import umpaz.brewinandchewin.common.registry.BnCRecipeTypes;
import umpaz.brewinandchewin.common.utility.AbstractedFluidIngredient;
import umpaz.brewinandchewin.common.utility.AbstractedFluidStack;
import umpaz.brewinandchewin.neoforge.utility.KegCompatibleFluidIngredients;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Brewin And Chewin keg fermentation recipes. */
public final class KegFermentingRecipeAdapter implements IRecipeAdapter<KegFermentingRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.BREWIN_AND_CHEWIN;
    }

    @Override
    public Class<KegFermentingRecipe> getRecipeClass() {
        return KegFermentingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(BnCItems.KEG);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<KegFermentingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        KegFermentingRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.getIngredients());
        List<LongSizedFluidIngredient> inputFluids = fluidInputs(source.getFluidIngredient());
        if (inputs.isEmpty() && inputFluids.isEmpty()) {
            return List.of();
        }

        Either<AbstractedFluidStack, ItemStack> result = source.getResult();
        if (result == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = List.of();
        Optional<ItemStack> itemResult = result.right();
        if (itemResult.isPresent()) {
            ItemStack output = itemResult.get();
            if (!output.isEmpty() && output.getCount() > 0) {
                outputs.add(output.copy());
            }
        } else {
            Optional<AbstractedFluidStack> fluidResult = result.left();
            if (fluidResult.isPresent()) {
                BottledResult bottled = bottledResult(level, fluidResult.get());
                if (bottled == null) {
                    return List.of();
                }
                inputs = addContainer(inputs, bottled.container(), bottled.operations());
                outputs.add(bottled.output());
            }
        }
        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            return List.of();
        }

        int processTime = Math.max(1, source.getFermentTime());
        long energy = Math.max(1L,
                (long) processTime * AdapterUtils.DEFAULT_ENERGY / 200L);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                inputFluids,
                List.of(),
                outputs,
                outputFluids,
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<KegFermentingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || (mergedInputs == null || mergedInputs.isEmpty())
                && (mergedFluids == null || mergedFluids.isEmpty())) {
            return List.of();
        }

        List<RecipeHolder<KegFermentingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<KegFermentingRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), KegFermentingRecipe.class)) {
            KegFermentingRecipe source = holder.value();
            List<CountedIngredient> itemRequirements =
                    AdapterUtils.mergeIngredients(source.getIngredients());
            Optional<AbstractedFluidStack> fluidResult = source.getResult().left();
            if (fluidResult.isPresent()) {
                BottledResult bottled = bottledResult(level, fluidResult.get());
                if (bottled == null) {
                    continue;
                }
                itemRequirements = addContainer(itemRequirements, bottled.container(), bottled.operations());
            }
            List<LongSizedFluidIngredient> fluidRequirements =
                    fluidInputs(source.getFluidIngredient());
            if (isMatch(itemRequirements, fluidRequirements, mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static List<CountedIngredient> addContainer(List<CountedIngredient> inputs,
                                                         ItemStack container, int operations) {
        if (container == null || container.isEmpty() || operations <= 0) {
            return List.of();
        }
        Ingredient containerIngredient = Ingredient.of(container.copyWithCount(1));
        List<CountedIngredient> result = new ArrayList<>(inputs);
        for (int i = 0; i < result.size(); i++) {
            CountedIngredient existing = result.get(i);
            if (AdapterUtils.areIngredientsEqual(existing.ingredient(), containerIngredient)) {
                long count;
                try {
                    count = Math.addExact(existing.count(), operations);
                } catch (ArithmeticException exception) {
                    return List.of();
                }
                result.set(i, new CountedIngredient(existing.ingredient(), count));
                return List.copyOf(result);
            }
        }
        result.add(new CountedIngredient(containerIngredient, operations));
        return List.copyOf(result);
    }

    @Nullable
    private static BottledResult bottledResult(Level level, AbstractedFluidStack fluid) {
        if (level == null || fluid == null || fluid.isEmpty()) {
            return null;
        }
        long available = fluid.unit().convertToLoader(fluid.amount());
        if (available <= 0L) {
            return null;
        }

        return level.getRecipeManager().getAllRecipesFor(BnCRecipeTypes.KEG_POURING).stream()
                .map(RecipeHolder::value)
                .sorted(Comparator.comparing(KegPouringRecipe::isStrict))
                .filter(pouring -> pouring.getRawFluid().matches(fluid))
                .map(pouring -> {
                    long perContainer = pouring.getLoaderAmount();
                    if (perContainer <= 0L || available % perContainer != 0L) {
                        return null;
                    }
                    long operations = available / perContainer;
                    ItemStack container = pouring.getContainer();
                    ItemStack output = pouring.getOutput();
                    if (operations <= 0L || operations > Integer.MAX_VALUE
                            || container == null || container.isEmpty()
                            || output == null || output.isEmpty()) {
                        return null;
                    }
                    long outputCount = (long) output.getCount() * operations;
                    if (outputCount <= 0L || outputCount > Integer.MAX_VALUE) {
                        return null;
                    }
                    return new BottledResult(container.copyWithCount(1),
                            output.copyWithCount((int) outputCount), (int) operations);
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static boolean isMatch(List<CountedIngredient> itemRequirements,
                                   List<LongSizedFluidIngredient> fluidRequirements,
                                   @Nullable Map<Ingredient, Long> mergedInputs,
                                   @Nullable Map<FluidStack, Long> mergedFluids) {
        return DelightRecipeAdapterUtils.matchesItems(
                itemRequirements, mergedInputs, List.of())
                && DelightRecipeAdapterUtils.matchesFluids(fluidRequirements, mergedFluids);
    }

    private static List<LongSizedFluidIngredient> fluidInputs(
            Optional<FluidIngredientWithAmount> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        FluidIngredientWithAmount input = source.get();
        FluidIngredient ingredient = toFluidIngredient(input.ingredient());
        long amount = input.loaderAmount();
        if (ingredient == null || ingredient.isEmpty() || amount <= 0L) {
            return List.of();
        }
        return List.of(new LongSizedFluidIngredient(ingredient, amount));
    }

    @Nullable
    private static FluidIngredient toFluidIngredient(AbstractedFluidIngredient source) {
        if (source == null) {
            return null;
        }

        if (source instanceof KegCompatibleFluidIngredients.Tag tag) {
            var key = tag.getTagKey();
            if (key != null) {
                return FluidIngredient.tag(key);
            }
        }

        List<FluidIngredient> choices = new ArrayList<>();
        for (AbstractedFluidStack display : source.displayStacks()) {
            if (display == null || display.isEmpty()) {
                continue;
            }
            Object loaderSpecific = display.loaderSpecific();
            if (!(loaderSpecific instanceof FluidStack fluid) || fluid.isEmpty()) {
                continue;
            }
            FluidIngredient choice = AdapterUtils.toSizedFluidIngredient(fluid).ingredient();
            choices.add(choice);
        }
        if (choices.isEmpty()) {
            return null;
        }
        return choices.size() == 1 ? choices.getFirst() : CompoundFluidIngredient.of(choices);
    }

    private record BottledResult(ItemStack container, ItemStack output, int operations) {
    }
}
