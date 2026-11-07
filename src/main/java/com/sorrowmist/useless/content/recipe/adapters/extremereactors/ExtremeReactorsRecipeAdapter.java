package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import it.zerono.mods.extremereactors.api.IMapping;
import it.zerono.mods.extremereactors.api.reactor.Reactant;
import it.zerono.mods.extremereactors.api.reactor.ReactantMappingsRegistry;
import it.zerono.mods.extremereactors.api.reactor.Reaction;
import it.zerono.mods.extremereactors.api.reactor.ReactionsRegistry;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerFluidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.IFluidizerRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.recipe.ReprocessorRecipe;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;
import it.zerono.mods.zerocore.lib.recipe.result.FluidStackRecipeResult;
import it.zerono.mods.zerocore.lib.recipe.result.ItemStackRecipeResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        // A first call can precede native IMC registration or tag binding. Never retain that
        // empty result (or old tag outputs) for the lifetime of the adapter/server process.
        return createGeneratedRecipes();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ModRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        ModRecipe source = holder.value();
        if (source instanceof ExtremeReactorsSyntheticRecipe synthetic) {
            return singleton(synthetic.convertedRecipe());
        }
        if (!isSupportedSource(source)) {
            reject(holder.id(), "Unsupported native recipe class (including dynamic subclasses): "
                    + source.getClass().getName());
            return List.of();
        }
        try {
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
        } catch (RuntimeException exception) {
            reject(holder.id(), exception.getClass().getSimpleName() + ": " + exception.getMessage());
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
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, mergedKeys, mold, List.of());
    }

    @Override
    public List<RecipeHolder<ModRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        // Normal manager calls already contain AE supplies. Empty maps also support direct
        // API callers with only actual stacks/AE keys, without adding those supplies twice.
        Map<Ingredient, Long> items = mergedInputs == null || mergedInputs.isEmpty()
                ? fallbackItems(actualInputs, mergedKeys) : mergedInputs;
        Map<FluidStack, Long> fluids = mergedFluids == null || mergedFluids.isEmpty()
                ? fallbackFluids(mergedKeys) : mergedFluids;
        List<RecipeHolder<ModRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> rawHolder : RecipeManagerAccess.getRecipes(level.getRecipeManager())) {
            if (!(rawHolder.value() instanceof ModRecipe source)
                    || source instanceof ExtremeReactorsSyntheticRecipe
                    || !isSupportedSource(source)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            RecipeHolder<ModRecipe> holder = (RecipeHolder<ModRecipe>) rawHolder;
            if (matchesConverted(holder, level, items, fluids, mold)) {
                matches.add(holder);
            }
        }
        for (RecipeHolder<ModRecipe> holder : getGeneratedRecipes(level)) {
            if (matchesConverted(holder, level, items, fluids, mold)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static Map<Ingredient, Long> fallbackItems(List<ItemStack> actualInputs, Map<AEKey, Long> keys) {
        Map<Ingredient, Long> result = AdapterUtils.mergeInputs(actualInputs);
        if (keys != null) {
            keys.forEach((key, amount) -> {
                if (key instanceof AEItemKey item && amount != null && amount > 0L) {
                    // Keep long amounts; do not expand them into int-sized stack counts.
                    result.merge(DataComponentIngredient.of(true, item.toStack(1)), amount,
                            (left, right) -> left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right);
                }
            });
        }
        return result;
    }

    private static Map<FluidStack, Long> fallbackFluids(Map<AEKey, Long> keys) {
        Map<FluidStack, Long> result = new LinkedHashMap<>();
        if (keys != null) {
            keys.forEach((key, amount) -> {
                if (key instanceof AEFluidKey fluid && amount != null && amount > 0L) {
                    result.put(fluid.toStack(1), amount);
                }
            });
        }
        return result;
    }

    private static boolean isSupportedSource(ModRecipe source) {
        Class<?> type = source.getClass();
        return type == FluidizerSolidRecipe.class
                || type == FluidizerSolidMixingRecipe.class
                || type == FluidizerFluidMixingRecipe.class
                || type == ReprocessorRecipe.class;
    }

    private boolean matchesConverted(
            RecipeHolder<ModRecipe> holder, Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        for (AdvancedAlloyFurnaceRecipe recipe : convertAll(holder, level)) {
            // The manager already merges physical stacks AND AE item/fluid keys into these
            // NBT-sensitive maps. Switching to actualInputs discards AE supplies, while adding
            // mergedKeys again double-counts them. Keep the authoritative long maps intact.
            boolean itemMatch = matchesMergedItems(recipe.inputs(), mergedInputs);
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
        CountedIngredient input = ExtremeReactorsIngredients.item(source.getIngredient());
        FluidStackRecipeResult result = source.getResult();
        FluidStack output = result == null ? FluidStack.EMPTY : result.getResult();
        requireOutput(output);
        return createRecipe(holder.id(), List.of(input), List.of(), List.of(), List.of(output.copy()),
                fluidizerMold(), fluidizerEnergy(source), fluidizerProcessTime(source));
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSolidMixingFluidizer(
            RecipeHolder<ModRecipe> holder, FluidizerSolidMixingRecipe source) {
        CountedIngredient first = ExtremeReactorsIngredients.item(source.getIngredient1());
        CountedIngredient second = ExtremeReactorsIngredients.item(source.getIngredient2());
        FluidStackRecipeResult result = source.getResult();
        FluidStack output = result == null ? FluidStack.EMPTY : result.getResult();
        requireOutput(output);
        return createRecipe(holder.id(), List.of(first, second), List.of(), List.of(), List.of(output.copy()),
                fluidizerMold(), fluidizerEnergy(source), fluidizerProcessTime(source));
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertFluidMixingFluidizer(
            RecipeHolder<ModRecipe> holder, FluidizerFluidMixingRecipe source) {
        SizedFluidIngredient first = ExtremeReactorsIngredients.fluid(source.getIngredient1());
        SizedFluidIngredient second = ExtremeReactorsIngredients.fluid(source.getIngredient2());
        FluidStackRecipeResult result = source.getResult();
        FluidStack output = result == null ? FluidStack.EMPTY : result.getResult();
        requireOutput(output);
        return createRecipe(holder.id(), List.of(), List.of(first, second), List.of(), List.of(output.copy()),
                fluidizerMold(), fluidizerEnergy(source), fluidizerProcessTime(source));
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertReprocessor(
            RecipeHolder<ModRecipe> holder, ReprocessorRecipe source) {
        CountedIngredient waste = ExtremeReactorsIngredients.item(source.getIngredient1());
        SizedFluidIngredient fluid = ExtremeReactorsIngredients.fluid(source.getIngredient2());
        ItemStackRecipeResult result = source.getResult();
        ItemStack output = result == null ? ItemStack.EMPTY : result.getResult();
        if (output == null || output.isEmpty()) throw new IllegalArgumentException("Empty native item output");

        long energy = (long) REPROCESSOR_ENERGY_PER_TICK * REPROCESSOR_REQUIRED_TICKS;
        int processTime = REPROCESSOR_TICK_INTERVAL * REPROCESSOR_REQUIRED_TICKS;
        return createRecipe(holder.id(), List.of(waste), List.of(fluid), List.of(output.copy()), List.of(),
                reprocessorMold(), energy, processTime);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation sourceId,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<FluidStack> outputFluids,
            Ingredient mold,
            long energy,
            int processTime) {
        if (mold == null || mold.isEmpty() || energy <= 0 || processTime <= 0) {
            throw new IllegalArgumentException("Missing machine mold or invalid native energy/time");
        }
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId), inputs, inputFluids, List.of(), outputs,
                outputFluids, List.of(), energy, processTime, Ingredient.EMPTY, 0,
                List.of(mold), AlloyFurnaceMode.NORMAL);
    }

    private static long fluidizerEnergy(IFluidizerRecipe source) {
        try {
            long perTick = Config.COMMON.fluidizer.energyPerRecipeTick.get();
            int multiplier = source.getEnergyUsageMultiplier();
            int ticks = source.getRecipeType().getTicks();
            if (perTick <= 0L || multiplier <= 0 || ticks <= 0) return -1L;
            return Math.multiplyExact(Math.multiplyExact(perTick, multiplier), ticks);
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    private static int fluidizerProcessTime(IFluidizerRecipe source) {
        long processTime = (long) source.getRecipeType().getTicks() * FLUIDIZER_TICK_INTERVAL;
        return processTime > 0 && processTime <= Integer.MAX_VALUE ? (int) processTime : -1;
    }

    private static void requireOutput(@Nullable FluidStack output) {
        if (output == null || output.isEmpty()) throw new IllegalArgumentException("Empty native fluid output");
    }

    private static void reject(ResourceLocation id, String reason) {
        RateLimitedLogger.warn(LOGGER, "Skipping unrepresentable Extreme Reactors recipe {}: {}", id, reason);
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
        return new ResourceLocation(MOD_ID, path);
    }

    private List<RecipeHolder<ModRecipe>> createGeneratedRecipes() {
        // ReactionsRegistry 底层是 HashMap，getReactions() 的迭代顺序由 Reactant 的哈希决定；
        // 而 Reactant.hashCode() 含 ReactantType 枚举，枚举沿用 Object 的身份哈希，跨 JVM 重启
        // 并不保证一致。若把该顺序编进配方 id，重启后同一个反应会拿到不同的 id，玩家已编码的
        // 万象样板就会因 id 失配而解析失败。这里先按反应自身的名字排序，让下面的 reactionIndex
        // 只由数据内容决定，从而跨会话稳定。
        List<Reaction> reactions = new ArrayList<>(ReactionsRegistry.getReactions());
        reactions.sort(Comparator.comparing(ExtremeReactorsRecipeAdapter::reactionKey));

        List<RecipeHolder<ModRecipe>> result = new ArrayList<>();
        int reactionIndex = 0;
        for (Reaction reaction : reactions) {
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
                    ResourceLocation recipeId = new ResourceLocation(
                            MOD_ID, "reactor/" + slug(fuel.getName()) + "_to_"
                                    + slug(waste.getName()) + "/" + inputIndex + "_" + outputIndex
                                    + "_" + reactionIndex);
                    try {
                        AdvancedAlloyFurnaceRecipe converted = convertReaction(
                                recipeId, reaction, inputs.get(inputIndex), outputs.get(outputIndex));
                        result.add(new RecipeHolder<>(recipeId,
                                new ExtremeReactorsSyntheticRecipe(converted)));
                    } catch (RuntimeException exception) {
                        reject(recipeId, exception.getClass().getSimpleName() + ": " + exception.getMessage());
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
        // 映射列表按数据包注册顺序追加，同一集合在不同会话中的先后并不保证一致；而配方 id 里
        // 编入了该列表的下标，所以必须先按 tag 位置排序，下标才是稳定的。
        result.sort(Comparator.comparing(PhysicalMapping::sortKey)
                .thenComparingInt(value -> value.mapping() == null ? 0 : value.mapping().getSourceAmount())
                .thenComparingInt(value -> value.mapping() == null ? 0 : value.mapping().getProductAmount()));
        return result;
    }

    /** Reaction order depends on reactant names, never enum identity hashes. */
    private static String reactionKey(Reaction reaction) {
        Reactant fuel = reaction == null ? null : reaction.getSource();
        Reactant waste = reaction == null ? null : reaction.getProduct();
        return (fuel == null ? "" : fuel.getName()) + "->" + (waste == null ? "" : waste.getName());
    }

    private static AdvancedAlloyFurnaceRecipe convertReaction(
            ResourceLocation recipeId, Reaction reaction,
            PhysicalMapping input, PhysicalMapping output) {
        if (reaction.getClass() != Reaction.class
                || reaction.getSourceAmount() != 1 || reaction.getProductAmount() != 1
                || !Float.isFinite(reaction.getReactivity()) || reaction.getReactivity() <= 0.0F
                || !Float.isFinite(reaction.getFissionRate()) || reaction.getFissionRate() <= 0.0F) {
            throw new IllegalArgumentException("Unknown or inactive native reactor reaction");
        }
        if (!reaction.getSource().equals(input.mapping().getSource())
                || !reaction.getProduct().equals(output.mapping().getSource())) {
            throw new IllegalArgumentException("Physical mapping refers to a different reactant");
        }
        Batch batch = calculateBatch(input, output);
        validateFuelMapping(reaction.getSource(), input, batch);

        List<CountedIngredient> itemInputs = new ArrayList<>();
        List<SizedFluidIngredient> fluidInputs = new ArrayList<>();
        if (input.fluid()) {
            TagKey<Fluid> tag = fluidTag(input);
            if (tag == null || !tag.isFor(Registries.FLUID)) {
                throw new IllegalArgumentException("Invalid native fuel fluid tag");
            }
            fluidInputs.add(new SizedFluidIngredient(FluidIngredient.tag(tag), batch.inputAmount()));
        } else {
            TagKey<Item> tag = itemTag(input);
            if (tag == null || !tag.isFor(Registries.ITEM)) {
                throw new IllegalArgumentException("Invalid native fuel item tag");
            }
            itemInputs.add(new CountedIngredient(Ingredient.of(tag), batch.inputAmount()));
        }

        List<ItemStack> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        if (output.fluid()) {
            FluidStack stack = ReactantMappingsRegistry.getFluidStackFrom(fluidMapping(output), batch.outputAmount());
            if (stack == null || stack.isEmpty() || stack.getAmount() != batch.outputAmount()) {
                throw new IllegalArgumentException("Unresolved native waste fluid output");
            }
            fluidOutputs.add(stack.copy());
        } else {
            ItemStack stack = ReactantMappingsRegistry.getSolidStackFrom(itemMapping(output), batch.outputAmount());
            if (stack == null || stack.isEmpty() || stack.getCount() != batch.outputAmount()) {
                throw new IllegalArgumentException("Unresolved native waste item output");
            }
            itemOutputs.add(stack.copy());
        }

        Ingredient mold = reactorMold();
        if (mold.isEmpty()) throw new IllegalArgumentException("Missing native reactor controller mold");
        return new AdvancedAlloyFurnaceRecipe(
                recipeId, itemInputs, fluidInputs, List.of(), itemOutputs, fluidOutputs, List.of(),
                AdapterUtils.DEFAULT_ENERGY, AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, List.of(mold), AlloyFurnaceMode.NORMAL);
    }

    private static Batch calculateBatch(PhysicalMapping input, PhysicalMapping output) {
        IMapping<?, ?> from = input.mapping();
        IMapping<?, ?> to = output.mapping();
        if (!nativeMapping(from) || !nativeMapping(to)) {
            throw new IllegalArgumentException("Unknown or nonpositive native physical mapping");
        }
        // ER 2.0.84 Reaction is one reactant unit -> one waste unit. ManyToMany divides
        // BEFORE multiplying, so choose a whole batch instead of rounding down its yield.
        long units = lcm(from.getSourceAmount(), to.getSourceAmount());
        if (units <= 0L || units > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Reactant batch exceeds the native int mapping API");
        }
        int inputAmount = Math.toIntExact(Math.multiplyExact(
                units / from.getSourceAmount(), from.getProductAmount()));
        int outputAmount = Math.toIntExact(Math.multiplyExact(
                units / to.getSourceAmount(), to.getProductAmount()));
        if (inputAmount <= 0 || outputAmount <= 0
                || from.getProductAmount((int) units) != inputAmount
                || to.getProductAmount((int) units) != outputAmount) {
            throw new IllegalArgumentException("Native physical mapping yield is not an exact positive batch");
        }
        return new Batch(inputAmount, outputAmount, (int) units);
    }

    private static boolean nativeMapping(@Nullable IMapping<?, ?> mapping) {
        return mapping != null && mapping.getSourceAmount() > 0 && mapping.getProductAmount() > 0
                && (mapping.getClass() == IMapping.ManyToMany.class
                || mapping.getClass() == IMapping.OneToOne.class
                && mapping.getSourceAmount() == mapping.getProductAmount());
    }

    private static void validateFuelMapping(Reactant fuel, PhysicalMapping input, Batch batch) {
        boolean resolved = false;
        if (input.fluid()) {
            TagKey<Fluid> tag = fluidTag(input);
            if (tag == null) throw new IllegalArgumentException("Missing fuel fluid tag");
            for (var member : BuiltInRegistries.FLUID.getTagOrEmpty(tag)) {
                resolved = true;
                validateForwardMapping(fuel, ReactantMappingsRegistry.getFromFluid(
                        new FluidStack(member.value(), 1)).orElse(null), batch);
            }
        } else {
            TagKey<Item> tag = itemTag(input);
            if (tag == null) throw new IllegalArgumentException("Missing fuel item tag");
            for (var member : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                resolved = true;
                validateForwardMapping(fuel, ReactantMappingsRegistry.getFromSolid(
                        member.value().getDefaultInstance()).orElse(null), batch);
            }
        }
        if (!resolved) throw new IllegalArgumentException("Fuel tag is not bound yet; retry after native tag loading");
    }

    private static void validateForwardMapping(Reactant fuel, @Nullable IMapping<?, Reactant> mapping, Batch batch) {
        // Overlapping tags (or re-registration) can make the native lookup select a different
        // reactant/rate than this inverse mapping. Never turn that stale inverse into free waste.
        if (!nativeMapping(mapping) || !fuel.equals(mapping.getProduct())
                || mapping.getProductAmount(batch.inputAmount()) != batch.reactantUnits()) {
            throw new IllegalArgumentException("Fuel tag has an ambiguous or stale native reactant mapping");
        }
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
                result.append(character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                        || character == '_' || character == '-' || character == '.' ? character : '_');
            }
        }
        return result.isEmpty() ? "unknown" : result.toString();
    }

    private static <T> List<T> singleton(@Nullable T value) {
        return value == null ? List.of() : List.of(value);
    }

    private record PhysicalMapping(boolean fluid, IMapping<?, ?> mapping) {
        /** 排序键：只依赖固/液类型与 tag 位置，与数据包注册顺序无关。 */
        private String sortKey() {
            Object product = mapping == null ? null : mapping.getProduct();
            String location = product instanceof TagKey<?> tag ? tag.location().toString() : "";
            return (fluid ? "1" : "0") + location;
        }
    }

    private record Batch(int inputAmount, int outputAmount, int reactantUnits) {
    }
}
