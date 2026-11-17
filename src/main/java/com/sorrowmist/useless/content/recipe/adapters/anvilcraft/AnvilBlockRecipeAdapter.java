package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.lib.v2.util.predicate.ChanceBlockState;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.AbstractProcessRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.BlockCompressRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.BlockCrushRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 转换铁砧触发的方块压合和方块粉碎配方。 */
public final class AnvilBlockRecipeAdapter implements IRecipeAdapter<AbstractProcessRecipe<?>> {
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
    public ItemStack getMoldItem() {
        return new ItemStack(Items.ANVIL);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AbstractProcessRecipe<?>> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        AbstractProcessRecipe<?> source = holder.value();
        if (!(source instanceof BlockCompressRecipe) && !(source instanceof BlockCrushRecipe)) return List.of();

        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (BlockStatePredicate predicate : source.getInputBlocks()) {
            Ingredient ingredient = Multiblock4DRecipeAdapter.blockIngredient(predicate);
            if (ingredient.isEmpty()) return List.of();
            AdapterUtils.mergeIngredient(materials, ingredient, 1L);
        }
        if (materials.isEmpty()) return List.of();

        List<ExpectedOutputScaler.WeightedItemOutput> outputs = new ArrayList<>();
        for (ChanceBlockState result : source.getResultBlocks()) {
            if (!(result.chance() instanceof ConstantValue chance) || !result.nbt().isEmpty()
                    || result.state().getBlock().asItem() == Items.AIR) return List.of();
            double probability = chance.value();
            if (!Double.isFinite(probability) || probability < 0 || probability > 1) return List.of();
            outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    new ItemStack(result.state().getBlock()), 1, 1, probability));
        }
        var scaled = ExpectedOutputScaler.scale(outputs);
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
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(),
                scaled.get().outputs(), List.of(), List.of(), energy, time,
                Ingredient.EMPTY, 0, List.of(Ingredient.of(Items.ANVIL)), AlloyFurnaceMode.NORMAL));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<AbstractProcessRecipe<?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<AbstractProcessRecipe<?>>> matches = new ArrayList<>();
        for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
            if (!(candidate.value() instanceof BlockCrushRecipe)
                    && !(candidate.value() instanceof BlockCompressRecipe)) continue;
            RecipeHolder<AbstractProcessRecipe<?>> typed = (RecipeHolder<AbstractProcessRecipe<?>>) candidate;
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(typed, level);
            if (converted.isEmpty()) continue;
            Map<Ingredient, Long> required = new LinkedHashMap<>();
            for (CountedIngredient input : converted.getFirst().inputs()) {
                AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
            }
            if (AdapterUtils.matchesRequired(mergedInputs, required)) matches.add(typed);
        }
        return List.copyOf(matches);
    }
}
