package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import committee.nova.mods.avaritia.init.registry.enums.CollectorTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exposes the four passive neutron collector tiers as timed alloy-furnace recipes. */
public final class ReAvaritiaCollectorRecipeAdapter
        implements IRecipeAdapter<ReAvaritiaSyntheticRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.AVARITIA;
    }

    @Override
    public Class<ReAvaritiaSyntheticRecipe> getRecipeClass() {
        return ReAvaritiaSyntheticRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return tierFor(mold) != null;
    }

    @Override
    public List<RecipeHolder<ReAvaritiaSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<ReAvaritiaSyntheticRecipe>> result = new ArrayList<>();
        for (CollectorTier tier : CollectorTier.values()) {
            ItemStack output = production(tier);
            if (output.isEmpty()) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                    id(tier),
                    List.of(),
                    List.of(),
                    List.of(output),
                    List.of(),
                    AdapterUtils.DEFAULT_ENERGY,
                    Math.max(1, tier.production_ticks),
                    Ingredient.EMPTY,
                    0,
                    AdapterUtils.toMoldIngredient(collectorMold(tier)),
                    AlloyFurnaceMode.NORMAL);
            result.add(new RecipeHolder<>(converted.id(), new ReAvaritiaSyntheticRecipe(converted)));
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ReAvaritiaSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<ReAvaritiaSyntheticRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        CollectorTier tier = tierFor(mold);
        if (level == null || tier == null) {
            return List.of();
        }

        for (RecipeHolder<ReAvaritiaSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            if (holder.value().convertedRecipe().mold().test(mold)) {
                return List.of(holder);
            }
        }
        return List.of();
    }

    private static ResourceLocation id(CollectorTier tier) {
        return ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.AVARITIA,
                "neutron_collector/" + tier.name + "_converted");
    }

    private static ItemStack production(CollectorTier tier) {
        ItemStack[] items = tier.production.getItems();
        if (items == null || items.length == 0 || items[0].isEmpty()) {
            return ItemStack.EMPTY;
        }
        return items[0].copyWithCount(1);
    }

    private static ItemStack collectorMold(CollectorTier tier) {
        return switch (tier) {
            case DEFAULT -> new ItemStack(ModBlocks.neutron_collector.get());
            case DENSE -> new ItemStack(ModBlocks.dense_neutron_collector.get());
            case DENSER -> new ItemStack(ModBlocks.denser_neutron_collector.get());
            case DENSEST -> new ItemStack(ModBlocks.densest_neutron_collector.get());
        };
    }

    @Nullable
    private static CollectorTier tierFor(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return null;
        }
        for (CollectorTier tier : CollectorTier.values()) {
            if (collectorMold(tier).is(mold.getItem())) {
                return tier;
            }
        }
        return null;
    }
}
