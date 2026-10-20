package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.enderio.core.common.recipes.OutputStack;
import com.sorrowmist.useless.compat.forge1201.enderio.NativeSoul;
import com.sorrowmist.useless.compat.forge1201.enderio.NativeSoulStorage;
import com.sorrowmist.useless.compat.forge1201.enderio.FilledSoulIngredient;
import com.enderio.machines.common.recipe.SoulBindingRecipe;
import com.enderio.base.common.item.tool.SoulVialItem;
import com.enderio.machines.common.souldata.SoulDataReloadListener;
import com.enderio.machines.common.init.MachineBlocks;
import com.enderio.base.common.init.EIOItems;
import com.enderio.machines.common.init.MachineRecipes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import net.minecraftforge.fluids.FluidStack;
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
        return new ItemStack(MachineBlocks.SOUL_BINDER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SoulBindingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        SoulBindingRecipe source = holder.value();
        FluidStack xp = EnderIOAdapterUtils.experienceFluid(source.getExpCost());
        if (!validSource(source)
                || (source.getExpCost() > 0 && xp == null)) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Ender IO soul-binding recipe: {}", holder.id());
            return List.of();
        }

        List<ItemStack> vials = displayVials(source);
        if (vials.isEmpty()) {
            return List.of();
        }

        if (hasDynamicOutput(source, vials)) {
            List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
            for (ItemStack vial : vials) {
                ItemStack output = boundDisplayOutput(displayOutput(source), vial);
                if (output == null || output.isEmpty()) {
                    // Ender IO's JEI category omits a vial when the result cannot actually be
                    // bound to that soul. Never replace it with the unbound/default result: that
                    // is precisely the misleading powered-spawner display this adapter must avoid.
                    continue;
                }

                List<CountedIngredient> inputs = baseInputs(exact(vial), source.getInput());
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
        List<CountedIngredient> inputs = baseInputs(vialIngredient, source.getInput());
        if (inputs == null) {
            return List.of();
        }
        return List.of(createRecipe(holder, source, inputs,
                List.of(displayOutput(source).copy(), emptySoulVial()),
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
        List<ItemStack> targets = matchingTargets(actualInputs, source.getInput());
        if (vials.isEmpty() || targets.isEmpty()) {
            return List.of();
        }

        FluidStack xp = experienceInput(source.getExpCost());
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ItemStack vial : vials) {
            for (ItemStack target : targets) {
                SoulBindingRecipe.Container input = nativeInput(
                        vial.copyWithCount(1), target.copyWithCount(1), xp.copy());
                if (!matches(source, input, level, holder)) {
                    continue;
                }

                ConvertedOutputs outputs = craft(source, input, level, holder);
                if (outputs == null || outputs.items().isEmpty() && outputs.fluids().isEmpty()) {
                    continue;
                }

                Ingredient vialIngredient = exact(vial);
                // Ender IO 6 binds the soul but does not copy the target item NBT.
                Ingredient targetIngredient = source.getInput();
                List<CountedIngredient> inputs = baseInputs(vialIngredient, targetIngredient);
                if (inputs == null) {
                    continue;
                }

                result.add(createRecipe(holder, source, inputs, outputs.items(), outputs.fluids(),
                        variantId(holder.id(), vial, null)));
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
        for (RecipeHolder<SoulBindingRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(manager,
                MachineRecipes.SOUL_BINDING.type().get())) {
            SoulBindingRecipe source = holder.value();
            List<CountedIngredient> inputs = baseInputs(soulVialIngredient(), source.getInput());
            FluidStack xp = EnderIOAdapterUtils.experienceFluid(source.getExpCost());
            boolean fluidMatches = source.getExpCost() == 0 || xp != null
                    && com.sorrowmist.useless.content.recipe.FluidIngredientAllocator.matchesLong(
                            List.of(com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient.from(xp)),
                            mergedFluids, 1L);
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
            return FilledSoulIngredient.of(EIOItems.FILLED_SOUL_VIAL.get());
        } catch (RuntimeException exception) {
            // Keep catalogue construction resilient during very early optional-mod setup. The
            // public filled-vial list is still a useful display fallback in that phase.
            RateLimitedLogger.warn(LOGGER, "Failed to create Ender IO's filled soul-vial ingredient", exception);
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
        if (source.getEntityType() != null) {
            addVial(candidates, NativeSoulStorage.forSoul(NativeSoul.of(source.getEntityType())));
        } else if (source.getMobCategory() != null) {
            var category = source.getMobCategory();
            BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(entityType -> entityType.getCategory().equals(category))
                    .map(NativeSoul::of)
                    .map(NativeSoulStorage::forSoul)
                    .forEach(vial -> addVial(candidates, vial));
        } else if (source.getSouldata() != null) {
            try {
                SoulDataReloadListener<?> soulData =
                        SoulDataReloadListener.fromString(source.getSouldata());
                BuiltInRegistries.ENTITY_TYPE.stream()
                        .filter(entityType -> {
                            var id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                            return id != null && soulData.matches(id).isPresent();
                        })
                        .map(NativeSoul::of)
                        .map(NativeSoulStorage::forSoul)
                        .forEach(vial -> addVial(candidates, vial));
            } catch (RuntimeException exception) {
                RateLimitedLogger.warn(LOGGER, "Failed to enumerate Ender IO soul-data vials for {}",
                        source.getSouldata(), exception);
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
                    ItemStack.isSameItemSameTags(existing, stack))) {
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
            return exact(distinct.get(0));
        }
        return SoulVialSetIngredient.of(distinct);
    }

    private static Ingredient allowedVialIngredient(
            SoulBindingRecipe source, List<ItemStack> vials) {
        // FilledSoulStorageIngredient is the only merged ingredient that preserves the important
        // "must already contain a soul" predicate. For a recipe without a selector this is
        // exactly Ender IO's complete candidate set; selector recipes normally take the exact
        // per-vial branch above when their output is soul-dependent.
        if (source.getEntityType() == null && source.getMobCategory() == null
                && source.getSouldata() == null) {
            return soulVialIngredient();
        }
        return vialIngredient(vials);
    }

    private static ItemStack emptySoulVial() {
        return EIOItems.EMPTY_SOUL_VIAL.asStack();
    }

    private static boolean hasDynamicOutput(SoulBindingRecipe source, List<ItemStack> vials) {
        if (!NativeSoulStorage.canBindSoul(displayOutput(source))) {
            return false;
        }
        for (ItemStack vial : vials) {
            ItemStack candidate = boundDisplayOutput(displayOutput(source), vial);
            if (candidate != null
                    && !ItemStack.isSameItemSameTags(candidate, displayOutput(source))) {
                return true;
            }
        }
        return false;
    }

    /** Generates the same bound result that Ender IO's JEI category previews. */
    @Nullable
    private static ItemStack boundDisplayOutput(ItemStack output, ItemStack vial) {
        ItemStack result = output.copy();
        NativeSoul soul = NativeSoulStorage.getBoundSoul(vial);
        if (soul != null && !soul.isEmpty()
                && NativeSoulStorage.canBindSoul(result)
                // SoulBindingCategory deliberately previews the entity type only. Captured
                // entity NBT belongs to the runtime craft path, not to the static JEI variant.
                && NativeSoulStorage.tryBindSoul(result, NativeSoul.of(soul.entityType()))) {
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
        NativeSoul soul;
        try {
            soul = NativeSoulStorage.getBoundSoul(vial);
        } catch (RuntimeException exception) {
            return false;
        }
        if (soul == null || soul.isEmpty()) {
            return false;
        }
        if (source.getSouldata() != null) {
            try {
                SoulDataReloadListener<?> listener = SoulDataReloadListener.fromString(source.getSouldata());
                return listener != null && listener.matches(soul.entityTypeId()).isPresent();
            } catch (RuntimeException exception) {
                return false;
            }
        }
        if (source.getMobCategory() != null) {
            return soul.entityType() != null && soul.entityType().getCategory().equals(source.getMobCategory());
        }
        if (source.getEntityType() != null) {
            return source.getEntityType().equals(soul.entityTypeId());
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
        return findDynamicPatternProfileLong(level, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            @Nullable Level level, @Nullable PatternStackView pattern) {
        return findDynamicPatternProfileLong("enderio", level, pattern);
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            String sourceId, @Nullable Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(sourceId, level,
                PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            String sourceId, @Nullable Level level, @Nullable PatternStackView pattern) {
        if (sourceId == null || !sourceId.equals("enderio")) return Optional.empty();
        if (level == null) {
            return Optional.empty();
        }
        return findDynamicPatternProfileLong(
                com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(level.getRecipeManager(), MachineRecipes.SOUL_BINDING.type().get()),
                level, pattern);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<SoulBindingRecipe>> recipes, @Nullable Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(recipes, level,
                PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            Iterable<RecipeHolder<SoulBindingRecipe>> recipes, @Nullable Level level,
            @Nullable PatternStackView pattern) {
        if (recipes == null || pattern == null || pattern.inputs().isEmpty()
                || pattern.outputs().isEmpty() || !validItemStacks(pattern)) {
            return Optional.empty();
        }

        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<SoulBindingRecipe> holder : recipes) {
            SoulBindingRecipe source = holder == null ? null : holder.value();
            if (!validSource(source)) {
                continue;
            }

            List<CountedIngredient> requirements = baseInputs(soulVialIngredient(), source.getInput());
            boolean dynamicOutput = hasDynamicOutput(source, displayVials(source));
            if (requirements == null || !matchesPatternInputs(requirements, pattern)
                    || !matchesStaticOutputs(source, pattern.outputs(), dynamicOutput)) {
                continue;
            }

            List<ItemStack> patternInputs = pattern.inputRepresentatives();
            int vialSlot = uniqueFilledVialSlot(patternInputs);
            int targetSlot = uniqueTargetSlot(patternInputs, source.getInput(), vialSlot);
            if (vialSlot < 0 || targetSlot < 0) {
                continue;
            }

            SoulBindingRecipe.Container input = nativeInput(
                    patternInputs.get(vialSlot).copyWithCount(1),
                    patternInputs.get(targetSlot).copyWithCount(1),
                    experienceInput(source.getExpCost()));
            if (!matches(source, input, level, holder)) {
                continue;
            }

            Set<Integer> dynamicInputs = new LinkedHashSet<>();
            dynamicInputs.add(vialSlot);
            matches.add(new DynamicPatternProfile(
                    dynamicInputs,
                    dynamicOutput ? Set.of(0) : Set.of(),
                    Map.of(vialSlot, soulVialMatcher(source))));
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
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
        FluidStack xp = EnderIOAdapterUtils.experienceFluid(source.getExpCost());
        return new AdvancedAlloyFurnaceRecipe(
                id, inputs, xp == null ? List.of() : List.of(xp), outputs, outputFluids,
                source.getBaseEnergyCost(), AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(new ItemStack(MachineBlocks.SOUL_BINDER.get())), AlloyFurnaceMode.NORMAL);
    }

    private static boolean validSource(@Nullable SoulBindingRecipe source) {
        return source != null && source.getInput() != null && !source.getInput().isEmpty()
                && displayOutput(source) != null && !displayOutput(source).isEmpty()
                && displayOutput(source).getCount() > 0 && source.getBaseEnergyCost() >= 0
                && source.getExpCost() >= 0;
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
                    || result.stream().anyMatch(existing -> ItemStack.isSameItemSameTags(existing, stack))) {
                continue;
            }
            result.add(stack.copyWithCount(1));
        }
        return result;
    }

    private static boolean isFilledVial(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(EIOItems.FILLED_SOUL_VIAL.get())
                && !NativeSoulStorage.getBoundSoul(stack).isEmpty();
    }

    private static SoulBindingRecipe.Container nativeInput(ItemStack vial, ItemStack target, FluidStack xp) {
        var slots = new net.minecraftforge.items.ItemStackHandler(2);
        slots.setStackInSlot(0, vial.copy());
        slots.setStackInSlot(1, target.copy());
        return new SoulBindingRecipe.Container(slots, xp::getAmount);
    }

    private static ItemStack displayOutput(SoulBindingRecipe source) {
        var outputs = source.getResultStacks(net.minecraft.core.RegistryAccess.EMPTY);
        return outputs == null || outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).getItem();
    }

    private static boolean matches(
            SoulBindingRecipe source, SoulBindingRecipe.Container input, Level level,
            RecipeHolder<SoulBindingRecipe> holder) {
        try {
            return source.matches(input, level);
        } catch (RuntimeException exception) {
            RateLimitedLogger.warn(LOGGER, "Failed to evaluate Ender IO soul-binding recipe {}", holder.id(), exception);
            return false;
        }
    }

    @Nullable
    private static ConvertedOutputs craft(
            SoulBindingRecipe source, SoulBindingRecipe.Container input, Level level,
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
            RateLimitedLogger.warn(LOGGER, "Failed to craft dynamic Ender IO soul-binding recipe {}", holder.id(), exception);
            return null;
        }
    }

    private static boolean matchesPatternInputs(
            List<CountedIngredient> requirements, PatternStackView pattern) {
        long required = totalRequiredItems(requirements);
        long actual = totalPatternItems(pattern.inputs());
        return required == actual
                && ItemIngredientAllocator.matches(requirements, List.of(), pattern.inputs(), 1L);
    }

    private static long totalRequiredItems(List<CountedIngredient> requirements) {
        long result = 0L;
        for (CountedIngredient requirement : requirements) {
            if (requirement == null || requirement.count() <= 0L) {
                continue;
            }
            if (result > Long.MAX_VALUE - requirement.count()) {
                return Long.MAX_VALUE;
            }
            result += requirement.count();
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

    private static boolean matchesStaticOutputs(
            SoulBindingRecipe source, List<GenericStack> patternOutputs, boolean dynamicOutput) {
        List<ItemStack> expected = staticOutputs(source);
        if (patternOutputs.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            ItemStack expectedStack = expected.get(index);
            GenericStack actual = patternOutputs.get(index);
            if (actual == null || actual.amount() <= 0L || actual.amount() != expectedStack.getCount()
                    || !(actual.what() instanceof AEItemKey itemKey)) {
                return false;
            }
            ItemStack actualStack = itemKey.toStack(1);
            if (index == 0) {
                // A soul-bound result is intentionally matched by item id only. Static outputs
                // must remain component-exact so unrelated component variants cannot share a
                // pattern profile.
                if (dynamicOutput
                        ? !actualStack.is(expectedStack.getItem())
                        : !ItemStack.isSameItemSameTags(expectedStack, actualStack)) {
                    return false;
                }
            } else if (!ItemStack.isSameItemSameTags(expectedStack, actualStack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validItemStacks(PatternStackView pattern) {
        return pattern.inputs().stream().allMatch(stack -> stack != null && stack.amount() > 0L
                        && stack.what() instanceof AEItemKey)
                && pattern.outputs().stream().allMatch(stack -> stack != null && stack.amount() > 0L
                        && stack.what() instanceof AEItemKey);
    }

    private static List<ItemStack> staticOutputs(SoulBindingRecipe source) {
        List<ItemStack> result = new ArrayList<>();
        result.add(displayOutput(source).copy());
        result.add(EIOItems.EMPTY_SOUL_VIAL.asStack());
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
        return new net.minecraft.resources.ResourceLocation(
                source.getNamespace(), source.getPath() + suffix);
    }

    private static net.minecraft.resources.ResourceLocation staticVariantId(
            net.minecraft.resources.ResourceLocation source) {
        return new net.minecraft.resources.ResourceLocation(
                source.getNamespace(), source.getPath() + "_converted_soul_static");
    }

    private static void appendFingerprint(StringBuilder result, ItemStack stack) {
        net.minecraft.resources.ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        result.append(item == null ? "unknown" : item.getNamespace() + "_" + item.getPath().replace('/', '_'))
                .append('_').append(componentFingerprint(stack));
    }

    private static String componentFingerprint(ItemStack stack) {
        net.minecraft.resources.ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String value = (item == null ? "unknown" : item.toString()) + "|" + com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical.snbt(stack.getTag());
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
