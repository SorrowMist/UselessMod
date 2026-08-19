package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts NeoVitae Hellfire Forge recipes into alloy-furnace recipes.
 *
 * <p>The hellfire forge normally consumes spiritus ("will") held in a spiritus gem. In the alloy
 * furnace the gem requirement is replaced by an FE energy cost scaled from {@code minDrain}, so the
 * four crafting inputs produce the same output without feeding the forge with spiritus.</p>
 */
public final class ForgeRecipeAdapter implements IRecipeAdapter<ForgeRecipe> {

    /** 1 spiritus point is mapped to 1000 FE so forge costs land in a sensible FE range. */
    private static final double SPIRITUS_TO_FE = 1000D;
    private static final int PROCESS_TIME = 200;

    @Override
    public Class<ForgeRecipe> getRecipeClass() {
        return ForgeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return NeoVitaeAdapterUtils.item("hellfire_forge");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ForgeRecipe> holder, Level level) {
        ForgeRecipe source = holder == null ? null : holder.value();
        if (source == null) {
            return List.of();
        }
        ItemStack output = source.getOutput();
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return List.of();
        }
        List<CountedIngredient> inputs = countedInputs(source);
        if (inputs == null) {
            return List.of();
        }

        double minSpiritus = source.getMinSpiritus() == null ? 0D : source.getMinSpiritus();
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, (long) Math.ceil(minSpiritus * SPIRITUS_TO_FE));
        return List.of(createRecipe(AdapterUtils.convertedId(holder.id()), inputs, output, energy));
    }

    @Override
    public List<RecipeHolder<ForgeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<ForgeRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<ForgeRecipe> holder : manager.getAllRecipesFor(NVRecipes.HELLFIRE_FORGE_TYPE.get())) {
            ForgeRecipe source = holder.value();
            if (source == null) {
                continue;
            }
            Map<Ingredient, Long> required = requirements(source);
            if (required != null && AdapterUtils.matchesRequired(mergedInputs, required)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static List<CountedIngredient> countedInputs(ForgeRecipe source) {
        Map<Ingredient, Long> required = requirements(source);
        if (required == null) {
            return null;
        }
        List<CountedIngredient> inputs = new ArrayList<>(required.size());
        for (Map.Entry<Ingredient, Long> entry : required.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        return inputs;
    }

    @Nullable
    private static Map<Ingredient, Long> requirements(ForgeRecipe source) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (Ingredient ingredient : source.getCraftingIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            AdapterUtils.mergeIngredient(required, ingredient, 1L);
        }
        return required.isEmpty() ? null : required;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, List<CountedIngredient> inputs, ItemStack output, long energy) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(output.copy()),
                List.of(),
                energy,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }
}
