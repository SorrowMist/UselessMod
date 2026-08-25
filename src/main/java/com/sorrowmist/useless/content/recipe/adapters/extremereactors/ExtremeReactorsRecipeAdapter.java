package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import it.zerono.mods.extremereactors.api.IMapping;
import it.zerono.mods.extremereactors.api.reactor.Reactant;
import it.zerono.mods.extremereactors.api.reactor.ReactantMappingsRegistry;
import it.zerono.mods.extremereactors.api.reactor.ReactantType;
import it.zerono.mods.extremereactors.api.reactor.Reaction;
import it.zerono.mods.extremereactors.api.reactor.ReactionsRegistry;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerFluidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.IFluidizerRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.recipe.ReprocessorRecipe;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;
import it.zerono.mods.zerocore.lib.recipe.ingredient.FluidStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.ingredient.ItemStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.result.FluidStackRecipeResult;
import it.zerono.mods.zerocore.lib.recipe.result.ItemStackRecipeResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Extreme Reactors recipes and fuel reactions to alloy-furnace recipes. */
public final class ExtremeReactorsRecipeAdapter implements IRecipeAdapter<ModRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "bigreactors";
    private static final int FLUIDIZER_TICK_INTERVAL = 10;
    private static final int REPROCESSOR_TICK_INTERVAL = 5;
    private static final int REPROCESSOR_REQUIRED_TICKS = 40;
    private static final int REPROCESSOR_ENERGY_PER_TICK = 25;
    private static final List<String> MOLD_IDS = List.of(
            "fluidizercontroller",
            "reprocessorcontroller",
            "basic_reactorcontroller",
            "reinforced_reactorcontroller");
    private volatile List<RecipeHolder<ModRecipe>> generatedRecipes;

    @Override
    public String sourceId() {
        return RecipeSourceIds.BIG_REACTORS;
    }

    @Override
    public Class<ModRecipe> getRecipeClass() {
        return ModRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return false;
        for (String moldId : MOLD_IDS) {
            Item item = registeredItem(moldId);
            if (item != null && mold.is(item)) return true;
        }
        return false;
    }

    @Override
    public List<RecipeHolder<ModRecipe>> getGeneratedRecipes(Level level) {
        List<RecipeHolder<ModRecipe>> cached = generatedRecipes;
        if (cached != null) return cached;
        synchronized (this) {
            if (generatedRecipes == null) {
                generatedRecipes = createGeneratedRecipes();
            }
            return generatedRecipes;
        }
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ModRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        ModRecipe source = holder.value();
        if (source instanceof ExtremeReactorsSyntheticRecipe synthetic) {
            return synthetic.convertedRecipe() == null
                    ? List.of() : List.of(synthetic.convertedRecipe());
        }
        if (source instanceof FluidizerSolidRecipe recipe) {
            return singleton(convertSolidFluidizer(holder, recipe));
        }
        if (source instanceof FluidizerSolidMixingRecipe recipe) {
            return singleton(convertSolidMixingFluidizer(holder, recipe));
        }
        if (source instanceof FluidizerFluidMixingRecipe recipe) {
            return singleton(convertFluidMixingFluidizer(holder, recipe));
        }
        if (source instanceof ReprocessorRecipe recipe) {
            return singleton(convertReprocessor(holder, recipe));
        }
        return List.of();
    }

    @Override
    public List<RecipeHolder<ModRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<ModRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<ModRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> rawHolder : level.getRecipeManager().getRecipes()) {
            if (!(rawHolder.value() instanceof ModRecipe source)
                    || source instanceof ExtremeReactorsSyntheticRecipe
                    || !isSupportedSource(source)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            RecipeHolder<ModRecipe> holder = (RecipeHolder<ModRecipe>) rawHolder;
            if (matchesConverted(holder, level, mergedInputs, mergedFluids, mold, actualInputs)) {
                matches.add(holder);
            }
        }
        for (RecipeHolder<ModRecipe> holder : getGeneratedRecipes(level)) {
            if (matchesConverted(holder, level, mergedInputs, mergedFluids, mold, actualInputs)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean isSupportedSource(ModRecipe source) {
        return source instanceof FluidizerSolidRecipe
                || source instanceof FluidizerSolidMixingRecipe
                || source instanceof FluidizerFluidMixingRecipe
                || source instanceof ReprocessorRecipe;
    }

    private boolean matchesConverted(
            RecipeHolder<ModRecipe> holder, Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
            boolean itemMatch = actualInputs != null && !actualInputs.isEmpty()
                    ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L)
                    : matchesMergedItems(recipe.inputs(), mergedInputs);
            if (itemMatch
                    && FluidIngredientAllocator.matchesLong(recipe.inputFluids(), mergedFluids, 1L)
                    && recipe.molds().size() == 1
                    && AdapterUtils.matchesMold(recipe.mold(), mold)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMergedItems(
            List<CountedIngredient> requirements, Map<Ingredient, Long> mergedInputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient requirement : requirements) {
            AdapterUtils.mergeIngredient(required, requirement.ingredient(), requirement.count());
        }
        return ItemIngredientAllocator.matches(
                mergedInputs == null ? Map.of() : mergedInputs, required);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSolidFluidizer(
            RecipeHolder<ModRecipe> holder, FluidizerSolidRecipe source) {
        CountedIngredient input = itemInput(source.getIngredient());
        FluidStackRecipeResult result = source.getResult();
        FluidStack output = result == null ? FluidStack.EMPTY : result.getResult();
        if (input == null || output == null || output.isEmpty()) return null;
        return createRecipe(holder.id(), List.of(input), List.of(), List.of(), List.of(output),
                source, fluidizerMold(), fluidizerEnergy(source), fluidizerProcessTime(source));
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSolidMixingFluidizer(
            RecipeHolder<ModRecipe> holder, FluidizerSolidMixingRecipe source) {
        CountedIngredient first = itemInput(source.getIngredient1());
        CountedIngredient second = itemInput(source.getIngredient2());
        FluidStackRecipeResult result = source.getResult();
        FluidStack output = result == null ? FluidStack.EMPTY : result.getResult();
        if (first == null || second == null || output == null || output.isEmpty()) return null;
        return createRecipe(holder.id(), List.of(first, second), List.of(), List.of(), List.of(output),
                source, fluidizerMold(), fluidizerEnergy(source), fluidizerProcessTime(source));
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertFluidMixingFluidizer(
            RecipeHolder<ModRecipe> holder, FluidizerFluidMixingRecipe source) {
        SizedFluidIngredient first = fluidInput(source.getIngredient1());
        SizedFluidIngredient second = fluidInput(source.getIngredient2());
        FluidStackRecipeResult result = source.getResult();
        FluidStack output = result == null ? FluidStack.EMPTY : result.getResult();
        if (first == null || second == null || output == null || output.isEmpty()) return null;
        return createRecipe(holder.id(), List.of(), List.of(first, second), List.of(), List.of(output),
                source, fluidizerMold(), fluidizerEnergy(source), fluidizerProcessTime(source));
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertReprocessor(
            RecipeHolder<ModRecipe> holder, ReprocessorRecipe source) {
        CountedIngredient waste = itemInput(source.getIngredient1());
        SizedFluidIngredient fluid = fluidInput(source.getIngredient2());
        ItemStackRecipeResult result = source.getResult();
        ItemStack output = result == null ? ItemStack.EMPTY : result.getResult();
        if (waste == null || fluid == null || output == null || output.isEmpty()) return null;

        long energy = (long) REPROCESSOR_ENERGY_PER_TICK * REPROCESSOR_REQUIRED_TICKS;
        int processTime = REPROCESSOR_TICK_INTERVAL * REPROCESSOR_REQUIRED_TICKS;
        return createRecipe(holder.id(), List.of(waste), List.of(fluid), List.of(output), List.of(),
                null, reprocessorMold(), energy, processTime);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation sourceId,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<FluidStack> outputFluids,
            @Nullable IFluidizerRecipe fluidizerSource,
            Ingredient mold,
            long energy,
            int processTime) {
        if (mold == null || mold.isEmpty() || energy < 0 || processTime <= 0) return null;
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId), inputs, inputFluids, List.of(), outputs,
                outputFluids, List.of(), energy, processTime, Ingredient.EMPTY, 0,
                List.of(mold), AlloyFurnaceMode.NORMAL);
    }

    private static long fluidizerEnergy(IFluidizerRecipe source) {
        try {
            long perTick = Config.COMMON.fluidizer.energyPerRecipeTick.get();
            return Math.multiplyExact(
                    Math.multiplyExact(perTick, source.getEnergyUsageMultiplier()),
                    source.getRecipeType().getTicks());
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    private static int fluidizerProcessTime(IFluidizerRecipe source) {
        long processTime = (long) source.getRecipeType().getTicks() * FLUIDIZER_TICK_INTERVAL;
        return processTime > 0 && processTime <= Integer.MAX_VALUE ? (int) processTime : -1;
    }

    @Nullable
    private static CountedIngredient itemInput(ItemStackRecipeIngredient source) {
        if (source == null || source.isEmpty() || source.asVanillaIngredients().isEmpty()) return null;
        List<ItemStack> matching = source.getMatchingElements();
        if (matching.isEmpty()) return null;
        long amount = matching.getFirst().getCount();
        return amount > 0 ? new CountedIngredient(source.asVanillaIngredients().getFirst(), amount) : null;
    }

    @Nullable
    private static SizedFluidIngredient fluidInput(FluidStackRecipeIngredient source) {
        if (source == null || source.isEmpty()) return null;
        List<FluidStack> matching = source.getMatchingElements();
        return matching.isEmpty() ? null : AdapterUtils.toSizedFluidIngredient(matching.getFirst());
    }

    private static Ingredient fluidizerMold() {
        return mold("fluidizercontroller");
    }

    private static Ingredient reprocessorMold() {
        return mold("reprocessorcontroller");
    }

    private static Ingredient reactorMold() {
        return mold("basic_reactorcontroller", "reinforced_reactorcontroller");
    }

    private static Ingredient mold(String... ids) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String id : ids) {
            Item item = registeredItem(id);
            if (item != null) stacks.add(item.getDefaultInstance());
        }
        return stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.of(stacks.toArray(ItemStack[]::new));
    }

    @Nullable
    private static Item registeredItem(String path) {
        return BuiltInRegistries.ITEM.getOptional(id(path)).orElse(null);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private List<RecipeHolder<ModRecipe>> createGeneratedRecipes() {
        List<RecipeHolder<ModRecipe>> result = new ArrayList<>();
        int reactionIndex = 0;
        for (Reaction reaction : ReactionsRegistry.getReactions()) {
            Reactant fuel = reaction.getSource();
            Reactant waste = reaction.getProduct();
            if (fuel == null || waste == null
                    || !fuel.getType().isFuel() || !waste.getType().isWaste()) {
                continue;
            }

            List<PhysicalMapping> inputs = physicalMappings(fuel);
            List<PhysicalMapping> outputs = physicalMappings(waste);
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                    ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                            MOD_ID, "reactor/" + slug(fuel.getName()) + "_to_"
                                    + slug(waste.getName()) + "/" + inputIndex + "_" + outputIndex
                                    + "_" + reactionIndex);
                    AdvancedAlloyFurnaceRecipe converted = convertReaction(
                            recipeId, reaction, inputs.get(inputIndex), outputs.get(outputIndex));
                    if (converted != null) {
                        result.add(new RecipeHolder<>(recipeId,
                                new ExtremeReactorsSyntheticRecipe(converted)));
                    }
                }
            }
            reactionIndex++;
        }
        return List.copyOf(result);
    }

    private static List<PhysicalMapping> physicalMappings(Reactant reactant) {
        List<PhysicalMapping> result = new ArrayList<>();
        ReactantMappingsRegistry.getToSolid(reactant)
                .ifPresent(mappings -> mappings.forEach(mapping -> result.add(new PhysicalMapping(false, mapping))));
        ReactantMappingsRegistry.getToFluid(reactant)
                .ifPresent(mappings -> mappings.forEach(mapping -> result.add(new PhysicalMapping(true, mapping))));
        return result;
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertReaction(
            ResourceLocation recipeId, Reaction reaction,
            PhysicalMapping input, PhysicalMapping output) {
        Batch batch = calculateBatch(reaction, input, output);
        if (batch == null) return null;

        List<CountedIngredient> itemInputs = new ArrayList<>();
        List<SizedFluidIngredient> fluidInputs = new ArrayList<>();
        if (input.fluid()) {
            TagKey<Fluid> tag = fluidTag(input);
            if (tag == null) return null;
            fluidInputs.add(new SizedFluidIngredient(FluidIngredient.tag(tag), batch.inputAmount()));
        } else {
            TagKey<Item> tag = itemTag(input);
            if (tag == null) return null;
            itemInputs.add(new CountedIngredient(Ingredient.of(tag), batch.inputAmount()));
        }

        List<ItemStack> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        if (output.fluid()) {
            FluidStack stack = ReactantMappingsRegistry.getFluidStackFrom(fluidMapping(output), batch.outputAmount());
            if (stack == null || stack.isEmpty()) return null;
            fluidOutputs.add(stack);
        } else {
            ItemStack stack = ReactantMappingsRegistry.getSolidStackFrom(itemMapping(output), batch.outputAmount());
            if (stack == null || stack.isEmpty()) return null;
            itemOutputs.add(stack);
        }

        Ingredient mold = reactorMold();
        if (mold.isEmpty()) return null;
        return new AdvancedAlloyFurnaceRecipe(
                recipeId, itemInputs, fluidInputs, List.of(), itemOutputs, fluidOutputs, List.of(),
                AdapterUtils.DEFAULT_ENERGY, AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, List.of(mold), AlloyFurnaceMode.NORMAL);
    }

    @Nullable
    private static Batch calculateBatch(Reaction reaction, PhysicalMapping input, PhysicalMapping output) {
        int reactionSource = reaction.getSourceAmount();
        int inputSource = input.mapping().getSourceAmount();
        int outputSource = output.mapping().getSourceAmount();
        if (reactionSource <= 0 || inputSource <= 0 || outputSource <= 0) return null;

        long batch = lcm(reactionSource, inputSource);
        for (int attempt = 0; attempt < 4; attempt++) {
            if (batch <= 0 || batch > Integer.MAX_VALUE) return null;
            int wasteUnits = reaction.getProductAmount((int) batch);
            if (wasteUnits > 0 && wasteUnits % outputSource == 0) {
                int inputAmount = input.mapping().getProductAmount((int) batch);
                int outputAmount = output.mapping().getProductAmount(wasteUnits);
                if (inputAmount > 0 && outputAmount > 0) {
                    return new Batch(inputAmount, outputAmount);
                }
            }
            long next = lcm(batch, outputSource);
            if (next == batch) return null;
            batch = next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static IMapping<Reactant, TagKey<Item>> itemMapping(PhysicalMapping mapping) {
        return (IMapping<Reactant, TagKey<Item>>) (IMapping<?, ?>) mapping.mapping();
    }

    @SuppressWarnings("unchecked")
    private static IMapping<Reactant, TagKey<Fluid>> fluidMapping(PhysicalMapping mapping) {
        return (IMapping<Reactant, TagKey<Fluid>>) (IMapping<?, ?>) mapping.mapping();
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Item> itemTag(PhysicalMapping mapping) {
        return (TagKey<Item>) mapping.mapping().getProduct();
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Fluid> fluidTag(PhysicalMapping mapping) {
        return (TagKey<Fluid>) mapping.mapping().getProduct();
    }

    private static long lcm(long left, long right) {
        if (left <= 0 || right <= 0) return -1L;
        long gcd = gcd(left, right);
        if (left / gcd > Long.MAX_VALUE / right) return -1L;
        return left / gcd * right;
    }

    private static long gcd(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return Math.abs(left);
    }

    private static String slug(String value) {
        StringBuilder result = new StringBuilder(value == null ? 7 : value.length());
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char character = Character.toLowerCase(value.charAt(i));
                result.append(Character.isLetterOrDigit(character)
                        || character == '_' || character == '-' || character == '.' ? character : '_');
            }
        }
        return result.isEmpty() ? "unknown" : result.toString();
    }

    private static <T> List<T> singleton(@Nullable T value) {
        return value == null ? List.of() : List.of(value);
    }

    private record PhysicalMapping(boolean fluid, IMapping<?, ?> mapping) {
    }

    private record Batch(int inputAmount, int outputAmount) {
    }
}
