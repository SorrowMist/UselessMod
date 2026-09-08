package com.sorrowmist.useless.content.recipe.adapters.summoningrituals;

import com.almostreliable.summoningrituals.core.Registration;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Summoning Rituals item-output altar recipes into alloy-furnace recipes. */
public final class SummoningRitualsAltarRecipeAdapter implements IRecipeAdapter<AltarRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ALTAR_ID = ResourceLocation.fromNamespaceAndPath(
            "summoningrituals", "altar");

    @Override
    public Class<AltarRecipe> getRecipeClass() {
        return AltarRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return BuiltInRegistries.ITEM.getOptional(ALTAR_ID)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AltarRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping unsupported Summoning Rituals altar recipe: {}", holder.id());
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                converted.processTime(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<AltarRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<AltarRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<AltarRecipe> holder : recipeManager.getAllRecipesFor(
                Registration.ALTAR_RECIPE_TYPE.get())) {
            AltarRecipe source = holder.value();
            Converted converted = source == null ? null : convertData(source);
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable AltarRecipe source) {
        if (source == null || source.ticks() <= 0
                || source.initiator() == null || source.initiator().isEmpty()) {
            return null;
        }

        var sourceInputs = source.inputs();
        if (sourceInputs == null
                || !sourceInputs.entityInputs().isEmpty()
                || !sourceInputs.fakeEntityInputs().isEmpty()) {
            // The alloy furnace can only represent item inputs, not sacrificed entities.
            return null;
        }

        var sourceOutputs = source.outputs();
        if (sourceOutputs == null || sourceOutputs.itemOutputs().isEmpty()
                || !sourceOutputs.entityOutputs().isEmpty()
                || sourceOutputs.commandOutput().isPresent()) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (!addRequirement(requirements, source.initiator(), 1L)) {
            return null;
        }
        for (SizedIngredient input : sourceInputs.itemInputs()) {
            if (input == null || !addRequirement(requirements, input.ingredient(), input.count())) {
                return null;
            }
        }

        // Item and entity outputs can coexist, but dropping the entity part would change the
        // ritual. Recipes with entity outputs were filtered above; these are item-only outputs.
        List<ItemStack> outputs = new ArrayList<>(sourceOutputs.itemOutputs().size());
        for (var itemOutput : sourceOutputs.itemOutputs()) {
            if (itemOutput == null || itemOutput.item() == null
                    || itemOutput.item().isEmpty() || itemOutput.item().getCount() <= 0) {
                return null;
            }
            outputs.add(itemOutput.item().copy());
        }
        if (requirements.isEmpty() || outputs.isEmpty()) {
            return null;
        }

        List<CountedIngredient> inputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(inputs, requirements, List.copyOf(outputs), Math.max(1, source.ticks()));
    }

    private static boolean addRequirement(
            Map<Ingredient, Long> requirements, @Nullable Ingredient ingredient, long count) {
        if (ingredient == null || ingredient.isEmpty() || count <= 0L) {
            return false;
        }
        try {
            AdapterUtils.mergeIngredient(requirements, ingredient, count);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            List<ItemStack> outputs,
            int processTime) {
    }
}
