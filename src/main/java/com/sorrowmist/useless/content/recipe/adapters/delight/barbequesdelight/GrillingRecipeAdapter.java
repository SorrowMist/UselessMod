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
import java.util.List;
import java.util.Map;

/** Converts Barbeque's Delight grill recipes. */
public final class GrillingRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final String SOURCE_CLASS =
            "com.mao.barbequesdelight.content.recipe.SimpleGrillingRecipe";
    private static final ResourceLocation GRILL_ID =
            ResourceLocation.fromNamespaceAndPath("barbequesdelight", "grill");

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
        Ingredient ingredient = DelightRecipeAdapterUtils.fieldValue(source, "ingredient",
                Ingredient.class);
        ItemStack output = DelightRecipeAdapterUtils.fieldValue(source, "output", ItemStack.class);
        if (AdapterUtils.isIngredientEmpty(ingredient) || output == null || output.isEmpty()
                || output.getCount() <= 0) {
            return null;
        }

        int processTime = Math.max(1, DelightRecipeAdapterUtils.intField(
                source, "barbecuingTime", AdapterUtils.DEFAULT_PROCESS_TIME));
        int energy = AdapterUtils.safeInt(
                (long) AdapterUtils.DEFAULT_ENERGY * processTime / 200L);
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                List.of(new CountedIngredient(ingredient, 1L)),
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                Math.max(1, energy),
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(moldStack())),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Nullable
    private static Item moldItem() {
        return BuiltInRegistries.ITEM.getOptional(GRILL_ID).orElse(null);
    }

    @Nullable
    private static ItemStack moldStack() {
        Item item = moldItem();
        return item == null ? null : item.getDefaultInstance();
    }
}
