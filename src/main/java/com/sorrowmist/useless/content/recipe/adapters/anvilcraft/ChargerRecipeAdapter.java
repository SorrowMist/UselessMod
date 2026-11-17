package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.dubhe.anvilcraft.recipe.ChargerChargingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 转换充能器配方并保留需要继承的物品组件。 */
public final class ChargerRecipeAdapter implements IRecipeAdapter<ChargerChargingRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<ChargerChargingRecipe> getRecipeClass() {
        return ChargerChargingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack[] options = Multiblock4DRecipeAdapter.mold("charger").getItems();
        return options.length == 0 ? ItemStack.EMPTY : options[0].copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChargerChargingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        ChargerChargingRecipe source = holder.value();
        Ingredient mold = Multiblock4DRecipeAdapter.mold("charger");
        if (source.getPower() >= 0 || source.getIngredient().isEmpty() || source.getResult().isEmpty()
                || mold.isEmpty()) return List.of();
        if (Multiblock4DRecipeAdapter.mold("charged_neutronium_ingot").test(source.getResult())) {
            return convertCharged(holder, level, List.of(source.getIngredient().getItems()));
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), List.of(new CountedIngredient(source.getIngredient(), 1)),
                List.of(), List.of(), List.of(source.getResult().copy()), List.of(), List.of(),
                AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, List.of(mold), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChargerChargingRecipe> holder, Level level,
            List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null) return List.of();
        if (!Multiblock4DRecipeAdapter.mold("charged_neutronium_ingot").test(holder.value().getResult())) {
            return convertAll(holder, level);
        }
        return convertCharged(holder, level, actualInputs);
    }

    private static List<AdvancedAlloyFurnaceRecipe> convertCharged(RecipeHolder<ChargerChargingRecipe> holder,
            Level level, List<ItemStack> candidates) {
        ChargerChargingRecipe source = holder.value();
        Ingredient mold = Multiblock4DRecipeAdapter.mold("charger");
        if (level == null || candidates == null || source.getPower() >= 0 || mold.isEmpty()) return List.of();
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || !source.getIngredient().test(candidate)) continue;
            ItemStack input = candidate.copyWithCount(1);
            ItemStack output = source.assemble(new SingleRecipeInput(input), level.registryAccess());
            if (output.isEmpty()) continue;
            var id = AdapterUtils.convertedId(holder.id());
            var exact = DataComponentIngredient.of(true, input);
            AdvancedAlloyFurnaceRecipe preview = new AdvancedAlloyFurnaceRecipe(id,
                    List.of(new CountedIngredient(exact, 1)), List.of(), List.of(), List.of(output), List.of(), List.of(),
                    AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                    AdapterUtils.DEFAULT_PROCESS_TIME,
                    Ingredient.EMPTY, 0, List.of(mold), AlloyFurnaceMode.NORMAL);
            var variantId = id.withSuffix("/" + AlloyFurnaceRecipeFingerprint.create(preview, level.registryAccess()));
            recipes.add(new AdvancedAlloyFurnaceRecipe(variantId, preview.inputs(), preview.inputFluids(),
                    preview.keyInputs(), preview.outputs(), preview.outputFluids(), preview.keyOutputs(),
                    preview.energy(), preview.processTime(), preview.catalyst(), preview.catalystUses(),
                    preview.molds(), preview.mode()));
        }
        return List.copyOf(recipes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<ChargerChargingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<ChargerChargingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<?> candidate : level.getRecipeManager().getRecipes()) {
            if (!(candidate.value() instanceof ChargerChargingRecipe)) continue;
            RecipeHolder<ChargerChargingRecipe> typed = (RecipeHolder<ChargerChargingRecipe>) candidate;
            if (!convertAll(typed, level).isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs, Map.of(typed.value().getIngredient(), 1L))) {
                matches.add(typed);
            }
        }
        return List.copyOf(matches);
    }
}
