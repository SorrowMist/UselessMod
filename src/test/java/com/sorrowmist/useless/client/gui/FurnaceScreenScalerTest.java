package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.platform.Window;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FurnaceScreenScalerTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void smallWindowFitsEveryFurnacePanelAndLargeWindowPreservesPreference() {
        for (int panel : new int[]{178, 200, 222, 236, 246, 260, 274, 296, 306, 330, 332, 336, 340, 350}) {
            double scale = FurnaceScreenScaler.scaleForWindow(2, 480, panel);
            assertTrue(panel * scale <= 480 - 16 + 1e-9);
            assertTrue(scale >= 1 && scale <= 2);
        }
        assertEquals(3, FurnaceScreenScaler.scaleForWindow(3, 2160, 336));
        assertEquals(1, FurnaceScreenScaler.scaleForWindow(4, 200, 336));
    }

    @Test
    void allCurrentReferenceLayoutsFitBothDimensionsWithoutIncreasingThePreference() {
        for (int[] panel : new int[][]{{564, 407}, {596, 318}, {596, 336}, {596, 369},
                {456, 356}, {456, 374}, {456, 323}, {456, 347}, {456, 309}, {456, 333},
                {236, 408}, {632, 466}, {476, 359}, {396, 269}, {396, 301}, {396, 334},
                {526, 298}, {526, 335}, {260, 220}}) {
            for (int[] window : new int[][]{{854, 480}, {1280, 720}, {1600, 1000}, {1920, 1080},
                    {2560, 1440}, {3400, 1440}, {3840, 2160}, {720, 1200}}) {
                double scale = FurnaceScreenScaler.scaleForWindow(4, window[0], window[1], panel[0], panel[1]);
                assertTrue(scale >= 1 && scale <= 4);
                assertTrue(panel[0] * scale <= window[0] + 1e-9);
                assertTrue(panel[1] * scale <= window[1] + 1e-9);
                if (scale > 1) {
                    assertTrue(panel[0] * scale <= window[0] - 16 + 1e-9);
                    assertTrue(panel[1] * scale <= window[1] - 16 + 1e-9);
                }
            }
        }
    }

    @Test
    void screenshotAt3400By1440RetainsGuiThreeForTheActual466HighWirelessView() {
        assertEquals(632, StaffLinkViewLayout.PANEL_WIDTH);
        assertEquals(466, StaffLinkViewLayout.PANEL_HEIGHT);
        double scale = FurnaceScreenScaler.scaleForWindow(3, 3400, 1440,
                StaffLinkViewLayout.PANEL_WIDTH, StaffLinkViewLayout.PANEL_HEIGHT);
        assertEquals(3, scale);
        assertEquals(1896, StaffLinkViewLayout.PANEL_WIDTH * scale);
        assertEquals(1398, StaffLinkViewLayout.PANEL_HEIGHT * scale);
        assertEquals(3, FurnaceScreenScaler.scaleForWindow(3, 2560, 1440, 632, 466));
        assertEquals(2, FurnaceScreenScaler.scaleForWindow(2, 1600, 1000, 632, 466));
        assertEquals(1, FurnaceScreenScaler.scaleForWindow(1, 3400, 1440, 632, 466));
    }

    @Test
    void aOnePixelShortfallShrinksOnlyOnePixelRatherThanDroppingAnEntireGuiStep() {
        double heightFit = FurnaceScreenScaler.scaleForWindow(3, 3400, 1413, 632, 466);
        assertEquals(1397.0 / 466, heightFit, 1e-12);
        assertTrue(3 - heightFit < .003);
        double widthFit = FurnaceScreenScaler.scaleForWindow(3, 1911, 1600, 632, 466);
        assertEquals(1895.0 / 632, widthFit, 1e-12);
        assertTrue(3 - widthFit < .002);
        assertEquals(1064.0 / 466, FurnaceScreenScaler.scaleForWindow(3, 1920, 1080, 632, 466), 1e-12);
        assertEquals(704.0 / 632, FurnaceScreenScaler.scaleForWindow(4, 720, 1200, 632, 466), 1e-12);
        assertEquals(1, FurnaceScreenScaler.scaleForWindow(4, 320, 200, 632, 466));
    }

    @Test
    void fractionalFitResizesNativeCoordinatesOnceGrowsBackAndRestoresOnClose() throws Exception {
        resetScaler();
        var client = mock(Minecraft.class);
        var window = mock(Window.class);
        var wireless = mock(StaffLinkScreen.class);
        var scale = new AtomicReference<>(3.0);
        var height = new AtomicInteger(1080);
        mockSavedScale(client);
        when(window.calculateScale(eq(3), anyBoolean())).thenReturn(3);
        when(client.getWindow()).thenReturn(window);
        when(window.getGuiScale()).thenAnswer(call -> scale.get());
        when(window.getWidth()).thenReturn(3400);
        when(window.getHeight()).thenAnswer(call -> height.get());
        when(window.getGuiScaledWidth()).thenAnswer(call -> (int) Math.ceil(3400 / scale.get()));
        when(window.getGuiScaledHeight()).thenAnswer(call -> (int) Math.ceil(height.get() / scale.get()));
        doAnswer(call -> { scale.set(call.getArgument(0)); return null; }).when(window).setGuiScale(anyDouble());
        when(wireless.getXSize()).thenReturn(632);
        when(wireless.getYSize()).thenReturn(466);
        client.screen = wireless;
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            FurnaceScreenScaler.update();
            assertEquals(1064.0 / 466, scale.get(), 1e-12);
            verify(wireless).resize(client, (int) Math.ceil(3400 / scale.get()), (int) Math.ceil(1080 / scale.get()));
            clearInvocations(window, wireless);
            FurnaceScreenScaler.update();
            verify(window, never()).setGuiScale(anyDouble());
            verify(wireless, never()).resize(any(), anyInt(), anyInt());
            height.set(1440);
            FurnaceScreenScaler.update();
            assertEquals(3, scale.get());
            verify(wireless).resize(client, 1134, 480);
            height.set(1080);
            FurnaceScreenScaler.update();
            var next = mock(Screen.class);
            client.screen = next;
            FurnaceScreenScaler.update();
            assertEquals(3, scale.get());
            verify(next).resize(client, 1134, 360);
            FurnaceScreenScaler.update();
            verify(next, times(1)).resize(any(), anyInt(), anyInt());
        } finally {
            resetScaler();
        }
    }

    @Test
    void anotherScaleChangeWhileClosingIsPreserved() throws Exception {
        resetScaler();
        var client = mock(Minecraft.class);
        var window = mock(Window.class);
        var wireless = mock(StaffLinkScreen.class);
        var scale = new AtomicReference<>(3.0);
        mockSavedScale(client);
        when(window.calculateScale(eq(3), anyBoolean())).thenReturn(3);
        when(client.getWindow()).thenReturn(window);
        when(window.getGuiScale()).thenAnswer(call -> scale.get());
        when(window.getWidth()).thenReturn(1920);
        when(window.getHeight()).thenReturn(1080);
        doAnswer(call -> { scale.set(call.getArgument(0)); return null; }).when(window).setGuiScale(anyDouble());
        when(wireless.getXSize()).thenReturn(632);
        when(wireless.getYSize()).thenReturn(466);
        client.screen = wireless;
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            FurnaceScreenScaler.update();
            clearInvocations(window);
            scale.set(2.0); // A vanilla settings/window update already selected a new scale.
            client.screen = null;
            FurnaceScreenScaler.update();
            assertEquals(2, scale.get());
            verify(window, never()).setGuiScale(anyDouble());
        } finally {
            resetScaler();
        }
    }

    @Test
    void aVanillaResizeMatchingTheLastFitRestoresTheCurrentVanillaScale() throws Exception {
        resetScaler();
        var client = mock(Minecraft.class);
        var window = mock(Window.class);
        var wireless = mock(StaffLinkScreen.class);
        var scale = new AtomicReference<>(3.0);
        var nativeScale = new AtomicInteger(3);
        var height = new AtomicInteger(948);
        mockSavedScale(client);
        when(client.getWindow()).thenReturn(window);
        when(window.calculateScale(eq(3), anyBoolean())).thenAnswer(call -> nativeScale.get());
        when(window.getGuiScale()).thenAnswer(call -> scale.get());
        when(window.getWidth()).thenReturn(1280);
        when(window.getHeight()).thenAnswer(call -> height.get());
        doAnswer(call -> { scale.set(call.getArgument(0)); return null; }).when(window).setGuiScale(anyDouble());
        when(wireless.getXSize()).thenReturn(632);
        when(wireless.getYSize()).thenReturn(466);
        client.screen = wireless;
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            FurnaceScreenScaler.update();
            assertEquals(2, scale.get());
            height.set(640);
            nativeScale.set(2); // Vanilla's new result equals the previous temporary fit.
            scale.set(2.0);
            FurnaceScreenScaler.update();
            assertEquals(624.0 / 466, scale.get(), 1e-12);
            client.screen = null;
            FurnaceScreenScaler.update();
            assertEquals(2, scale.get());
            assertEquals(3, client.options.guiScale().get());
            verify(client.options.guiScale(), never()).set(anyInt());
        } finally {
            resetScaler();
        }
    }

    @SuppressWarnings("unchecked")
    private static void mockSavedScale(Minecraft client) throws Exception {
        var options = mock(net.minecraft.client.Options.class);
        net.minecraft.client.OptionInstance<Integer> scale = mock(net.minecraft.client.OptionInstance.class);
        when(scale.get()).thenReturn(3);
        when(options.guiScale()).thenReturn(scale);
        var field = Minecraft.class.getDeclaredField("options");
        field.setAccessible(true);
        field.set(client, options);
    }

    private static void resetScaler() throws Exception {
        var active = FurnaceScreenScaler.class.getDeclaredField("fitting");
        active.setAccessible(true);
        active.setBoolean(null, false);
        var applied = FurnaceScreenScaler.class.getDeclaredField("appliedScale");
        applied.setAccessible(true);
        applied.setDouble(null, 0);
    }
}
