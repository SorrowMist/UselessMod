package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingAdapterUtils;
import committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Re-Avaritia's five-slot extreme smithing recipes. */
public final class ReAvaritiaExtremeSmithingRecipeAdapter
        implements IRecipeAdapter<ExtremeSmithingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String sourceId() {
        return RecipeSourceIds.AVARITIA;
    }

    @Override
    public Class<ExtremeSmithingRecipe> getRecipeClass() {
        return ExtremeSmithingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.extreme_smithing_table.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ExtremeSmithingRecipe> holder, Level level) {
        return convertAll(holder, level, List.of());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ExtremeSmithingRecipe> holder,
            Level level,
            List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Re-Avaritia extreme smithing recipe: {}", holder.id());
            return List.of();
        }

        if (actualInputs != null && !actualInputs.isEmpty()) {
            ExtremeSmithingRecipeInput input = resolveInput(holder.value(), actualInputs);
            if (input == null || level == null || !holder.value().matches(input, level)) {
                return List.of();
            }
            ItemStack assembled;
            try {
                assembled = holder.value().assemble(input, level.registryAccess());
            } catch (RuntimeException exception) {
                return List.of();
            }
            if (assembled == null || assembled.isEmpty() || assembled.getCount() <= 0) {
                return List.of();
            }
            converted = converted.withOutputs(List.of(assembled.copy()));
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<ExtremeSmithingRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<ExtremeSmithingRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<ExtremeSmithingRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.EXTREME_SMITHING_RECIPE.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable ExtremeSmithingRecipe source) {
        if (source == null || source.template == null || source.base == null || source.additions == null
                || source.template.isEmpty() || source.base.isEmpty() || source.additions.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requirements, source.template, 1L);
        AdapterUtils.mergeIngredient(requirements, source.base, 1L);
        AdapterUtils.mergeIngredient(requirements, source.additions, 3L);

        ItemStack result = ExtendedCraftingAdapterUtils.copyResult(source);
        if (result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        return new Converted(
                ExtendedCraftingAdapterUtils.countedIngredients(requirements),
                List.of(result),
                requirements);
    }

    @Nullable
    private static ExtremeSmithingRecipeInput resolveInput(
            ExtremeSmithingRecipe source, List<ItemStack> actualInputs) {
        List<ItemStack> available = new ArrayList<>();
        for (ItemStack stack : actualInputs) {
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                available.add(stack.copy());
            }
        }
        if (available.isEmpty()) {
            return null;
        }

        Ingredient[] slots = {
                source.template,
                source.base,
                source.additions,
                source.additions,
                source.additions
        };
        ItemStack[] selected = new ItemStack[slots.length];
        if (!assignSlot(0, slots, available, selected)) {
            return null;
        }
        return new ExtremeSmithingRecipeInput(
                selected[0], selected[1], selected[2], selected[3], selected[4]);
    }

    private static boolean assignSlot(
            int slot, Ingredient[] ingredients, List<ItemStack> available, ItemStack[] selected) {
        if (slot >= ingredients.length) {
            return true;
        }
        Ingredient ingredient = ingredients[slot];
        for (int index = 0; index < available.size(); index++) {
            ItemStack candidate = available.get(index);
            if (!ingredient.test(candidate)) {
                continue;
            }
            candidate.shrink(1);
            selected[slot] = candidate.copyWithCount(1);
            if (assignSlot(slot + 1, ingredients, available, selected)) {
                return true;
            }
            candidate.grow(1);
        }
        selected[slot] = ItemStack.EMPTY;
        return false;
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements) {
        private Converted withOutputs(List<ItemStack> replacement) {
            return new Converted(inputs, replacement, requirements);
        }
    }
}
