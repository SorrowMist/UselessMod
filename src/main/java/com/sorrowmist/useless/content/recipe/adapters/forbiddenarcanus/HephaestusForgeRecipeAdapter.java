package com.sorrowmist.useless.content.recipe.adapters.forbiddenarcanus;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualRequirements;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.TransmuteInputResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition;
import com.stal111.forbidden_arcanus.core.init.ModBlocks;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts item-producing Forbidden Arcanus Hephaestus Forge rituals. */
public final class HephaestusForgeRecipeAdapter implements IRecipeAdapter<ForbiddenArcanusSyntheticRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RITUAL_INPUTS = 8;

    @Override
    public Class<ForbiddenArcanusSyntheticRecipe> getRecipeClass() {
        return ForbiddenArcanusSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.HEPHAESTUS_FORGE_TIER_1.get());
    }

    @Override
    public List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        Registry<Ritual> registry = level.registryAccess().registryOrThrow(FARegistries.RITUAL);
        List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> result = new ArrayList<>();
        for (Holder<Ritual> holder : registry.holders().toList()) {
            ResourceLocation sourceId = holder.unwrapKey().map(key -> key.location()).orElse(null);
            if (sourceId == null) {
                continue;
            }

            for (AdvancedAlloyFurnaceRecipe converted : convertRitual(sourceId, holder.value())) {
                result.add(new RecipeHolder<>(converted.id(), new ForbiddenArcanusSyntheticRecipe(converted)));
            }
        }
        return result;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ForbiddenArcanusSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ForbiddenArcanusSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe != null && AdapterUtils.matchesRequired(mergedInputs, requirements(recipe.inputs()))) {
                matches.add(holder);
            }
        }
        return matches;
    }

    static List<AdvancedAlloyFurnaceRecipe> convertRitual(ResourceLocation sourceId, Ritual ritual) {
        if (sourceId == null || ritual == null) {
            return List.of();
        }
        if (ritual.mainIngredient() == null || ritual.mainIngredient().isEmpty()) {
            warn(sourceId, "has an empty main ingredient");
            return List.of();
        }
        if (ritual.result() == null) {
            warn(sourceId, "has no ritual result");
            return List.of();
        }
        if (ritual.duration() <= 0) {
            warn(sourceId, "has an invalid duration");
            return List.of();
        }
        if (!(ritual.result() instanceof CreateItemResult)
                && !(ritual.result() instanceof TransmuteInputResult)) {
            warn(sourceId, "does not produce a supported item result");
            return List.of();
        }

        List<CountedIngredient> ritualInputs = new ArrayList<>();
        long pedestalInputCount = 0L;
        if (ritual.inputs() == null || ritual.inputs().isEmpty()) {
            warn(sourceId, "has no pedestal inputs");
            return List.of();
        }
        for (RitualInput input : ritual.inputs()) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty() || input.amount() <= 0) {
                warn(sourceId, "has an invalid pedestal input");
                return List.of();
            }
            pedestalInputCount = saturatingAdd(pedestalInputCount, input.amount());
            if (pedestalInputCount > MAX_RITUAL_INPUTS) {
                warn(sourceId, "has more than eight pedestal inputs");
                return List.of();
            }
            ritualInputs.add(new CountedIngredient(input.ingredient(), input.amount()));
        }

        List<ItemStack> mainRepresentations = concreteRepresentations(ritual.mainIngredient());
        if (mainRepresentations.isEmpty()) {
            warn(sourceId, "has no enumerable main ingredient");
            return List.of();
        }

        long energy = energyFor(sourceId, ritual);
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (int index = 0; index < mainRepresentations.size(); index++) {
            ItemStack main = mainRepresentations.get(index);
            ItemStack output;
            try {
                output = ritual.result().getResultItem(main.copy()).copy();
            } catch (RuntimeException exception) {
                warn(sourceId, "failed to resolve its result");
                LOGGER.debug("Failed to resolve Forbidden Arcanus ritual result {}", sourceId, exception);
                return List.of();
            }
            if (output.isEmpty() || output.getCount() <= 0) {
                warn(sourceId, "has an empty item result");
                return List.of();
            }

            List<CountedIngredient> inputs = new ArrayList<>(ritualInputs.size() + 1);
            inputs.add(new CountedIngredient(Ingredient.of(main.copyWithCount(1)), 1L));
            inputs.addAll(ritualInputs);

            ResourceLocation id = variantId(sourceId, main, index);
            result.add(new AdvancedAlloyFurnaceRecipe(
                    id,
                    List.copyOf(inputs),
                    List.of(),
                    List.of(output),
                    List.of(),
                    energy,
                    Math.max(1, ritual.duration()),
                    Ingredient.EMPTY,
                    0,
                    AdapterUtils.toMoldIngredient(new ItemStack(ModBlocks.HEPHAESTUS_FORGE_TIER_1.get())),
                    AlloyFurnaceMode.NORMAL));
        }
        return List.copyOf(result);
    }

    private static long energyFor(ResourceLocation sourceId, Ritual ritual) {
        try {
            RitualRequirements requirements = ritual.requirements();
            EssencesDefinition essences = requirements == null ? null : requirements.essences();
            if (essences == null) {
                return AdapterUtils.DEFAULT_ENERGY;
            }

            long multiplier = 0L;
            multiplier = saturatingAdd(multiplier, Math.max(0, essences.aureal()));
            multiplier = saturatingAdd(multiplier, Math.max(0, essences.souls()));
            multiplier = saturatingAdd(multiplier, Math.max(0, essences.blood()));
            multiplier = saturatingAdd(multiplier, Math.max(0, essences.experience()));
            return saturatingMultiply(AdapterUtils.DEFAULT_ENERGY, Math.max(1L, multiplier));
        } catch (RuntimeException exception) {
            LOGGER.debug("Failed to read Forbidden Arcanus ritual energy requirements {}; using default energy",
                    sourceId, exception);
            return AdapterUtils.DEFAULT_ENERGY;
        }
    }

    private static Map<Ingredient, Long> requirements(List<CountedIngredient> inputs) {
        Map<Ingredient, Long> result = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty() || input.count() <= 0) {
                continue;
            }
            AdapterUtils.mergeIngredient(result, input.ingredient(), input.count());
        }
        return result;
    }

    private static List<ItemStack> concreteRepresentations(Ingredient ingredient) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copyWithCount(1);
            if (result.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, copy))) {
                result.add(copy);
            }
        }
        return result;
    }

    private static ResourceLocation variantId(ResourceLocation sourceId, ItemStack main, int index) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(main.getItem());
        String itemSuffix = itemId == null ? "unknown" : itemId.getNamespace() + "_" + itemId.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                sourceId.getNamespace(),
                sourceId.getPath() + "_main_" + itemSuffix.replace('/', '_') + "_" + index);
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static void warn(ResourceLocation sourceId, String reason) {
        LOGGER.warn("Skipping Forbidden Arcanus Hephaestus Forge ritual {}: {}", sourceId, reason);
    }
}
