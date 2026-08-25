package com.sorrowmist.useless.compat.neovitae;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.breakinblocks.neovitae.api.recipe.AraVitaeInput;
import com.breakinblocks.neovitae.api.recipe.AraVitaeRecipe;
import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.datamap.NVDataMaps;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorPotionRecipe;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeInput;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeSpiritusInfusionRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeTransformRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeUpgradeRecipe;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternProfile;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.adapters.neovitae.HellfireForgeRecipeAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Optional Neo Vitae support for component-aware AE processing patterns. */
public final class NeoVitaeDynamicPatternSupport {
    private NeoVitaeDynamicPatternSupport() {
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Level level, List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(level, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            Level level, @Nullable PatternStackView pattern) {
        if (level == null || pattern == null || pattern.inputs().isEmpty()
                || pattern.outputs().isEmpty() || !validItemStacks(pattern)) {
            return Optional.empty();
        }

        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<ForgeRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(NVRecipes.HELLFIRE_FORGE_TYPE.get())) {
            DynamicPatternProfile profile = forgeProfileLong(holder, level, pattern);
            if (profile != null && addUnique(matches, profile)) return Optional.empty();
        }
        for (RecipeHolder<AthanorRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(NVRecipes.ATHANOR_TYPE.get())) {
            DynamicPatternProfile profile = athanorProfileLong(holder, level, pattern);
            if (profile != null && addUnique(matches, profile)) return Optional.empty();
        }
        for (RecipeHolder<AraVitaeRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(NVRecipes.ARA_VITAE_TYPE.get())) {
            DynamicPatternProfile profile = araProfileLong(holder, level, pattern);
            if (profile != null && addUnique(matches, profile)) return Optional.empty();
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    private static boolean addUnique(List<DynamicPatternProfile> matches,
                                     DynamicPatternProfile profile) {
        matches.add(profile);
        return matches.size() > 1;
    }

    @Nullable
    private static DynamicPatternProfile forgeProfile(
            RecipeHolder<ForgeRecipe> holder, Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        ForgeRecipe source = holder == null ? null : holder.value();
        if (source instanceof ForgeUpgradeRecipe upgrade) {
            return upgradeProfile(upgrade, level, patternInputs, patternOutputs);
        }
        if (source instanceof ForgeSpiritusInfusionRecipe infusion) {
            return infusionProfile(infusion, level, patternInputs, patternOutputs);
        }
        if (source instanceof ForgeTransformRecipe transform) {
            return transformProfile(transform, level, patternInputs, patternOutputs);
        }
        return null;
    }

    @Nullable
    private static DynamicPatternProfile forgeProfileLong(
            RecipeHolder<ForgeRecipe> holder, Level level, PatternStackView pattern) {
        ForgeRecipe source = holder == null ? null : holder.value();
        if (source instanceof ForgeUpgradeRecipe upgrade) {
            return upgradeProfileLong(upgrade, level, pattern);
        }
        if (source instanceof ForgeSpiritusInfusionRecipe infusion) {
            return infusionProfileLong(infusion, level, pattern);
        }
        if (source instanceof ForgeTransformRecipe transform) {
            return transformProfileLong(transform, level, pattern);
        }
        return null;
    }

    @Nullable
    private static DynamicPatternProfile transformProfileLong(
            ForgeTransformRecipe source, Level level, PatternStackView pattern) {
        List<CountedIngredient> catalysts = counted(source.getCatalysts());
        int targetSlot = uniqueDynamicSlotLong(pattern, source.getTransformInput(), catalysts);
        if (targetSlot < 0) return null;

        ItemStack output = assemble(source, pattern.inputRepresentatives(), forgeGem(), 4, level);
        if (!matchesSingleOutputLong(output, pattern.outputs())) return null;
        return profile(Set.of(targetSlot), Set.of(0),
                Map.of(targetSlot, ingredientMatcher(source.getTransformInput())));
    }

    @Nullable
    private static DynamicPatternProfile upgradeProfileLong(
            ForgeUpgradeRecipe source, Level level, PatternStackView pattern) {
        List<CountedIngredient> catalysts = counted(source.getCraftingIngredients());
        int targetSlot = uniqueDynamicSlotLong(
                pattern, HellfireForgeRecipeAdapter::isUpgradeTarget, catalysts);
        if (targetSlot < 0) return null;

        ItemStack output = assemble(source, pattern.inputRepresentatives(), forgeGem(), 4, level);
        if (!matchesSingleOutputLong(output, pattern.outputs())) return null;
        return profile(Set.of(targetSlot), Set.of(0),
                Map.of(targetSlot, itemMatcher(HellfireForgeRecipeAdapter::isUpgradeTarget)));
    }

    @Nullable
    private static DynamicPatternProfile infusionProfileLong(
            ForgeSpiritusInfusionRecipe source, Level level, PatternStackView pattern) {
        if (pattern.inputs().size() != 2) return null;
        List<ItemStack> representatives = pattern.inputRepresentatives();
        int gemSlot = uniqueItemSlotLong(pattern,
                stack -> source.getGemInput().test(stack) && isSpiritusGem(stack));
        int targetSlot = uniqueItemSlotLong(pattern, HellfireForgeRecipeAdapter::isInfusionTarget);
        if (gemSlot < 0 || targetSlot < 0 || gemSlot == targetSlot) return null;

        ForgeInput input = new ForgeInput(representatives, representatives.get(gemSlot), gemSlot);
        if (!source.matches(input, level)) return null;
        ItemStack output = assemble(source, representatives,
                representatives.get(gemSlot), gemSlot, level);
        if (!matchesSingleOutputLong(output, pattern.outputs())) return null;

        return profile(Set.of(gemSlot, targetSlot), Set.of(0), Map.of(
                gemSlot, itemMatcher(stack -> source.getGemInput().test(stack)
                        && isSpiritusGem(stack)),
                targetSlot, itemMatcher(HellfireForgeRecipeAdapter::isInfusionTarget)));
    }

    @Nullable
    private static DynamicPatternProfile athanorProfileLong(
            RecipeHolder<AthanorRecipe> holder, Level level, PatternStackView pattern) {
        AthanorRecipe source = holder == null ? null : holder.value();
        if (!(source instanceof AthanorPotionRecipe potion) || source.getInputs().isEmpty()) return null;

        List<CountedIngredient> requirements = counted(source.getInputs());
        if (!matchesPatternInputsLong(requirements, pattern)) return null;
        int potionSlot = uniqueItemSlotLong(pattern, source.getInputs().getFirst()::test);
        if (potionSlot < 0) return null;

        PotionContents contents = pattern.inputRepresentatives().get(potionSlot)
                .get(DataComponents.POTION_CONTENTS);
        List<ItemStack> expected = potionOutputs(source, contents);
        if (!matchesDynamicOutputsLong(expected, pattern.outputs())) return null;

        Set<Integer> outputSlots = new LinkedHashSet<>();
        for (int slot = 0; slot < expected.size(); slot++) outputSlots.add(slot);
        return profile(Set.of(potionSlot), outputSlots,
                Map.of(potionSlot, itemMatcher(source.getInputs().getFirst()::test)));
    }

    @Nullable
    private static DynamicPatternProfile araProfileLong(
            RecipeHolder<AraVitaeRecipe> holder, Level level, PatternStackView pattern) {
        AraVitaeRecipe source = holder == null ? null : holder.value();
        if (source == null || !source.shouldCopyInputComponents()
                || !matchesPatternInputsLong(counted(List.of(source.getInput())), pattern)) {
            return null;
        }

        int inputSlot = uniqueItemSlotLong(pattern, source.getInput()::test);
        if (inputSlot < 0 || !matchesSingleOutputLong(source.getResult(), pattern.outputs())) return null;

        ItemStack output;
        try {
            output = source.assemble(new AraVitaeInput(
                    pattern.inputRepresentatives().get(inputSlot), 0), level.registryAccess());
        } catch (RuntimeException exception) {
            return null;
        }
        if (!matchesSingleOutputLong(output, pattern.outputs())) return null;
        return profile(Set.of(inputSlot), Set.of(0),
                Map.of(inputSlot, itemMatcher(source.getInput()::test)));
    }

    @Nullable
    private static DynamicPatternProfile transformProfile(
            ForgeTransformRecipe source, Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        List<CountedIngredient> catalysts = counted(source.getCatalysts());
        int targetSlot = uniqueDynamicSlot(patternInputs, source.getTransformInput(), catalysts);
        if (targetSlot < 0 || !matchesWithOneMore(catalysts, patternInputs)) return null;

        ItemStack output = assemble(source, patternInputs, forgeGem(), 4, level);
        if (!matchesSingleOutput(output, patternOutputs)) return null;
        return profile(Set.of(targetSlot), Set.of(0),
                Map.of(targetSlot, ingredientMatcher(source.getTransformInput())));
    }

    @Nullable
    private static DynamicPatternProfile upgradeProfile(
            ForgeUpgradeRecipe source, Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        List<CountedIngredient> catalysts = counted(source.getCraftingIngredients());
        int targetSlot = uniqueDynamicSlot(patternInputs,
                HellfireForgeRecipeAdapter::isUpgradeTarget, catalysts);
        if (targetSlot < 0 || !matchesWithOneMore(catalysts, patternInputs)) return null;

        ItemStack output = assemble(source, patternInputs, forgeGem(), 4, level);
        if (!matchesSingleOutput(output, patternOutputs)) return null;
        return profile(Set.of(targetSlot), Set.of(0),
                Map.of(targetSlot, itemMatcher(HellfireForgeRecipeAdapter::isUpgradeTarget)));
    }

    @Nullable
    private static DynamicPatternProfile infusionProfile(
            ForgeSpiritusInfusionRecipe source, Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        if (patternInputs.size() != 2) return null;

        int gemSlot = uniqueItemSlot(patternInputs,
                stack -> source.getGemInput().test(stack) && isSpiritusGem(stack));
        int targetSlot = uniqueItemSlot(patternInputs,
                HellfireForgeRecipeAdapter::isInfusionTarget);
        if (gemSlot < 0 || targetSlot < 0 || gemSlot == targetSlot
                || patternInputs.get(gemSlot).getCount() != 1
                || patternInputs.get(targetSlot).getCount() != 1) {
            return null;
        }

        ForgeInput input = new ForgeInput(patternInputs,
                patternInputs.get(gemSlot), gemSlot);
        if (!source.matches(input, level)) return null;
        ItemStack output = assemble(source, patternInputs,
                patternInputs.get(gemSlot), gemSlot, level);
        if (!matchesSingleOutput(output, patternOutputs)) return null;

        return profile(Set.of(gemSlot, targetSlot), Set.of(0), Map.of(
                gemSlot, itemMatcher(stack -> source.getGemInput().test(stack)
                        && isSpiritusGem(stack)),
                targetSlot, itemMatcher(HellfireForgeRecipeAdapter::isInfusionTarget)));
    }

    @Nullable
    private static DynamicPatternProfile athanorProfile(
            RecipeHolder<AthanorRecipe> holder, Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        AthanorRecipe source = holder == null ? null : holder.value();
        if (!(source instanceof AthanorPotionRecipe potion)
                || source.getInputs().isEmpty()) return null;

        List<CountedIngredient> requirements = counted(source.getInputs());
        if (!matchesPatternInputs(requirements, patternInputs)) return null;
        int potionSlot = uniqueItemSlot(patternInputs,
                source.getInputs().getFirst()::test);
        if (potionSlot < 0) return null;

        PotionContents contents = patternInputs.get(potionSlot).get(DataComponents.POTION_CONTENTS);
        List<ItemStack> expected = potionOutputs(source, contents);
        if (!matchesDynamicOutputs(expected, patternOutputs)) return null;

        Set<Integer> outputSlots = new LinkedHashSet<>();
        for (int slot = 0; slot < expected.size(); slot++) outputSlots.add(slot);
        return profile(Set.of(potionSlot), outputSlots,
                Map.of(potionSlot, itemMatcher(source.getInputs().getFirst()::test)));
    }

    @Nullable
    private static DynamicPatternProfile araProfile(
            RecipeHolder<AraVitaeRecipe> holder, Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        AraVitaeRecipe source = holder == null ? null : holder.value();
        if (source == null || !source.shouldCopyInputComponents()
                || !matchesPatternInputs(counted(List.of(source.getInput())), patternInputs)) {
            return null;
        }

        int inputSlot = uniqueItemSlot(patternInputs, source.getInput()::test);
        if (inputSlot < 0 || !matchesSingleOutput(source.getResult(), patternOutputs)) return null;

        ItemStack output;
        try {
            output = source.assemble(new AraVitaeInput(patternInputs.get(inputSlot), 0),
                    level.registryAccess());
        } catch (RuntimeException exception) {
            return null;
        }
        if (!matchesSingleOutput(output, patternOutputs)) return null;
        return profile(Set.of(inputSlot), Set.of(0),
                Map.of(inputSlot, itemMatcher(source.getInput()::test)));
    }

    private static DynamicPatternProfile profile(
            Set<Integer> inputSlots, Set<Integer> outputSlots,
            Map<Integer, DynamicComponentPatternDetails.InputMatcher> matchers) {
        return new DynamicPatternProfile(Map.of(), inputSlots, outputSlots, matchers);
    }

    private static List<CountedIngredient> counted(List<Ingredient> ingredients) {
        return AdapterUtils.mergeIngredients(ingredients);
    }

    private static boolean matchesWithOneMore(
            List<CountedIngredient> staticRequirements, List<ItemStack> inputs) {
        long required = totalRequired(staticRequirements);
        return required < Long.MAX_VALUE && required + 1L == totalItems(inputs);
    }

    private static boolean matchesPatternInputs(
            List<CountedIngredient> requirements, List<ItemStack> inputs) {
        return totalRequired(requirements) == totalItems(inputs)
                && ItemIngredientAllocator.matches(requirements, inputs, 1L);
    }

    private static boolean matchesPatternInputsLong(
            List<CountedIngredient> requirements, PatternStackView pattern) {
        return totalRequired(requirements) == totalItemsLong(pattern.inputs())
                && ItemIngredientAllocator.matches(requirements, List.of(), pattern.inputs(), 1L);
    }

    private static int uniqueDynamicSlotLong(
            PatternStackView pattern, Ingredient dynamic,
            List<CountedIngredient> staticRequirements) {
        if (dynamic == null || dynamic.isEmpty()) return -1;
        return uniqueDynamicSlotLong(pattern, dynamic::test, staticRequirements);
    }

    private static int uniqueDynamicSlotLong(
            PatternStackView pattern, Predicate<ItemStack> dynamic,
            List<CountedIngredient> staticRequirements) {
        List<ItemStack> representatives = pattern.inputRepresentatives();
        int result = -1;
        for (int slot = 0; slot < representatives.size(); slot++) {
            ItemStack input = representatives.get(slot);
            if (input == null || input.isEmpty() || !dynamic.test(input)) continue;
            List<GenericStack> remaining = new ArrayList<>(pattern.inputs());
            remaining.remove(slot);
            if (totalRequired(staticRequirements) != totalItemsLong(remaining)
                    || !ItemIngredientAllocator.matches(staticRequirements, List.of(), remaining, 1L)) {
                continue;
            }
            if (result >= 0) return -1;
            result = slot;
        }
        return result;
    }

    private static int uniqueItemSlotLong(
            PatternStackView pattern, Predicate<ItemStack> predicate) {
        List<ItemStack> representatives = pattern.inputRepresentatives();
        int result = -1;
        for (int slot = 0; slot < representatives.size(); slot++) {
            ItemStack input = representatives.get(slot);
            if (input == null || input.isEmpty() || !predicate.test(input)) continue;
            if (result >= 0) return -1;
            result = slot;
        }
        return result;
    }

    private static int uniqueDynamicSlot(
            List<ItemStack> inputs, Ingredient dynamic, List<CountedIngredient> staticRequirements) {
        if (dynamic == null || dynamic.isEmpty()) return -1;
        return uniqueDynamicSlot(inputs, dynamic::test, staticRequirements);
    }

    private static int uniqueDynamicSlot(
            List<ItemStack> inputs, Predicate<ItemStack> dynamic,
            List<CountedIngredient> staticRequirements) {
        int result = -1;
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack input = inputs.get(slot);
            if (input == null || input.isEmpty() || !dynamic.test(input)) continue;
            if (!ItemIngredientAllocator.matches(staticRequirements,
                    removeOne(inputs, slot), 1L)) continue;
            if (result >= 0) return -1;
            result = slot;
        }
        return result;
    }

    private static int uniqueItemSlot(List<ItemStack> inputs, Predicate<ItemStack> predicate) {
        int result = -1;
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack input = inputs.get(slot);
            if (input == null || input.isEmpty() || !predicate.test(input)) continue;
            if (result >= 0) return -1;
            result = slot;
        }
        return result;
    }

    private static List<ItemStack> removeOne(List<ItemStack> inputs, int slot) {
        List<ItemStack> result = new ArrayList<>(inputs);
        ItemStack stack = result.get(slot);
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) return List.of();
        if (stack.getCount() == 1) result.remove(slot);
        else result.set(slot, stack.copyWithCount(stack.getCount() - 1));
        return result;
    }

    private static long totalRequired(List<CountedIngredient> requirements) {
        long result = 0L;
        for (CountedIngredient requirement : requirements) {
            if (requirement == null || requirement.count() <= 0) continue;
            if (result > Long.MAX_VALUE - requirement.count()) return Long.MAX_VALUE;
            result += requirement.count();
        }
        return result;
    }

    private static long totalItems(List<ItemStack> inputs) {
        long result = 0L;
        for (ItemStack input : inputs) {
            if (input == null || input.isEmpty() || input.getCount() <= 0) continue;
            if (result > Long.MAX_VALUE - input.getCount()) return Long.MAX_VALUE;
            result += input.getCount();
        }
        return result;
    }

    private static long totalItemsLong(List<GenericStack> inputs) {
        long result = 0L;
        for (GenericStack input : inputs) {
            if (input == null || input.amount() <= 0L) continue;
            if (result > Long.MAX_VALUE - input.amount()) return Long.MAX_VALUE;
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

    private static boolean matchesSingleOutputLong(ItemStack expected, List<GenericStack> actual) {
        return actual != null && actual.size() == 1
                && matchesDynamicOutputLong(expected, actual.getFirst());
    }

    private static boolean matchesDynamicOutputsLong(List<ItemStack> expected, List<GenericStack> actual) {
        if (expected == null || actual == null || expected.size() != actual.size()) return false;
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!matchesDynamicOutputLong(expected.get(slot), actual.get(slot))) return false;
        }
        return true;
    }

    private static boolean matchesDynamicOutputLong(ItemStack expected, GenericStack actual) {
        if (expected == null || expected.isEmpty() || actual == null || actual.amount() <= 0L
                || actual.amount() != expected.getCount()
                || !(actual.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        return expected.getItem() == itemKey.getItem();
    }

    private static DynamicComponentPatternDetails.InputMatcher itemMatcher(
            Predicate<ItemStack> predicate) {
        return input -> {
            if (!(input instanceof AEItemKey itemKey)) return false;
            try {
                return predicate.test(itemKey.toStack(1));
            } catch (RuntimeException exception) {
                return false;
            }
        };
    }

    private static DynamicComponentPatternDetails.InputMatcher ingredientMatcher(
            Ingredient ingredient) {
        return itemMatcher(ingredient::test);
    }

    private static boolean isSpiritusGem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Double maximum = stack.getItemHolder().getData(NVDataMaps.SPIRITUS_GEM_MAX_AMOUNTS);
        return maximum != null && maximum > 0.0;
    }

    private static ItemStack forgeGem() {
        ItemStack gem = new ItemStack(Items.PAPER);
        gem.set(NVDataComponents.SPIRITUS_AMOUNT.get(), Double.MAX_VALUE);
        return gem;
    }

    private static ItemStack assemble(ForgeRecipe source, List<ItemStack> inputs,
                                      ItemStack gem, int gemIndex, Level level) {
        try {
            return source.assemble(new ForgeInput(inputs, gem, gemIndex),
                    level.registryAccess()).copy();
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean matchesSingleOutput(ItemStack expected, List<ItemStack> outputs) {
        return outputs != null && outputs.size() == 1 && matchesDynamicOutput(expected, outputs.getFirst());
    }

    private static boolean matchesDynamicOutputs(List<ItemStack> expected, List<ItemStack> actual) {
        if (expected.size() != actual.size()) return false;
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!matchesDynamicOutput(expected.get(slot), actual.get(slot))) return false;
        }
        return true;
    }

    private static boolean matchesDynamicOutput(ItemStack expected, ItemStack actual) {
        return expected != null && !expected.isEmpty()
                && actual != null && !actual.isEmpty()
                && expected.getItem() == actual.getItem()
                && expected.getCount() == actual.getCount();
    }

    private static List<ItemStack> potionOutputs(
            AthanorRecipe source, @Nullable PotionContents potion) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack output : source.getGuaranteedOutput()) {
            result.add(potionOutput(output, potion));
        }
        for (Pair<ItemStack, Double> output : source.getChanceOutput()) {
            if (output != null && output.getFirst() != null) {
                result.add(potionOutput(output.getFirst(), potion));
            }
        }
        return result;
    }

    private static ItemStack potionOutput(ItemStack output, @Nullable PotionContents potion) {
        ItemStack result = output.copy();
        if (potion == null || !potion.hasEffects()) return result;
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : potion.getAllEffects()) {
            effects.add(new MobEffectInstance(effect));
        }
        result.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), effects));
        return result;
    }
}
