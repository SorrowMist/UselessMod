package com.sorrowmist.useless.content.recipe.adapters.apothicflux;

import com.chuan.apothicflux.recipe.RitualCraftingRecipe;
import com.chuan.apothicflux.registry.ModRegistry;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Apothic Flux ritual-crafting recipes into alloy-furnace recipes. */
public final class RitualCraftingRecipeAdapter implements IRecipeAdapter<RitualCraftingRecipe> {
    private static final ResourceLocation INFUSED_BREATH_ID =
            ResourceLocation.fromNamespaceAndPath("apothic_enchanting", "infused_breath");
    private static final int OUTPUT_FLUID_AMOUNT = 1_000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.APOTHIC_FLUX;
    }

    @Override
    public Class<RitualCraftingRecipe> getRecipeClass() {
        return RitualCraftingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return BuiltInRegistries.ITEM.getOptional(INFUSED_BREATH_ID)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<RitualCraftingRecipe> holder, Level level) {
        AdvancedAlloyFurnaceRecipe converted = convertRecipe(holder);
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<RitualCraftingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<RitualCraftingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<RitualCraftingRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(ModRegistry.RITUAL_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = convertRecipe(holder);
            if (converted == null) continue;

            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts(converted.inputs()))) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertRecipe(
            @Nullable RecipeHolder<RitualCraftingRecipe> holder) {
        if (holder == null || holder.value() == null) return null;

        RitualCraftingRecipe source = holder.value();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.inputs());
        if (inputs.isEmpty()) return null;

        ItemStack outputItem = source.outputItem();
        List<ItemStack> outputs = outputItem == null || outputItem.isEmpty()
                ? List.of()
                : List.of(outputItem.copy());
        List<FluidStack> outputFluids = fluidOutputs(source.outputFluid());
        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            // Entity-only rituals cannot be represented by the alloy-furnace recipe model.
            return null;
        }

        ItemStack mold = getMoldItem();
        if (mold.isEmpty()) return null;

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                outputs,
                outputFluids,
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                Math.max(1, source.craftTime()),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(mold),
                AlloyFurnaceMode.NORMAL);
    }

    private static Map<Ingredient, Long> requiredCounts(List<CountedIngredient> inputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty()
                    || input.count() <= 0) {
                continue;
            }
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return required;
    }

    private static List<FluidStack> fluidOutputs(@Nullable Optional<String> outputFluid) {
        if (outputFluid == null || outputFluid.isEmpty()) return List.of();

        ResourceLocation id = ResourceLocation.tryParse(outputFluid.get());
        if (id == null) return List.of();

        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid == null || fluid == Fluids.EMPTY) return List.of();
        return List.of(new FluidStack(fluid, OUTPUT_FLUID_AMOUNT));
    }
}
