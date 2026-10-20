package com.sorrowmist.useless.content.recipe.adapters.summoningrituals;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.almostreliable.summoningrituals.Registration;
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
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import com.almostreliable.summoningrituals.recipe.component.IngredientStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Summoning Rituals item-output altar recipes into alloy-furnace recipes. */
public final class SummoningRitualsAltarRecipeAdapter implements IRecipeAdapter<AltarRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ALTAR_ID = new ResourceLocation(
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
            RateLimitedLogger.warn(LOGGER, "Skipping unsupported Summoning Rituals altar recipe: {}", holder.id());
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
        for (RecipeHolder<AltarRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(recipeManager,
                Registration.ALTAR_RECIPE.type().get())) {
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
        if (source == null || source.getRecipeTime() <= 0
                || source.getCatalyst() == null || source.getCatalyst().isEmpty()) {
            return null;
        }

        var sourceInputs = source.getInputs();
        if (sourceInputs == null || !source.getSacrifices().isEmpty()) return null;
        var sourceOutputs = source.getOutputs();
        if (sourceOutputs == null || sourceOutputs.size() == 0) return null;
        List<ItemStack> outputs = new ArrayList<>();
        boolean[] unsupported = {false};
        sourceOutputs.forEach((type, output, index) -> {
            if (type != com.almostreliable.summoningrituals.recipe.component.RecipeOutputs.OutputType.ITEM
                    || !(output.getOutput() instanceof ItemStack stack) || stack.isEmpty()) unsupported[0]=true;
            else {
                ItemStack result = nativeItemOutput(stack, output.getData());
                if (result == null || result.isEmpty()) unsupported[0] = true;
                else outputs.add(result);
            }
        });
        if (unsupported[0]) return null;

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (!addRequirement(requirements, source.getCatalyst(), 1L)) {
            return null;
        }
        for (IngredientStack input : sourceInputs) {
            if (input == null || !addRequirement(requirements, input.ingredient(), input.count())) {
                return null;
            }
        }

        if (requirements.isEmpty() || outputs.isEmpty()) {
            return null;
        }

        List<CountedIngredient> inputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(inputs, requirements, List.copyOf(outputs), Math.max(1, source.getRecipeTime()));
    }

    /** Summoning Rituals applies data to each spawned ItemEntity, not to the item tag.
     * It splits the template into batches of at most four before replacing entity NBT.
     * Reject other entity-only semantics instead of silently changing ownership/lifetime.
     */
    static ItemStack nativeItemOutput(ItemStack template, net.minecraft.nbt.CompoundTag data) {
        if (data == null || data.isEmpty()) return template.copy();
        if (!data.getAllKeys().equals(java.util.Set.of("Item"))
                || !data.contains("Item", net.minecraft.nbt.Tag.TAG_COMPOUND)) return null;
        ItemStack replaced = ItemStack.of(data.getCompound("Item"));
        if (replaced.isEmpty()) return null;
        long total = ((template.getCount() + 3L) / 4L) * replaced.getCount();
        if (total <= 0 || total > Integer.MAX_VALUE) return null;
        replaced.setCount((int)total);
        return replaced;
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
