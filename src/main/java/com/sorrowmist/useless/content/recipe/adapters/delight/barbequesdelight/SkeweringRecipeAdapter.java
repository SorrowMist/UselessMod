package com.sorrowmist.useless.content.recipe.adapters.delight.barbequesdelight;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightSyntheticRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Barbeque's Delight skewer recipes, consuming the skewer as an input. */
public final class SkeweringRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final String SOURCE_CLASS =
            "com.mao.barbequesdelight.content.recipe.SimpleSkeweringRecipe";
    private static final ResourceLocation BASIN_ID =
            ResourceLocation.fromNamespaceAndPath("barbequesdelight", "basin");

    @Override
    public String sourceId() {
        return RecipeSourceIds.BARBEQUES_DELIGHT;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item basin = DelightRecipeAdapterUtils.registeredItem(BASIN_ID);
        return basin == null ? ItemStack.EMPTY : basin.getDefaultInstance();
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
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DelightSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe != null && DelightRecipeAdapterUtils.matchesItems(
                    recipe.inputs(), mergedInputs, List.of())
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSource(ResourceLocation sourceId,
                                                              Object source) {
        Ingredient tool = DelightRecipeAdapterUtils.fieldValue(source, "tool", Ingredient.class);
        Ingredient ingredient = DelightRecipeAdapterUtils.fieldValue(source, "ingredient",
                Ingredient.class);
        Ingredient side = DelightRecipeAdapterUtils.fieldValue(source, "side", Ingredient.class);
        ItemStack output = DelightRecipeAdapterUtils.fieldValue(source, "output", ItemStack.class);
        int ingredientCount = DelightRecipeAdapterUtils.intField(source, "ingredientCount", 0);
        int sideCount = DelightRecipeAdapterUtils.intField(source, "sideCount", 0);
        if (AdapterUtils.isIngredientEmpty(tool) || AdapterUtils.isIngredientEmpty(ingredient)
                || ingredientCount <= 0 || (sideCount > 0 && AdapterUtils.isIngredientEmpty(side))
                || output == null || output.isEmpty() || output.getCount() <= 0) {
            return null;
        }

        Map<Ingredient, Long> inputMap = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(inputMap, tool, 1L);
        AdapterUtils.mergeIngredient(inputMap, ingredient, ingredientCount);
        if (sideCount > 0) {
            AdapterUtils.mergeIngredient(inputMap, side, sideCount);
        }
        List<CountedIngredient> inputs = inputMap.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        if (inputs.isEmpty()) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(moldStack())),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Nullable
    private static Item moldItem() {
        return BuiltInRegistries.ITEM.getOptional(BASIN_ID).orElse(null);
    }

    @Nullable
    private static ItemStack moldStack() {
        Item item = moldItem();
        return item == null ? null : item.getDefaultInstance();
    }
}
