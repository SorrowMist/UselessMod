package com.sorrowmist.useless.content.recipe.adapters.draconicevolution;

import appeng.api.stacks.GenericStack;
import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.api.DraconicAPI;
import com.brandon3055.draconicevolution.api.crafting.IFusionDataTransfer;
import com.brandon3055.draconicevolution.api.crafting.IFusionInjector;
import com.brandon3055.draconicevolution.api.crafting.IFusionInventory;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.StackIngredient;
import com.brandon3055.draconicevolution.init.DEContent;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DraconicFusionRecipeAdapter implements IRecipeAdapter<IFusionRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[] DEFAULT_CHARGE_TICKS = {300, 220, 140, 60};
    private static final int[] DEFAULT_CRAFT_TICKS = {300, 220, 140, 60};

    @Override
    public Class<IFusionRecipe> getRecipeClass() {
        return IFusionRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return injectorTier(mold) >= 0;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<IFusionRecipe> holder, Level level) {
        if (holder == null) {
            return List.of();
        }

        IFusionRecipe source = holder.value();
        ConvertedFusionData data = convertData(source, level);
        if (data == null) {
            LOGGER.warn("Skipping unsupported Draconic Evolution fusion recipe: {}", holder.id());
            return List.of();
        }

        return List.of(createRecipe(holder, source, data));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IFusionRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null) {
            return List.of();
        }

        IFusionRecipe source = holder.value();
        ItemStack template = resultItem(source, level);
        if (template.isEmpty()) {
            LOGGER.warn("Skipping unsupported Draconic Evolution fusion recipe: {}", holder.id());
            return List.of();
        }
        if (!(template.getItem() instanceof IFusionDataTransfer)) {
            return convertAll(holder, level);
        }

        CatalystRequirement sourceCatalyst = catalystRequirement(source.getCatalyst());
        if (sourceCatalyst.ingredient().isEmpty()) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>();
        for (ItemStack catalyst : matchingCatalysts(source.getCatalyst(), actualInputs)) {
            ItemStack assembled;
            try {
                FusionInventorySnapshot inventory = new FusionInventorySnapshot(catalyst, source.getRecipeTier());
                assembled = source.assemble(inventory, level == null ? null : level.registryAccess()).copy();
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to assemble dynamic Draconic Evolution fusion recipe {}", holder.id(), exception);
                continue;
            }
            if (assembled.isEmpty()) {
                continue;
            }

            Ingredient exactCatalyst = DataComponentIngredient.of(true, catalyst.copyWithCount(1));
            ConvertedFusionData data = convertData(source, assembled, exactCatalyst, sourceCatalyst.count());
            if (data != null) {
                converted.add(createRecipe(holder, source, data));
            }
        }

        if (converted.isEmpty()) {
            LOGGER.warn("Skipping dynamic Draconic Evolution fusion recipe without a valid catalyst result: {}",
                    holder.id());
        }
        return converted;
    }

    /**
     * Identifies component-carrying inputs and outputs in a plain JEI processing
     * pattern. Canonical inputs connect a parent recipe to the static output key
     * advertised by a dynamic Draconic Evolution child recipe.
     */
    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Level level, List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(level, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<IFusionRecipe>> recipes, @Nullable Level level,
            List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(
                recipes, level, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            Level level, PatternStackView pattern) {
        if (level == null) {
            return Optional.empty();
        }
        return findDynamicPatternProfileLong(
                level.getRecipeManager().getAllRecipesFor(DraconicAPI.FUSION_RECIPE_TYPE.get()),
                level, pattern);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            Iterable<RecipeHolder<IFusionRecipe>> recipes, @Nullable Level level,
            PatternStackView pattern) {
        if (recipes == null || pattern == null || pattern.inputs().isEmpty()
                || pattern.outputs().isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> inputRepresentatives = pattern.inputRepresentatives();
        List<ItemStack> outputRepresentatives = pattern.outputRepresentatives();
        if (inputRepresentatives.size() != pattern.inputs().size()
                || outputRepresentatives.size() != pattern.outputs().size()) {
            return Optional.empty();
        }

        GenericStack primaryPatternOutput = pattern.outputs().getFirst();
        ItemStack primaryOutputRepresentative = outputRepresentatives.getFirst();

        List<RecipeHolder<IFusionRecipe>> recipeList = new ArrayList<>();
        for (RecipeHolder<IFusionRecipe> holder : recipes) {
            if (holder == null || holder.value() == null) {
                continue;
            }
            recipeList.add(holder);
        }

        Map<Item, ItemStack> canonicalDynamicOutputs = canonicalDynamicOutputs(recipeList, level);
        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<IFusionRecipe> holder : recipeList) {

            IFusionRecipe source = holder.value();
            ItemStack staticResult = resultItem(source, level);
            if (!matchesPrimaryPatternOutput(staticResult, primaryPatternOutput, primaryOutputRepresentative)) {
                continue;
            }

            ConvertedFusionData data = convertData(source, level);
            if (data == null || totalRequiredItems(data.inputs()) != totalPatternItems(pattern.inputs())
                    || !ItemIngredientAllocator.matches(data.inputs(), List.of(), pattern.inputs(), 1L)) {
                continue;
            }

            Map<Integer, ItemStack> canonicalInputs = new LinkedHashMap<>();
            Set<Integer> idOnlyInputs = new LinkedHashSet<>();
            boolean ambiguousDynamicInput = false;
            for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                ItemStack input = inputRepresentatives.get(slot);

                ItemStack canonical = canonicalDynamicOutputs.get(input.getItem());
                if (canonical == null) {
                    continue;
                }
                if (hasDuplicateInputItem(inputRepresentatives, slot)
                        || !hasComponentAgnosticRequirement(data.inputs(), input)) {
                    ambiguousDynamicInput = true;
                    break;
                }

                canonicalInputs.put(slot, canonical.copyWithCount(1));
                idOnlyInputs.add(slot);
            }
            if (ambiguousDynamicInput) {
                continue;
            }

            Set<Integer> idOnlyOutputs = new LinkedHashSet<>();
            if (staticResult.getItem() instanceof IFusionDataTransfer) {
                CatalystRequirement catalyst = catalystRequirement(source.getCatalyst());
                int catalystSlot = findUniqueCatalystSlot(pattern.inputs(), inputRepresentatives, catalyst);
                if (catalystSlot < 0 || catalyst.ingredient().getCustomIngredient() != null) {
                    continue;
                }

                idOnlyInputs.add(catalystSlot);
                idOnlyOutputs.add(0);
            }

            if (idOnlyInputs.isEmpty() && idOnlyOutputs.isEmpty()) {
                continue;
            }

            matches.add(new DynamicPatternProfile(canonicalInputs, idOnlyInputs, idOnlyOutputs));
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }

        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static Map<Item, ItemStack> canonicalDynamicOutputs(
            List<RecipeHolder<IFusionRecipe>> recipes, @Nullable Level level) {
        Map<Item, ItemStack> results = new LinkedHashMap<>();
        Set<Item> ambiguous = new LinkedHashSet<>();
        for (RecipeHolder<IFusionRecipe> holder : recipes) {
            ItemStack result = resultItem(holder.value(), level);
            if (result.isEmpty() || !(result.getItem() instanceof IFusionDataTransfer)
                    || ambiguous.contains(result.getItem())) {
                continue;
            }

            ItemStack normalized = result.copyWithCount(1);
            ItemStack existing = results.get(result.getItem());
            if (existing == null) {
                results.put(result.getItem(), normalized);
            } else if (!ItemStack.isSameItemSameComponents(existing, normalized)) {
                results.remove(result.getItem());
                ambiguous.add(result.getItem());
            }
        }
        return results;
    }

    private static boolean matchesPrimaryPatternOutput(
            ItemStack recipeOutput, GenericStack patternOutput, ItemStack patternOutputRepresentative) {
        if (recipeOutput.isEmpty() || patternOutput == null || patternOutput.amount() <= 0L
                || patternOutputRepresentative == null || patternOutputRepresentative.isEmpty()
                || recipeOutput.getCount() != patternOutput.amount()
                || !recipeOutput.is(patternOutputRepresentative.getItem())) {
            return false;
        }
        return recipeOutput.getItem() instanceof IFusionDataTransfer
                || ItemStack.isSameItemSameComponents(
                recipeOutput.copyWithCount(1), patternOutputRepresentative.copyWithCount(1));
    }

    private static int findUniqueCatalystSlot(
            List<GenericStack> patternInputs, List<ItemStack> inputRepresentatives,
            CatalystRequirement catalyst) {
        if (catalyst.ingredient().isEmpty() || catalyst.count() <= 0
                || inputRepresentatives.size() != patternInputs.size()) {
            return -1;
        }

        int catalystSlot = -1;
        for (int slot = 0; slot < patternInputs.size(); slot++) {
            GenericStack input = patternInputs.get(slot);
            ItemStack representative = inputRepresentatives.get(slot);
            if (input == null || input.amount() != catalyst.count()
                    || representative == null || representative.isEmpty()
                    || !catalyst.ingredient().test(representative)) {
                continue;
            }
            if (catalystSlot >= 0) {
                return -1;
            }
            catalystSlot = slot;
        }
        return catalystSlot >= 0 && !hasDuplicateInputItem(inputRepresentatives, catalystSlot)
                ? catalystSlot
                : -1;
    }

    private static boolean hasComponentAgnosticRequirement(
            List<CountedIngredient> requirements, ItemStack input) {
        boolean found = false;
        for (CountedIngredient requirement : requirements) {
            Ingredient ingredient = requirement.ingredient();
            if (ingredient == null || !ingredient.test(input)) {
                continue;
            }
            if (ingredient.getCustomIngredient() != null) {
                return false;
            }
            found = true;
        }
        return found;
    }

    private static long totalRequiredItems(List<CountedIngredient> inputs) {
        long total = 0L;
        for (CountedIngredient input : inputs) {
            if (input != null && input.count() > 0) {
                total = total > Long.MAX_VALUE - input.count()
                        ? Long.MAX_VALUE
                        : total + input.count();
            }
        }
        return total;
    }

    private static long totalPatternItems(List<GenericStack> inputs) {
        long total = 0L;
        for (GenericStack input : inputs) {
            if (input != null && input.amount() > 0L) {
                total = total > Long.MAX_VALUE - input.amount()
                        ? Long.MAX_VALUE
                        : total + input.amount();
            }
        }
        return total;
    }

    private static boolean hasDuplicateInputItem(List<ItemStack> inputs, int catalystSlot) {
        ItemStack catalyst = inputs.get(catalystSlot);
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (slot != catalystSlot) {
                ItemStack candidate = inputs.get(slot);
                if (candidate != null && !candidate.isEmpty() && candidate.is(catalyst.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            RecipeHolder<IFusionRecipe> holder, IFusionRecipe source, ConvertedFusionData data) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                data.inputs(),
                List.of(),
                data.outputs(),
                List.of(),
                source.getEnergyCost(),
                processTime(source.getRecipeTier()),
                Ingredient.EMPTY,
                0,
                injectorMold(source.getRecipeTier()),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public List<RecipeHolder<IFusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        int moldTier = injectorTier(mold);
        if (level == null || moldTier < 0 || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<IFusionRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<IFusionRecipe> holder : level.getRecipeManager().getAllRecipesFor(
                DraconicAPI.FUSION_RECIPE_TYPE.get())) {
            IFusionRecipe recipe = holder.value();
            if (moldTier < tierIndex(recipe.getRecipeTier())) {
                continue;
            }
            ConvertedFusionData data = convertData(recipe, level);
            if (data != null && AdapterUtils.matchesRequired(mergedInputs, data.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static ConvertedFusionData convertData(IFusionRecipe source, @Nullable Level level) {
        if (source == null || source.getEnergyCost() < 0L || source.getCatalyst().isEmpty()) {
            return null;
        }

        CatalystRequirement catalyst = catalystRequirement(source.getCatalyst());
        ItemStack result = resultItem(source, level);
        return convertData(source, result, catalyst.ingredient(), catalyst.count());
    }

    @Nullable
    private static ConvertedFusionData convertData(
            IFusionRecipe source, ItemStack result, Ingredient catalystRequirement, long catalystAmount) {
        if (source == null || source.getEnergyCost() < 0L || catalystRequirement == null
                || catalystRequirement.isEmpty() || catalystAmount <= 0L) {
            return null;
        }
        if (result.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requirements, catalystRequirement, catalystAmount);

        List<ItemStack> outputs = new ArrayList<>();
        mergeOutput(outputs, result);
        for (IFusionRecipe.IFusionIngredient fusionIngredient : source.fusionIngredients()) {
            Ingredient ingredient = fusionIngredient.get();
            if (ingredient == null || ingredient.isEmpty()) {
                return null;
            }
            AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
            if (!fusionIngredient.consume()) {
                ItemStack returned = singleReturnStack(ingredient);
                if (returned.isEmpty()) {
                    return null;
                }
                mergeOutput(outputs, returned);
            }
        }

        List<CountedIngredient> inputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new ConvertedFusionData(inputs, outputs, requirements);
    }

    private static ItemStack resultItem(IFusionRecipe source, @Nullable Level level) {
        if (source == null) {
            return ItemStack.EMPTY;
        }
        return source.getResultItem(level == null ? null : level.registryAccess()).copy();
    }

    private static List<ItemStack> matchingCatalysts(Ingredient catalyst, List<ItemStack> actualInputs) {
        if (catalyst == null || catalyst.isEmpty() || actualInputs == null || actualInputs.isEmpty()) {
            return List.of();
        }

        List<ItemStack> matches = new ArrayList<>();
        for (ItemStack stack : actualInputs) {
            if (stack == null || stack.isEmpty() || !catalyst.test(stack)) {
                continue;
            }
            boolean duplicate = matches.stream()
                    .anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack));
            if (!duplicate) {
                matches.add(stack.copy());
            }
        }
        return matches;
    }

    private static CatalystRequirement catalystRequirement(Ingredient catalyst) {
        if (catalyst == null || catalyst.isEmpty()) {
            return new CatalystRequirement(Ingredient.EMPTY, 0L);
        }
        if (catalyst.getCustomIngredient() instanceof StackIngredient stackIngredient) {
            Ingredient itemOnly = Ingredient.of(stackIngredient.getItems()
                    .map(stack -> stack.copyWithCount(1)));
            return new CatalystRequirement(itemOnly, Math.max(1, stackIngredient.getCount()));
        }
        return new CatalystRequirement(catalyst, 1L);
    }

    private static ItemStack singleReturnStack(Ingredient ingredient) {
        ItemStack[] candidates = ingredient.getItems();
        if (candidates.length == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack first = candidates[0].copyWithCount(1);
        for (int i = 1; i < candidates.length; i++) {
            if (!ItemStack.isSameItemSameComponents(first, candidates[i])) {
                return ItemStack.EMPTY;
            }
        }
        return first;
    }

    private static void mergeOutput(List<ItemStack> outputs, ItemStack output) {
        for (ItemStack existing : outputs) {
            if (ItemStack.isSameItemSameComponents(existing, output)) {
                existing.grow(output.getCount());
                return;
            }
        }
        outputs.add(output.copy());
    }

    private static Ingredient injectorMold(TechLevel minimumTier) {
        int minimum = tierIndex(minimumTier);
        List<ItemStack> injectors = new ArrayList<>();
        if (minimum <= TechLevel.DRACONIUM.index) {
            injectors.add(new ItemStack(DEContent.BASIC_CRAFTING_INJECTOR.get()));
        }
        if (minimum <= TechLevel.WYVERN.index) {
            injectors.add(new ItemStack(DEContent.WYVERN_CRAFTING_INJECTOR.get()));
        }
        if (minimum <= TechLevel.DRACONIC.index) {
            injectors.add(new ItemStack(DEContent.AWAKENED_CRAFTING_INJECTOR.get()));
        }
        injectors.add(new ItemStack(DEContent.CHAOTIC_CRAFTING_INJECTOR.get()));
        return Ingredient.of(injectors.stream());
    }

    private static int injectorTier(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return -1;
        }
        if (mold.is(DEContent.BASIC_CRAFTING_INJECTOR.get().asItem())) {
            return TechLevel.DRACONIUM.index;
        }
        if (mold.is(DEContent.WYVERN_CRAFTING_INJECTOR.get().asItem())) {
            return TechLevel.WYVERN.index;
        }
        if (mold.is(DEContent.AWAKENED_CRAFTING_INJECTOR.get().asItem())) {
            return TechLevel.DRACONIC.index;
        }
        if (mold.is(DEContent.CHAOTIC_CRAFTING_INJECTOR.get().asItem())) {
            return TechLevel.CHAOTIC.index;
        }
        return -1;
    }

    private static int processTime(TechLevel tier) {
        int index = tierIndex(tier);
        long charge = configTicks(DEConfig.fusionChargeTime, DEFAULT_CHARGE_TICKS, index);
        long craft = configTicks(DEConfig.fusionCraftTime, DEFAULT_CRAFT_TICKS, index);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, charge + craft));
    }

    private static int configTicks(@Nullable List<Integer> configured, int[] defaults, int index) {
        if (configured != null && index < configured.size() && configured.get(index) != null) {
            return Math.max(1, configured.get(index));
        }
        return defaults[index];
    }

    private static int tierIndex(@Nullable TechLevel tier) {
        return tier == null ? TechLevel.DRACONIUM.index
                : Math.max(TechLevel.DRACONIUM.index, Math.min(TechLevel.CHAOTIC.index, tier.index));
    }

    private static final class FusionInventorySnapshot implements IFusionInventory {
        private ItemStack catalyst;
        private ItemStack output = ItemStack.EMPTY;
        private final TechLevel minimumTier;

        private FusionInventorySnapshot(ItemStack catalyst, @Nullable TechLevel minimumTier) {
            this.catalyst = catalyst.copy();
            this.minimumTier = minimumTier == null ? TechLevel.DRACONIUM : minimumTier;
        }

        @Override
        public ItemStack getCatalystStack() {
            return catalyst;
        }

        @Override
        public ItemStack getOutputStack() {
            return output;
        }

        @Override
        public void setCatalystStack(ItemStack stack) {
            catalyst = stack.copy();
        }

        @Override
        public void setOutputStack(ItemStack stack) {
            output = stack.copy();
        }

        @Override
        public List<IFusionInjector> getInjectors() {
            return List.of();
        }

        @Override
        public TechLevel getMinimumTier() {
            return minimumTier;
        }

        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? catalyst : ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 1;
        }
    }

    private record ConvertedFusionData(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements) {
    }

    private record CatalystRequirement(Ingredient ingredient, long count) {
    }

    public record DynamicPatternProfile(
            Map<Integer, ItemStack> canonicalInputs,
            Set<Integer> idOnlyInputSlots,
            Set<Integer> idOnlyOutputSlots) {
        public DynamicPatternProfile {
            Map<Integer, ItemStack> inputCopies = new LinkedHashMap<>();
            for (Map.Entry<Integer, ItemStack> entry : canonicalInputs.entrySet()) {
                if (entry.getKey() == null || entry.getKey() < 0
                        || entry.getValue() == null || entry.getValue().isEmpty()) {
                    throw new IllegalArgumentException("Canonical pattern inputs must be non-empty and non-negative");
                }
                inputCopies.put(entry.getKey(), entry.getValue().copyWithCount(1));
            }
            canonicalInputs = Collections.unmodifiableMap(inputCopies);
            idOnlyInputSlots = Set.copyOf(idOnlyInputSlots);
            idOnlyOutputSlots = Set.copyOf(idOnlyOutputSlots);
            if (idOnlyInputSlots.stream().anyMatch(slot -> slot == null || slot < 0)
                    || idOnlyOutputSlots.stream().anyMatch(slot -> slot == null || slot < 0)
                    || !idOnlyInputSlots.containsAll(canonicalInputs.keySet())) {
                throw new IllegalArgumentException("Dynamic pattern slots must be non-negative and consistent");
            }
        }
    }
}
