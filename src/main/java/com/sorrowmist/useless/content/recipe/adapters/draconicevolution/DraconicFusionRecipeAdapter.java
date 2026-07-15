package com.sorrowmist.useless.content.recipe.adapters.draconicevolution;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.api.DraconicAPI;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.StackIngredient;
import com.brandon3055.draconicevolution.init.DEContent;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        return List.of(new AdvancedAlloyFurnaceRecipe(
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
        ));
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

        ItemStack result = source.getResultItem(level == null ? null : level.registryAccess()).copy();
        if (result.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requirements, source.getCatalyst(), catalystCount(source.getCatalyst()));

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

    private static long catalystCount(Ingredient catalyst) {
        if (catalyst.getCustomIngredient() instanceof StackIngredient stackIngredient) {
            return Math.max(1, stackIngredient.getCount());
        }
        return 1L;
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

    private record ConvertedFusionData(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements) {
    }
}
