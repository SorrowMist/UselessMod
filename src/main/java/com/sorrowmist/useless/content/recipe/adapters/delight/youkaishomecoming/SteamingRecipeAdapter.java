package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import dev.xkmc.youkaishomecoming.content.pot.steamer.SteamingRecipe;
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

/** Converts Youkai's steamer recipes. */
public final class SteamingRecipeAdapter implements IRecipeAdapter<SteamingRecipe> {
    private static final ResourceLocation STEAMER_POT_ID =
            ResourceLocation.fromNamespaceAndPath("youkaisfeasts", "steamer_pot");

    @Override
    public String sourceId() {
        return RecipeSourceIds.YOUKAI_HOMECOMING;
    }

    @Override
    public Class<SteamingRecipe> getRecipeClass() {
        return SteamingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = DelightRecipeAdapterUtils.registeredItem(STEAMER_POT_ID);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SteamingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        SteamingRecipe source = holder.value();
        Ingredient input = source.getIngredients().isEmpty()
                ? Ingredient.EMPTY : source.getIngredients().getFirst();
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (input.isEmpty() || output == null || output.isEmpty() || output.getCount() <= 0) {
            return List.of();
        }

        int processTime = source.getCookingTime() > 0
                ? source.getCookingTime() : AdapterUtils.DEFAULT_PROCESS_TIME;
        long energy = Math.max(1L,
                (long) processTime * AdapterUtils.DEFAULT_ENERGY
                        / AdapterUtils.DEFAULT_PROCESS_TIME);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
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
    public List<RecipeHolder<SteamingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<SteamingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<SteamingRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), SteamingRecipe.class)) {
            SteamingRecipe recipe = holder.value();
            Ingredient input = recipe.getIngredients().isEmpty()
                    ? Ingredient.EMPTY : recipe.getIngredients().getFirst();
            if (!input.isEmpty() && AdapterUtils.matchesRequired(
                    mergedInputs, Map.of(input, 1L))) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
