package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.mojang.logging.LogUtils;
import com.sammy.malum.common.recipe.SpiritFocusingRecipe;
import com.sammy.malum.registry.common.recipe.RecipeTypeRegistry;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts Malum 1.6.7 spirit-crucible recipes ({@code malum:spirit_focusing}).
 *
 * <p>{@link SpiritFocusingRecipe#input} is a reusable tool, not a consumed material: the crucible
 * damages it instead of shrinking it. The converted recipe records that input with
 * {@link CountedIngredient#consumed()} {@code false}, so the alloy furnace requires it to be
 * present but never consumes it. Native durability loss/cracking is not represented by that
 * slot, so only recipes with zero {@code durabilityCost} are converted. Positive wear costs
 * are rejected rather than silently waived. All consumable spirit shards and outputs are exact.</p>
 */
public final class SpiritFocusingRecipeAdapter implements IRecipeAdapter<SpiritFocusingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<SpiritFocusingRecipe> getRecipeClass() {
        return SpiritFocusingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MalumAdapterUtils.item("spirit_crucible");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SpiritFocusingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Malum spirit focusing recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(AdapterUtils.convertedId(holder.id()), converted));
    }

    @Override
    public List<RecipeHolder<SpiritFocusingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<SpiritFocusingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<SpiritFocusingRecipe> holder :
                RecipeManagerAccess.getAllRecipesFor(recipeManager, RecipeTypeRegistry.SPIRIT_FOCUSING.get())) {
            SpiritFocusingRecipe source = holder.value();
            if (!isValid(source)
                    || !AdapterUtils.hasMatchingIngredient(mergedInputs, source.input, 1L)
                    || !AdapterUtils.matchesRequired(mergedInputs, spiritRequirements(source))) {
                continue;
            }
            matches.add(holder);
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable SpiritFocusingRecipe source) {
        if (!isValid(source)) {
            return null;
        }

        List<CountedIngredient> inputs = new ArrayList<>();
        // The crucible keeps the focusing item in its slot and only spends durability. The alloy
        // furnace has the same notion through CountedIngredient#consumed == false.
        inputs.add(new CountedIngredient(source.input, 1L, false));
        for (Map.Entry<Ingredient, Long> entry : spiritRequirements(source).entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        return new Converted(List.copyOf(inputs), source.output.copy(), source.time);
    }

    private static boolean isValid(@Nullable SpiritFocusingRecipe source) {
        if (source == null || source.time <= 0 || source.durabilityCost != 0
                || source.input == null || source.input.isEmpty()
                || source.output == null || source.output.isEmpty() || source.output.getCount() <= 0
                || source.spirits == null || source.spirits.isEmpty()) {
            return false;
        }
        return MalumAdapterUtils.addSpirits(MalumAdapterUtils.requirements(), source.spirits);
    }

    private static Map<Ingredient, Long> spiritRequirements(SpiritFocusingRecipe source) {
        Map<Ingredient, Long> requirements = MalumAdapterUtils.requirements();
        if (!MalumAdapterUtils.addSpirits(requirements, source.spirits)) {
            return Map.of();
        }
        return requirements;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(
            net.minecraft.resources.ResourceLocation id, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                converted.processTime(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private record Converted(List<CountedIngredient> inputs, ItemStack output, int processTime) {
    }
}
