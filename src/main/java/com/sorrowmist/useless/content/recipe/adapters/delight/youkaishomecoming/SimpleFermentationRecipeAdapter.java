package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightSyntheticRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Lite Youkai's Homecoming simple fermentation recipes. */
public final class SimpleFermentationRecipeAdapter
        implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final String SOURCE_CLASS =
            "dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe";
    private static final ResourceLocation FERMENTATION_TANK_ID =
            ResourceLocation.fromNamespaceAndPath("youkaisfeasts", "fermentation_tank");

    @Override
    public String sourceId() {
        return RecipeSourceIds.YOUKAI_HOMECOMING;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = DelightRecipeAdapterUtils.registeredItem(FERMENTATION_TANK_ID);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DelightSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> generated = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() == null || !SOURCE_CLASS.equals(holder.value().getClass().getName())) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = convertSource(holder.id(), holder.value());
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(), new DelightSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(generated);
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DelightSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe != null
                    && DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)
                    && (!recipe.inputs().isEmpty() || !recipe.inputFluids().isEmpty())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSource(ResourceLocation sourceId,
                                                              Object source) {
        @SuppressWarnings("unchecked")
        List<Ingredient> ingredients = DelightRecipeAdapterUtils.fieldValue(
                source, "ingredients", List.class);
        @SuppressWarnings("unchecked")
        List<ItemStack> results = DelightRecipeAdapterUtils.fieldValue(
                source, "results", List.class);
        FluidStack inputFluid = DelightRecipeAdapterUtils.fieldValue(
                source, "inputFluid", FluidStack.class);
        FluidStack outputFluid = DelightRecipeAdapterUtils.fieldValue(
                source, "outputFluid", FluidStack.class);

        List<Ingredient> itemInputs = new ArrayList<>();
        if (ingredients != null) {
            itemInputs.addAll(ingredients);
        }
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(itemInputs);
        List<ItemStack> outputs = itemOutputs(results);
        List<LongSizedFluidIngredient> inputFluids = fluidInputs(inputFluid);
        List<FluidStack> outputFluids = fluidOutputs(outputFluid);

        ItemStack bottledOutput = YoukaiRecipeAdapterUtils.bottledOutput(outputFluid);
        if (bottledOutput != null) {
            outputs = new ArrayList<>(outputs);
            outputs.add(bottledOutput);
            outputFluids = List.of();

            Item container = YoukaiRecipeAdapterUtils.emptyContainer(outputFluid);
            if (container != null) {
                for (int i = 0; i < bottledOutput.getCount(); i++) {
                    itemInputs.add(Ingredient.of(container));
                }
                inputs = AdapterUtils.mergeIngredients(itemInputs);
            }
        }
        if ((inputs.isEmpty() && inputFluids.isEmpty())
                || (outputs.isEmpty() && outputFluids.isEmpty())) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                inputs,
                inputFluids,
                List.of(),
                outputs,
                outputFluids,
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                Math.max(1, DelightRecipeAdapterUtils.intField(
                        source, "time", AdapterUtils.DEFAULT_PROCESS_TIME)),
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(moldStack())),
                AlloyFurnaceMode.NORMAL
        );
    }

    private static List<ItemStack> itemOutputs(@Nullable List<ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : source) {
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    private static List<LongSizedFluidIngredient> fluidInputs(@Nullable FluidStack inputFluid) {
        if (inputFluid == null || inputFluid.isEmpty() || inputFluid.getAmount() <= 0) {
            return List.of();
        }
        return List.of(LongSizedFluidIngredient.from(inputFluid));
    }

    private static List<FluidStack> fluidOutputs(@Nullable FluidStack outputFluid) {
        if (outputFluid == null || outputFluid.isEmpty() || outputFluid.getAmount() <= 0) {
            return List.of();
        }
        return List.of(outputFluid.copy());
    }

    @Nullable
    private static ItemStack moldStack() {
        Item item = DelightRecipeAdapterUtils.registeredItem(FERMENTATION_TANK_ID);
        return item == null ? null : item.getDefaultInstance();
    }
}
