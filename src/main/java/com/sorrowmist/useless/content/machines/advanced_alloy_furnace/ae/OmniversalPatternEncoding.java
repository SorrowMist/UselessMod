package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public final class OmniversalPatternEncoding {
    private OmniversalPatternEncoding() {
    }

    public static ItemStack encode(ItemStack sourcePattern, AlloyFurnaceRecipeCatalog.Entry entry, Level level) {
        if (sourcePattern == null || sourcePattern.isEmpty() || entry == null || level == null) {
            return ItemStack.EMPTY;
        }
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(sourcePattern, level);
        if (!(decoded instanceof AEProcessingPattern processing)
                || decoded instanceof OmniversalPatternDetails) {
            return ItemStack.EMPTY;
        }
        var encoded = sourcePattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return ItemStack.EMPTY;

        List<Integer> dynamicInputs = new ArrayList<>();
        List<Integer> dynamicOutputs = new ArrayList<>();
        IPatternDetails resolved = AdvancedAlloyFurnacePatternResolver.resolve(processing, level);
        if (resolved instanceof DynamicComponentPattern dynamic) {
            for (int slot = 0; slot < resolved.getInputs().length; slot++) {
                if (dynamic.isItemIdInput(slot)) dynamicInputs.add(slot);
            }
            for (int slot = 0; slot < resolved.getOutputs().size(); slot++) {
                if (dynamic.isItemIdOutput(slot)) dynamicOutputs.add(slot);
            }
        }

        AdvancedAlloyFurnaceRecipe recipe = entry.recipe();
        dynamicInputs = resolveItemIdInputSlots(recipe, processing, dynamicInputs);
        Map<Integer, List<TagKey<Item>>> tagInputs =
                resolveTagInputSlots(recipe, processing);
        List<OmniversalPatternData.TagInputSlot> encodedTagInputs = new ArrayList<>();
        for (Map.Entry<Integer, List<TagKey<Item>>> tagInput : tagInputs.entrySet()) {
            for (TagKey<Item> tag : tagInput.getValue()) {
                encodedTagInputs.add(new OmniversalPatternData.TagInputSlot(tagInput.getKey(), tag));
            }
        }
        List<AEItemKey> displayMolds = new ArrayList<>();
        if (recipe.molds().size() > 1) {
            for (Ingredient mold : recipe.molds()) {
                ItemStack[] options = mold.getItems();
                if (options.length > 0) {
                    AEItemKey key = AEItemKey.of(options[0]);
                    if (key != null) displayMolds.add(key);
                }
            }
        }
        Optional<AEItemKey> displayMold = recipe.molds().size() == 1
                ? firstDisplayMold(recipe.molds().getFirst()) : Optional.empty();
        OmniversalPatternData data = new OmniversalPatternData(
                OmniversalPatternData.CURRENT_VERSION,
                entry.identity().recipeId(),
                entry.identity().fingerprint(),
                !recipe.molds().isEmpty(),
                displayMold,
                displayMolds,
                encodedTagInputs,
                dynamicInputs,
                dynamicOutputs);

        ItemStack result = new ItemStack(ModItems.OMNIVERSAL_PATTERN.get());
        result.set(AEComponents.ENCODED_PROCESSING_PATTERN, encoded);
        result.set(UComponents.OMNIVERSAL_PATTERN_DATA.get(), data);
        return result;
    }

    private static Optional<AEItemKey> firstDisplayMold(Ingredient mold) {
        ItemStack[] options = mold.getItems();
        return options.length == 0 ? Optional.empty() : Optional.ofNullable(AEItemKey.of(options[0]));
    }

    /**
     * Adds the input slots whose source ingredient ignores item components.
     *
     * <p>AE2 processing patterns encode a concrete {@link AEItemKey}, even for a vanilla
     * {@link Ingredient} such as {@code Ingredient.of(Items.GOAT_HORN)}. That accidentally
     * makes the pattern require the exact horn instrument selected while encoding. The furnace
     * recipe does not have that restriction, so its omniversal pattern must use item-id matching
     * for those slots. Non-simple ingredients, including {@code DataComponentIngredient}, remain
     * exact.</p>
     *
     * <p>The source pattern may have condensed repeated inputs. Classifying its already-condensed
     * slots avoids relying on recipe-input positions and keeps the stored slot indices aligned with
     * AE2's {@link AEProcessingPattern#getInputs()}.</p>
     */
    static List<Integer> resolveItemIdInputSlots(
            AdvancedAlloyFurnaceRecipe recipe,
            AEProcessingPattern source,
            Iterable<Integer> declaredSlots) {
        TreeSet<Integer> slots = new TreeSet<>();
        if (declaredSlots != null) {
            for (Integer slot : declaredSlots) {
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        slots.addAll(componentAgnosticInputSlots(recipe, source));
        return List.copyOf(slots);
    }

    static Map<Integer, List<TagKey<Item>>> resolveTagInputSlots(
            AdvancedAlloyFurnaceRecipe recipe, AEProcessingPattern source) {
        if (recipe == null || source == null || recipe.inputs().isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<TagKey<Item>>> result = new java.util.TreeMap<>();
        IPatternDetails.IInput[] inputs = source.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            if (input == null) continue;
            java.util.LinkedHashSet<TagKey<Item>> tags = new java.util.LinkedHashSet<>();
            boolean matchedRequirement = false;
            boolean hasNonTagRequirement = false;
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible == null || !(possible.what() instanceof AEItemKey itemKey)) {
                    hasNonTagRequirement = true;
                    continue;
                }
                boolean matchedPossible = false;
                for (CountedIngredient requirement : recipe.inputs()) {
                    if (requirement == null || requirement.count() <= 0L) continue;
                    Ingredient ingredient = requirement.ingredient();
                    if (ingredient == null || !ingredient.test(itemKey.toStack(1))) continue;
                    matchedPossible = true;
                    matchedRequirement = true;
                    Optional<TagKey<Item>> tag = directItemTag(ingredient);
                    if (tag.isPresent()) {
                        tags.add(tag.get());
                    } else {
                        // A concrete or component-sensitive requirement sharing this encoded
                        // item must keep the slot strict; otherwise the tag branch would widen
                        // the recipe beyond the original allocator semantics.
                        hasNonTagRequirement = true;
                    }
                }
                if (!matchedPossible) {
                    hasNonTagRequirement = true;
                }
            }
            if (matchedRequirement && !hasNonTagRequirement && !tags.isEmpty()) {
                result.put(slot, List.copyOf(tags));
            }
        }
        return Map.copyOf(result);
    }

    private static Optional<TagKey<Item>> directItemTag(Ingredient ingredient) {
        if (ingredient == null || ingredient.isCustom()) return Optional.empty();
        Ingredient.Value[] values = ingredient.getValues();
        if (values.length != 1 || !(values[0] instanceof Ingredient.TagValue tagValue)) {
            return Optional.empty();
        }
        return Optional.of(tagValue.tag());
    }

    /**
     * Finds encoded item slots that can be matched by item id without changing source-recipe
     * semantics. A non-simple requirement that accepts the encoded stack makes the slot strict:
     * this handles overlapping ordinary and component-sensitive requirements conservatively.
     */
    static List<Integer> componentAgnosticInputSlots(
            AdvancedAlloyFurnaceRecipe recipe, AEProcessingPattern source) {
        if (recipe == null || source == null || recipe.inputs().isEmpty()) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();
        IPatternDetails.IInput[] inputs = source.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            if (input == null || !isComponentAgnosticRecipeInput(recipe.inputs(), input.getPossibleInputs())) {
                continue;
            }
            result.add(slot);
        }
        return List.copyOf(result);
    }

    private static boolean isComponentAgnosticRecipeInput(
            List<CountedIngredient> requirements, GenericStack[] possibleInputs) {
        if (possibleInputs == null || possibleInputs.length == 0) {
            return false;
        }

        boolean foundItem = false;
        for (GenericStack possible : possibleInputs) {
            if (possible == null || !(possible.what() instanceof AEItemKey itemKey)
                    || !isComponentAgnosticRequirement(requirements, itemKey.toStack(1))) {
                return false;
            }
            foundItem = true;
        }
        return foundItem;
    }

    private static boolean isComponentAgnosticRequirement(
            List<CountedIngredient> requirements, ItemStack encodedStack) {
        boolean matched = false;
        for (CountedIngredient requirement : requirements) {
            if (requirement == null || requirement.count() <= 0L) {
                continue;
            }
            Ingredient ingredient = requirement.ingredient();
            if (ingredient == null || !ingredient.test(encodedStack)) {
                continue;
            }
            if (!ingredient.isSimple()) {
                return false;
            }
            matched = true;
        }
        return matched;
    }

    public static ItemStack createProcessingPattern(AdvancedAlloyFurnaceRecipe recipe) {
        List<GenericStack> inputs = new ArrayList<>();
        recipe.inputs().forEach(input -> {
            ItemStack[] options = input.ingredient().getItems();
            if (options.length > 0 && input.count() > 0) {
                AEItemKey key = AEItemKey.of(options[0]);
                if (key != null) inputs.add(new GenericStack(key, input.count()));
            }
        });
        recipe.inputFluids().stream().map(GenericStack::fromFluidStack).forEach(inputs::add);
        inputs.addAll(recipe.keyInputs());

        List<GenericStack> outputs = new ArrayList<>();
        recipe.outputs().stream().map(GenericStack::fromItemStack).forEach(outputs::add);
        recipe.outputFluids().stream().map(GenericStack::fromFluidStack).forEach(outputs::add);
        outputs.addAll(recipe.keyOutputs());
        inputs.removeIf(java.util.Objects::isNull);
        outputs.removeIf(java.util.Objects::isNull);
        if (inputs.isEmpty() || outputs.isEmpty()) return ItemStack.EMPTY;
        return PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
    }
}
