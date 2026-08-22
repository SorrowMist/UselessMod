package com.sorrowmist.useless.content.recipe.adapters.modernindustrialization;

import aztech.modern_industrialization.MIRegistries;
import aztech.modern_industrialization.blocks.forgehammer.ForgeHammerRecipe;
import aztech.modern_industrialization.items.ForgeTool;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
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

/** Converts Modern Industrialization Forge Hammer recipes to alloy-furnace recipes. */
public final class ForgeHammerRecipeAdapter implements IRecipeAdapter<ForgeHammerRecipe> {
    private static final ResourceLocation FORGE_HAMMER_ID =
            ResourceLocation.fromNamespaceAndPath("modern_industrialization", "forge_hammer");

    @Override
    public String sourceId() {
        return RecipeSourceIds.MODERN_INDUSTRIALIZATION;
    }

    @Override
    public Class<ForgeHammerRecipe> getRecipeClass() {
        return ForgeHammerRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        ItemStack mold = new ItemStack(BuiltInRegistries.ITEM.get(FORGE_HAMMER_ID));
        return mold.isEmpty() ? null : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ForgeHammerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        ForgeHammerRecipe source = holder.value();
        if (source.ingredient() == null || source.ingredient().isEmpty()
                || source.count() <= 0 || source.result().isEmpty()) {
            return List.of();
        }

        List<Ingredient> molds = new ArrayList<>();
        Ingredient machineMold = AdapterUtils.toMoldIngredient(getMoldItem());
        if (machineMold.isEmpty()) return List.of();
        molds.add(machineMold);
        if (source.hammerDamage() > 0) {
            molds.add(Ingredient.of(ForgeTool.TAG));
        }

        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.ingredient(), source.count())),
                List.of(),
                List.of(),
                List.of(source.result().copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
        return List.of(converted);
    }

    @Override
    public List<RecipeHolder<ForgeHammerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ForgeHammerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ForgeHammerRecipe> holder : manager.getAllRecipesFor(
                MIRegistries.FORGE_HAMMER_RECIPE_TYPE.get())) {
            ForgeHammerRecipe source = holder.value();
            if (source != null && source.ingredient() != null && !source.ingredient().isEmpty()
                    && AdapterUtils.hasMatchingIngredient(
                    mergedInputs, source.ingredient(), source.count())) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
