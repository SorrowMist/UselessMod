package com.sorrowmist.useless.compat.neoecoae;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the native nine-input workstation type in Forge ECO 20.5.0, not the large-machine type. */
public final class NeoEcoWorkstationRecipeAdapter implements IRecipeAdapter<IntegratedWorkingStationRecipe> {
    private static final ResourceLocation MOLD = new ResourceLocation("neoecoae", "integrated_working_station");

    @Override
    public String sourceId() {
        return RecipeSourceIds.NEO_ECO_AE;
    }

    @Override
    public Class<IntegratedWorkingStationRecipe> getRecipeClass() {
        return IntegratedWorkingStationRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(BuiltInRegistries.ITEM.get(MOLD));
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        ItemStack required = getMoldItem();
        return !required.isEmpty() && mold != null && !mold.isEmpty() && ItemStack.isSameItem(required, mold);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IntegratedWorkingStationRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        Converted data = convertData(holder.value());
        ItemStack mold = getMoldItem();
        if (data == null || mold.isEmpty()) {
            RateLimitedLogger.warn(LogUtils.getLogger(),
                    "Skipping unsupported Forge ECO workstation recipe (invalid data, mold or fluid predicate): {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), data.items(), data.fluids(), List.of(),
                data.itemOutputs(), data.fluidOutputs(), List.of(), holder.value().energy(),
                holder.value().processingTicks(), Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(mold)), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<IntegratedWorkingStationRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> items, Map<FluidStack, Long> fluids, @Nullable ItemStack mold) {
        if (level == null || items == null || fluids == null || !matchesMold(mold)) return List.of();
        var matches = new ArrayList<RecipeHolder<IntegratedWorkingStationRecipe>>();
        for (var holder : RecipeManagerAccess.getAllRecipesFor(
                level.getRecipeManager(), NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            Converted data = convertData(holder.value());
            if (data != null && AdapterUtils.matchesRequired(items, data.requirements())
                    && FluidIngredientAllocator.matches(data.fluids(), fluids, 1L)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static Converted convertData(@Nullable IntegratedWorkingStationRecipe source) {
        if (source == null || source.energy() < 0 || source.processingTicks() <= 0
                || source.inputItems() == null || source.inputItems().size() > 9) return null;
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        // ECO uses cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient on this version.
        // Keep each native Ingredient (including Forge custom/NBT predicates), never its display stack.
        for (var input : source.inputItems()) {
            if (input == null || input.count() <= 0 || input.ingredient() == null
                    || input.ingredient().isEmpty()) return null;
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        var sourceFluid = source.inputFluid();
        if (sourceFluid == null || sourceFluid.ingredient() == null) return null;
        List<SizedFluidIngredient> fluids = List.of();
        if (!sourceFluid.ingredient().isEmpty()) {
            FluidIngredient predicate = convertFluidPredicate(sourceFluid.ingredient().toJson());
            if (sourceFluid.amount() <= 0 || predicate == null || predicate.isEmpty()) return null;
            fluids = List.of(new SizedFluidIngredient(predicate, sourceFluid.amount()));
        }
        List<ItemStack> itemOutputs = source.itemOutput() == null || source.itemOutput().isEmpty()
                ? List.of() : List.of(source.itemOutput().copy());
        List<FluidStack> fluidOutputs = source.fluidOutput() == null || source.fluidOutput().isEmpty()
                ? List.of() : List.of(source.fluidOutput().copy());
        if ((required.isEmpty() && fluids.isEmpty()) || (itemOutputs.isEmpty() && fluidOutputs.isEmpty())) return null;
        return new Converted(required.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue())).toList(),
                required, fluids, itemOutputs, fluidOutputs);
    }

    /** Preserve live tags and alternatives, with ECO's null/empty-native-NBT equivalence. */
    @Nullable
    static FluidIngredient convertFluidPredicate(JsonElement json) {
        if (json.isJsonArray()) {
            var choices = new ArrayList<FluidIngredient>();
            for (JsonElement child : json.getAsJsonArray()) {
                FluidIngredient converted = convertFluidPredicate(child);
                if (converted == null) return null; // Never silently drop an alternative.
                choices.add(converted);
            }
            return FluidIngredient.any(choices);
        }
        if (!json.isJsonObject()) return null;
        var object = json.getAsJsonObject();
        // The furnace has no conjunction of a live fluid tag and an NBT predicate. Enumerating
        // today's tag members would change reload semantics, so explicitly refuse this case.
        if (object.has("tag") && object.has("nbt")) return null;
        FluidIngredient result = FluidIngredient.fromJson(json);
        if (object.has("nbt")) {
            var nbt = net.minecraftforge.common.crafting.CraftingHelper.getNBT(object.get("nbt"));
            if (nbt.isEmpty()) {
                var plain = new FluidStack(BuiltInRegistries.FLUID.get(
                        new ResourceLocation(object.get("fluid").getAsString())), 1);
                // ECO compares absent NBT as an empty CompoundTag; the furnace distinguishes it.
                result = FluidIngredient.any(List.of(result, FluidIngredient.nbt(true, plain)));
            }
        }
        return result;
    }

    private record Converted(List<CountedIngredient> items, Map<Ingredient, Long> requirements,
                             List<SizedFluidIngredient> fluids, List<ItemStack> itemOutputs,
                             List<FluidStack> fluidOutputs) {
    }
}
