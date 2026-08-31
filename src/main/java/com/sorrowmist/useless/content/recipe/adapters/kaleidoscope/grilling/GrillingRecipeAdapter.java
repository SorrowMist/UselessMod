package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.grilling;

import cn.breezeth.kaleidoscope_grilling.skewer.SkewerRecipes;
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
import java.util.List;
import java.util.Map;

/** Converts Kaleidoscope Grilling's skewer-cooking stage. */
public final class GrillingRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final int GRILLING_TIME = 800;
    private static final ResourceLocation GRILL_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_grilling", "grill");

    @Override
    public String sourceId() {
        return RecipeSourceIds.KALEIDOSCOPE_GRILLING;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item grill = DelightRecipeAdapterUtils.registeredItem(GRILL_ID);
        return grill == null ? ItemStack.EMPTY : grill.getDefaultInstance();
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
        for (SkewerRecipes.Cooking cooking : SkewerRecipes.cookingRecipes()) {
            if (cooking == null || cooking.raw() == null || cooking.raw().isEmpty()
                    || cooking.cooked() == null || cooking.cooked().isEmpty()) {
                continue;
            }

            ResourceLocation rawId = BuiltInRegistries.ITEM.getKey(cooking.raw().getItem());
            AdvancedAlloyFurnaceRecipe converted = convertSource(rawId, cooking);
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(),
                        new DelightSyntheticRecipe(converted)));
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
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation sourceId, SkewerRecipes.Cooking cooking) {
        ItemStack raw = cooking.raw();
        ItemStack cooked = cooking.cooked();
        if (sourceId == null || raw == null || raw.isEmpty()
                || cooked == null || cooked.isEmpty() || cooked.getCount() <= 0) {
            return null;
        }

        Item rawItem = BuiltInRegistries.ITEM.getOptional(sourceId).orElse(null);
        if (rawItem == null) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                List.of(new CountedIngredient(Ingredient.of(rawItem), 1L)),
                List.of(),
                List.of(),
                List.of(cooked.copy()),
                List.of(),
                List.of(),
                Math.max(1L, (long) GRILLING_TIME * AdapterUtils.DEFAULT_ENERGY / 200L),
                GRILLING_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(grillStack())),
                AlloyFurnaceMode.NORMAL);
    }

    @Nullable
    private static ItemStack grillStack() {
        Item grill = BuiltInRegistries.ITEM.getOptional(GRILL_ID).orElse(null);
        return grill == null ? null : grill.getDefaultInstance();
    }
}
