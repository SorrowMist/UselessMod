package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class OmniversalPatternEncoding {
    private OmniversalPatternEncoding() {
    }

    public static ItemStack encode(ItemStack sourcePattern, AlloyFurnaceRecipeCatalog.Entry entry, Level level) {
        if (sourcePattern == null || sourcePattern.isEmpty() || entry == null || level == null) {
            return ItemStack.EMPTY;
        }
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(sourcePattern, level);
        return encode(sourcePattern, decoded, entry, level);
    }

    /** Encodes from the caller's already decoded pattern to avoid a second AE2 decode pass. */
    public static ItemStack encode(
            ItemStack sourcePattern, IPatternDetails decoded,
            AlloyFurnaceRecipeCatalog.Entry entry, Level level) {
        if (sourcePattern == null || sourcePattern.isEmpty() || decoded == null
                || entry == null || level == null) {
            return ItemStack.EMPTY;
        }
        if (!(decoded instanceof AEProcessingPattern processing)
                || decoded instanceof OmniversalPatternDetails) {
            return ItemStack.EMPTY;
        }
        var encoded = sourcePattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return ItemStack.EMPTY;

        List<Integer> dynamicInputs = new ArrayList<>();
        List<Integer> dynamicOutputs = new ArrayList<>();
        IPatternDetails resolved = AdvancedAlloyFurnacePatternResolver.resolve(
                processing, level, entry.sourceId());
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
        Map<Integer, List<TagKey<Fluid>>> fluidTagInputs =
                resolveFluidTagInputSlots(recipe, processing);
        List<OmniversalPatternData.MoldTagInputSlot> moldTagInputs =
                resolveMoldTagInputSlots(recipe);
        List<OmniversalPatternData.TagInputSlot> encodedTagInputs = new ArrayList<>();
        for (Map.Entry<Integer, List<TagKey<Item>>> tagInput : tagInputs.entrySet()) {
            for (TagKey<Item> tag : tagInput.getValue()) {
                encodedTagInputs.add(new OmniversalPatternData.TagInputSlot(tagInput.getKey(), tag));
            }
        }
        List<OmniversalPatternData.FluidTagInputSlot> encodedFluidTagInputs = new ArrayList<>();
        for (Map.Entry<Integer, List<TagKey<Fluid>>> tagInput : fluidTagInputs.entrySet()) {
            for (TagKey<Fluid> tag : tagInput.getValue()) {
                encodedFluidTagInputs.add(new OmniversalPatternData.FluidTagInputSlot(tagInput.getKey(), tag));
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
                entry.sourceId(),
                !recipe.molds().isEmpty(),
                displayMold,
                displayMolds,
                encodedTagInputs,
                encodedFluidTagInputs,
                moldTagInputs,
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

    static Map<Integer, List<TagKey<Fluid>>> resolveFluidTagInputSlots(
            AdvancedAlloyFurnaceRecipe recipe, AEProcessingPattern source) {
        if (recipe == null || source == null || recipe.inputFluids().isEmpty()) return Map.of();

        Map<Integer, List<TagKey<Fluid>>> result = new java.util.TreeMap<>();
        IPatternDetails.IInput[] inputs = source.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            if (input == null) continue;
            java.util.LinkedHashSet<TagKey<Fluid>> tags = new java.util.LinkedHashSet<>();
            boolean matchedRequirement = false;
            boolean hasNonTagRequirement = false;
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible == null || !(possible.what() instanceof appeng.api.stacks.AEFluidKey fluidKey)) {
                    hasNonTagRequirement = true;
                    continue;
                }
                boolean matchedPossible = false;
                FluidStack representative = fluidKey.toStack(1);
                for (SizedFluidIngredient requirement : recipe.inputFluids()) {
                    if (requirement == null || requirement.ingredient() == null
                            || requirement.amount() <= 0 || !requirement.ingredient().test(representative)) continue;
                    matchedPossible = true;
                    matchedRequirement = true;
                    if (!collectTagOnlyFluidIngredients(requirement.ingredient(), tags)) {
                        hasNonTagRequirement = true;
                    }
                }
                if (!matchedPossible) hasNonTagRequirement = true;
            }
            if (matchedRequirement && !hasNonTagRequirement && !tags.isEmpty()) {
                result.put(slot, List.copyOf(tags));
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Keeps pure tag-backed mold requirements visible on the encoded pattern. Mold requirements are
     * deliberately not added to the AE processing inputs: they are reusable machine-side tools, not
     * consumed network materials. The bound recipe remains authoritative at execution time.
     */
    public static List<OmniversalPatternData.MoldTagInputSlot> resolveMoldTagInputSlots(
            AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null || recipe.molds().isEmpty()) return List.of();

        List<OmniversalPatternData.MoldTagInputSlot> result = new ArrayList<>();
        for (int moldSlot = 0; moldSlot < recipe.molds().size(); moldSlot++) {
            Ingredient mold = recipe.molds().get(moldSlot);
            java.util.LinkedHashSet<TagKey<Item>> tags = new java.util.LinkedHashSet<>();
            if (!collectTagOnlyItemIngredients(mold, tags)) continue;
            for (TagKey<Item> tag : tags) {
                result.add(new OmniversalPatternData.MoldTagInputSlot(moldSlot, tag));
            }
        }
        return List.copyOf(result);
    }

    private static boolean collectTagOnlyItemIngredients(
            Ingredient ingredient, java.util.Set<TagKey<Item>> tags) {
        if (ingredient == null || ingredient.isEmpty()) return false;
        if (ingredient.isCustom()) {
            return collectKnownCustomTagIngredient(ingredient, tags);
        }
        boolean found = false;
        for (Ingredient.Value value : ingredient.getValues()) {
            if (!(value instanceof Ingredient.TagValue tagValue)) return false;
            tags.add(tagValue.tag());
            found = true;
        }
        return found;
    }

    /**
     * Some compatibility mods expose a semantic category as a custom Ingredient so they can apply
     * their own matching rules. The encoded pattern cannot serialize that custom predicate. An
     * exact registered tag is faithful; when no exact tag exists, a contained category tag is used
     * for display only while the original custom predicate remains authoritative at runtime.
     */
    private static boolean collectKnownCustomTagIngredient(
            Ingredient ingredient, java.util.Set<TagKey<Item>> tags) {
        ItemStack[] candidates;
        try {
            candidates = ingredient.getItems();
        } catch (RuntimeException exception) {
            return false;
        }
        if (candidates == null || candidates.length == 0) return false;
        Set<Item> candidateItems = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || candidate.getCount() <= 0
                    || !candidate.getComponentsPatch().isEmpty()
                    || !candidateItems.add(candidate.getItem())
                    || !ingredient.test(new ItemStack(candidate.getItem()))) {
                return false;
            }
        }

        List<ItemTagCandidate> categoryTags = new ArrayList<>();
        boolean found = false;
        for (Pair<TagKey<Item>, ? extends Iterable<Holder<Item>>> tag : BuiltInRegistries.ITEM.getTags().toList()) {
            Set<Item> tagItems = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Holder<Item> holder : tag.getSecond()) {
                tagItems.add(holder.value());
            }
            if (tagItems.equals(candidateItems)) {
                tags.add(tag.getFirst());
                found = true;
                continue;
            }

            // Some semantic ingredients enumerate a broad item category instead of using a
            // registered tag. A registered category tag is still useful for display when every
            // member belongs to the ingredient; the original custom Ingredient remains the actual
            // mold predicate at runtime. Prefer the largest such category to avoid displaying an
            // arbitrary one-item tag for a broad ingredient.
            // getItems() is only a display representation for custom ingredients. In particular,
            // HoeIngredient exposes one representative hoe even though its predicate accepts every
            // hoe. Do not require the representative list to contain the whole registered tag;
            // probe the tag members against the custom predicate instead.
            if (tagItems.size() >= 2
                    && tagItems.stream().anyMatch(candidateItems::contains)) {
                boolean acceptsAllMembers = true;
                for (Item item : tagItems) {
                    try {
                        if (!ingredient.test(new ItemStack(item))) {
                            acceptsAllMembers = false;
                            break;
                        }
                    } catch (RuntimeException exception) {
                        acceptsAllMembers = false;
                        break;
                    }
                }
                if (acceptsAllMembers) {
                    categoryTags.add(new ItemTagCandidate(tag.getFirst(), tagItems.size()));
                }
            }
        }
        if (found || categoryTags.isEmpty()) return found;

        categoryTags.sort(Comparator.comparingInt(ItemTagCandidate::size).reversed()
                .thenComparing(candidate -> candidate.tag().location().toString()));
        tags.add(categoryTags.getFirst().tag());
        return true;
    }

    private record ItemTagCandidate(TagKey<Item> tag, int size) {
    }

    private static boolean collectTagOnlyFluidIngredients(
            FluidIngredient ingredient, java.util.Set<TagKey<Fluid>> tags) {
        if (ingredient instanceof TagFluidIngredient tag) {
            tags.add(tag.tag());
            return true;
        }
        if (ingredient instanceof CompoundFluidIngredient compound) {
            boolean tagOnly = true;
            for (FluidIngredient child : compound.children()) {
                if (!collectTagOnlyFluidIngredients(child, tags)) tagOnly = false;
            }
            return tagOnly;
        }
        return false;
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
        if (recipe == null) return ItemStack.EMPTY;
        List<GenericStack> inputs = new ArrayList<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input == null || input.count() <= 0 || input.ingredient() == null
                    || input.ingredient().isEmpty()) return ItemStack.EMPTY;
            ItemStack representative = AdapterUtils.itemRepresentative(input.ingredient());
            AEItemKey key = representative == null ? null : AEItemKey.of(representative);
            if (key == null) return ItemStack.EMPTY;
            inputs.add(new GenericStack(key, input.count()));
        }
        for (SizedFluidIngredient input : recipe.inputFluids()) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty()
                    || input.amount() <= 0) return ItemStack.EMPTY;
            FluidStack[] candidates = input.getFluids();
            if (candidates.length == 0 || candidates[0] == null || candidates[0].isEmpty()) {
                return ItemStack.EMPTY;
            }
            GenericStack fluid = GenericStack.fromFluidStack(
                    candidates[0].copyWithAmount(input.amount()));
            if (fluid == null || fluid.what() == null || fluid.amount() <= 0L) return ItemStack.EMPTY;
            // AE processing patterns have one concrete key per slot. Keep one representative
            // here; omniversal metadata restores the Tag/Compound semantics on decode.
            inputs.add(fluid);
        }
        for (GenericStack input : recipe.keyInputs()) {
            if (input == null || input.what() == null || input.amount() <= 0L) return ItemStack.EMPTY;
            inputs.add(input);
        }

        List<GenericStack> outputs = new ArrayList<>();
        for (ItemStack output : recipe.outputs()) {
            if (output == null || output.isEmpty() || output.getCount() <= 0) return ItemStack.EMPTY;
            GenericStack converted = GenericStack.fromItemStack(output);
            if (converted == null || converted.what() == null || converted.amount() <= 0L) {
                return ItemStack.EMPTY;
            }
            outputs.add(converted);
        }
        for (FluidStack output : recipe.outputFluids()) {
            if (output == null || output.isEmpty() || output.getAmount() <= 0) return ItemStack.EMPTY;
            GenericStack converted = GenericStack.fromFluidStack(output);
            if (converted == null || converted.what() == null || converted.amount() <= 0L) {
                return ItemStack.EMPTY;
            }
            outputs.add(converted);
        }
        for (GenericStack output : recipe.keyOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0L) return ItemStack.EMPTY;
            outputs.add(output);
        }
        if (inputs.isEmpty() || outputs.isEmpty()) return ItemStack.EMPTY;
        return PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
    }
}
