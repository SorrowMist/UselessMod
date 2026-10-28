package com.sorrowmist.useless.compat.jei;

import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JeiDragPreviewRendererTest {
    @Test
    void absentJeiReturnsBeforeResolvingOrDrawingJeiClasses() {
        ModList mods = mock(ModList.class);
        when(mods.isLoaded("jei")).thenReturn(false);
        try (var lists = mockStatic(ModList.class)) {
            lists.when(ModList::get).thenReturn(mods);
            assertFalse(JeiDragPreviewRenderer.drawActivePreview(null, 10, 20));
            verify(mods).isLoaded("jei");
        }
    }

    @Test
    void preBootstrapCallHasNoJeiLinkageOrGraphicsRequirement() {
        try (var lists = mockStatic(ModList.class)) {
            lists.when(ModList::get).thenReturn(null);
            assertFalse(JeiDragPreviewRenderer.drawActivePreview(null, 10, 20));
        }
    }
}
