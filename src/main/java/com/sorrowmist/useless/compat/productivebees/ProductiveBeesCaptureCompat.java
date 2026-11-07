package com.sorrowmist.useless.compat.productivebees;

import cy.jdkdigital.productivebees.common.entity.bee.ProductiveBee;
import cy.jdkdigital.productivebees.util.BeeCreator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ProductiveBeesCaptureCompat {
    private ProductiveBeesCaptureCompat() {
    }

    /**
     * @return a configured egg for Productive Bees, an empty stack when its bee type is invalid,
     *         or {@code null} when the entity is not a Productive Bees bee
     */
    @Nullable
    public static ItemStack tryCreateSpawnEgg(LivingEntity entity) {
        if (!(entity instanceof ProductiveBee productiveBee)) {
            return null;
        }

        // 12.6.0 uses a String for both methods (BeeIngredient's type is a ResourceLocation,
        // ProductiveBee's is not). Retain the native configured egg's EntityTag/type unchanged.
        String beeType = productiveBee.getBeeType();
        if (beeType == null || beeType.isBlank() || ResourceLocation.tryParse(beeType) == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack egg = BeeCreator.getSpawnEgg(beeType);
            return egg == null || egg.isEmpty() ? ItemStack.EMPTY : egg.copy();
        } catch (RuntimeException exception) {
            // A missing native egg must not fall through to a generic/free species replacement.
            return ItemStack.EMPTY;
        }
    }
}
