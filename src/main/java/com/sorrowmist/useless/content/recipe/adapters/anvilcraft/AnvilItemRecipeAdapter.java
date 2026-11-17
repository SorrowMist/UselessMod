package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import net.minecraft.core.HolderSet;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.AbstractProcessRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.ItemCompressRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.ItemCrushRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.ItemInjectRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.MeshRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.StampingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.UnpackRecipe;
import dev.dubhe.anvilcraft.recipe.generate.MeshRecipeGeneratingCache;
import dev.anvilcraft.lib.v2.util.predicate.ChanceBlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 转换铁砧工艺中可静态表达的物品加工配方。 */
public final class AnvilItemRecipeAdapter implements IRecipeAdapter<AbstractProcessRecipe<?>> {
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
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && mold.is(Items.ANVIL);
    }

    @Override
    public List<RecipeHolder<AbstractProcessRecipe<?>>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        return new MeshRecipeGeneratingCache(level.registryAccess()).buildRecipes().orElse(List.of()).stream()
                .map(holder -> new RecipeHolder<AbstractProcessRecipe<?>>(holder.id(), holder.value())).toList();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AbstractProcessRecipe<?>> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AbstractProcessRecipe<?> source = holder.value();
        List<Ingredient> molds = molds(source);
        if (molds.isEmpty() || molds.stream().anyMatch(Ingredient::isEmpty)) return List.of();
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (ItemIngredientPredicate predicate : source.getInputItems()) {
            Ingredient ingredient = ingredient(predicate);
            if (ingredient.isEmpty() || predicate.count() <= 0) return List.of();
            ItemStack[] options = predicate.getItems();
            if (options.length == 0) return List.of();
            // 流体储罐矿车的配方会将输入组件转移至产物，静态输出无法保留该状态。
            if (source instanceof UnpackRecipe && containsFluidTankMinecart(options)) return List.of();
            AdapterUtils.mergeIngredient(materials, ingredient, predicate.count());
        }
        if (source instanceof ItemInjectRecipe) {
            Ingredient block = Multiblock4DRecipeAdapter.blockIngredient(source.getFirstInputBlock());
            if (block.isEmpty()) return List.of();
            AdapterUtils.mergeIngredient(materials, block, 1);
        }
        if (materials.isEmpty() || !source.getCatalysts().isEmpty()) return List.of();
        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        for (ChanceItemStack result : source.getResultItems()) {
            if (result.stack().isEmpty() || !addOutput(weighted, result)) return List.of();
        }
        if (source instanceof ItemInjectRecipe) {
            for (ChanceBlockState result : source.getResultBlocks()) {
                if (!(result.chance() instanceof ConstantValue chance) || !Float.isFinite(chance.value())
                        || chance.value() < 0 || chance.value() > 1
                        || !result.nbt().isEmpty() || result.state().getBlock().asItem() == Items.AIR) return List.of();
                weighted.add(new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(result.state().getBlock()),
                        1, 1, chance.value()));
                if (chance.value() < 1.0f) {
                    ItemStack[] alternatives = Multiblock4DRecipeAdapter.blockIngredient(source.getFirstInputBlock()).getItems();
                    if (alternatives.length != 1) return List.of();
                    weighted.add(new ExpectedOutputScaler.WeightedItemOutput(alternatives[0], 1, 1, 1.0 - chance.value()));
                }
            }
        }
        var scaled = ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) return List.of();
        int operations = scaled.get().operations();
        List<CountedIngredient> inputs = new ArrayList<>();
        long energy;
        int time;
        try {
            for (Map.Entry<Ingredient, Long> entry : materials.entrySet()) {
                inputs.add(new CountedIngredient(entry.getKey(), Math.multiplyExact(entry.getValue(), operations)));
            }
            time = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
            energy = AdapterUtils.energyForProcessTime(time);
        } catch (ArithmeticException exception) {
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(), scaled.get().outputs(),
                List.of(), List.of(), energy, time, Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL));
    }

    private static List<Ingredient> molds(AbstractProcessRecipe<?> source) {
        Ingredient anvil = Ingredient.of(Items.ANVIL);
        if (source instanceof ItemCompressRecipe || source instanceof ItemCrushRecipe || source instanceof ItemInjectRecipe) return List.of(anvil);
        if (source instanceof MeshRecipe) {
            ItemStack[] tables = Multiblock4DRecipeAdapter.mold("sifting_table").getItems();
            if (tables.length == 0) return List.of();
            return List.of(anvil, Ingredient.of(Items.SCAFFOLDING, tables[0].getItem()));
        }
        if (source instanceof StampingRecipe) return List.of(anvil, Multiblock4DRecipeAdapter.mold("stamping_platform"));
        if (source instanceof UnpackRecipe) {
            ItemStack[] unpacking = Multiblock4DRecipeAdapter.mold("unpacking_table").getItems();
            if (unpacking.length == 0) return List.of();
            return List.of(anvil, Ingredient.of(Items.IRON_TRAPDOOR, unpacking[0].getItem()));
        }
        return List.of();
    }

    private static boolean containsFluidTankMinecart(ItemStack[] options) {
        Ingredient minecart = Multiblock4DRecipeAdapter.mold("fluid_tank_minecart");
        for (ItemStack option : options) if (minecart.test(option)) return true;
        return false;
    }

    static boolean addOutput(List<ExpectedOutputScaler.WeightedItemOutput> outputs, ChanceItemStack result) {
        NumberProvider provider = result.count();
        int min;
        int max;
        double chance = 1;
        if (provider instanceof ConstantValue constant) {
            min = max = (int) Math.floor(constant.value());
        } else if (provider instanceof UniformGenerator uniform
                && uniform.min() instanceof ConstantValue lower && uniform.max() instanceof ConstantValue upper) {
            min = (int) Math.floor(lower.value());
            max = (int) Math.floor(upper.value());
        } else if (provider instanceof BinomialDistributionGenerator binomial
                && binomial.n() instanceof ConstantValue rolls && binomial.p() instanceof ConstantValue probability) {
            min = max = (int) Math.floor(rolls.value());
            chance = probability.value();
        } else {
            return false;
        }
        if (min < 0 || max < min || max > 99 || !Double.isFinite(chance) || chance < 0 || chance > 1) return false;
        outputs.add(new ExpectedOutputScaler.WeightedItemOutput(result.stack().copyWithCount(1), min, max, chance));
        return true;
    }

    static Ingredient ingredient(ItemIngredientPredicate predicate) {
        if (predicate.items().isEmpty() || !predicate.subPredicates().isEmpty() || predicate.getItems().length == 0) {
            return Ingredient.EMPTY;
        }
        HolderSet<Item> items = predicate.items().get();
        if (!predicate.components().alwaysMatches()) {
            return DataComponentIngredient.of(false, predicate.components(), items);
        }
        return items.unwrapKey().map(Ingredient::of).orElseGet(() -> Ingredient.of(predicate.getItems()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<AbstractProcessRecipe<?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mold == null || mold.isEmpty()) return List.of();
        List<RecipeHolder<AbstractProcessRecipe<?>>> matches = new ArrayList<>();
        for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
            if (!(candidate.value() instanceof AbstractProcessRecipe<?> source) || molds(source).size() != 1) continue;
            RecipeHolder<AbstractProcessRecipe<?>> typed = (RecipeHolder<AbstractProcessRecipe<?>>) candidate;
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(typed, level);
            if (converted.isEmpty() || !AdapterUtils.matchesMold(converted.getFirst().mold(), mold)) continue;
            Map<Ingredient, Long> required = new LinkedHashMap<>();
            for (CountedIngredient input : converted.getFirst().inputs()) {
                AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
            }
            if (AdapterUtils.matchesRequired(mergedInputs, required)) matches.add(typed);
        }
        return List.copyOf(matches);
    }
}
