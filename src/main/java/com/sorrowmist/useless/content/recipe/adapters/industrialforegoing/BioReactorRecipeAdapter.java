package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.block.generator.tile.BioReactorTile;
import com.buuz135.industrial.config.machine.generator.BioReactorConfig;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleGenerator;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts Industrial Foregoing's tag-driven bioreactor operation. */
public final class BioReactorRecipeAdapter
        implements IRecipeAdapter<IndustrialForegoingSyntheticRecipe> {
    private static final int SINGLE_SLOT_FLUID = 95;

    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<IndustrialForegoingSyntheticRecipe> getRecipeClass() {
        return IndustrialForegoingSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModuleGenerator.BIOREACTOR.getBlock());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();

        List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> result = new ArrayList<>();
        Set<TagKey<Item>> tags = new LinkedHashSet<>();
        for (TagKey<Item> tag : BioReactorTile.VALID) {
            if (tag != null && tags.add(tag)) {
                AdvancedAlloyFurnaceRecipe recipe = createRecipe(tag);
                result.add(new RecipeHolder<>(recipe.id(),
                        new IndustrialForegoingSyntheticRecipe(recipe)));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IndustrialForegoingSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        Map<Ingredient, Long> safeInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> safeFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IndustrialForegoingSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (matchesRecipe(recipe, safeInputs, safeFluids)) matches.add(holder);
        }
        return matches;
    }

    private static boolean matchesRecipe(AdvancedAlloyFurnaceRecipe recipe,
                                         Map<Ingredient, Long> mergedInputs,
                                         Map<FluidStack, Long> mergedFluids) {
        if (recipe == null || recipe.inputs().isEmpty()) return false;
        Map<Ingredient, Long> requiredInputs = new java.util.LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(requiredInputs, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, requiredInputs)
                && FluidIngredientAllocator.matchesLong(recipe.inputFluids(), mergedFluids, 1L);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(TagKey<Item> inputTag) {
        ResourceLocation tagId = inputTag.location();
        String tagPath = tagId.getNamespace() + "_" + tagId.getPath().replace('/', '_');
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.INDUSTRIAL_FOREGOING, "bioreactor_" + tagPath);
        FluidIngredient water = FluidIngredient.single(Fluids.WATER);
        FluidStack biofuel = new FluidStack(
                ModuleCore.BIOFUEL.getSourceFluid().get(), SINGLE_SLOT_FLUID);
        return new AdvancedAlloyFurnaceRecipe(
                recipeId,
                List.of(new CountedIngredient(Ingredient.of(inputTag), 1L)),
                List.of(new LongSizedFluidIngredient(water, SINGLE_SLOT_FLUID)),
                List.of(),
                List.of(),
                List.of(biofuel),
                List.of(),
                Math.max(1, BioReactorConfig.powerPerOperation),
                Math.max(1, BioReactorConfig.maxProgress),
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(new ItemStack(ModuleGenerator.BIOREACTOR.getBlock()))),
                AlloyFurnaceMode.NORMAL);
    }
}
