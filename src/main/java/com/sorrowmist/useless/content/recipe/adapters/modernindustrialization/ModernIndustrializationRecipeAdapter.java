package com.sorrowmist.useless.content.recipe.adapters.modernindustrialization;

import aztech.modern_industrialization.machines.init.MIMachineRecipeTypes;
import aztech.modern_industrialization.machines.init.MachineTier;
import aztech.modern_industrialization.machines.recipe.MachineRecipe;
import aztech.modern_industrialization.machines.recipe.MachineRecipeType;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts Modern Industrialization machine recipes to alloy-furnace recipes. */
public final class ModernIndustrializationRecipeAdapter implements IRecipeAdapter<MachineRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "modern_industrialization";
    private static final int UNLIMITED_EU = Integer.MAX_VALUE;
    private static final Map<MachineRecipeType, List<MachineMold>> MACHINE_MOLDS = createMachineMolds();
    private static final Map<Item, MachineRecipeType> TYPE_BY_MACHINE_ITEM = indexMachineItems();

    @Override
    public String sourceId() {
        return RecipeSourceIds.MODERN_INDUSTRIALIZATION;
    }

    @Override
    public Class<MachineRecipe> getRecipeClass() {
        return MachineRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && TYPE_BY_MACHINE_ITEM.containsKey(mold.getItem());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<MachineRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        MachineRecipe source = holder.value();
        MachineRecipeType type = machineRecipeType(source);
        if (type == null) return List.of();
        List<MachineMold> machineMolds = MACHINE_MOLDS.get(type);
        if (machineMolds == null || source.duration <= 0 || source.eu < 0) {
            return List.of();
        }

        OptionalInt operations = calculateBatchOperations(source);
        if (operations.isEmpty()) {
            warnSkipped(holder, "probability batch cannot be represented");
            return List.of();
        }

        Optional<Ingredient> machineMold = machineMold(machineMolds, source.eu);
        if (machineMold.isEmpty()) {
            warnSkipped(holder, "no listed machine tier can process the recipe EU/t");
            return List.of();
        }

        ConversionData data = convertData(source, operations.getAsInt(), machineMold.get());
        if (data == null) {
            warnSkipped(holder, "recipe contains an unsupported or overflowing input/output");
            return List.of();
        }

        try {
            long energy = Math.multiplyExact(source.getTotalEu(), (long) operations.getAsInt());
            long processTimeLong = Math.multiplyExact((long) source.duration, operations.getAsInt());
            if (processTimeLong <= 0 || processTimeLong > Integer.MAX_VALUE) {
                warnSkipped(holder, "scaled process time overflows");
                return List.of();
            }

            return List.of(new AdvancedAlloyFurnaceRecipe(
                    AdapterUtils.convertedId(holder.id()),
                    data.inputs(),
                    data.inputFluids(),
                    List.of(),
                    data.outputs(),
                    data.outputFluids(),
                    List.of(),
                    energy,
                    (int) processTimeLong,
                    Ingredient.EMPTY,
                    0,
                    data.molds(),
                    AlloyFurnaceMode.NORMAL));
        } catch (ArithmeticException exception) {
            warnSkipped(holder, "scaled energy overflows");
            return List.of();
        }
    }

    @Override
    public List<RecipeHolder<MachineRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        MachineRecipeType type = TYPE_BY_MACHINE_ITEM.get(mold.getItem());
        if (type == null) return List.of();

        Map<Ingredient, Long> safeInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> safeFluids = mergedFluids == null ? Map.of() : mergedFluids;
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<MachineRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<MachineRecipe> holder : manager.getAllRecipesFor(type)) {
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (converted.stream().anyMatch(recipe -> matchesInputs(recipe, safeInputs, safeFluids)
                    && AdapterUtils.matchesMold(recipe.mold(), mold))) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean matchesInputs(AdvancedAlloyFurnaceRecipe recipe,
                                          Map<Ingredient, Long> mergedInputs,
                                          Map<FluidStack, Long> mergedFluids) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, required)
                && FluidIngredientAllocator.matches(recipe.inputFluids(), mergedFluids, 1L);
    }

    @Nullable
    private static ConversionData convertData(MachineRecipe source, int operations,
                                              Ingredient machineMold) {
        Map<Ingredient, Long> inputCounts = new LinkedHashMap<>();
        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = new ArrayList<>();
        List<Ingredient> molds = new ArrayList<>();
        molds.add(machineMold);

        for (MachineRecipe.ItemInput input : source.itemInputs) {
            Rational probability = probability(input.probability());
            if (probability == null) return null;
            if (probability.isZero()) {
                if (!addRepeatedMolds(molds, input.ingredient(), input.amount())) return null;
                continue;
            }
            long amount = scaleAmount(input.amount(), probability, operations);
            if (amount <= 0) return null;
            AdapterUtils.mergeIngredient(inputCounts, input.ingredient(), amount);
        }

        for (MachineRecipe.FluidInput input : source.fluidInputs) {
            Rational probability = probability(input.probability());
            if (probability == null) return null;
            if (probability.isZero()) {
                Optional<Ingredient> bucketMold = bucketMold(input.fluid());
                if (bucketMold.isEmpty()) return null;
                molds.add(bucketMold.get());
                continue;
            }
            long amount = scaleAmount(input.amount(), probability, operations);
            if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
            if (!mergeFluidInput(inputFluids, input.fluid(), (int) amount)) return null;
        }

        for (MachineRecipe.ItemOutput output : source.itemOutputs) {
            Rational probability = probability(output.probability());
            if (probability == null) return null;
            if (probability.isZero()) continue;
            ItemStack stack = output.getStack();
            if (stack == null || stack.isEmpty()) return null;
            long amount = scaleAmount(stack.getCount(), probability, operations);
            if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
            if (!mergeItemOutput(outputs, stack.copyWithCount((int) amount))) return null;
        }

        for (MachineRecipe.FluidOutput output : source.fluidOutputs) {
            Rational probability = probability(output.probability());
            if (probability == null) return null;
            if (probability.isZero()) continue;
            long amount = scaleAmount(output.amount(), probability, operations);
            if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
            if (!mergeFluidOutput(outputFluids,
                    new FluidStack(output.fluid(), (int) amount))) return null;
        }

        if (outputs.isEmpty() && outputFluids.isEmpty()) return null;

        List<CountedIngredient> inputs = inputCounts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new ConversionData(inputs, List.copyOf(inputFluids), List.copyOf(outputs),
                List.copyOf(outputFluids), List.copyOf(molds));
    }

    private static OptionalInt calculateBatchOperations(MachineRecipe source) {
        long operations = 1L;
        for (MachineRecipe.ItemInput input : source.itemInputs) {
            operations = addProbabilityDenominator(operations, input.probability());
            if (operations <= 0) return OptionalInt.empty();
        }
        for (MachineRecipe.FluidInput input : source.fluidInputs) {
            operations = addProbabilityDenominator(operations, input.probability());
            if (operations <= 0) return OptionalInt.empty();
        }
        for (MachineRecipe.ItemOutput output : source.itemOutputs) {
            operations = addProbabilityDenominator(operations, output.probability());
            if (operations <= 0) return OptionalInt.empty();
        }
        for (MachineRecipe.FluidOutput output : source.fluidOutputs) {
            operations = addProbabilityDenominator(operations, output.probability());
            if (operations <= 0) return OptionalInt.empty();
        }
        return operations <= Integer.MAX_VALUE ? OptionalInt.of((int) operations) : OptionalInt.empty();
    }

    private static long addProbabilityDenominator(long current, float rawProbability) {
        Rational probability = probability(rawProbability);
        if (probability == null) return -1L;
        if (probability.isZero() || probability.isOne()) return current;

        long divisor = AdapterUtils.gcd(current, probability.denominator());
        long factor = probability.denominator() / divisor;
        if (current > Integer.MAX_VALUE / factor) return -1L;
        return current * factor;
    }

    @Nullable
    private static Rational probability(float rawProbability) {
        if (!Float.isFinite(rawProbability) || rawProbability < 0.0f || rawProbability > 1.0f) {
            return null;
        }
        BigDecimal decimal = new BigDecimal(Float.toString(rawProbability)).stripTrailingZeros();
        int scale = Math.max(0, decimal.scale());
        BigInteger denominator = BigInteger.TEN.pow(scale);
        BigInteger numerator = decimal.movePointRight(scale).toBigInteger();
        BigInteger divisor = numerator.gcd(denominator);
        denominator = denominator.divide(divisor);
        numerator = numerator.divide(divisor);
        if (denominator.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                || numerator.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return null;
        }
        return new Rational(numerator.longValueExact(), denominator.longValueExact());
    }

    private static long scaleAmount(long amount, Rational probability, long operations) {
        if (amount <= 0 || probability.isZero()) return 0L;
        long factor = operations / probability.denominator();
        try {
            return Math.multiplyExact(Math.multiplyExact(amount, probability.numerator()), factor);
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    private static boolean addRepeatedMolds(List<Ingredient> molds, Ingredient mold, int amount) {
        if (mold == null || mold.isEmpty() || amount <= 0) return false;
        for (int i = 0; i < amount; i++) molds.add(mold);
        return true;
    }

    private static boolean mergeFluidInput(List<SizedFluidIngredient> target,
                                           FluidIngredient ingredient, int amount) {
        if (ingredient == null || ingredient.isEmpty() || amount <= 0) return false;
        for (int i = 0; i < target.size(); i++) {
            SizedFluidIngredient existing = target.get(i);
            if (!existing.ingredient().equals(ingredient)) continue;
            long merged = (long) existing.amount() + amount;
            if (merged > Integer.MAX_VALUE) return false;
            target.set(i, new SizedFluidIngredient(ingredient, (int) merged));
            return true;
        }
        target.add(new SizedFluidIngredient(ingredient, amount));
        return true;
    }

    private static boolean mergeItemOutput(List<ItemStack> target, ItemStack output) {
        for (ItemStack existing : target) {
            if (!ItemStack.isSameItemSameComponents(existing, output)) continue;
            long merged = (long) existing.getCount() + output.getCount();
            if (merged > Integer.MAX_VALUE) return false;
            existing.setCount((int) merged);
            return true;
        }
        target.add(output.copy());
        return true;
    }

    private static boolean mergeFluidOutput(List<FluidStack> target, FluidStack output) {
        for (FluidStack existing : target) {
            if (!FluidStack.isSameFluidSameComponents(existing, output)) continue;
            long merged = (long) existing.getAmount() + output.getAmount();
            if (merged > Integer.MAX_VALUE) return false;
            existing.setAmount((int) merged);
            return true;
        }
        target.add(output.copy());
        return true;
    }

    private static Optional<Ingredient> bucketMold(FluidIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return Optional.empty();
        Map<Item, ItemStack> buckets = new LinkedHashMap<>();
        try {
            for (FluidStack stack : ingredient.getStacks()) {
                addBucket(buckets, stack == null ? null : stack.getFluid());
            }
        } catch (RuntimeException ignored) {
            // Registry probing below can still resolve tag-backed fluid ingredients.
        }
        if (!buckets.isEmpty()) {
            return Optional.of(Ingredient.of(buckets.values().toArray(ItemStack[]::new)));
        }
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            FluidStack candidate = new FluidStack(fluid, 1000);
            try {
                if (ingredient.test(candidate)) addBucket(buckets, fluid);
            } catch (RuntimeException ignored) {
                // Some custom ingredients do not support arbitrary registry probing.
            }
        }
        if (buckets.isEmpty()) return Optional.empty();
        return Optional.of(Ingredient.of(buckets.values().toArray(ItemStack[]::new)));
    }

    private static void addBucket(Map<Item, ItemStack> buckets, @Nullable Fluid fluid) {
        if (fluid == null) return;
        ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, 1000));
        if (bucket.isEmpty() || !(bucket.getItem() instanceof BucketItem)) return;
        buckets.putIfAbsent(bucket.getItem(), bucket.copyWithCount(1));
    }

    @Nullable
    private static MachineRecipeType machineRecipeType(MachineRecipe recipe) {
        return recipe.getType() instanceof MachineRecipeType type ? type : null;
    }

    private static Optional<Ingredient> machineMold(List<MachineMold> machineMolds, int eu) {
        Map<Item, ItemStack> stacks = new LinkedHashMap<>();
        for (MachineMold machineMold : machineMolds) {
            if (eu > machineMold.maxEu()) continue;
            Item item = BuiltInRegistries.ITEM.getOptional(id(machineMold.itemId())).orElse(null);
            if (item == null || item == Items.AIR) continue;
            stacks.putIfAbsent(item, item.getDefaultInstance());
        }
        if (stacks.isEmpty()) return Optional.empty();
        return Optional.of(Ingredient.of(stacks.values().toArray(ItemStack[]::new)));
    }

    private static Map<MachineRecipeType, List<MachineMold>> createMachineMolds() {
        return Map.ofEntries(
                Map.entry(MIMachineRecipeTypes.ASSEMBLER, lv("assembler")),
                Map.entry(MIMachineRecipeTypes.CENTRIFUGE, lv("centrifuge")),
                Map.entry(MIMachineRecipeTypes.CHEMICAL_REACTOR, lv("chemical_reactor")),
                Map.entry(MIMachineRecipeTypes.COMPRESSOR, tiered("compressor")),
                Map.entry(MIMachineRecipeTypes.CUTTING_MACHINE, tiered("cutting_machine")),
                Map.entry(MIMachineRecipeTypes.DISTILLERY, lv("distillery")),
                Map.entry(MIMachineRecipeTypes.ELECTROLYZER, lv("electrolyzer")),
                Map.entry(MIMachineRecipeTypes.MACERATOR, tiered("macerator")),
                Map.entry(MIMachineRecipeTypes.MIXER, tiered("mixer")),
                Map.entry(MIMachineRecipeTypes.PACKER, steelAndElectric("packer")),
                Map.entry(MIMachineRecipeTypes.POLARIZER, lv("polarizer")),
                Map.entry(MIMachineRecipeTypes.UNPACKER, steel("steel_unpacker")),
                Map.entry(MIMachineRecipeTypes.WIREMILL, steel("steel_wiremill")),
                Map.entry(MIMachineRecipeTypes.BLAST_FURNACE, fixed("steam_blast_furnace", "electric_blast_furnace")),
                Map.entry(MIMachineRecipeTypes.COKE_OVEN, fixed("coke_oven")),
                Map.entry(MIMachineRecipeTypes.DISTILLATION_TOWER, fixed("distillation_tower")),
                Map.entry(MIMachineRecipeTypes.FUSION_REACTOR, fixed("fusion_reactor")),
                Map.entry(MIMachineRecipeTypes.HEAT_EXCHANGER, fixed("heat_exchanger")),
                Map.entry(MIMachineRecipeTypes.IMPLOSION_COMPRESSOR, fixed("implosion_compressor")),
                Map.entry(MIMachineRecipeTypes.OIL_DRILLING_RIG, fixed("oil_drilling_rig")),
                Map.entry(MIMachineRecipeTypes.PRESSURIZER, fixed("pressurizer")),
                Map.entry(MIMachineRecipeTypes.QUARRY, fixed("steam_quarry", "electric_quarry")),
                Map.entry(MIMachineRecipeTypes.VACUUM_FREEZER, fixed("vacuum_freezer")));
    }

    private static List<MachineMold> fixed(String... ids) {
        List<MachineMold> result = new ArrayList<>(ids.length);
        for (String id : ids) result.add(new MachineMold(id, UNLIMITED_EU));
        return List.copyOf(result);
    }

    private static List<MachineMold> lv(String... ids) {
        List<MachineMold> result = new ArrayList<>(ids.length);
        for (String id : ids) result.add(new MachineMold(id, MachineTier.LV.getMaxEu()));
        return List.copyOf(result);
    }

    private static List<MachineMold> steel(String... ids) {
        List<MachineMold> result = new ArrayList<>(ids.length);
        for (String id : ids) result.add(new MachineMold(id, MachineTier.STEEL.getMaxEu()));
        return List.copyOf(result);
    }

    private static List<MachineMold> tiered(String baseName) {
        return List.of(
                new MachineMold("bronze_" + baseName, MachineTier.BRONZE.getMaxEu()),
                new MachineMold("steel_" + baseName, MachineTier.STEEL.getMaxEu()),
                new MachineMold("electric_" + baseName, MachineTier.LV.getMaxEu()));
    }

    private static List<MachineMold> steelAndElectric(String baseName) {
        return List.of(
                new MachineMold("steel_" + baseName, MachineTier.STEEL.getMaxEu()),
                new MachineMold("electric_" + baseName, MachineTier.LV.getMaxEu()));
    }

    private static Map<Item, MachineRecipeType> indexMachineItems() {
        Map<Item, MachineRecipeType> result = new LinkedHashMap<>();
        for (Map.Entry<MachineRecipeType, List<MachineMold>> entry : MACHINE_MOLDS.entrySet()) {
            for (MachineMold machineMold : entry.getValue()) {
                Item item = BuiltInRegistries.ITEM.getOptional(id(machineMold.itemId())).orElse(null);
                if (item != null && item != Items.AIR) result.put(item, entry.getKey());
            }
        }
        return Map.copyOf(result);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void warnSkipped(RecipeHolder<MachineRecipe> holder, String reason) {
        LOGGER.warn("Skipping Modern Industrialization recipe {}: {}", holder.id(), reason);
    }

    private record MachineMold(String itemId, int maxEu) {
    }

    private record Rational(long numerator, long denominator) {
        private boolean isZero() {
            return numerator == 0L;
        }

        private boolean isOne() {
            return numerator == denominator;
        }
    }

    private record ConversionData(
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<FluidStack> outputFluids,
            List<Ingredient> molds) {
    }
}
