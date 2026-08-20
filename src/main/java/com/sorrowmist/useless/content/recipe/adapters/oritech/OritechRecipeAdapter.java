package com.sorrowmist.useless.content.recipe.adapters.oritech;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.OritechRecipeType;
import rearth.oritech.init.recipes.RecipeContent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Oritech processing recipes to alloy-furnace recipes. */
public final class OritechRecipeAdapter implements IRecipeAdapter<OritechRecipe> {
    private static final int TAINTED_OUTPUT_MULTIPLIER = 3;

    private static final Map<OritechRecipeType, Item> MOLD_BY_TYPE = Map.ofEntries(
            Map.entry(RecipeContent.ASSEMBLER, BlockContent.ASSEMBLER_BLOCK.asItem()),
            Map.entry(RecipeContent.ATOMIC_FORGE, BlockContent.ATOMIC_FORGE_BLOCK.asItem()),
            Map.entry(RecipeContent.CENTRIFUGE, BlockContent.CENTRIFUGE_BLOCK.asItem()),
            Map.entry(RecipeContent.CENTRIFUGE_FLUID, BlockContent.CENTRIFUGE_BLOCK.asItem()),
            Map.entry(RecipeContent.COOLER, BlockContent.COOLER_BLOCK.asItem()),
            Map.entry(RecipeContent.FOUNDRY, BlockContent.FOUNDRY_BLOCK.asItem()),
            Map.entry(RecipeContent.GRINDER, BlockContent.FRAGMENT_FORGE_BLOCK.asItem()),
            Map.entry(RecipeContent.PULVERIZER, BlockContent.PULVERIZER_BLOCK.asItem()),
            Map.entry(RecipeContent.LASER, BlockContent.LASER_ARM_BLOCK.asItem()),
            Map.entry(RecipeContent.PARTICLE_COLLISION, BlockContent.ACCELERATOR_CONTROLLER.asItem()),
            Map.entry(RecipeContent.REFINERY, BlockContent.REFINERY_BLOCK.asItem())
    );

    @Override
    public Class<OritechRecipe> getRecipeClass() {
        return OritechRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return !recipeTypesForMold(mold).isEmpty();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<OritechRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        OritechRecipe source = holder.value();
        OritechRecipeType type = source.getOriType();
        if (!MOLD_BY_TYPE.containsKey(type) || source.getTime() <= 0) {
            return List.of();
        }

        List<SizedFluidIngredient> inputFluids = convertInputFluid(source.getFluidInput());
        if (inputFluids == null) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>();
        addConverted(converted, holder, source, type, inputFluids, false);
        if (type == RecipeContent.REFINERY) {
            addConverted(converted, holder, source, type, inputFluids, true);
        }
        return converted;
    }

    @Override
    public List<RecipeHolder<OritechRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        Map<Ingredient, Long> safeInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> safeFluids = mergedFluids == null ? Map.of() : mergedFluids;
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<OritechRecipe>> matches = new ArrayList<>();

        for (OritechRecipeType type : recipeTypesForMold(mold)) {
            for (RecipeHolder<OritechRecipe> holder : manager.getAllRecipesFor(type)) {
                OritechRecipe source = holder.value();
                if (source == null || source.getOriType() != type) {
                    continue;
                }

                List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.getInputs());
                List<SizedFluidIngredient> fluids = convertInputFluid(source.getFluidInput());
                if (fluids == null || (inputs.isEmpty() && fluids.isEmpty())) {
                    continue;
                }

                Map<Ingredient, Long> requirements = new LinkedHashMap<>();
                for (CountedIngredient input : inputs) {
                    AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count());
                }
                if (!AdapterUtils.matchesRequired(safeInputs, requirements)
                        || !FluidIngredientAllocator.matches(fluids, safeFluids, 1L)) {
                    continue;
                }
                matches.add(holder);
            }
        }
        return matches;
    }

    private static void addConverted(
            List<AdvancedAlloyFurnaceRecipe> converted,
            RecipeHolder<OritechRecipe> holder,
            OritechRecipe source,
            OritechRecipeType type,
            List<SizedFluidIngredient> inputFluids,
            boolean tainted) {
        Item mold = tainted
                ? BlockContent.TAINTED_REFINERY_BLOCK.asItem()
                : MOLD_BY_TYPE.get(type);
        if (mold == null) {
            return;
        }

        int outputMultiplier = tainted ? TAINTED_OUTPUT_MULTIPLIER : 1;
        List<ItemStack> outputs = convertItemOutputs(source, outputMultiplier);
        List<FluidStack> outputFluids = convertFluidOutputs(source, outputMultiplier);
        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            return;
        }

        String suffix = tainted ? "tainted_converted" : "converted";
        converted.add(new AdvancedAlloyFurnaceRecipe(
                variantId(holder.id(), suffix),
                AdapterUtils.mergeIngredients(source.getInputs()),
                inputFluids,
                List.of(),
                outputs,
                outputFluids,
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                source.getTime(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(new ItemStack(mold)),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Nullable
    private static List<SizedFluidIngredient> convertInputFluid(
            @Nullable rearth.oritech.util.FluidIngredient source) {
        if (source == null || source.amount() <= 0) {
            return List.of();
        }
        long amount = source.amount();
        if (amount > Integer.MAX_VALUE) {
            return null;
        }

        FluidIngredient ingredient;
        if (source.hasTag()) {
            if (source.getTag() == null) {
                return null;
            }
            ingredient = FluidIngredient.tag(source.getTag());
        } else {
            Fluid fluid = source.getFluid();
            if (fluid == null || fluid == Fluids.EMPTY) {
                return null;
            }
            ingredient = FluidIngredient.single(fluid);
        }
        return List.of(new SizedFluidIngredient(ingredient, (int) amount));
    }

    private static List<ItemStack> convertItemOutputs(OritechRecipe source, int multiplier) {
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack output : source.getResults()) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            int count = AdapterUtils.safeInt((long) output.getCount() * multiplier);
            if (count > 0) {
                outputs.add(output.copyWithCount(count));
            }
        }
        return outputs;
    }

    private static List<FluidStack> convertFluidOutputs(OritechRecipe source, int multiplier) {
        List<FluidStack> outputs = new ArrayList<>();
        for (dev.architectury.fluid.FluidStack output : source.getFluidOutputs()) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            int amount = AdapterUtils.safeInt(output.getAmount() * multiplier);
            if (amount <= 0) {
                continue;
            }
            FluidStack converted = new FluidStack(output.getFluid(), amount);
            converted.applyComponents(output.getPatch());
            outputs.add(converted);
        }
        return outputs;
    }

    private static List<OritechRecipeType> recipeTypesForMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return List.of();
        }
        Item item = mold.getItem();
        if (item == BlockContent.TAINTED_REFINERY_BLOCK.asItem()) {
            return List.of(RecipeContent.REFINERY);
        }

        List<OritechRecipeType> types = new ArrayList<>();
        for (Map.Entry<OritechRecipeType, Item> entry : MOLD_BY_TYPE.entrySet()) {
            if (entry.getValue() == item) {
                types.add(entry.getKey());
            }
        }
        return types;
    }

    private static net.minecraft.resources.ResourceLocation variantId(
            net.minecraft.resources.ResourceLocation original, String suffix) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                original.getNamespace(), original.getPath() + "_" + suffix);
    }
}
