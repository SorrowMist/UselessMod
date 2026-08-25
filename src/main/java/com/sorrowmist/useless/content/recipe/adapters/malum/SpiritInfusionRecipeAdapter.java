package com.sorrowmist.useless.content.recipe.adapters.malum;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sammy.malum.common.recipe.SpiritInfusionRecipe;
import com.sammy.malum.registry.common.recipe.MalumRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts Malum spirit-altar recipes, including component-carrying infusion results. */
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
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SpiritInfusionRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value(), null, null);
        if (converted == null) {
            LOGGER.warn("Skipping invalid Malum spirit infusion recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(AdapterUtils.convertedId(holder.id()), converted));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SpiritInfusionRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null || !holder.value().carryOverComponentData) {
            return convertAll(holder, level);
        }

        SpiritInfusionRecipe source = holder.value();
        if (!hasValidPrimaryInput(source)) {
            return List.of();
        }
        ServerLevel serverLevel = level instanceof ServerLevel server ? server : null;
        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>();
        for (ItemStack primary : MalumAdapterUtils.distinctMatches(actualInputs, source.input.ingredient())) {
            Converted data = convertData(source, primary, serverLevel);
            if (data != null) {
                converted.add(createRecipe(variantId(holder.id(), primary), data));
            }
        }
        return converted;
    }

    @Override
    public List<RecipeHolder<SpiritInfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<SpiritInfusionRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<SpiritInfusionRecipe> holder : recipeManager.getAllRecipesFor(
                MalumRecipeTypes.SPIRIT_INFUSION.get())) {
            Converted converted = convertData(holder.value(), null, null);
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /** Identifies carry-over altar patterns whose primary input and output are component-dynamic. */
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
                level.getRecipeManager().getAllRecipesFor(MalumRecipeTypes.SPIRIT_INFUSION.get()),
                pattern);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<SpiritInfusionRecipe>> recipes,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(recipes,
                PatternStackView.fromLegacy(patternInputs, patternOutputs));
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
            if (source == null || !source.carryOverComponentData || !hasValidPrimaryInput(source)
                    || source.input.ingredient().getCustomIngredient() != null) {
                continue;
            }

            Converted converted = convertData(source, null, null);
            if (converted == null || !matchesStaticOutput(converted.output(), pattern.outputs().getFirst())
                    || !matchesPatternInputs(converted.inputs(), pattern)) {
                continue;
            }

            int primarySlot = uniquePrimarySlot(source.input.ingredient(), pattern.inputRepresentatives());
            if (primarySlot < 0) {
                continue;
            }
            matches.add(new DynamicPatternProfile(Set.of(primarySlot), Set.of(0)));
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    @Nullable
    private static Converted convertData(
            @Nullable SpiritInfusionRecipe source, @Nullable ItemStack actualPrimary,
            @Nullable ServerLevel serverLevel) {
        if (!hasValidPrimaryInput(source) || source.result == null || source.result.isEmpty()
                || source.result.getCount() <= 0 || source.extraInputs == null) {
            return null;
        }

        Map<Ingredient, Long> requirements = MalumAdapterUtils.requirements();
        Ingredient primaryRequirement = actualPrimary == null
                ? source.input.ingredient()
                : DataComponentIngredient.of(true, actualPrimary.copyWithCount(1));
        if (!MalumAdapterUtils.addIngredient(requirements, primaryRequirement, source.input.count())) {
            return null;
        }
        for (SizedIngredient extra : source.extraInputs) {
            if (extra == null || extra.ingredient() == null
                    || !MalumAdapterUtils.addIngredient(requirements, extra.ingredient(), extra.count())) {
                return null;
            }
        }
        if (!MalumAdapterUtils.addSpirits(requirements, source.spirits)) {
            return null;
        }

        ItemStack output = source.result.copy();
        if (source.carryOverComponentData && actualPrimary != null && serverLevel != null) {
            try {
                output = source.getOutput(serverLevel, actualPrimary.copy());
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to create component-carrying Malum infusion output", exception);
                return null;
            }
        }
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return null;
        }

        List<CountedIngredient> inputs = MalumAdapterUtils.counted(requirements);
        if (inputs == null) {
            return null;
        }
        return new Converted(inputs, requirements, output.copy());
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
        return source != null && source.input != null && source.input.ingredient() != null
                && !source.input.ingredient().isEmpty() && source.input.count() > 0;
    }

    private static boolean matchesPatternInputs(
            List<CountedIngredient> requirements, PatternStackView pattern) {
        return totalRequiredItems(requirements) == totalPatternItems(pattern.inputs())
                && ItemIngredientAllocator.matches(requirements, List.of(), pattern.inputs(), 1L);
    }

    private static boolean matchesStaticOutput(ItemStack expected, GenericStack actual) {
        if (expected == null || expected.isEmpty() || actual == null || actual.amount() <= 0L
                || actual.amount() != expected.getCount() || !(actual.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(expected, itemKey.toStack(1));
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

    private static long totalRequiredItems(List<CountedIngredient> inputs) {
        long result = 0L;
        for (CountedIngredient input : inputs) {
            if (input == null || input.count() <= 0L) {
                continue;
            }
            if (result > Long.MAX_VALUE - input.count()) {
                return Long.MAX_VALUE;
            }
            result += input.count();
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
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(primary.getItem());
        return ResourceLocation.fromNamespaceAndPath(
                source.getNamespace(),
                source.getPath() + "_converted_" + itemId.getNamespace() + "_"
                        + itemId.getPath().replace('/', '_'));
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            ItemStack output) {
    }

    public record DynamicPatternProfile(Set<Integer> idOnlyInputSlots, Set<Integer> idOnlyOutputSlots) {
        public DynamicPatternProfile {
            idOnlyInputSlots = Set.copyOf(new LinkedHashSet<>(idOnlyInputSlots));
            idOnlyOutputSlots = Set.copyOf(new LinkedHashSet<>(idOnlyOutputSlots));
        }
    }
}
