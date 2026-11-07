package com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import me.desht.pneumaticcraft.api.crafting.AmadronTradeResource;
import me.desht.pneumaticcraft.api.crafting.ingredient.FluidIngredient;
import me.desht.pneumaticcraft.api.crafting.ingredient.StackedIngredient;
import me.desht.pneumaticcraft.api.crafting.recipe.AmadronRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.AssemblyRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.FluidMixerRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.HeatFrameCoolingRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.PneumaticCraftRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.PressureChamberRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.RefineryRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import me.desht.pneumaticcraft.common.core.ModBlocks;
import me.desht.pneumaticcraft.common.core.ModItems;
import me.desht.pneumaticcraft.common.core.ModRecipeTypes;
import me.desht.pneumaticcraft.common.recipes.PneumaticCraftRecipeType;
import me.desht.pneumaticcraft.common.recipes.machine.PressureChamberRecipeImpl;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Converts PneumaticCraft's static machine recipes into alloy-furnace recipes. */
public final class PneumaticCraftRecipeAdapter<T extends PneumaticCraftRecipe>
        implements IRecipeAdapter<T> {
    enum Kind {
        ASSEMBLY,
        PRESSURE_CHAMBER,
        FLUID_MIXER,
        HEAT_FRAME_COOLING,
        REFINERY,
        THERMO_PLANT,
        AMADRON
    }

    private final Class<T> recipeClass;
    private final Kind kind;
    private final Function<Level, Collection<RecipeHolder<T>>> recipeProvider;
    private final ItemStack moldItem;

    PneumaticCraftRecipeAdapter(
            Class<T> recipeClass,
            Kind kind,
            Function<Level, Collection<RecipeHolder<T>>> recipeProvider,
            ItemStack moldItem) {
        this.recipeClass = recipeClass;
        this.kind = kind;
        this.recipeProvider = recipeProvider;
        this.moldItem = moldItem;
    }

    public static PneumaticCraftRecipeAdapter<AssemblyRecipe> assembly() {
        return new PneumaticCraftRecipeAdapter<>(
                AssemblyRecipe.class, Kind.ASSEMBLY,
                PneumaticCraftRecipeAdapter::assemblyRecipes,
                new ItemStack(ModBlocks.ASSEMBLY_PLATFORM.get()));
    }

    public static PneumaticCraftRecipeAdapter<FluidMixerRecipe> fluidMixer() {
        return fixed(FluidMixerRecipe.class, Kind.FLUID_MIXER,
                level -> recipes(level, ModRecipeTypes.FLUID_MIXER),
                new ItemStack(ModBlocks.FLUID_MIXER.get()));
    }

    public static PneumaticCraftRecipeAdapter<HeatFrameCoolingRecipe> heatFrameCooling() {
        return fixed(HeatFrameCoolingRecipe.class, Kind.HEAT_FRAME_COOLING,
                level -> recipes(level, ModRecipeTypes.HEAT_FRAME_COOLING),
                new ItemStack(ModItems.HEAT_FRAME.get()));
    }

    public static PneumaticCraftRecipeAdapter<PressureChamberRecipe> pressureChamber() {
        return fixed(PressureChamberRecipe.class, Kind.PRESSURE_CHAMBER,
                level -> recipes(level, ModRecipeTypes.PRESSURE_CHAMBER),
                new ItemStack(ModBlocks.PRESSURE_CHAMBER_VALVE.get()));
    }

    public static PneumaticCraftRecipeAdapter<RefineryRecipe> refinery() {
        return fixed(RefineryRecipe.class, Kind.REFINERY,
                level -> recipes(level, ModRecipeTypes.REFINERY),
                new ItemStack(ModBlocks.REFINERY.get()));
    }

    public static PneumaticCraftRecipeAdapter<ThermoPlantRecipe> thermoPlant() {
        return fixed(ThermoPlantRecipe.class, Kind.THERMO_PLANT,
                level -> recipes(level, ModRecipeTypes.THERMO_PLANT),
                new ItemStack(ModBlocks.THERMOPNEUMATIC_PROCESSING_PLANT.get()));
    }

    public static PneumaticCraftRecipeAdapter<AmadronRecipe> amadron() {
        return fixed(AmadronRecipe.class, Kind.AMADRON,
                level -> recipes(level, ModRecipeTypes.AMADRON),
                new ItemStack(ModItems.AMADRON_TABLET.get()));
    }

    private static <T extends PneumaticCraftRecipe> PneumaticCraftRecipeAdapter<T> fixed(
            Class<T> recipeClass,
            Kind kind,
            Function<Level, Collection<RecipeHolder<T>>> recipeProvider,
            ItemStack moldItem) {
        return new PneumaticCraftRecipeAdapter<>(recipeClass, kind, recipeProvider, moldItem);
    }

    private static Collection<RecipeHolder<AssemblyRecipe>> assemblyRecipes(Level level) {
        List<RecipeHolder<AssemblyRecipe>> result = new ArrayList<>();
        result.addAll(recipes(level, ModRecipeTypes.ASSEMBLY_DRILL));
        result.addAll(recipes(level, ModRecipeTypes.ASSEMBLY_LASER));
        result.addAll(recipes(level, ModRecipeTypes.ASSEMBLY_DRILL_LASER));
        return result;
    }

    private static <R extends PneumaticCraftRecipe> List<RecipeHolder<R>> recipes(
            Level level, Supplier<PneumaticCraftRecipeType<R>> type) {
        return ModRecipeTypes.getRecipes(level, type).stream().map(RecipeHolder::of).toList();
    }

    @Override
    public String sourceId() {
        return RecipeSourceIds.PNEUMATICCRAFT;
    }

    @Override
    public Class<T> getRecipeClass() {
        return recipeClass;
    }

    @Override
    public ItemStack getMoldItem() {
        return moldItem.copy();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<T>> getGeneratedRecipes(Level level) {
        if (level == null || kind != Kind.ASSEMBLY) return List.of();

        Collection<RecipeHolder<AssemblyRecipe>> generated =
                recipes(level, ModRecipeTypes.ASSEMBLY_DRILL_LASER);
        return (List<RecipeHolder<T>>) (List<?>) List.copyOf(generated);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<T> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        AdvancedAlloyFurnaceRecipe converted = switch (kind) {
            case ASSEMBLY -> convertAssembly(holder.id(), (AssemblyRecipe) holder.value());
            case PRESSURE_CHAMBER -> convertPressureChamber(
                    holder.id(), (PressureChamberRecipe) holder.value());
            case FLUID_MIXER -> convertFluidMixer(holder.id(), (FluidMixerRecipe) holder.value());
            case HEAT_FRAME_COOLING -> convertHeatFrameCooling(
                    holder.id(), (HeatFrameCoolingRecipe) holder.value());
            case REFINERY -> convertRefinery(holder.id(), (RefineryRecipe) holder.value());
            case THERMO_PLANT -> convertThermoPlant(holder.id(), (ThermoPlantRecipe) holder.value());
            case AMADRON -> convertAmadron(holder.id(), (AmadronRecipe) holder.value());
        };
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<T>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<T>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        Map<Ingredient, Long> safeInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> safeFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<T>> matches = new ArrayList<>();
        for (RecipeHolder<T> holder : recipeProvider.apply(level)) {
            boolean matched = com.sorrowmist.useless.content.recipe.AdapterGuards.callEach(
                    this, "matchRecipe", () -> holder.id().toString(), () -> {
                        if (kind == Kind.HEAT_FRAME_COOLING
                                && !matchesHeatFrameInput((HeatFrameCoolingRecipe) holder.value(), actualInputs)) {
                            return false;
                        }
                        return convertAll(holder, level).stream()
                                .anyMatch(recipe -> matchesConverted(recipe, safeInputs, safeFluids));
                    }, false);
            if (matched) matches.add(holder);
        }
        return matches;
    }

    static boolean matchesConverted(
            AdvancedAlloyFurnaceRecipe recipe,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids) {
        Map<Ingredient, Long> requiredInputs = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(requiredInputs, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, requiredInputs)
                && FluidIngredientAllocator.matchesLong(recipe.inputFluids(), mergedFluids, 1L);
    }

    private boolean matchesHeatFrameInput(
            HeatFrameCoolingRecipe recipe, List<ItemStack> actualInputs) {
        if (actualInputs == null || actualInputs.isEmpty()) return true;
        Ingredient input = recipe.getInput();
        return input != null && actualInputs.stream().anyMatch(input::test);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertAssembly(
            ResourceLocation id, AssemblyRecipe source) {
        if (source.getInput() == null || source.getInput().isEmpty()
                || source.getInputAmount() <= 0 || source.getOutput().isEmpty()) {
            return null;
        }

        ItemStack program = switch (source.getProgramType()) {
            case DRILL -> new ItemStack(ModItems.ASSEMBLY_PROGRAM_DRILL.get());
            case LASER -> new ItemStack(ModItems.ASSEMBLY_PROGRAM_LASER.get());
            case DRILL_LASER -> new ItemStack(ModItems.ASSEMBLY_PROGRAM_DRILL_LASER.get());
        };
        return build(id,
                List.of(new CountedIngredient(itemPredicate(source.getInput()), source.getInputAmount())),
                List.of(),
                List.of(source.getOutput().copy()),
                List.of(Ingredient.of(getMoldItem()), Ingredient.of(program)),
                AdapterUtils.DEFAULT_PROCESS_TIME);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertPressureChamber(
            ResourceLocation id, PressureChamberRecipe source) {
        // Enchanting/disenchanting and third-party subclasses compute their outputs from inventory.
        if (source.getClass() != PressureChamberRecipeImpl.class) return null;
        List<CountedIngredient> inputs = countedInputs(source.getInputsForDisplay());
        List<ItemStack> outputs = new ArrayList<>();
        for (List<ItemStack> slot : source.getResultsForDisplay()) {
            if (slot.size() != 1) return null; // display alternatives are not simultaneous outputs
            outputs.add(slot.get(0).copy());
        }
        if (inputs.isEmpty() || outputs.isEmpty()) return null;
        return build(id, inputs, List.of(), outputs,
                List.of(Ingredient.of(getMoldItem())), AdapterUtils.DEFAULT_PROCESS_TIME);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertFluidMixer(
            ResourceLocation id, FluidMixerRecipe source) {
        List<SizedFluidIngredient> inputs = sizedFluids(source.getInput1(), source.getInput2());
        List<ItemStack> outputs = source.getOutputItem().isEmpty()
                ? List.of() : List.of(source.getOutputItem().copy());
        List<FluidStack> outputFluids = source.getOutputFluid().isEmpty()
                ? List.of() : List.of(source.getOutputFluid().copy());
        if (inputs.isEmpty() || outputs.isEmpty() && outputFluids.isEmpty()) return null;
        int time = source.getProcessingTime() > 0
                ? source.getProcessingTime() : AdapterUtils.DEFAULT_PROCESS_TIME;
        return build(id, List.of(), inputs, outputs,
                List.of(Ingredient.of(getMoldItem())), time, outputFluids);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertHeatFrameCooling(
            ResourceLocation id, HeatFrameCoolingRecipe source) {
        Ingredient input = source.getInput();
        if (input == null || input.isEmpty() || source.getOutput().isEmpty()) return null;
        // Native bonuses use temperature-dependent stochastic rounding. A fixed maximum is not
        // an equivalent recipe; only the deterministic (no bonus) case can be flattened.
        if (source.getBonusMultiplier() > 0 && source.getBonusLimit() > 0) return null;
        // FluidIngredient tests container capabilities; it is not an ordinary item predicate.
        if (input instanceof FluidIngredient) return null;
        return build(id, countedInputs(List.of(input)), List.of(), List.of(source.getOutput().copy()),
                List.of(Ingredient.of(getMoldItem())), AdapterUtils.DEFAULT_PROCESS_TIME);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertRefinery(
            ResourceLocation id, RefineryRecipe source) {
        List<SizedFluidIngredient> inputs = sizedFluids(source.getInput());
        List<FluidStack> outputs = copiedFluids(source.getOutputs());
        if (inputs.isEmpty() || outputs.isEmpty()) return null;
        return build(id, List.of(), inputs, List.of(),
                List.of(Ingredient.of(getMoldItem())),
                AdapterUtils.DEFAULT_PROCESS_TIME, outputs);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertThermoPlant(
            ResourceLocation id, ThermoPlantRecipe source) {
        Ingredient input = source.getInputItem();
        List<CountedIngredient> inputs = input == null || input.isEmpty()
                ? List.of() : countedInputs(List.of(input));
        List<SizedFluidIngredient> fluids = sizedFluids(source.getInputFluid());

        List<ItemStack> outputs = source.getOutputItem().isEmpty()
                ? List.of() : List.of(source.getOutputItem().copy());
        List<FluidStack> outputFluids = source.getOutputFluid().isEmpty()
                ? List.of() : List.of(source.getOutputFluid().copy());
        if (inputs.isEmpty() && fluids.isEmpty() || outputs.isEmpty() && outputFluids.isEmpty()) {
            return null;
        }
        return build(id, inputs, fluids, outputs,
                List.of(Ingredient.of(getMoldItem())),
                AdapterUtils.DEFAULT_PROCESS_TIME, outputFluids);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertAmadron(
            ResourceLocation id, AmadronRecipe source) {
        // Player offers, limited stock and location restrictions cannot become infinite trades.
        if (!source.isStaticOffer() || source.isLocationLimited() || source.getMaxStock() > 0) return null;
        AmadronTradeResource input = source.getInput();
        AmadronTradeResource output = source.getOutput();
        if (input == null || output == null || input.isEmpty() || output.isEmpty()) return null;

        List<CountedIngredient> inputs = new ArrayList<>();
        List<SizedFluidIngredient> fluids = new ArrayList<>();
        ItemStack inputItem = input.getItem();
        FluidStack inputFluid = input.getFluid();
        if (inputItem != null && !inputItem.isEmpty()) {
            inputs.add(new CountedIngredient(itemIngredient(inputItem), inputItem.getCount()));
        } else if (inputFluid != null && !inputFluid.isEmpty()) {
            SizedFluidIngredient sized = AdapterUtils.toSizedFluidIngredient(inputFluid);
            if (sized != null) fluids.add(sized);
        }

        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = new ArrayList<>();
        ItemStack outputItem = output.getItem();
        FluidStack outputFluid = output.getFluid();
        if (outputItem != null && !outputItem.isEmpty()) outputs.add(outputItem.copy());
        else if (outputFluid != null && !outputFluid.isEmpty()) outputFluids.add(outputFluid.copy());

        if (inputs.isEmpty() && fluids.isEmpty() || outputs.isEmpty() && outputFluids.isEmpty()) {
            return null;
        }
        return build(id, inputs, fluids, outputs,
                List.of(Ingredient.of(getMoldItem())),
                AdapterUtils.DEFAULT_PROCESS_TIME, outputFluids);
    }

    private static AdvancedAlloyFurnaceRecipe build(
            ResourceLocation id,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<Ingredient> molds,
            int processTime) {
        return build(id, inputs, inputFluids, outputs, molds, processTime, List.of());
    }

    private static AdvancedAlloyFurnaceRecipe build(
            ResourceLocation id,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<Ingredient> molds,
            int processTime,
            List<FluidStack> outputFluids) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(id), inputs, inputFluids, List.of(), outputs,
                outputFluids, List.of(), AdapterUtils.DEFAULT_ENERGY,
                Math.max(1, processTime), Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL);
    }

    private static List<CountedIngredient> countedInputs(List<Ingredient> inputs) {
        List<CountedIngredient> result = new ArrayList<>();
        for (Ingredient input : inputs) {
            if (input == null || input.isEmpty()) throw new IllegalArgumentException("Empty PNC input");
            // 6.0.23 consumes getItems()[0].getCount(), not a newer SizedIngredient.
            ItemStack[] examples = input.getItems();
            if (examples.length == 0) throw new IllegalArgumentException("PNC input has no examples");
            int count = examples[0].getCount();
            for (ItemStack example : examples) if (example.getCount() != count)
                throw new IllegalArgumentException("PNC input alternatives have different counts");
            result.add(new CountedIngredient(itemPredicate(input), count));
        }
        return List.copyOf(result);
    }

    private static Ingredient itemPredicate(Ingredient input) {
        if (input instanceof FluidIngredient) throw new IllegalArgumentException("PNC container predicate");
        if (!(input instanceof StackedIngredient)) return input;
        JsonElement json = input.toJson().deepCopy();
        Iterable<JsonElement> entries = json.isJsonArray() ? json.getAsJsonArray() : List.of(json);
        for (JsonElement entry : entries) {
            entry.getAsJsonObject().remove("type");
            entry.getAsJsonObject().remove("count");
        }
        return Ingredient.fromJson(json); // native stacked ingredient tests item/tag, not stack NBT
    }

    private static List<SizedFluidIngredient> sizedFluids(FluidIngredient... inputs) {
        List<SizedFluidIngredient> result = new ArrayList<>();
        for (FluidIngredient input : inputs) {
            if (input == null || input == FluidIngredient.EMPTY) continue;
            // CompoundFluidIngredient's stream/amount semantics are not a static sized union.
            if (input.getClass() != FluidIngredient.class || input.getAmount() <= 0)
                throw new IllegalArgumentException("Unsupported PNC fluid ingredient");
            JsonObject json = input.toJson().getAsJsonObject().deepCopy();
            json.remove("type");
            com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient predicate;
            if (json.has("tag")) {
                if (json.has("nbt")) {
                    json.addProperty("type", "useless_mod:nbt_fluid_tag");
                    json.addProperty("strict", false); // both native fuzzy modes use compareNbt(..., true)
                }
                predicate = com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.fromJson(json);
            } else {
                // Native JSON writes only the first literal fluid, while getFluidStacks() drops
                // NBT entirely. Combine the full ID list with the serialized NBT predicate.
                var requiredNbt = json.has("nbt")
                        ? net.minecraftforge.common.crafting.CraftingHelper.getNBT(json.get("nbt")) : null;
                predicate = com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.any(
                        input.getFluidStacks().stream().map(stack -> {
                            if (requiredNbt == null)
                                return com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.single(stack);
                            var exact = new FluidStack(stack.getFluid(), stack.getAmount(), requiredNbt);
                            return com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.nbt(false, exact);
                        }).toList());
            }
            if (predicate.isEmpty()) throw new IllegalArgumentException("Empty PNC fluid predicate");
            result.add(new SizedFluidIngredient(predicate, input.getAmount()));
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> copiedItems(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty() && stack.getCount() > 0) result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    private static List<FluidStack> copiedFluids(List<FluidStack> stacks) {
        List<FluidStack> result = new ArrayList<>();
        if (stacks != null) {
            for (FluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty() && stack.getAmount() > 0) result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    private static Ingredient itemIngredient(ItemStack stack) {
        ItemStack representative = stack.copyWithCount(1);
        return !representative.hasTag()
                ? Ingredient.of(representative)
                : DataComponentIngredient.of(true, representative);
    }

}
