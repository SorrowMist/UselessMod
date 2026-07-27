package com.sorrowmist.useless.compat;

import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EapMenuLocatorCompatTest {
    @Test
    void registrationIsIdempotentAndRoundTripsTheLocator() {
        assertDoesNotThrow(EapMenuLocatorCompat::registerLocator);
        assertDoesNotThrow(EapMenuLocatorCompat::registerLocator);

        CuriosItemLocator expected = new CuriosItemLocator("curio", 3);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MenuLocators.writeToPacket(buffer, expected);

            CuriosItemLocator decoded = assertInstanceOf(
                    CuriosItemLocator.class, MenuLocators.readFromPacket(buffer));
            assertEquals(expected.slotId(), decoded.slotId());
            assertEquals(expected.index(), decoded.index());
        } finally {
            buffer.release();
        }
    }
}
