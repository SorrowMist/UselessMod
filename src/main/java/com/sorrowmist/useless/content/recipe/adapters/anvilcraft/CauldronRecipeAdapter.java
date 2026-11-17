package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.AbstractProcessRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.NeutronIrradiationRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SolidLiquidRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SuperHeatingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import dev.dubhe.anvilcraft.util.FluidStackPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 转换可静态表达的炼药锅加工配方。 */
public final class CauldronRecipeAdapter implements IRecipeAdapter<AbstractProcessRecipe<?>> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<AbstractProcessRecipe<?>> getRecipeClass() {
        return (Class) AbstractProcessRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AbstractProcessRecipe<?>> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AbstractProcessRecipe<?> source = holder.value();
        List<Ingredient> molds = new ArrayList<>(molds(source));
        if (molds.isEmpty() || molds.stream().anyMatch(Ingredient::isEmpty)) {
            return List.of();
        }
        for (ItemIngredientPredicate catalyst : source.getCatalysts()) {
            Ingredient ingredient = AnvilItemRecipeAdapter.ingredient(catalyst);
            if (ingredient.isEmpty() || catalyst.count() != 1) return List.of();
            molds.add(ingredient);
        }
        HasCauldronSimple cauldron = source.getHasCauldron();
        FluidStackPredicate fluidPredicate = cauldron.fluid();
        if (fluidPredicate.isNegate() || fluidPredicate.component().filter(predicate -> predicate.isNegate()).isPresent() || cauldron.consume() < 0
                || cauldron.chance() != 1.0f) return List.of();
        if (cauldron.ignited()) molds.add(Ingredient.of(Items.FLINT_AND_STEEL));

        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (ItemIngredientPredicate predicate : source.getInputItems()) {
            Ingredient ingredient = AnvilItemRecipeAdapter.ingredient(predicate);
            if (ingredient.isEmpty() || predicate.count() <= 0) return List.of();
            AdapterUtils.mergeIngredient(materials, ingredient, predicate.count());
        }
        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        // 仅转换配方声明的固定产物；RoyalPreferenceOutcome 根据世界偏好和炼药锅周围物品追加的奖励无法由静态配方表达。
        for (ChanceItemStack result : source.getResultItems()) {
            if (result.stack().isEmpty() || !AnvilItemRecipeAdapter.addOutput(weighted, result)) return List.of();
        }
        var scaled = ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty()) return List.of();
        int operations = scaled.get().operations();
        List<CountedIngredient> inputs = new ArrayList<>();
        List<FluidStack> outputs = new ArrayList<>();
        long energy;
        int time;
        try {
            for (Map.Entry<Ingredient, Long> entry : materials.entrySet()) {
                inputs.add(new CountedIngredient(entry.getKey(), Math.multiplyExact(entry.getValue(), operations)));
            }
            for (FluidStack fluid : cauldron.transforms()) {
                outputs.add(fluid.copyWithAmount(Math.multiplyExact(fluid.getAmount(), operations)));
            }
            time = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
            energy = AdapterUtils.energyForProcessTime(time);
        } catch (ArithmeticException exception) {
            return List.of();
        }
        if (fluidPredicate.fluids().isEmpty()) {
            if (!acceptsEmpty(cauldron)
                    || inputs.isEmpty() && outputs.isEmpty() || scaled.get().outputs().isEmpty() && outputs.isEmpty()) {
                return List.of();
            }
            return List.of(recipe(AdapterUtils.convertedId(holder.id()), inputs, List.of(), scaled.get().outputs(),
                    outputs, energy, time, molds));
        }
        int minimum = fluidPredicate.amount().flatMap(bounds -> bounds.min()).orElse(0);
        int amount = Math.max(cauldron.consume(), Math.max(minimum, cauldron.consume() == 0 ? 1000 : 0));
        if (amount <= 0 || fluidPredicate.amount().flatMap(bounds -> bounds.max()).orElse(Integer.MAX_VALUE) < amount) {
            return List.of();
        }
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (Holder<Fluid> fluid : fluidPredicate.fluids().get()) {
            FluidStack input = new FluidStack(fluid, amount);
            fluidPredicate.component().ifPresent(predicate -> input.applyComponents(predicate.patch()));
            if (input.isEmpty() || !fluidPredicate.test(input)) continue;
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.value());
            if (fluidId == null) continue;
            List<FluidStack> variantOutputs = new ArrayList<>(outputs);
            int remainder = amount - cauldron.consume();
            if (remainder > 0 && outputs.stream().anyMatch(output -> !FluidStack.isSameFluidSameComponents(input, output))) {
                continue;
            }
            try {
                if (remainder > 0) variantOutputs.add(input.copyWithAmount(Math.multiplyExact(remainder, operations)));
                input.setAmount(Math.multiplyExact(amount, operations));
            } catch (ArithmeticException exception) {
                continue;
            }
            if (scaled.get().outputs().isEmpty() && variantOutputs.isEmpty()) continue;
            ResourceLocation base = AdapterUtils.convertedId(holder.id());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(base.getNamespace(),
                    base.getPath() + "/fluid/" + fluidId.getNamespace() + "/" + fluidId.getPath());
            LongSizedFluidIngredient fluidInput = fluidInput(input, fluidPredicate.component().isPresent() || remainder > 0);
            recipes.add(recipe(id, inputs, List.of(fluidInput),
                    scaled.get().outputs(), variantOutputs, energy, time, molds));
        }
        return List.copyOf(recipes);
    }

    static boolean acceptsEmpty(HasCauldronSimple cauldron) {
        return cauldron.consume() == 0 && cauldron.fluid().test(FluidStack.EMPTY);
    }

    static LongSizedFluidIngredient fluidInput(FluidStack input, boolean exactComponents) {
        return exactComponents
                ? new LongSizedFluidIngredient(DataComponentFluidIngredient.of(true, input), input.getAmount())
                : LongSizedFluidIngredient.from(input);
    }

    private static List<Ingredient> molds(AbstractProcessRecipe<?> source) {
        Ingredient anvil = Ingredient.of(Items.ANVIL);
        Ingredient cauldron = Ingredient.of(Items.CAULDRON);
        if (source instanceof FastCookingRecipe) return List.of(anvil, cauldron, Ingredient.of(Items.CAMPFIRE));
        if (source instanceof NeutronIrradiationRecipe) {
            return List.of(anvil, cauldron, Multiblock4DRecipeAdapter.mold("neutron_irradiator"));
        }
        if (source instanceof SuperHeatingRecipe) return List.of(anvil, cauldron, Multiblock4DRecipeAdapter.mold("heater"));
        if (source instanceof TimeWarpRecipe) return List.of(anvil, cauldron, Multiblock4DRecipeAdapter.mold("corrupted_beacon"));
        if (source instanceof SolidLiquidRecipe) return List.of(anvil, cauldron);
        return List.of();
    }

    private static AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, List<CountedIngredient> inputs,
            List<LongSizedFluidIngredient> fluids, List<ItemStack> outputs, List<FluidStack> fluidOutputs,
            long energy, int time, List<Ingredient> molds) {
        return new AdvancedAlloyFurnaceRecipe(id, inputs, fluids, List.of(), outputs, fluidOutputs, List.of(),
                energy, time, Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL);
    }
}
