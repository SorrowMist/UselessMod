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

        ResourceLocation beeType = productiveBee.getBeeType();
        return beeType == null ? ItemStack.EMPTY : BeeCreator.getSpawnEgg(beeType);
    }
}
