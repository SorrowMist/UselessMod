package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.client.gui.apple.AppleDropdown;
import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffLinkScreenTest {
    // Match LinkMedium.supported(): a null-rejecting immutable list with more than two entries.
    private static final List<LinkMedium> OPTIONS = List.copyOf(List.of(
            LinkMedium.ITEM, LinkMedium.AE_ITEM, LinkMedium.FLUID, LinkMedium.ENERGY));

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void savingNamesRequiresOneBoundContainerAndNeverRenamesAMultiSelection() {
        assertFalse(StaffLinkScreen.canRenameAnchor(false, 0));
        assertFalse(StaffLinkScreen.canRenameAnchor(false, 1));
        assertTrue(StaffLinkScreen.canRenameAnchor(true, 0));
        assertTrue(StaffLinkScreen.canRenameAnchor(true, 1));
        assertFalse(StaffLinkScreen.canRenameAnchor(true, 2));
        assertFalse(StaffLinkScreen.canRenameAnchor(true, 40));
    }

    @Test
    void noSnapshotOrSelectionLeavesTheMediumUnselected() {
        StaffLinkMenu menu = mock(StaffLinkMenu.class, CALLS_REAL_METHODS);
        assertNull(menu.getSelectedMedium());
        assertEquals(-1, StaffLinkScreen.mediumOptionIndex(OPTIONS, menu.getSelectedMedium()));
    }

    @Test
    void missingMediumAndEmptyOptionsNeverInventASelection() {
        assertEquals(-1, StaffLinkScreen.mediumOptionIndex(List.of(), null));
        assertEquals(-1, StaffLinkScreen.mediumOptionIndex(List.of(), LinkMedium.ITEM));
        assertEquals(-1, StaffLinkScreen.mediumOptionIndex(OPTIONS, LinkMedium.STRESS));
    }

    @Test
    void configuredMediumKeepsItsActualPositionInSupportedMenuOrder() {
        for (int i = 0; i < OPTIONS.size(); i++) {
            assertEquals(i, StaffLinkScreen.mediumOptionIndex(OPTIONS, OPTIONS.get(i)));
        }
    }

    @Test
    void disabledDropdownRendersPlaceholderBeforeTheSnapshotArrives() throws ReflectiveOperationException {
        assertDropdownLabels(false, new LinkMedium[]{null}, new String[]{"—"});
    }

    @Test
    void dropdownSurvivesConfigurationArrivalAndRemovalBetweenFrames() throws ReflectiveOperationException {
        assertDropdownLabels(true,
                new LinkMedium[]{null, LinkMedium.ENERGY, null, LinkMedium.FLUID, LinkMedium.STRESS},
                new String[]{"—", "Energy", "—", "Fluid", "—"});
    }

    @Test
    void whitelistAndBlacklistSetExplicitStateWithoutToggling() throws Exception {
        var screen = mock(StaffLinkScreen.class, CALLS_REAL_METHODS);
        for (String name : List.of("detailPatternField", "detailOutValueField", "detailInValueField",
                "detailWhitelistButton", "detailBlacklistButton", "detailOutOpButton",
                "detailInOpButton", "detailCloseButton")) {
            var field = StaffLinkScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(screen, mock(field.getType()));
        }
        var change = StaffLinkScreen.class.getDeclaredMethod("setDetailExclude", boolean.class);
        assertNotNull(change);
        // An unopened details panel must not submit anything, even for repeated clicks.
        change.setAccessible(true);
        assertDoesNotThrow(() -> change.invoke(screen, true));
        assertDoesNotThrow(() -> change.invoke(screen, true));
        assertDoesNotThrow(() -> change.invoke(screen, false));
    }

    @Test
    void hidingAFieldClearsItsOwnFocusEvenWithoutScreenFocus() throws Exception {
        var screen = mock(StaffLinkScreen.class, CALLS_REAL_METHODS);
        var box = mock(net.minecraft.client.gui.components.EditBox.class);
        box.visible = false;
        box.active = true;
        var method = StaffLinkScreen.class.getDeclaredMethod("clearFocusIfHidden", net.minecraft.client.gui.components.EditBox.class);
        method.setAccessible(true);
        method.invoke(screen, box);
        verify(box).setFocused(false);
    }

    @Test
    void concreteJeiMarkerReplacesFocusedPatternTextRatherThanBeingOverwritten() throws Exception {
        var screen = mock(StaffLinkScreen.class, CALLS_REAL_METHODS);
        var menu = mock(StaffLinkMenu.class);
        doReturn(menu).when(screen).getMenu();
        var old = com.sorrowmist.useless.content.stafflink.LinkFilterSlot.ofPattern(
                com.sorrowmist.useless.content.stafflink.LinkFilterPattern.parse("minecraft:iron_*"))
                .withExclude(true).withLimits(10, 20);
        when(menu.getFilterMirror()).thenReturn(java.util.Collections.nCopies(18, old));
        var field = mock(net.minecraft.client.gui.components.EditBox.class);
        when(field.isFocused()).thenReturn(true);
        when(field.getValue()).thenReturn("minecraft:iron_*");
        for (var entry : java.util.Map.<String, Object>of("detailPanelOpen", true, "detailIndex", 4,
                "detailPatternField", field).entrySet()) {
            var member = StaffLinkScreen.class.getDeclaredField(entry.getKey());
            member.setAccessible(true);
            member.set(screen, entry.getValue());
        }
        screen.setFilterMarkerFromJei(4, com.sorrowmist.useless.content.stafflink.LinkFilterSlot.ofItem(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)));
        verify(field).setValue("");
        var replacement = org.mockito.ArgumentCaptor.forClass(com.sorrowmist.useless.content.stafflink.LinkFilterSlot.class);
        verify(menu).setFilterSlot(eq(4), replacement.capture());
        assertTrue(replacement.getValue().item().is(net.minecraft.world.item.Items.STONE));
        assertTrue(replacement.getValue().exclude());
        assertEquals(old.outCond(), replacement.getValue().outCond());
        assertEquals(old.inCond(), replacement.getValue().inCond());
    }

    @Test
    void releaseClearsJeiHighlightEvenWhenJeiSkipsCompletionOrAnotherModConsumesTheRelease() {
        var screen = mock(StaffLinkScreen.class);
        var event = new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(screen, 1, 2, 0);
        event.setCanceled(true);
        com.sorrowmist.useless.event.client.ClientEventBusSubscriber.onScreenMouseReleased(event);
        verify(screen).setJeiDragActive(false);
        assertTrue(event.isCanceled());
        clearInvocations(screen);
        com.sorrowmist.useless.event.client.ClientEventBusSubscriber.onScreenMouseReleased(
                new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(screen, 1, 2, 1));
        verifyNoInteractions(screen);
    }

    private void assertDropdownLabels(boolean enabled, LinkMedium[] states, String[] labels)
            throws ReflectiveOperationException {
        Minecraft client = mock(Minecraft.class);
        Font font = mock(Font.class);
        var fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(client, font);
        AtomicReference<LinkMedium> medium = new AtomicReference<>();
        AppleHost host = new AppleHost(426, 336);
        host.add(new AppleDropdown(host, 258, 77, 152, 14,
                () -> List.of(Component.literal("Item"), Component.literal("AE Item"),
                        Component.literal("Fluid"), Component.literal("Energy")),
                () -> StaffLinkScreen.mediumOptionIndex(OPTIONS, medium.get()),
                i -> fail("Rendering must not submit a configuration"))
                .plain().rightAlign().enabledWhen(() -> enabled));
        host.layout(854, 480, 214, 72);
        try (var minecraft = mockStatic(Minecraft.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            for (int i = 0; i < states.length; i++) {
                medium.set(states[i]);
                GuiGraphics graphics = mock(GuiGraphics.class);
                assertDoesNotThrow(() -> host.render(graphics, -1, -1, 0));
                String expected = labels[i];
                var rendered = org.mockito.ArgumentCaptor.forClass(net.minecraft.util.FormattedCharSequence.class);
                verify(graphics).drawString(eq(font), rendered.capture(),
                        anyInt(), anyInt(), anyInt(), eq(false));
                StringBuilder actual = new StringBuilder();
                rendered.getValue().accept((index, style, codePoint) -> {
                    actual.appendCodePoint(codePoint);
                    return true;
                });
                assertEquals(expected, actual.toString());
            }
        }
    }
}
