package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.content.enchanter.EnchanterRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Ender IO enchanter recipes to the alloy furnace. */
public final class EnchanterRecipeAdapter implements IRecipeAdapter<EnchanterRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<EnchanterRecipe> getRecipeClass() {
        return EnchanterRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EIOBlocks.ENCHANTER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<EnchanterRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        EnchanterRecipe source = holder.value();
        CountedIngredient catalyst = EnderIOAdapterUtils.counted(source.input());
        if (catalyst == null || source.getEnchantmentLevel(source.input().count()) <= 0) {
            LOGGER.warn("Skipping invalid Ender IO enchanter recipe: {}", holder.id());
            return List.of();
        }

        List<CountedIngredient> inputs = List.of(
                new CountedIngredient(Ingredient.of(Items.WRITABLE_BOOK), 1),
                catalyst,
                new CountedIngredient(Ingredient.of(Tags.Items.GEMS_LAPIS),
                        Math.max(1, source.getLapisForLevel(1)))
        );
        ItemStack output = source.getBookForLevel(1);
        if (output.isEmpty()) {
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(output),
                List.of(),
                Math.max(1, source.getXPCostForLevel(1)),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<EnchanterRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<EnchanterRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<EnchanterRecipe> holder : manager.getAllRecipesFor(
                com.enderio.enderio.init.EIORecipes.ENCHANTING.type().get())) {
            EnchanterRecipe source = holder.value();
            CountedIngredient catalyst = EnderIOAdapterUtils.counted(source.input());
            if (catalyst == null) {
                continue;
            }
            Map<Ingredient, Long> requirements = new LinkedHashMap<>();
            requirements.put(Ingredient.of(Items.WRITABLE_BOOK), 1L);
            AdapterUtils.mergeIngredient(requirements, catalyst.ingredient(), catalyst.count());
            AdapterUtils.mergeIngredient(requirements, Ingredient.of(Tags.Items.GEMS_LAPIS),
                    Math.max(1, source.getLapisForLevel(1)));
            if (AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
