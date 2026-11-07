package com.sorrowmist.useless.content.recipe.adapters.malum;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sammy.malum.common.recipe.SpiritInfusionRecipe;
import com.sammy.malum.registry.common.recipe.RecipeTypeRegistry;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternProfile;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import team.lodestar.lodestone.systems.recipe.IngredientWithCount;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts Malum 1.6.7 spirit-altar recipes ({@code malum:spirit_infusion}).
 *
 * <p>The native recipe consumes the central {@link IngredientWithCount input} and every
 * {@code extra_items} entry exactly once per operation, plus the listed spirit shards. The altar's
 * base crafting time is 300 ticks. When {@code useNbtFromInput} is true (the default in native
 * data), the altar replaces the result's NBT with the primary input's NBT. Static catalogue
 * recipes therefore carry the recipe's own output; runtime conversion with the concrete primary
 * input produces the exact component-carrying output.</p>
 */
public final class SpiritInfusionRecipeAdapter implements IRecipeAdapter<SpiritInfusionRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROCESS_TIME = 300;

    @Override
    public Class<SpiritInfusionRecipe> getRecipeClass() {
        return SpiritInfusionRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MalumAdapterUtils.item("spirit_altar");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SpiritInfusionRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value(), null);
        if (converted == null) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Malum spirit infusion recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(AdapterUtils.convertedId(holder.id()), converted));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SpiritInfusionRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        SpiritInfusionRecipe source = holder.value();
        if (!source.useNbtFromInput || actualInputs == null || actualInputs.isEmpty()) {
            return convertAll(holder, level);
        }
        if (!hasValidPrimaryInput(source) || source.extraItems == null) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>();
        for (ItemStack primary : MalumAdapterUtils.distinctMatches(actualInputs, source.input.ingredient)) {
            Converted data = convertData(source, primary);
            if (data != null) {
                converted.add(createRecipe(variantId(holder.id(), primary), data));
            }
        }
        return converted.isEmpty() ? List.of() : List.copyOf(converted);
    }

    @Override
    public List<RecipeHolder<SpiritInfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<SpiritInfusionRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<SpiritInfusionRecipe> holder :
                RecipeManagerAccess.getAllRecipesFor(recipeManager, RecipeTypeRegistry.SPIRIT_INFUSION.get())) {
            Map<Ingredient, Long> requirements = matchingRequirements(holder.value());
            if (requirements != null && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /**
     * Identifies altar patterns whose primary input and output are component-dynamic. The alloy
     * furnace must resolve the concrete primary input at execution time because Malum copies its
     * NBT onto the result.
     */
    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            @Nullable Level level, List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(level, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            @Nullable Level level, @Nullable PatternStackView pattern) {
        if (level == null) {
            return Optional.empty();
        }
        return findDynamicPatternProfileLong(
                RecipeManagerAccess.getAllRecipesFor(
                        level.getRecipeManager(), RecipeTypeRegistry.SPIRIT_INFUSION.get()),
                pattern);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<SpiritInfusionRecipe>> recipes,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(
                recipes, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            Iterable<RecipeHolder<SpiritInfusionRecipe>> recipes, @Nullable PatternStackView pattern) {
        if (recipes == null || pattern == null || pattern.inputs().isEmpty()
                || pattern.outputs().size() != 1 || !validItemStacks(pattern)) {
            return Optional.empty();
        }

        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<SpiritInfusionRecipe> holder : recipes) {
            SpiritInfusionRecipe source = holder == null ? null : holder.value();
            DynamicPatternProfile profile = dynamicProfile(source, pattern);
            if (profile == null) {
                continue;
            }
            matches.add(profile);
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    @Nullable
    private static DynamicPatternProfile dynamicProfile(
            @Nullable SpiritInfusionRecipe source, PatternStackView pattern) {
        if (source == null || !source.useNbtFromInput || !hasValidPrimaryInput(source)
                || source.extraItems == null || source.spirits == null || source.spirits.isEmpty()
                || source.output == null
                || source.output.isEmpty() || source.output.getCount() <= 0) {
            return null;
        }
        // Marking a custom ingredient as item-id-only would discard its component predicate.
        if (!source.input.ingredient.isVanilla()) {
            return null;
        }

        Map<Ingredient, Long> requirements = matchingRequirements(source);
        if (requirements == null || !matchesPatternInputs(requirements, pattern)
                || !matchesPatternOutput(source, pattern)) {
            return null;
        }

        int primarySlot = uniquePrimarySlot(source.input.ingredient, pattern.inputRepresentatives());
        if (primarySlot < 0) {
            return null;
        }
        ItemStack primary = pattern.inputRepresentatives().get(primarySlot);
        ItemStack expected = MalumAdapterUtils.altarOutput(source.output, true, primary);
        if (expected == null || !matchesOutput(expected, pattern.outputs().get(0))) {
            return null;
        }
        return new DynamicPatternProfile(Set.of(primarySlot), Set.of(0));
    }

    @Nullable
    private static Converted convertData(
            @Nullable SpiritInfusionRecipe source, @Nullable ItemStack actualPrimary) {
        if (!hasValidPrimaryInput(source) || source.output == null || source.output.isEmpty()
                || source.output.getCount() <= 0 || source.extraItems == null || source.spirits == null
                || source.spirits.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = MalumAdapterUtils.requirements();
        Ingredient primaryRequirement = actualPrimary == null
                ? source.input.ingredient
                : DataComponentIngredient.of(true, actualPrimary.copyWithCount(1));
        if (!MalumAdapterUtils.addIngredient(requirements, primaryRequirement, source.input.count)) {
            return null;
        }
        for (IngredientWithCount extra : source.extraItems) {
            if (extra == null || extra.ingredient == null || extra.ingredient.isEmpty()
                    || !MalumAdapterUtils.addIngredient(requirements, extra.ingredient, extra.count)) {
                return null;
            }
        }
        if (!MalumAdapterUtils.addSpirits(requirements, source.spirits)) {
            return null;
        }

        ItemStack output = MalumAdapterUtils.altarOutput(
                source.output, source.useNbtFromInput, actualPrimary);
        if (output == null) {
            return null;
        }

        List<CountedIngredient> inputs = MalumAdapterUtils.counted(requirements);
        if (inputs == null) {
            return null;
        }
        return new Converted(inputs, requirements, output);
    }

    @Nullable
    private static Map<Ingredient, Long> matchingRequirements(@Nullable SpiritInfusionRecipe source) {
        if (!hasValidPrimaryInput(source) || source.output == null || source.output.isEmpty()
                || source.output.getCount() <= 0 || source.extraItems == null || source.spirits == null
                || source.spirits.isEmpty()) {
            return null;
        }
        Map<Ingredient, Long> requirements = MalumAdapterUtils.requirements();
        if (!MalumAdapterUtils.addIngredient(requirements, source.input.ingredient, source.input.count)) {
            return null;
        }
        for (IngredientWithCount extra : source.extraItems) {
            if (extra == null || extra.ingredient == null || extra.ingredient.isEmpty()
                    || !MalumAdapterUtils.addIngredient(requirements, extra.ingredient, extra.count)) {
                return null;
            }
        }
        if (!MalumAdapterUtils.addSpirits(requirements, source.spirits)) {
            return null;
        }
        return requirements;
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(ResourceLocation id, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private static boolean hasValidPrimaryInput(@Nullable SpiritInfusionRecipe source) {
        return source != null && source.input != null && source.input.ingredient != null
                && !source.input.ingredient.isEmpty() && source.input.count > 0;
    }

    private static boolean matchesPatternInputs(
            Map<Ingredient, Long> requirements, PatternStackView pattern) {
        return totalRequiredItems(requirements.values()) == totalPatternItems(pattern.inputs())
                && ItemIngredientAllocator.matches(
                        countedRequirements(requirements), List.of(), pattern.inputs(), 1L);
    }

    private static boolean matchesPatternOutput(SpiritInfusionRecipe source, PatternStackView pattern) {
        if (pattern.outputs().size() != 1) {
            return false;
        }
        GenericStack output = pattern.outputs().get(0);
        return output != null && output.amount() == source.output.getCount()
                && output.what() instanceof AEItemKey itemKey
                && itemKey.getItem() == source.output.getItem();
    }

    private static boolean matchesOutput(ItemStack expected, GenericStack actual) {
        if (expected == null || expected.isEmpty() || actual == null || actual.amount() <= 0L
                || actual.amount() != expected.getCount() || !(actual.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        return ItemStack.isSameItemSameTags(expected, itemKey.toStack(1));
    }

    private static int uniquePrimarySlot(Ingredient primary, List<ItemStack> patternInputs) {
        int result = -1;
        for (int slot = 0; slot < patternInputs.size(); slot++) {
            ItemStack input = patternInputs.get(slot);
            if (input == null || input.isEmpty() || !primary.test(input)) {
                continue;
            }
            if (result >= 0) {
                return -1;
            }
            result = slot;
        }
        return result;
    }

    private static List<CountedIngredient> countedRequirements(Map<Ingredient, Long> requirements) {
        List<CountedIngredient> result = new ArrayList<>(requirements.size());
        for (Map.Entry<Ingredient, Long> entry : requirements.entrySet()) {
            result.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    private static long totalRequiredItems(Iterable<Long> counts) {
        long result = 0L;
        for (Long count : counts) {
            if (count == null || count <= 0L) {
                continue;
            }
            if (result > Long.MAX_VALUE - count) {
                return Long.MAX_VALUE;
            }
            result += count;
        }
        return result;
    }

    private static long totalPatternItems(List<GenericStack> inputs) {
        long result = 0L;
        for (GenericStack input : inputs) {
            if (input == null || input.amount() <= 0L || !(input.what() instanceof AEItemKey)) {
                continue;
            }
            if (result > Long.MAX_VALUE - input.amount()) {
                return Long.MAX_VALUE;
            }
            result += input.amount();
        }
        return result;
    }

    private static boolean validItemStacks(PatternStackView pattern) {
        return pattern.inputs().stream().allMatch(stack -> stack != null && stack.amount() > 0L
                        && stack.what() instanceof AEItemKey)
                && pattern.outputs().stream().allMatch(stack -> stack != null && stack.amount() > 0L
                        && stack.what() instanceof AEItemKey);
    }

    private static ResourceLocation variantId(ResourceLocation source, ItemStack primary) {
        String key = MalumAdapterUtils.itemKey(primary.getItem()) + "|"
                + (primary.hasTag() ? primary.getTag() : "");
        return new ResourceLocation(
                source.getNamespace(),
                source.getPath() + "_converted_" + AdapterUtils.stableHash(key));
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            ItemStack output) {
    }
}
