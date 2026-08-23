package com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft;

import appeng.api.stacks.AEKey;
import com.mojang.datafixers.util.Either;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import me.desht.pneumaticcraft.api.crafting.AmadronTradeResource;
import me.desht.pneumaticcraft.api.crafting.ingredient.FluidContainerIngredient;
import me.desht.pneumaticcraft.api.crafting.recipe.AmadronRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.AssemblyRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.FluidMixerRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.HeatFrameCoolingRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.PneumaticCraftRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.PressureChamberRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.RefineryRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import me.desht.pneumaticcraft.common.registry.ModBlocks;
import me.desht.pneumaticcraft.common.registry.ModItems;
import me.desht.pneumaticcraft.common.registry.ModRecipeTypes;
import me.desht.pneumaticcraft.common.recipes.machine.PressureDisenchantingRecipe;
import me.desht.pneumaticcraft.common.recipes.machine.PressureEnchantingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
                level -> ModRecipeTypes.getRecipes(level, ModRecipeTypes.FLUID_MIXER),
                new ItemStack(ModBlocks.FLUID_MIXER.get()));
    }

    public static PneumaticCraftRecipeAdapter<HeatFrameCoolingRecipe> heatFrameCooling() {
        return fixed(HeatFrameCoolingRecipe.class, Kind.HEAT_FRAME_COOLING,
                level -> ModRecipeTypes.getRecipes(level, ModRecipeTypes.HEAT_FRAME_COOLING),
                new ItemStack(ModItems.HEAT_FRAME.get()));
    }

    public static PneumaticCraftRecipeAdapter<PressureChamberRecipe> pressureChamber() {
        return fixed(PressureChamberRecipe.class, Kind.PRESSURE_CHAMBER,
                level -> ModRecipeTypes.getRecipes(level, ModRecipeTypes.PRESSURE_CHAMBER),
                new ItemStack(ModBlocks.PRESSURE_CHAMBER_VALVE.get()));
    }

    public static PneumaticCraftRecipeAdapter<RefineryRecipe> refinery() {
        return fixed(RefineryRecipe.class, Kind.REFINERY,
                level -> ModRecipeTypes.getRecipes(level, ModRecipeTypes.REFINERY),
                new ItemStack(ModBlocks.REFINERY.get()));
    }

    public static PneumaticCraftRecipeAdapter<ThermoPlantRecipe> thermoPlant() {
        return fixed(ThermoPlantRecipe.class, Kind.THERMO_PLANT,
                level -> ModRecipeTypes.getRecipes(level, ModRecipeTypes.THERMO_PLANT),
                new ItemStack(ModBlocks.THERMOPNEUMATIC_PROCESSING_PLANT.get()));
    }

    public static PneumaticCraftRecipeAdapter<AmadronRecipe> amadron() {
        return fixed(AmadronRecipe.class, Kind.AMADRON,
                level -> ModRecipeTypes.getRecipes(level, ModRecipeTypes.AMADRON),
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
        result.addAll(ModRecipeTypes.getRecipes(level, ModRecipeTypes.ASSEMBLY_DRILL));
        result.addAll(ModRecipeTypes.getRecipes(level, ModRecipeTypes.ASSEMBLY_LASER));
        result.addAll(ModRecipeTypes.getRecipes(level, ModRecipeTypes.ASSEMBLY_DRILL_LASER));
        return result;
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
                ModRecipeTypes.getRecipes(level, ModRecipeTypes.ASSEMBLY_DRILL_LASER);
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
            if (kind == Kind.HEAT_FRAME_COOLING
                    && !matchesHeatFrameInput((HeatFrameCoolingRecipe) holder.value(), actualInputs)) {
                continue;
            }

            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (converted.stream().anyMatch(recipe -> matchesConverted(recipe, safeInputs, safeFluids))) {
                matches.add(holder);
            }
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
                && FluidIngredientAllocator.matches(recipe.inputFluids(), mergedFluids, 1L);
    }

    private boolean matchesHeatFrameInput(
            HeatFrameCoolingRecipe recipe, List<ItemStack> actualInputs) {
        if (actualInputs == null || actualInputs.isEmpty()) return true;
        Either<Ingredient, FluidContainerIngredient> input = recipe.getInput();
        if (input == null) return false;
        if (input.left().isPresent()) {
            Ingredient ingredient = input.left().get();
            return actualInputs.stream().anyMatch(ingredient::test);
        }
        return input.right().map(container ->
                actualInputs.stream().anyMatch(container::test)).orElse(false);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertAssembly(
            ResourceLocation id, AssemblyRecipe source) {
        if (source.getInput() == null || source.getInput().ingredient().isEmpty()
                || source.getInput().count() <= 0 || source.getOutput().isEmpty()) {
            return null;
        }

        ItemStack program = switch (source.getProgramType()) {
            case DRILL -> new ItemStack(ModItems.ASSEMBLY_PROGRAM_DRILL.get());
            case LASER -> new ItemStack(ModItems.ASSEMBLY_PROGRAM_LASER.get());
            case DRILL_LASER -> new ItemStack(ModItems.ASSEMBLY_PROGRAM_DRILL_LASER.get());
        };
        return build(id,
                List.of(new CountedIngredient(source.getInput().ingredient(), source.getInput().count())),
                List.of(),
                List.of(source.getOutput().copy()),
                List.of(Ingredient.of(new ItemStack(ModBlocks.ASSEMBLY_PLATFORM.get())),
                        Ingredient.of(program)),
                AdapterUtils.DEFAULT_PROCESS_TIME);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertPressureChamber(
            ResourceLocation id, PressureChamberRecipe source) {
        if (source instanceof PressureEnchantingRecipe
                || source instanceof PressureDisenchantingRecipe) {
            return null;
        }
        List<CountedIngredient> inputs = countedInputs(source.getInputs());
        List<ItemStack> outputs = copiedItems(source.getOutputs());
        if (inputs.isEmpty() || outputs.isEmpty()) return null;
        return build(id, inputs, List.of(), outputs,
                List.of(Ingredient.of(new ItemStack(ModBlocks.PRESSURE_CHAMBER_VALVE.get()))),
                AdapterUtils.DEFAULT_PROCESS_TIME);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertFluidMixer(
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
                List.of(Ingredient.of(new ItemStack(ModBlocks.FLUID_MIXER.get()))), time, outputFluids);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertHeatFrameCooling(
            ResourceLocation id, HeatFrameCoolingRecipe source) {
        Ingredient input = source.getInput().map(
                ingredient -> ingredient,
                container -> Ingredient.of(container.getItems()));
        if (input == null || input.isEmpty() || source.getOutput().isEmpty()) return null;

        int outputCount = maximumHeatFrameOutput(source);
        ItemStack output = source.getOutput().copyWithCount(outputCount);
        return build(id,
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(output),
                List.of(Ingredient.of(new ItemStack(ModItems.HEAT_FRAME.get()))),
                AdapterUtils.DEFAULT_PROCESS_TIME);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertRefinery(
            ResourceLocation id, RefineryRecipe source) {
        List<SizedFluidIngredient> inputs = sizedFluids(source.getInput());
        List<FluidStack> outputs = copiedFluids(source.getOutputs());
        if (inputs.isEmpty() || outputs.isEmpty()) return null;
        return build(id, List.of(), inputs, List.of(),
                List.of(Ingredient.of(new ItemStack(ModBlocks.REFINERY.get()))),
                AdapterUtils.DEFAULT_PROCESS_TIME, outputs);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertThermoPlant(
            ResourceLocation id, ThermoPlantRecipe source) {
        List<CountedIngredient> inputs = new ArrayList<>();
        source.getInputItem().filter(ingredient -> !ingredient.isEmpty())
                .ifPresent(ingredient -> inputs.add(new CountedIngredient(ingredient, 1L)));
        List<SizedFluidIngredient> fluids = source.getInputFluid()
                .filter(PneumaticCraftRecipeAdapter::validFluid)
                .map(List::of).orElse(List.of());

        List<ItemStack> outputs = source.getOutputItem().isEmpty()
                ? List.of() : List.of(source.getOutputItem().copy());
        List<FluidStack> outputFluids = source.getOutputFluid().isEmpty()
                ? List.of() : List.of(source.getOutputFluid().copy());
        if (inputs.isEmpty() && fluids.isEmpty() || outputs.isEmpty() && outputFluids.isEmpty()) {
            return null;
        }
        return build(id, inputs, fluids, outputs,
                List.of(Ingredient.of(new ItemStack(ModBlocks.THERMOPNEUMATIC_PROCESSING_PLANT.get()))),
                AdapterUtils.DEFAULT_PROCESS_TIME, outputFluids);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertAmadron(
            ResourceLocation id, AmadronRecipe source) {
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
                List.of(Ingredient.of(new ItemStack(ModItems.AMADRON_TABLET.get()))),
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

    private static List<CountedIngredient> countedInputs(
            List<net.neoforged.neoforge.common.crafting.SizedIngredient> inputs) {
        List<CountedIngredient> result = new ArrayList<>();
        if (inputs == null) return result;
        for (net.neoforged.neoforge.common.crafting.SizedIngredient input : inputs) {
            if (input != null && input.ingredient() != null && !input.ingredient().isEmpty()
                    && input.count() > 0) {
                result.add(new CountedIngredient(input.ingredient(), input.count()));
            }
        }
        return List.copyOf(result);
    }

    private static List<SizedFluidIngredient> sizedFluids(SizedFluidIngredient... inputs) {
        List<SizedFluidIngredient> result = new ArrayList<>();
        if (inputs != null) {
            for (SizedFluidIngredient input : inputs) {
                if (validFluid(input)) result.add(input);
            }
        }
        return List.copyOf(result);
    }

    private static boolean validFluid(SizedFluidIngredient input) {
        return input != null && input.ingredient() != null && !input.ingredient().isEmpty()
                && input.amount() > 0;
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
        return representative.getComponents().isEmpty()
                ? Ingredient.of(representative)
                : DataComponentIngredient.of(true, representative);
    }

    private static int maximumHeatFrameOutput(HeatFrameCoolingRecipe recipe) {
        if (recipe.getBonusMultiplier() <= 0.0f) return 1;
        double maximum = 1.0 + Math.max(0.0, recipe.getBonusLimit());
        if (maximum >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) Math.ceil(maximum));
    }
}
