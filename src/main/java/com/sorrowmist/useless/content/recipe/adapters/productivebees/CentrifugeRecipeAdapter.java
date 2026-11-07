package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.productivebees.ProductiveBeesCentrifugeIngredient;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/** Static, unupgraded basic-centrifuge recipes, using the existing expected-output batch policy. */
public final class CentrifugeRecipeAdapter implements IRecipeAdapter<CentrifugeRecipe> {
    private static final ResourceLocation MOLD = new ResourceLocation("productivebees", "centrifuge");

    @Override
    public Class<CentrifugeRecipe> getRecipeClass() {
        return CentrifugeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = ForgeRegistries.ITEMS.getValue(MOLD);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        ItemStack expected = getMoldItem();
        return !expected.isEmpty() && mold != null && !mold.isEmpty() && mold.is(expected.getItem());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CentrifugeRecipe> holder, Level level) {
        if (holder == null) return List.of();
        CentrifugeRecipe source = holder.value();
        if (source.getClass() != CentrifugeRecipe.class) {
            return ProductiveBeesAdapterUtils.unsupported(holder,
                    "Custom centrifuge matching/assembly may depend on the world or inventory");
        }
        if (!ProductiveBeesCentrifugeIngredient.isRegistered()) {
            return ProductiveBeesAdapterUtils.unsupported(holder, "Native centrifuge input serializer is not registered");
        }
        try {
            ItemStack mold = getMoldItem();
            ProductiveBeesAdapterUtils.require(!mold.isEmpty(), "Missing native centrifuge mold");
            Ingredient input = ProductiveBeesAdapterUtils.centrifugeInput(source.ingredient);
            Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ProductiveBeesAdapterUtils.centrifugeOutputs(source);
            ProductiveBeesAdapterUtils.require(scaled.isPresent(), "Invalid/overflowing native output batch");
            int operations = scaled.get().operations();
            FluidStack fluid = ProductiveBeesAdapterUtils.centrifugeFluid(source, operations);
            ProductiveBeesAdapterUtils.require(!scaled.get().outputs().isEmpty() || !fluid.isEmpty(), "No native output");
            int nativeTime = source.getProcessingTime();
            ProductiveBeesAdapterUtils.require(nativeTime > 0, "Nonpositive native processing time");
            OptionalInt time = ExpectedOutputScaler.multiplyToInt(nativeTime, operations);
            ProductiveBeesAdapterUtils.require(time.isPresent(), "Overflowing native processing time batch");
            long energy = Math.multiplyExact((long) AdapterUtils.DEFAULT_ENERGY, operations);

            return List.of(new AdvancedAlloyFurnaceRecipe(
                    AdapterUtils.convertedId(holder.id()),
                    List.of(new CountedIngredient(input, operations)),
                    List.of(),
                    scaled.get().outputs(),
                    fluid.isEmpty() ? List.of() : List.of(fluid),
                    energy,
                    time.getAsInt(),
                    Ingredient.EMPTY,
                    0,
                    AdapterUtils.toMoldIngredient(mold),
                    AlloyFurnaceMode.NORMAL));
        } catch (RuntimeException exception) {
            return ProductiveBeesAdapterUtils.unsupported(holder, exception.getMessage());
        }
    }

    @Override
    public List<RecipeHolder<CentrifugeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (mergedInputs == null || mergedInputs.isEmpty()) return List.of();
        return find(level, mold, converted -> {
            CountedIngredient input = converted.inputs().get(0);
            return ItemIngredientAllocator.matches(mergedInputs, Map.of(input.ingredient(), input.count()));
        });
    }

    @Override
    public List<RecipeHolder<CentrifugeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        List<GenericStack> keys = new ArrayList<>();
        if (mergedKeys != null) {
            mergedKeys.forEach((key, count) -> {
                if (key instanceof AEItemKey && count != null && count > 0L) keys.add(new GenericStack(key, count));
            });
        }
        // mergedInputs already includes those keys in the manager. Do not count it again here,
        // and do not replace a concrete NBT/capability-bearing supply with its display items.
        return find(level, mold, converted -> ItemIngredientAllocator.matches(
                converted.inputs(), actualInputs, keys, 1L));
    }

    private List<RecipeHolder<CentrifugeRecipe>> find(
            Level level, @Nullable ItemStack mold, Predicate<AdvancedAlloyFurnaceRecipe> matches) {
        if (level == null || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        if (manager == null) return List.of();
        List<RecipeHolder<CentrifugeRecipe>> result = new ArrayList<>();
        // Read the live native manager, without a cache or synthetic/JEI recipes. The exact-class
        // check must precede any overridable recipe method, including getId/getType.
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (recipe.getClass() != CentrifugeRecipe.class) continue;
            CentrifugeRecipe source = (CentrifugeRecipe) recipe;
            if (source.id == null) continue;
            RecipeHolder<CentrifugeRecipe> holder = new RecipeHolder<>(source.id, source);
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (!converted.isEmpty() && matches.test(converted.get(0))) result.add(holder);
        }
        return List.copyOf(result);
    }
}
