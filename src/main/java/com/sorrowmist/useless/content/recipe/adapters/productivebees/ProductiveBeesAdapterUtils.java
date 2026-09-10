package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredient;
import cy.jdkdigital.productivebees.common.entity.bee.ConfigurableBee;
import cy.jdkdigital.productivebees.init.ModTags;
import cy.jdkdigital.productivebees.setup.BeeReloadListener;
import cy.jdkdigital.productivebees.util.BeeCreator;
import cy.jdkdigital.productivelib.common.recipe.TagOutputRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class ProductiveBeesAdapterUtils {
    private ProductiveBeesAdapterUtils() {
    }

    static Optional<ExpectedOutputScaler.ScaledOutputs> scaleOutputs(
            Map<ItemStack, TagOutputRecipe.ChancedOutput> outputs) {
        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        for (Map.Entry<ItemStack, TagOutputRecipe.ChancedOutput> entry : outputs.entrySet()) {
            TagOutputRecipe.ChancedOutput value = entry.getValue();
            weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    entry.getKey(), value.min(), value.max(), value.chance()
            ));
        }
        return ExpectedOutputScaler.scale(weightedOutputs);
    }

    @Nullable
    static BeeIngredient resolveBee(@Nullable Supplier<BeeIngredient> supplier) {
        if (supplier == null) return null;
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static ItemStack spawnEgg(@Nullable BeeIngredient bee) {
        if (bee == null || bee.getBeeType() == null) return ItemStack.EMPTY;
        try {
            return BeeCreator.getSpawnEgg(bee.getBeeType());
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    static Ingredient breedingIngredient(ResourceLocation beeType) {
        CompoundTag data = BeeReloadListener.INSTANCE.getData(beeType);
        return data == null
                ? Ingredient.of(ModTags.DEFAULT_BREEDING)
                : ConfigurableBee.getBreedingIngredientFromString(data.getString("breedingItem"));
    }

    static int breedingItemCount(ResourceLocation beeType) {
        CompoundTag data = BeeReloadListener.INSTANCE.getData(beeType);
        return data == null ? 1 : data.getInt("breedingItemCount");
    }
}
