package com.sorrowmist.useless.content.recipe.adapters.enderio;

import appeng.api.stacks.AEItemKey;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.api.soul.binding.ingredients.FilledSoulStorageIngredient;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.souldata.SoulDataReloadListener;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts Ender IO soul-binding recipes to the alloy furnace. */
public final class SoulBindingRecipeAdapter implements IRecipeAdapter<SoulBindingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<SoulBindingRecipe> getRecipeClass() {
        return SoulBindingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EIOBlocks.SOUL_BINDER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SoulBindingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        SoulBindingRecipe source = holder.value();
        FluidStack xp = EnderIOAdapterUtils.experienceFluid(source.experience());
        if (!validSource(source)
                || (source.experience() > 0 && xp == null)) {
            LOGGER.warn("Skipping invalid Ender IO soul-binding recipe: {}", holder.id());
            return List.of();
        }

        List<ItemStack> vials = displayVials(source);
        if (vials.isEmpty()) {
            return List.of();
        }

        if (hasDynamicOutput(source, vials)) {
            List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
            for (ItemStack vial : vials) {
                ItemStack output = boundDisplayOutput(source.output(), vial);
                if (output == null || output.isEmpty()) {
                    // Ender IO's JEI category omits a vial when the result cannot actually be
                    // bound to that soul. Never replace it with the unbound/default result: that
                    // is precisely the misleading powered-spawner display this adapter must avoid.
                    continue;
                }

                List<CountedIngredient> inputs = baseInputs(exact(vial), source.input());
                if (inputs == null) {
                    continue;
                }
                result.add(createRecipe(holder, source, inputs,
                        List.of(output, emptySoulVial()),
                        variantId(holder.id(), vial, null)));
            }
            return result;
        }

        // If the output is independent of the soul, mirror SoulBindingCategory's single entry:
        // show every allowed filled vial together and keep the original target Ingredient. The
        // runtime overload below still validates the concrete vial and target with Ender IO.
        Ingredient vialIngredient = allowedVialIngredient(source, vials);
        List<CountedIngredient> inputs = baseInputs(vialIngredient, source.input());
        if (inputs == null) {
            return List.of();
        }
        return List.of(createRecipe(holder, source, inputs,
                List.of(source.output().copy(), emptySoulVial()),
                staticVariantId(holder.id())));
    }

    /**
     * A soul-binding result receives the soul from the vial at craft time. The catalogue contains
     * exact component-aware vial variants when the result is dynamic; this overload still
     * replaces the displayed result with the real output for the vial and target that are actually
     * in the furnace.
     */
    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SoulBindingRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null || level == null) {
            return convertAll(holder, level);
        }

        SoulBindingRecipe source = holder.value();
        if (!validSource(source) || actualInputs == null || actualInputs.isEmpty()) {
            return List.of();
        }

        List<ItemStack> vials = filledVials(actualInputs);
        List<ItemStack> targets = matchingTargets(actualInputs, source.input());
        if (vials.isEmpty() || targets.isEmpty()) {
            return List.of();
        }

        FluidStack xp = experienceInput(source.experience());
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ItemStack vial : vials) {
            for (ItemStack target : targets) {
                SoulBindingRecipe.Input input = new SoulBindingRecipe.Input(
                        vial.copyWithCount(1), target.copyWithCount(1), xp.copy());
                if (!matches(source, input, level, holder)) {
                    continue;
                }

                ConvertedOutputs outputs = craft(source, input, level, holder);
                if (outputs == null || outputs.items().isEmpty() && outputs.fluids().isEmpty()) {
                    continue;
                }

                Ingredient vialIngredient = exact(vial);
                Ingredient targetIngredient = source.copyInputComponents()
                        ? exact(target) : source.input();
                List<CountedIngredient> inputs = baseInputs(vialIngredient, targetIngredient);
                if (inputs == null) {
                    continue;
                }

                result.add(createRecipe(holder, source, inputs, outputs.items(), outputs.fluids(),
                        variantId(holder.id(), vial, source.copyInputComponents() ? target : null)));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<SoulBindingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedFluids == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<SoulBindingRecipe>> result = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<SoulBindingRecipe> holder : manager.getAllRecipesFor(
                EIORecipes.SOUL_BINDING.type().get())) {
            SoulBindingRecipe source = holder.value();
            List<CountedIngredient> inputs = baseInputs(soulVialIngredient(), source.input());
            FluidStack xp = EnderIOAdapterUtils.experienceFluid(source.experience());
            boolean fluidMatches = xp == null || mergedFluids.entrySet().stream()
                    .anyMatch(entry -> entry.getValue() >= xp.getAmount()
                            && FluidStack.isSameFluidSameComponents(entry.getKey(), xp));
            if (inputs != null && fluidMatches
                    && AdapterUtils.matchesRequired(mergedInputs, EnderIOAdapterUtils.requirements(inputs))) {
                result.add(holder);
            }
        }
        return result;
    }

    /** Uses Ender IO's own filled-storage predicate for unrestricted soul-vial inputs. */
    private static Ingredient soulVialIngredient() {
        try {
            return FilledSoulStorageIngredient.of(EIOItems.SOUL_VIAL);
        } catch (RuntimeException exception) {
            // Keep catalogue construction resilient during very early optional-mod setup. The
            // public filled-vial list is still a useful display fallback in that phase.
            LOGGER.warn("Failed to create Ender IO's filled soul-vial ingredient", exception);
            return vialIngredient(SoulVialItem.getAllFilled());
        }
    }

    /**
     * Mirrors the vial list that Ender IO's SoulBindingCategory puts in its JEI input slot.
     * Selector properties are mutually exclusive in the Ender IO serializer, so the first
     * matching branch is also the category's display precedence.
     */
    private static List<ItemStack> displayVials(SoulBindingRecipe source) {
        List<ItemStack> candidates = new ArrayList<>();
        if (source.entityType().isPresent()) {
            addVial(candidates, SoulVialItem.forSoul(Soul.of(source.entityType().get())));
        } else if (source.mobCategory().isPresent()) {
            var category = source.mobCategory().get();
            BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(entityType -> entityType.getCategory().equals(category))
                    .map(Soul::of)
                    .map(SoulVialItem::forSoul)
                    .forEach(vial -> addVial(candidates, vial));
        } else if (source.soulData().isPresent()) {
            try {
                SoulDataReloadListener<?> soulData =
                        SoulDataReloadListener.fromString(source.soulData().get());
                BuiltInRegistries.ENTITY_TYPE.stream()
                        .filter(entityType -> {
                            var id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                            return id != null && soulData.matches(id).isPresent();
                        })
                        .map(Soul::of)
                        .map(SoulVialItem::forSoul)
                        .forEach(vial -> addVial(candidates, vial));
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to enumerate Ender IO soul-data vials for {}",
                        source.soulData().get(), exception);
            }
        } else {
            List<ItemStack> filled = SoulVialItem.getAllFilled();
            if (filled != null) {
                filled.forEach(vial -> addVial(candidates, vial));
            }
        }
        return distinctFilledVials(candidates);
    }

    private static void addVial(List<ItemStack> target, @Nullable ItemStack vial) {
        if (vial != null && !vial.isEmpty()) {
            target.add(vial.copyWithCount(1));
        }
    }

    private static List<ItemStack> distinctFilledVials(@Nullable Iterable<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        for (ItemStack stack : stacks) {
            if (!isFilledVial(stack)
                    || result.stream().anyMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, stack))) {
                continue;
            }
            result.add(stack.copyWithCount(1));
        }
        return result;
    }

    private static Ingredient vialIngredient(@Nullable Iterable<ItemStack> vials) {
        List<ItemStack> distinct = distinctFilledVials(vials);
        if (distinct.isEmpty()) {
            return Ingredient.EMPTY;
        }
        if (distinct.size() == 1) {
            return exact(distinct.getFirst());
        }
        return SoulVialSetIngredient.of(distinct);
    }

    private static Ingredient allowedVialIngredient(
            SoulBindingRecipe source, List<ItemStack> vials) {
        // FilledSoulStorageIngredient is the only merged ingredient that preserves the important
        // "must already contain a soul" predicate. For a recipe without a selector this is
        // exactly Ender IO's complete candidate set; selector recipes normally take the exact
        // per-vial branch above when their output is soul-dependent.
        if (source.entityType().isEmpty() && source.mobCategory().isEmpty()
                && source.soulData().isEmpty()) {
            return soulVialIngredient();
        }
        return vialIngredient(vials);
    }

    private static ItemStack emptySoulVial() {
        return EIOItems.SOUL_VIAL.get().getDefaultInstance();
    }

    private static boolean hasDynamicOutput(SoulBindingRecipe source, List<ItemStack> vials) {
        if (!SoulBoundUtils.canBindSoul(source.output())) {
            return false;
        }
        for (ItemStack vial : vials) {
            ItemStack candidate = boundDisplayOutput(source.output(), vial);
            if (candidate != null
                    && !ItemStack.isSameItemSameComponents(candidate, source.output())) {
                return true;
            }
        }
        return false;
    }

    /** Generates the same bound result that Ender IO's JEI category previews. */
    @Nullable
    private static ItemStack boundDisplayOutput(ItemStack output, ItemStack vial) {
        ItemStack result = output.copy();
        Soul soul = SoulBoundUtils.getBoundSoul(vial);
        if (soul != null && !soul.isEmpty()
                && SoulBoundUtils.canBindSoul(result)
                // SoulBindingCategory deliberately previews the entity type only. Captured
                // entity NBT belongs to the runtime craft path, not to the static JEI variant.
                && SoulBoundUtils.tryBindSoul(result, Soul.of(soul.entityType()))) {
            return result;
        }
        return null;
    }

    /**
     * Keeps the AE component-relaxed vial slot semantically equivalent to Ender IO's
     * {@code SoulBindingRecipe.matches}: extra vial components are allowed, but an empty vial or
     * a soul that does not satisfy the recipe selector is not.
     */
    private static boolean matchesSoulVial(SoulBindingRecipe source, ItemStack vial) {
        if (source == null || !isFilledVial(vial)) {
            return false;
        }
        Soul soul;
        try {
            soul = SoulBoundUtils.getBoundSoul(vial);
        } catch (RuntimeException exception) {
            return false;
        }
        if (soul == null || soul.isEmpty()) {
            return false;
        }
        if (source.soulData().isPresent()) {
            try {
                SoulDataReloadListener<?> listener = SoulDataReloadListener.fromString(
                        source.soulData().get());
                return listener != null && listener.matches(soul.entityType()).isPresent();
            } catch (RuntimeException exception) {
                return false;
            }
        }
        if (source.mobCategory().isPresent()) {
            return soul.entityType().getCategory().equals(source.mobCategory().get());
        }
        if (source.entityType().isPresent()) {
            return source.entityType().get().equals(soul.entityTypeId());
        }
        return true;
    }

    private static DynamicComponentPatternDetails.InputMatcher soulVialMatcher(
            SoulBindingRecipe source) {
        return input -> input instanceof AEItemKey itemKey
                && matchesSoulVial(source, itemKey.toStack(1));
    }

    /** Used by the pattern resolver to mark the soul vial/output component slots as dynamic. */
    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            @Nullable Level level, List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        if (level == null) {
            return Optional.empty();
        }
        return findDynamicPatternProfile(
                level.getRecipeManager().getAllRecipesFor(EIORecipes.SOUL_BINDING.type().get()),
                level, patternInputs, patternOutputs);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<SoulBindingRecipe>> recipes, @Nullable Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        if (recipes == null || patternInputs == null || patternInputs.isEmpty()
                || patternOutputs == null || patternOutputs.isEmpty()) {
            return Optional.empty();
        }

        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<SoulBindingRecipe> holder : recipes) {
            SoulBindingRecipe source = holder == null ? null : holder.value();
            if (!validSource(source)) {
                continue;
            }

            List<CountedIngredient> requirements = baseInputs(soulVialIngredient(), source.input());
            boolean dynamicOutput = hasDynamicOutput(source, displayVials(source));
            if (requirements == null || !matchesPatternInputs(requirements, patternInputs)
                    || !matchesStaticOutputs(source, patternOutputs, dynamicOutput)) {
                continue;
            }

            int vialSlot = uniqueFilledVialSlot(patternInputs);
            int targetSlot = uniqueTargetSlot(patternInputs, source.input(), vialSlot);
            if (vialSlot < 0 || targetSlot < 0) {
                continue;
            }

            SoulBindingRecipe.Input input = new SoulBindingRecipe.Input(
                    patternInputs.get(vialSlot).copyWithCount(1),
                    patternInputs.get(targetSlot).copyWithCount(1),
                    experienceInput(source.experience()));
            if (!matches(source, input, level, holder)) {
                continue;
            }

            Set<Integer> dynamicInputs = new LinkedHashSet<>();
            dynamicInputs.add(vialSlot);
            if (source.copyInputComponents()) {
                dynamicInputs.add(targetSlot);
            }
            matches.add(new DynamicPatternProfile(
                    dynamicInputs,
                    dynamicOutput ? Set.of(0) : Set.of(),
                    Map.of(vialSlot, soulVialMatcher(source))));
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    @Nullable
    private static List<CountedIngredient> baseInputs(Ingredient soul, Ingredient target) {
        if (soul == null || soul.isEmpty() || target == null || target.isEmpty()) {
            return null;
        }
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requirements, soul, 1L);
        AdapterUtils.mergeIngredient(requirements, target, 1L);
        return requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            RecipeHolder<SoulBindingRecipe> holder, SoulBindingRecipe source,
            List<CountedIngredient> inputs, List<ItemStack> outputs,
            net.minecraft.resources.ResourceLocation id) {
        return createRecipe(holder, source, inputs, outputs, List.of(), id);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            RecipeHolder<SoulBindingRecipe> holder, SoulBindingRecipe source,
            List<CountedIngredient> inputs, List<ItemStack> outputs, List<FluidStack> outputFluids,
            net.minecraft.resources.ResourceLocation id) {
        FluidStack xp = EnderIOAdapterUtils.experienceFluid(source.experience());
        return new AdvancedAlloyFurnaceRecipe(
                id, inputs, xp == null ? List.of() : List.of(xp), outputs, outputFluids,
                source.energy(), AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(new ItemStack(EIOBlocks.SOUL_BINDER.get())), AlloyFurnaceMode.NORMAL);
    }

    private static boolean validSource(@Nullable SoulBindingRecipe source) {
        return source != null && source.input() != null && !source.input().isEmpty()
                && source.output() != null && !source.output().isEmpty()
                && source.output().getCount() > 0 && source.energy() >= 0
                && source.experience() >= 0;
    }

    private static FluidStack experienceInput(int levels) {
        FluidStack xp = EnderIOAdapterUtils.experienceFluid(levels);
        return xp == null ? FluidStack.EMPTY : xp;
    }

    private static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
    }

    private static List<ItemStack> filledVials(List<ItemStack> stacks) {
        return distinctFilledVials(stacks);
    }

    private static List<ItemStack> matchingTargets(List<ItemStack> stacks, Ingredient target) {
        List<ItemStack> result = new ArrayList<>();
        if (stacks == null || target == null || target.isEmpty()) {
            return result;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !target.test(stack)
                    || result.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                continue;
            }
            result.add(stack.copyWithCount(1));
        }
        return result;
    }

    private static boolean isFilledVial(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(EIOItems.SOUL_VIAL.get())) {
            return false;
        }

        try {
            Soul soul = SoulBoundUtils.getBoundSoul(stack);
            if (soul != null && !soul.isEmpty()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the capability and known-display-stack checks below.
        }

        try {
            SoulBindable bindable = stack.getCapability(EnderIOCapabilities.SOUL_BINDABLE_ITEM);
            if (bindable != null) {
                if (bindable.hasSoul() && bindable.getBoundSoul() != null
                        && !bindable.getBoundSoul().isEmpty()) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // During early client setup the capability provider may not exist yet. The public
            // filled-vial list below is still enough for catalogue/pattern display.
        }

        List<ItemStack> knownFilled = SoulVialItem.getAllFilled();
        return knownFilled != null && knownFilled.stream()
                .anyMatch(known -> ItemStack.isSameItemSameComponents(known, stack));
    }

    private static boolean matches(
            SoulBindingRecipe source, SoulBindingRecipe.Input input, Level level,
            RecipeHolder<SoulBindingRecipe> holder) {
        try {
            return source.matches(input, level);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to evaluate Ender IO soul-binding recipe {}", holder.id(), exception);
            return false;
        }
    }

    @Nullable
    private static ConvertedOutputs craft(
            SoulBindingRecipe source, SoulBindingRecipe.Input input, Level level,
            RecipeHolder<SoulBindingRecipe> holder) {
        try {
            List<OutputStack> crafted = source.craft(input, level.registryAccess());
            if (crafted == null || crafted.isEmpty()) {
                return null;
            }

            List<ItemStack> items = new ArrayList<>();
            List<FluidStack> fluids = new ArrayList<>();
            for (OutputStack output : crafted) {
                if (output == null || output.isEmpty()) {
                    continue;
                }
                if (output.isItem()) {
                    ItemStack item = output.getItem();
                    if (item != null && !item.isEmpty() && item.getCount() > 0) {
                        items.add(item.copy());
                    }
                } else if (output.isFluid()) {
                    FluidStack fluid = output.getFluid();
                    if (fluid != null && !fluid.isEmpty() && fluid.getAmount() > 0) {
                        fluids.add(fluid.copy());
                    }
                }
            }
            return new ConvertedOutputs(List.copyOf(items), List.copyOf(fluids));
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to craft dynamic Ender IO soul-binding recipe {}", holder.id(), exception);
            return null;
        }
    }

    private static boolean matchesPatternInputs(
            List<CountedIngredient> requirements, List<ItemStack> patternInputs) {
        long required = requirements.stream().mapToLong(CountedIngredient::count).sum();
        long actual = patternInputs.stream().filter(stack -> stack != null && !stack.isEmpty())
                .mapToLong(ItemStack::getCount).sum();
        return required == actual && ItemIngredientAllocator.matches(requirements, patternInputs, 1L);
    }

    private static boolean matchesStaticOutputs(
            SoulBindingRecipe source, List<ItemStack> patternOutputs, boolean dynamicOutput) {
        List<ItemStack> expected = staticOutputs(source);
        if (patternOutputs.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            ItemStack expectedStack = expected.get(index);
            ItemStack actualStack = patternOutputs.get(index);
            if (actualStack == null || actualStack.isEmpty()
                    || actualStack.getCount() != expectedStack.getCount()) {
                return false;
            }
            if (index == 0) {
                // A soul-bound result is intentionally matched by item id only. Static outputs
                // must remain component-exact so unrelated component variants cannot share a
                // pattern profile.
                if (dynamicOutput
                        ? !actualStack.is(expectedStack.getItem())
                        : !ItemStack.isSameItemSameComponents(expectedStack, actualStack)) {
                    return false;
                }
            } else if (!ItemStack.isSameItemSameComponents(expectedStack, actualStack)) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> staticOutputs(SoulBindingRecipe source) {
        List<ItemStack> result = new ArrayList<>();
        result.add(source.output().copy());
        result.add(EIOItems.SOUL_VIAL.get().getDefaultInstance());
        return result;
    }

    private static int uniqueFilledVialSlot(List<ItemStack> inputs) {
        int result = -1;
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (!isFilledVial(inputs.get(slot))) {
                continue;
            }
            if (result >= 0) {
                return -1;
            }
            result = slot;
        }
        return result;
    }

    private static int uniqueTargetSlot(
            List<ItemStack> inputs, Ingredient target, int vialSlot) {
        int result = -1;
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (slot == vialSlot || !target.test(inputs.get(slot))) {
                continue;
            }
            if (result >= 0) {
                return -1;
            }
            result = slot;
        }
        return result;
    }

    private static net.minecraft.resources.ResourceLocation variantId(
            net.minecraft.resources.ResourceLocation source, ItemStack vial, @Nullable ItemStack target) {
        StringBuilder suffix = new StringBuilder("_converted_soul_");
        appendFingerprint(suffix, vial);
        if (target != null) {
            suffix.append("_target_");
            appendFingerprint(suffix, target);
        }
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                source.getNamespace(), source.getPath() + suffix);
    }

    private static net.minecraft.resources.ResourceLocation staticVariantId(
            net.minecraft.resources.ResourceLocation source) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                source.getNamespace(), source.getPath() + "_converted_soul_static");
    }

    private static void appendFingerprint(StringBuilder result, ItemStack stack) {
        net.minecraft.resources.ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        result.append(item == null ? "unknown" : item.getNamespace() + "_" + item.getPath().replace('/', '_'))
                .append('_').append(componentFingerprint(stack));
    }

    private static String componentFingerprint(ItemStack stack) {
        net.minecraft.resources.ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String value = (item == null ? "unknown" : item.toString()) + "|" + stack.getComponents();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ConvertedOutputs(List<ItemStack> items, List<FluidStack> fluids) {
    }

    public record DynamicPatternProfile(
            Set<Integer> idOnlyInputSlots,
            Set<Integer> idOnlyOutputSlots,
            Map<Integer, DynamicComponentPatternDetails.InputMatcher> inputMatchers) {
        public DynamicPatternProfile(Set<Integer> idOnlyInputSlots, Set<Integer> idOnlyOutputSlots) {
            this(idOnlyInputSlots, idOnlyOutputSlots, Map.of());
        }

        public DynamicPatternProfile {
            idOnlyInputSlots = Set.copyOf(new LinkedHashSet<>(idOnlyInputSlots));
            idOnlyOutputSlots = Set.copyOf(new LinkedHashSet<>(idOnlyOutputSlots));
            inputMatchers = inputMatchers == null ? Map.of() : Map.copyOf(inputMatchers);
        }
    }
}
