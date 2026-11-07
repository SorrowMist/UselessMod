package com.sorrowmist.useless.compat.productivebees;

import com.sorrowmist.useless.InventoryTestBootstrap;
import cy.jdkdigital.productivebees.common.entity.bee.ProductiveBee;
import cy.jdkdigital.productivebees.util.BeeCreator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductiveBeesCaptureCompatTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void stringBeeTypeCallsTheRealNativeSpeciesEggApi() {
        ProductiveBee bee = mock(ProductiveBee.class);
        when(bee.getBeeType()).thenReturn("minecraft:bee");
        ItemStack egg = ProductiveBeesCaptureCompat.tryCreateSpawnEgg(bee);
        assertNotNull(egg);
        assertEquals(Items.BEE_SPAWN_EGG, egg.getItem());
        assertEquals(1, egg.getCount());
    }

    @Test
    void nativeConfiguredEggPayloadIsCopiedWithoutRemovingItsEntityTypeNbt() {
        ProductiveBee bee = mock(ProductiveBee.class);
        when(bee.getBeeType()).thenReturn("productivebees:iron");
        ItemStack configured = new ItemStack(Items.BEE_SPAWN_EGG);
        // The real 12.6.0 tag-writing API uses EntityTag/type, not 1.21 components.
        BeeCreator.setTag("productivebees:iron", configured);
        configured.getOrCreateTagElement("EntityTag").putString("native_payload", "retained");
        try (var creator = mockStatic(BeeCreator.class)) {
            creator.when(() -> BeeCreator.getSpawnEgg("productivebees:iron")).thenReturn(configured);
            ItemStack result = ProductiveBeesCaptureCompat.tryCreateSpawnEgg(bee);
            assertNotSame(configured, result);
            assertEquals(configured.getTag(), result.getTag());
            result.getOrCreateTagElement("EntityTag").putString("type", "changed");
            assertEquals("productivebees:iron", configured.getTagElement("EntityTag").getString("type"));
        }
    }

    @Test
    void invalidUnresolvedOrNullNativeEggsNeverRequestTheGenericEntityFallback() {
        assertNull(ProductiveBeesCaptureCompat.tryCreateSpawnEgg(mock(LivingEntity.class)));
        ProductiveBee bee = mock(ProductiveBee.class);
        for (String type : new String[]{null, "", "not a valid id"}) {
            when(bee.getBeeType()).thenReturn(type);
            assertTrue(ProductiveBeesCaptureCompat.tryCreateSpawnEgg(bee).isEmpty());
        }
        when(bee.getBeeType()).thenReturn("productivebees:missing_test_species");
        assertTrue(ProductiveBeesCaptureCompat.tryCreateSpawnEgg(bee).isEmpty());
        try (var creator = mockStatic(BeeCreator.class)) {
            creator.when(() -> BeeCreator.getSpawnEgg("productivebees:missing_test_species")).thenReturn(null);
            assertTrue(ProductiveBeesCaptureCompat.tryCreateSpawnEgg(bee).isEmpty());
        }
    }
}
