package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.adapters.draconicevolution.DraconicFusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.malum.SpiritInfusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.occultism.OccultismRitualRecipeAdapter;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds provider-local component-aware views of otherwise unchanged AE patterns. */
public final class AdvancedAlloyFurnacePatternResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DRACONIC_EVOLUTION_MOD_ID = "draconicevolution";
    private static final String OCCULTISM_MOD_ID = "occultism";
    private static final String MALUM_MOD_ID = "malum";

    private AdvancedAlloyFurnacePatternResolver() {
    }

    @Nullable
    public static IPatternDetails decode(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return null;
        }

        /*
         * AE2's generic decoder deliberately catches every exception thrown by
         * an EncodedPatternItem and returns null.  That is useful for ordinary
         * invalid patterns, but it makes a recipe-bound omniversal pattern look
         * exactly like an empty slot.  Decode our item explicitly so the
         * binding can be validated and failures can be diagnosed, while still
         * leaving all other AE2 pattern types on the normal path.
         */
        if (stack.is(ModItems.OMNIVERSAL_PATTERN.get())) {
            AEItemKey definition = AEItemKey.of(stack);
            OmniversalPatternData data = definition == null
                    ? null
                    : definition.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
            try {
                return OmniversalPatternDetails.decode(definition, level);
            } catch (RuntimeException exception) {
                LOGGER.warn("Ignoring invalid omniversal pattern (recipe={}, fingerprint={})",
                        data == null ? "<missing>" : data.recipeId(),
                        data == null ? "<missing>" : data.recipeFingerprint(),
                        exception);
                return null;
            }
        }

        IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, level);
        return decoded == null ? null : resolve(decoded, level);
    }

    public static IPatternDetails resolve(IPatternDetails pattern, Level level) {
        if (pattern instanceof DynamicComponentPattern
                || !(pattern instanceof AEProcessingPattern processingPattern)) {
            return pattern;
        }

        try {
            if (ModList.get().isLoaded(DRACONIC_EVOLUTION_MOD_ID)) {
                IPatternDetails resolved = resolveDynamicDraconicPattern(processingPattern, level);
                if (resolved != processingPattern) {
                    return resolved;
                }
            }
            if (ModList.get().isLoaded(OCCULTISM_MOD_ID)) {
                IPatternDetails resolved = resolveDynamicOccultismPattern(processingPattern, level);
                if (resolved != processingPattern) {
                    return resolved;
                }
            }
            if (ModList.get().isLoaded(MALUM_MOD_ID)) {
                return resolveDynamicMalumPattern(processingPattern, level);
            }
            return processingPattern;
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to create a component-aware AE view for pattern {}",
                    pattern.getDefinition(), exception);
            return pattern;
        }
    }

    private static IPatternDetails resolveDynamicDraconicPattern(
            AEProcessingPattern pattern, Level level) {
        List<ItemStack> inputs = itemInputs(pattern);
        List<ItemStack> outputs = itemOutputs(pattern);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return pattern;
        }

        var dynamicProfile = DraconicFusionRecipeAdapter.findDynamicPatternProfile(level, inputs, outputs);
        if (dynamicProfile.isEmpty()) {
            return pattern;
        }

        var profile = dynamicProfile.get();
        AEProcessingPattern executionPattern = profile.canonicalInputs().isEmpty()
                ? pattern
                : withCanonicalInputs(pattern, profile.canonicalInputs());
        return new DynamicComponentPatternDetails(
                executionPattern,
                profile.idOnlyInputSlots(),
                profile.idOnlyOutputSlots(),
                level.registryAccess());
    }

    private static IPatternDetails resolveDynamicOccultismPattern(
            AEProcessingPattern pattern, Level level) {
        List<ItemStack> inputs = itemInputs(pattern);
        List<ItemStack> outputs = itemOutputs(pattern);
        var profile = OccultismRitualRecipeAdapter.findDynamicPatternProfile(level, inputs, outputs);
        if (profile.isEmpty()) {
            return pattern;
        }
        return new DynamicComponentPatternDetails(
                pattern,
                profile.get().idOnlyInputSlots(),
                profile.get().idOnlyOutputSlots(),
                level.registryAccess());
    }

    private static IPatternDetails resolveDynamicMalumPattern(
            AEProcessingPattern pattern, Level level) {
        List<ItemStack> inputs = itemInputs(pattern);
        List<ItemStack> outputs = itemOutputs(pattern);
        var profile = SpiritInfusionRecipeAdapter.findDynamicPatternProfile(level, inputs, outputs);
        if (profile.isEmpty()) {
            return pattern;
        }
        return new DynamicComponentPatternDetails(
                pattern,
                profile.get().idOnlyInputSlots(),
                profile.get().idOnlyOutputSlots(),
                level.registryAccess());
    }

    private static List<ItemStack> itemInputs(IPatternDetails pattern) {
        List<ItemStack> result = new ArrayList<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            AEItemKey itemKey = firstItemKey(input.getPossibleInputs());
            long amount = input.getMultiplier();
            if (itemKey == null || amount <= 0 || amount > Integer.MAX_VALUE) {
                return List.of();
            }
            result.add(itemKey.toStack((int) amount));
        }
        return result;
    }

    private static List<ItemStack> itemOutputs(IPatternDetails pattern) {
        List<ItemStack> result = new ArrayList<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (!(output.what() instanceof AEItemKey itemKey)
                    || output.amount() <= 0 || output.amount() > Integer.MAX_VALUE) {
                return List.of();
            }
            result.add(itemKey.toStack((int) output.amount()));
        }
        return result;
    }

    @Nullable
    private static AEItemKey firstItemKey(GenericStack[] possibleInputs) {
        if (possibleInputs == null) {
            return null;
        }
        for (GenericStack possibleInput : possibleInputs) {
            if (possibleInput != null && possibleInput.what() instanceof AEItemKey itemKey) {
                return itemKey;
            }
        }
        return null;
    }

    static AEProcessingPattern withCanonicalInputs(
            AEProcessingPattern source, Map<Integer, ItemStack> canonicalInputs) {
        return new CanonicalInputProcessingPattern(source, canonicalInputs);
    }

    private static final class CanonicalInputProcessingPattern extends AEProcessingPattern {
        private final IPatternDetails.IInput[] inputs;

        private CanonicalInputProcessingPattern(
                AEProcessingPattern source, Map<Integer, ItemStack> canonicalInputs) {
            super(source.getDefinition());
            IPatternDetails.IInput[] sourceInputs = source.getInputs();
            this.inputs = new IPatternDetails.IInput[sourceInputs.length];
            for (int slot = 0; slot < sourceInputs.length; slot++) {
                this.inputs[slot] = sourceInputs[slot];
            }
            for (Map.Entry<Integer, ItemStack> entry : canonicalInputs.entrySet()) {
                int slot = entry.getKey();
                if (slot < 0 || slot >= inputs.length) {
                    throw new IllegalArgumentException("Canonical input slot is outside the processing pattern: " + slot);
                }
                inputs[slot] = prependCanonicalInput(inputs[slot], entry.getValue());
            }
        }

        @Override
        public IPatternDetails.IInput[] getInputs() {
            return inputs.clone();
        }
    }

    static IPatternDetails.IInput prependCanonicalInput(
            IPatternDetails.IInput source, ItemStack canonicalStack) {
        if (source == null || canonicalStack == null || canonicalStack.isEmpty()) {
            throw new IllegalArgumentException("Canonical processing input must be non-empty");
        }

        GenericStack[] original = source.getPossibleInputs();
        long candidateAmount = original.length > 0 && original[0] != null
                ? original[0].amount()
                : 1L;
        if (candidateAmount <= 0) {
            candidateAmount = 1L;
        }

        AEItemKey canonicalKey = AEItemKey.of(canonicalStack.copyWithCount(1));
        if (canonicalKey == null) {
            throw new IllegalArgumentException("Canonical processing input did not produce an AE item key");
        }
        GenericStack canonical = new GenericStack(canonicalKey, candidateAmount);

        List<GenericStack> possibleInputs = new ArrayList<>(original.length + 1);
        possibleInputs.add(canonical);
        for (GenericStack possible : original) {
            if (possible != null && !possible.equals(canonical)) {
                possibleInputs.add(possible);
            }
        }
        return new CanonicalInput(source, possibleInputs.toArray(GenericStack[]::new));
    }

    private record CanonicalInput(
            IPatternDetails.IInput source, GenericStack[] possibleInputs) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return source.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return source.isValid(input, level);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return source.getRemainingKey(template);
        }
    }
}
