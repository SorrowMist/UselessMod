package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
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

/** Converts Youkai's Homecoming drying-rack recipes. */
public final class DryingRackRecipeAdapter implements IRecipeAdapter<DryingRackRecipe> {
    private static final ResourceLocation RACK_ID =
            ResourceLocation.fromNamespaceAndPath("youkaisfeasts", "drying_rack");

    @Override
    public String sourceId() {
        return RecipeSourceIds.YOUKAI_HOMECOMING;
    }

    @Override
    public Class<DryingRackRecipe> getRecipeClass() {
        return DryingRackRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        var item = DelightRecipeAdapterUtils.registeredItem(RACK_ID);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DryingRackRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        DryingRackRecipe source = holder.value();
        Ingredient ingredient = source.getIngredients().isEmpty()
                ? Ingredient.EMPTY : source.getIngredients().getFirst();
        if (ingredient.isEmpty()) {
            return List.of();
        }
        ItemStack result = source.getResultItem(level == null ? null : level.registryAccess());
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        int processTime = source.getCookingTime() > 0 ? source.getCookingTime() : 100;
        int energy = AdapterUtils.safeInt(
                (long) processTime * AdapterUtils.DEFAULT_ENERGY / 100L);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(ingredient, 1L)),
                List.of(),
                List.of(),
                List.of(result.copy()),
                List.of(),
                List.of(),
                Math.max(1, energy),
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<DryingRackRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<DryingRackRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DryingRackRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), DryingRackRecipe.class)) {
            DryingRackRecipe source = holder.value();
            Ingredient ingredient = source.getIngredients().isEmpty()
                    ? Ingredient.EMPTY : source.getIngredients().getFirst();
            if (!ingredient.isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs,
                    Map.of(ingredient, 1L))) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
