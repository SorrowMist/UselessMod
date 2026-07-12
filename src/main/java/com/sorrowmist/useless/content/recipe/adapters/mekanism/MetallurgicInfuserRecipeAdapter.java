package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.ItemStackToChemicalRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mekanism 冶金灌注机配方适配器
 * <p>
 * 将冶金灌注配方转换为高级合金熔炉配方。
 * 由于高级熔炉不支持化学品输入，需要将化学品转换为对应的物品输入。
 * <p>
 * 转换逻辑：
 * - 通过物品转化学品配方查找化学品对应的物品来源
 * - 使用最小公倍数计算批量配方（例如：1红石粉=10mb，1富集红石=80mb，
 *   配方需要10mb红石化学品，则转换为1红石粉 或 8铜锭+1富集红石出8个产物）
 */
public class MetallurgicInfuserRecipeAdapter implements IRecipeAdapter<ItemStackChemicalToItemStackRecipe> {

    private final Map<ResourceLocation, List<ChemicalSource>> chemicalSourceCache = new HashMap<>();
    @Nullable
    private RecipeManager cachedRecipeManager;

    @Override
    public Class<ItemStackChemicalToItemStackRecipe> getRecipeClass() {
        return ItemStackChemicalToItemStackRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.METALLURGIC_INFUSER.get());
    }

    protected RecipeType<ItemStackChemicalToItemStackRecipe> getMekanismRecipeType() {
        return MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackChemicalToItemStackRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        if (holder == null || level == null) return recipes;

        ItemStackChemicalToItemStackRecipe originalRecipe = holder.value();

        if (!originalRecipe.getType().equals(getMekanismRecipeType())) {
            return recipes;
        }

        ResourceLocation originalId = holder.id();

        var itemInput = originalRecipe.getItemInput();
        if (itemInput == null || itemInput.hasNoMatchingInstances()) {
            return recipes;
        }

        var chemicalInput = originalRecipe.getChemicalInput();
        if (chemicalInput == null) {
            return recipes;
        }

        List<ItemStack> outputs = originalRecipe.getOutputDefinition();
        if (outputs.isEmpty()) {
            return recipes;
        }

        for (AdvancedAlloyFurnaceRecipe directRecipe : createDirectRecipes(originalId, itemInput, chemicalInput, originalRecipe, outputs)) {
            addIfUnique(recipes, directRecipe);
        }

        List<ChemicalSource> sources = findChemicalSources(level, chemicalInput);

        for (ChemicalSource source : sources) {
            AdvancedAlloyFurnaceRecipe recipe = createRecipe(
                    originalId, itemInput, chemicalInput, originalRecipe, source, outputs
            );
            if (recipe != null) {
                addIfUnique(recipes, recipe);
            }
        }

        return recipes;
    }

    private List<ChemicalSource> findChemicalSources(Level level, ChemicalStackIngredient chemicalInput) {
        List<ChemicalSource> sources = new ArrayList<>();
        if (level == null) return sources;

        var chemicalReps = chemicalInput.getRepresentations();
        if (chemicalReps.isEmpty()) return sources;

        RecipeManager recipeManager = level.getRecipeManager();
        if (cachedRecipeManager != recipeManager) {
            chemicalSourceCache.clear();
            cachedRecipeManager = recipeManager;
        }
        buildChemicalSourceCache(recipeManager);

        for (ChemicalStack chemicalRep : chemicalReps) {
            ResourceLocation chemicalId = chemicalRep.getChemicalHolder().getKey().location();
            for (ChemicalSource source : chemicalSourceCache.getOrDefault(chemicalId, List.of())) {
                if (sources.stream().noneMatch(existing -> isSameSource(existing, source))) {
                    sources.add(source);
                }
            }
        }

        return sources;
    }

    private void buildChemicalSourceCache(RecipeManager recipeManager) {
        if (!chemicalSourceCache.isEmpty()) {
            return;
        }
        List<RecipeHolder<ItemStackToChemicalRecipe>> conversionRecipes = new ArrayList<>();
        conversionRecipes.addAll(recipeManager.getAllRecipesFor(MekanismRecipeTypes.TYPE_CHEMICAL_CONVERSION.value()));
        conversionRecipes.addAll(recipeManager.getAllRecipesFor(MekanismRecipeTypes.TYPE_OXIDIZING.value()));

        for (RecipeHolder<ItemStackToChemicalRecipe> holder : conversionRecipes) {
            ItemStackToChemicalRecipe recipe = holder.value();
            var itemInput = recipe.getInput();
            if (itemInput == null || itemInput.hasNoMatchingInstances()) {
                continue;
            }

            List<ChemicalStack> outputDefinitions = recipe.getOutputDefinition();

            for (ChemicalStack chemicalOutput : outputDefinitions) {
                ResourceLocation outputChemicalId = chemicalOutput.getChemicalHolder().getKey().location();
                if (chemicalOutput.getAmount() <= 0) {
                    continue;
                }
                ChemicalSource source = new ChemicalSource(itemInput, chemicalOutput.getAmount(), holder.id(), outputChemicalId);
                List<ChemicalSource> cachedSources = chemicalSourceCache.computeIfAbsent(outputChemicalId, id -> new ArrayList<>());
                if (cachedSources.stream().noneMatch(existing -> isSameSource(existing, source))) {
                    cachedSources.add(source);
                }
            }
        }
    }

    public void clearCache() {
        chemicalSourceCache.clear();
        cachedRecipeManager = null;
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation originalId,
            ItemStackIngredient itemInput,
            ChemicalStackIngredient chemicalInput,
            ItemStackChemicalToItemStackRecipe originalRecipe,
            ChemicalSource chemicalSource,
            List<ItemStack> outputs) {

        long requiredChemicalAmount = getRequiredChemicalAmount(chemicalInput, originalRecipe, chemicalSource.chemicalId());
        if (requiredChemicalAmount <= 0) {
            return null;
        }

        long sourceAmount = chemicalSource.amount();
        if (sourceAmount <= 0) {
            return null;
        }
        long gcd = AdapterUtils.gcd(requiredChemicalAmount, sourceAmount);
        long multiplier = sourceAmount / gcd;
        long conversionCount = requiredChemicalAmount / gcd;

        String suffix = "_" + chemicalSource.recipeId().getNamespace() + "_" + chemicalSource.recipeId().getPath().replace('/', '_') + "_converted";
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + suffix
        );

        List<CountedIngredient> countedIngredients = new ArrayList<>();

        addCountedIngredient(countedIngredients, itemInput, multiplier);
        addCountedIngredient(countedIngredients, chemicalSource.ingredient(), conversionCount);

        if (countedIngredients.isEmpty()) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),
                scaleOutputs(outputs, multiplier),
                List.of(),
                AdapterUtils.mekanismMetallurgicInfuserEnergyCost(multiplier),
                AdapterUtils.mekanismMetallurgicInfuserProcessTime(multiplier),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private List<AdvancedAlloyFurnaceRecipe> createDirectRecipes(
            ResourceLocation originalId,
            ItemStackIngredient itemInput,
            ChemicalStackIngredient chemicalInput,
            ItemStackChemicalToItemStackRecipe originalRecipe,
            List<ItemStack> outputs) {

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        addCountedIngredient(countedIngredients, itemInput, 1);
        if (countedIngredients.isEmpty()) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        long requiredChemicalAmount = getEffectiveChemicalAmount(chemicalInput, originalRecipe);
        for (ChemicalStack chemicalStack : chemicalInput.getRepresentations()) {
            FluidStack chemicalFluid = getChemicalFluid(chemicalStack, requiredChemicalAmount);
            if (chemicalFluid.isEmpty()) {
                continue;
            }
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(chemicalFluid.getFluid());
            ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                    originalId.getNamespace(),
                    originalId.getPath() + "_" + fluidId.getNamespace() + "_" + fluidId.getPath().replace('/', '_') + "_chemical_converted"
            );

            recipes.add(new AdvancedAlloyFurnaceRecipe(
                    convertedId,
                    List.copyOf(countedIngredients),
                    List.of(chemicalFluid),
                    scaleOutputs(outputs, 1),
                    List.of(),
                    AdapterUtils.mekanismMetallurgicInfuserEnergyCost(1),
                    AdapterUtils.mekanismMetallurgicInfuserProcessTime(1),
                    Ingredient.EMPTY,
                    0,
                    AdapterUtils.toMoldIngredient(getMoldItem()),
                    AlloyFurnaceMode.NORMAL
            ));
        }
        return recipes;
    }

    private List<ItemStack> scaleOutputs(List<ItemStack> outputs, long multiplier) {
        List<ItemStack> scaledOutputs = new ArrayList<>();
        for (ItemStack output : outputs) {
            ItemStack scaled = output.copy();
            scaled.setCount(AdapterUtils.safeInt(output.getCount() * multiplier));
            scaledOutputs.add(scaled);
        }
        return scaledOutputs;
    }

    private FluidStack getChemicalFluid(ChemicalStack chemicalStack) {
        return getChemicalFluid(chemicalStack, chemicalStack.getAmount());
    }

    private FluidStack getChemicalFluid(ChemicalStack chemicalStack, long amount) {
        ResourceLocation chemicalId = chemicalStack.getChemicalHolder().getKey().location();
        Fluid fluid = BuiltInRegistries.FLUID.get(chemicalId);
        if (BuiltInRegistries.FLUID.getKey(fluid).equals(chemicalId)) {
            return new FluidStack(fluid, AdapterUtils.safeInt(amount));
        }
        Fluid fallbackFluid = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(chemicalId.getNamespace(), chemicalId.getPath() + "_chemical"));
        if (BuiltInRegistries.FLUID.getKey(fallbackFluid).equals(ResourceLocation.fromNamespaceAndPath(chemicalId.getNamespace(), chemicalId.getPath() + "_chemical"))) {
            return new FluidStack(fallbackFluid, AdapterUtils.safeInt(amount));
        }
        return FluidStack.EMPTY;
    }

    private long getRequiredChemicalAmount(ChemicalStackIngredient chemicalInput, ItemStackChemicalToItemStackRecipe originalRecipe, ResourceLocation chemicalId) {
        for (ChemicalStack representation : chemicalInput.getRepresentations()) {
            if (representation.getChemicalHolder().getKey().location().equals(chemicalId)) {
                return getEffectiveChemicalAmount(chemicalInput, originalRecipe);
            }
        }
        return 0;
    }

    private long getEffectiveChemicalAmount(ChemicalStackIngredient chemicalInput, ItemStackChemicalToItemStackRecipe originalRecipe) {
        long amount = chemicalInput.amount();
        if (originalRecipe.perTickUsage()) {
            return amount * AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
        }
        return amount;
    }

    @Override
    @Nullable
    public RecipeHolder<ItemStackChemicalToItemStackRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || (mergedInputs.isEmpty() && mergedFluids.isEmpty())) {
            return null;
        }
        if (mold != null && !mold.isEmpty() && !matchesMold(mold)) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ItemStackChemicalToItemStackRecipe>> recipes = recipeManager.getAllRecipesFor(
                getMekanismRecipeType()
        );

        for (RecipeHolder<ItemStackChemicalToItemStackRecipe> holder : recipes) {
            ItemStackChemicalToItemStackRecipe recipe = holder.value();

            var itemInput = recipe.getItemInput();
            if (itemInput == null || itemInput.hasNoMatchingInstances()) continue;

            var chemicalInput = recipe.getChemicalInput();
            if (chemicalInput == null) continue;

            boolean hasMainItem = matchesIngredient(mergedInputs, itemInput);
            if (!hasMainItem) continue;

            long requiredChemicalAmount = getEffectiveChemicalAmount(chemicalInput, recipe);

            for (ChemicalStack chemicalStack : chemicalInput.getRepresentations()) {
                FluidStack chemicalFluid = getChemicalFluid(chemicalStack, requiredChemicalAmount);
                if (!chemicalFluid.isEmpty() && matchesFluid(mergedFluids, chemicalFluid)) {
                    return holder;
                }
            }

            List<ChemicalSource> sources = findChemicalSources(level, chemicalInput);
            for (ChemicalSource source : sources) {
                AdvancedAlloyFurnaceRecipe converted = createRecipe(holder.id(), itemInput, chemicalInput, recipe, source, recipe.getOutputDefinition());
                if (converted != null && matchesCountedInputs(mergedInputs, converted.inputs())) {
                    return holder;
                }
            }
        }

        return null;
    }

    private boolean matchesCountedInputs(Map<Ingredient, Long> mergedInputs, List<CountedIngredient> requiredInputs) {
        for (CountedIngredient requiredInput : requiredInputs) {
            if (!AdapterUtils.hasMatchingIngredient(mergedInputs, requiredInput.ingredient(), requiredInput.count())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFluid(Map<FluidStack, Long> mergedFluids, FluidStack requiredFluid) {
        long found = 0;
        for (Map.Entry<FluidStack, Long> entry : mergedFluids.entrySet()) {
            if (FluidStack.isSameFluidSameComponents(entry.getKey(), requiredFluid)) {
                found += entry.getValue();
            }
        }
        return found >= requiredFluid.getAmount();
    }

    private void addIfUnique(List<AdvancedAlloyFurnaceRecipe> recipes, AdvancedAlloyFurnaceRecipe recipe) {
        if (findRecipeWithSameInputsOutputs(recipes, recipe) == null) {
            recipes.add(recipe);
        }
    }

    private boolean isSameSource(ChemicalSource a, ChemicalSource b) {
        return a.recipeId().equals(b.recipeId()) && a.chemicalId().equals(b.chemicalId()) && a.amount() == b.amount();
    }

    /**
     * 查找列表中是否有相同输入输出的配方
     * 用于合并重复的配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe findRecipeWithSameInputsOutputs(
            List<AdvancedAlloyFurnaceRecipe> recipes,
            AdvancedAlloyFurnaceRecipe newRecipe) {
        for (AdvancedAlloyFurnaceRecipe existing : recipes) {
            if (hasSameInputsOutputs(existing, newRecipe)) {
                return existing;
            }
        }
        return null;
    }

    /**
     * 比较两个配方是否有相同的输入和输出
     */
    private boolean hasSameInputsOutputs(AdvancedAlloyFurnaceRecipe a, AdvancedAlloyFurnaceRecipe b) {
        if (a.inputs().size() != b.inputs().size()) {
            return false;
        }
        if (a.inputFluids().size() != b.inputFluids().size()) {
            return false;
        }

        for (CountedIngredient inputA : a.inputs()) {
            boolean found = false;
            for (CountedIngredient inputB : b.inputs()) {
                if (inputA.count() == inputB.count() && AdapterUtils.areIngredientsEqual(inputA.ingredient(), inputB.ingredient())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        for (FluidStack fluidA : a.inputFluids()) {
            boolean found = false;
            for (FluidStack fluidB : b.inputFluids()) {
                if (FluidStack.isSameFluidSameComponents(fluidA, fluidB) && fluidA.getAmount() == fluidB.getAmount()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        if (a.outputs().size() != b.outputs().size()) {
            return false;
        }
        for (int i = 0; i < a.outputs().size(); i++) {
            ItemStack outputA = a.outputs().get(i);
            ItemStack outputB = b.outputs().get(i);
            if (!ItemStack.isSameItemSameComponents(outputA, outputB)) {
                return false;
            }
            if (outputA.getCount() != outputB.getCount()) {
                return false;
            }
        }

        return true;
    }

    // ========== Helper Methods ==========

    @Nullable
    private static CountedIngredient countedIngredient(ItemStackIngredient input, long multiplier) {
        Ingredient ingredient = ingredient(input);
        if (ingredient.isEmpty()) {
            return null;
        }
        return new CountedIngredient(ingredient, input.ingredient().count() * multiplier);
    }

    private static Ingredient ingredient(ItemStackIngredient input) {
        if (input == null || input.hasNoMatchingInstances()) {
            return Ingredient.EMPTY;
        }
        List<ItemStack> representations = input.getRepresentations();
        if (representations.isEmpty()) {
            return Ingredient.EMPTY;
        }
        return Ingredient.of(representations.stream().map(stack -> stack.copyWithCount(1)));
    }

    private static boolean matchesIngredient(Map<Ingredient, Long> mergedInputs, ItemStackIngredient required) {
        if (required == null || required.hasNoMatchingInstances()) {
            return false;
        }
        long requiredCount = required.ingredient().count();
        for (Map.Entry<Ingredient, Long> entry : mergedInputs.entrySet()) {
            for (ItemStack stack : entry.getKey().getItems()) {
                if (required.testType(stack) && entry.getValue() >= requiredCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addCountedIngredient(List<CountedIngredient> countedIngredients, ItemStackIngredient input, long multiplier) {
        CountedIngredient counted = countedIngredient(input, multiplier);
        if (counted == null) {
            return;
        }
        for (int i = 0; i < countedIngredients.size(); i++) {
            CountedIngredient existing = countedIngredients.get(i);
            if (AdapterUtils.areIngredientsEqual(existing.ingredient(), counted.ingredient())) {
                countedIngredients.set(i, new CountedIngredient(existing.ingredient(), existing.count() + counted.count()));
                return;
            }
        }
        countedIngredients.add(counted);
    }

    private record ChemicalSource(
            ItemStackIngredient ingredient,
            long amount,
            ResourceLocation recipeId,
            ResourceLocation chemicalId
    ) {}
}
