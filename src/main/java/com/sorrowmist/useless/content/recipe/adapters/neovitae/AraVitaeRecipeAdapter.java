package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.api.recipe.AraVitaeInput;
import com.breakinblocks.neovitae.api.recipe.AraVitaeRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Ara Vitae recipes and preserves copied input components at runtime. */
public final class AraVitaeRecipeAdapter implements IRecipeAdapter<AraVitaeRecipe> {
    private static final ItemStack MOLD = new ItemStack(NVBlocks.ARA_VITAE.asItem());

    @Override
    public Class<AraVitaeRecipe> getRecipeClass() {
        return AraVitaeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MOLD.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AraVitaeRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AraVitaeRecipe source = holder.value();
        if (source.getResult().isEmpty() || source.getCraftSpeed() <= 0
                || source.getTotalBlood() < 0) return List.of();
        return List.of(convert(holder.id(), source, source.getResult()));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AraVitaeRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null || actualInputs == null) return List.of();
        AraVitaeRecipe source = holder.value();
        if (!source.shouldCopyInputComponents()) return convertAll(holder, level);

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ItemStack input : NeoVitaeAdapterUtils.distinctMatches(actualInputs, source.getInput())) {
            ItemStack output;
            try {
                output = source.assemble(new AraVitaeInput(input, 0),
                        level == null ? null : level.registryAccess()).copy();
            } catch (RuntimeException exception) {
                continue;
            }
            if (!output.isEmpty()) {
                result.add(convert(holder.id(), source, output, input));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<AraVitaeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<AraVitaeRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<AraVitaeRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(NVRecipes.ARA_VITAE_TYPE.get())) {
            AraVitaeRecipe source = holder.value();
            if (source != null && NeoVitaeAdapterUtils.matchesItems(
                    mergedInputs, List.of(new CountedIngredient(source.getInput(), 1L)))) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static AdvancedAlloyFurnaceRecipe convert(
            net.minecraft.resources.ResourceLocation id, AraVitaeRecipe source,
            ItemStack output) {
        return convert(id, source, output, null);
    }

    private static AdvancedAlloyFurnaceRecipe convert(
            net.minecraft.resources.ResourceLocation id, AraVitaeRecipe source,
            ItemStack output, @Nullable ItemStack actualInput) {
        List<CountedIngredient> inputs = actualInput == null
                ? List.of(new CountedIngredient(source.getInput(), 1L))
                : List.of(new CountedIngredient(NeoVitaeAdapterUtils.exact(actualInput), 1L));
        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                NeoVitaeAdapterUtils.energyFor(source.getTotalBlood()),
                NeoVitaeAdapterUtils.ceilDivide(source.getTotalBlood(), source.getCraftSpeed()),
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(MOLD)),
                AlloyFurnaceMode.NORMAL);
    }
}
