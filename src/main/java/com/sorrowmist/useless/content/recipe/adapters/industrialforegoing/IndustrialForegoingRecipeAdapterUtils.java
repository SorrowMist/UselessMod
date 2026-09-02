package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.fluid.OreTitaniumFluidType;
import com.buuz135.industrial.module.ModuleCore;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class IndustrialForegoingRecipeAdapterUtils {
    private IndustrialForegoingRecipeAdapterUtils() {
    }

    static List<ResourceLocation> validRawMaterialTags() {
        return BuiltInRegistries.ITEM.getTagNames()
                .map(tag -> tag.location())
                .filter(IndustrialForegoingRecipeAdapterUtils::isValidRawMaterialTag)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    static boolean isValidRawMaterialTag(ResourceLocation id) {
        return id != null
                && "c".equals(id.getNamespace())
                && id.getPath().startsWith("raw_materials/")
                && OreTitaniumFluidType.isValid(id);
    }

    static TagKey<Item> itemTag(ResourceLocation id) {
        return TagKey.create(Registries.ITEM, id);
    }

    static FluidStack rawOreMeat(ResourceLocation id, int amount) {
        return OreTitaniumFluidType.getFluidWithTag(ModuleCore.RAW_ORE_MEAT, amount, id);
    }

    static FluidStack fermentedOreMeat(ResourceLocation id, int amount) {
        return OreTitaniumFluidType.getFluidWithTag(ModuleCore.FERMENTED_ORE_MEAT, amount, id);
    }

    static String tagPath(ResourceLocation id) {
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    static boolean matchesItems(List<CountedIngredient> required,
                                Map<Ingredient, Long> available) {
        if (required == null || required.isEmpty()) {
            return available == null || available.isEmpty();
        }
        if (available == null || available.isEmpty()) return false;
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (CountedIngredient input : required) {
            if (input != null && input.ingredient() != null
                    && !input.ingredient().isEmpty() && input.count() > 0) {
                AdapterUtils.mergeIngredient(requirements, input.ingredient(), input.count());
            }
        }
        return AdapterUtils.matchesRequired(available, requirements);
    }

    static boolean matches(AdvancedAlloyFurnaceRecipe recipe,
                           Map<Ingredient, Long> availableItems,
                           Map<FluidStack, Long> availableFluids) {
        return recipe != null
                && matchesItems(recipe.inputs(), availableItems)
                && FluidIngredientAllocator.matchesLong(
                recipe.inputFluids(), availableFluids == null ? Map.of() : availableFluids, 1L);
    }

    static List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> holders(
            List<AdvancedAlloyFurnaceRecipe> recipes) {
        List<RecipeHolder<IndustrialForegoingSyntheticRecipe>> result =
                new ArrayList<>(recipes.size());
        for (AdvancedAlloyFurnaceRecipe recipe : recipes) {
            if (recipe != null) {
                result.add(new RecipeHolder<>(recipe.id(),
                        new IndustrialForegoingSyntheticRecipe(recipe)));
            }
        }
        return List.copyOf(result);
    }

    static int positive(int value) {
        return Math.max(1, value);
    }

    static long energyPerTick(int powerPerTick, int processTime) {
        return Math.max(1L, (long) positive(powerPerTick) * positive(processTime));
    }

    static long multiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    static int scaledLatexAmount(FluidStack output, long sourceUnitAmount) {
        if (output == null || output.isEmpty() || output.getAmount() <= 0) return 0;
        long amount = multiply(output.getAmount(), sourceUnitAmount);
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }
}
