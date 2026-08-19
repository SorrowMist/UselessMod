package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.breakinblocks.neovitae.api.recipe.AraVitaeRecipe;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
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
import java.util.List;
import java.util.Map;

/**
 * Converts NeoVitae Blood Altar (Ara Vitae) recipes into alloy-furnace recipes.
 *
 * <p>The blood altar normally requires a built multi-block altar and Essentia Vitae (LP) at the
 * required tier. In the alloy furnace the altar's LP cost is paid as FE energy and the craft time
 * is derived from {@code bloodNeeded / craftSpeed}, so the same output is produced without the
 * altar structure or blood supply.</p>
 */
public final class AraVitaeRecipeAdapter implements IRecipeAdapter<AraVitaeRecipe> {

    /** 1 LP is mapped to 1 FE for the base cost. */
    private static final long LP_TO_FE = 1L;

    @Override
    public Class<AraVitaeRecipe> getRecipeClass() {
        return AraVitaeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return NeoVitaeAdapterUtils.item("ara_vitae");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AraVitaeRecipe> holder, Level level) {
        AraVitaeRecipe source = holder == null ? null : holder.value();
        if (source == null || source.getInput() == null || source.getInput().isEmpty()) {
            return List.of();
        }
        ItemStack output = source.getResult();
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return List.of();
        }
        return List.of(createRecipe(AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.getInput(), 1)), output, source));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AraVitaeRecipe> holder, Level level, List<ItemStack> actualInputs) {
        AraVitaeRecipe source = holder == null ? null : holder.value();
        if (source == null || !source.shouldCopyInputComponents()) {
            return convertAll(holder, level);
        }
        if (source.getInput() == null || source.getInput().isEmpty()) {
            return List.of();
        }
        ItemStack base = source.getResult();
        if (base == null || base.isEmpty() || base.getCount() <= 0) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (ItemStack input : actualInputs) {
            if (input == null || input.isEmpty() || !source.getInput().test(input)) {
                continue;
            }
            if (!seen.add(input.getItem())) {
                continue;
            }
            ItemStack output = base.copy();
            output.applyComponents(input.getComponentsPatch());
            recipes.add(createRecipe(variantId(holder.id(), input),
                    List.of(new CountedIngredient(source.getInput(), 1)), output, source));
        }
        return recipes.isEmpty() ? convertAll(holder, level) : recipes;
    }

    @Override
    public List<RecipeHolder<AraVitaeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<AraVitaeRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<AraVitaeRecipe> holder : manager.getAllRecipesFor(NVRecipes.ARA_VITAE_TYPE.get())) {
            AraVitaeRecipe source = holder.value();
            if (source == null || source.getInput() == null || source.getInput().isEmpty()) {
                continue;
            }
            if (AdapterUtils.countMatchingIngredient(mergedInputs, source.getInput()) >= 1L) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, List<CountedIngredient> inputs, ItemStack output, AraVitaeRecipe source) {
        int processTime = Math.max(1, source.getTotalBlood() / Math.max(1, source.getCraftSpeed()));
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, (long) source.getTotalBlood() * LP_TO_FE);
        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(output.copy()),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private static ResourceLocation variantId(ResourceLocation source, ItemStack input) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem());
        String suffix = itemId == null ? "dynamic" : itemId.getNamespace() + "_" + itemId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), source.getPath() + "_converted_" + suffix);
    }
}
