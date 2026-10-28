package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.IGuiTexture;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppleEmptyStateTest {
    @Test
    void emptySegmentedOptionsRenderWithoutLookingUpAnOption() {
        AtomicInteger selectionsRead = new AtomicInteger();
        AppleSegmented widget = new AppleSegmented(10, 20, 80, 14, List::of,
                () -> { selectionsRead.incrementAndGet(); return -1; },
                i -> fail("An empty segmented control must not select an option"));
        try (var textures = mockStatic(AppleTex.class)) {
            textures.when(() -> AppleTex.round(anyInt(), anyFloat())).thenReturn(mock(IGuiTexture.class));
            assertDoesNotThrow(() -> widget.drawInBackground(mock(GuiGraphics.class), -1, -1, 0));
        }
        assertEquals(0, selectionsRead.get());
    }

    @Test
    void emptySegmentedOptionsCannotDispatchIndexZero() {
        AtomicInteger selected = new AtomicInteger(-1);
        AppleSegmented widget = new AppleSegmented(10, 20, 80, 14, List::of, () -> -1, selected::set);
        assertFalse(widget.onClick(20, 25));
        assertEquals(-1, selected.get());
    }
}
