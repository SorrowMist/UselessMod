package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.client.gui.ChainGroupScreen;
import com.sorrowmist.useless.client.gui.DimensionConfigScreen;
import com.sorrowmist.useless.client.gui.StaffLinkScreen;
import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.*;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JeiGhostHandlersTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <S extends Screen> IGhostIngredientHandler<S> handler(Class<S> type) {
        var registration = mock(IGuiHandlerRegistration.class);
        new JEIPlugin().registerGuiHandlers(registration);
        ArgumentCaptor<IGhostIngredientHandler> capture = ArgumentCaptor.forClass(IGhostIngredientHandler.class);
        verify(registration).addGhostIngredientHandler(eq(type), capture.capture());
        return capture.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ITypedIngredient<ItemStack> item(ItemStack value) {
        ITypedIngredient<ItemStack> typed = mock(ITypedIngredient.class);
        when(typed.getItemStack()).thenReturn(Optional.of(value));
        when(typed.getIngredient()).thenReturn(value);
        return typed;
    }

    @Test
    void inventoryAndRecipeTransferScreensAreNotMistakenForGhostEditors() {
        var registration = mock(IGuiHandlerRegistration.class);
        new JEIPlugin().registerGuiHandlers(registration);
        verify(registration, times(3)).addGhostIngredientHandler(any(), any());
    }

    @Test
    void guardedTargetsRejectClosedResizedMovedAndChangedContextsEvenForDirectAccept() {
        Minecraft client = mock(Minecraft.class);
        Screen screen = mock(Screen.class);
        client.screen = screen;
        screen.width = 800; screen.height = 500;
        var area = new AtomicReference<>(new Rect2i(30, 40, 16, 16));
        var context = new AtomicBoolean(true);
        var accepted = new AtomicBoolean();
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            var target = JeiGhostTarget.<ItemStack>guarded(screen, area::get, context::get, v -> accepted.set(true));
            assertEquals(30, target.getArea().getX());
            target.accept(new ItemStack(Items.STONE));
            assertTrue(accepted.getAndSet(false));
            context.set(false);
            target.accept(new ItemStack(Items.STONE));
            assertFalse(accepted.get());
            assertEquals(0, target.getArea().getWidth());
            context.set(true);
            area.set(new Rect2i(31, 40, 16, 16));
            target.accept(new ItemStack(Items.STONE));
            assertFalse(accepted.get());
            area.set(new Rect2i(30, 40, 16, 16));
            screen.width++;
            target.accept(new ItemStack(Items.STONE));
            assertFalse(accepted.get());
            screen.width--;
            client.screen = mock(Screen.class);
            target.accept(new ItemStack(Items.STONE));
            assertFalse(accepted.get());
        }
    }

    @Test
    void dimensionOverlaysAndNonBlocksNeverExposeUnderlyingSlots() {
        var screen = mock(DimensionConfigScreen.class);
        var handler = handler(DimensionConfigScreen.class);
        when(screen.acceptsGhostIngredients()).thenReturn(false);
        assertTrue(handler.getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true).isEmpty());
        when(screen.acceptsGhostIngredients()).thenReturn(true);
        assertTrue(handler.getTargetsTyped(screen, item(new ItemStack(Items.DIAMOND)), true).isEmpty());
        verify(screen, never()).getMenu();
    }

    @Test
    void wirelessDoesNotExposeTargetsWithoutPanelOrCompatibleRouteAndClearsDragOnCompletion() {
        var screen = mock(StaffLinkScreen.class);
        var menu = mock(StaffLinkMenu.class);
        when(screen.getMenu()).thenReturn(menu);
        var handler = handler(StaffLinkScreen.class);
        var stone = item(new ItemStack(Items.STONE));
        assertTrue(handler.getTargetsTyped(screen, stone, true).isEmpty());
        when(screen.isFilterPanelOpen()).thenReturn(true);
        assertTrue(handler.getTargetsTyped(screen, stone, true).isEmpty());
        var route = new StaffLinkRoute(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 0, false,
                LinkFlow.RELEASE, LinkMedium.ITEM, 16, 5, null, 0, List.of());
        when(menu.getSelectedConfig()).thenReturn(route);
        when(screen.jeiDropGuard()).thenReturn(() -> true);
        assertEquals(18, handler.getTargetsTyped(screen, stone, false).size());
        verify(screen, never()).setJeiDragActive(true);
        assertEquals(18, handler.getTargetsTyped(screen, stone, true).size());
        verify(screen).setJeiDragActive(true);
        clearInvocations(screen);
        handler.onComplete();
        verify(screen).setJeiDragActive(false);
        clearInvocations(screen);
        handler.onComplete();
        verifyNoInteractions(screen);
        ITypedIngredient<net.minecraftforge.fluids.FluidStack> water = mock(ITypedIngredient.class);
        when(water.getIngredient()).thenReturn(new net.minecraftforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 1000));
        // A fluid ingredient must not turn into a bucket on an item route. Container capabilities
        // themselves need Forge's transformer and are exercised by the real hidden-client cases.
        assertTrue(handler.getTargetsTyped(screen, water, true).isEmpty());
    }

    @Test
    void wirelessDetailOnlyExposesItsThreeSlotsAndStaleDropsDoNotSubmit() {
        var client = mock(Minecraft.class);
        var screen = mock(StaffLinkScreen.class);
        client.screen = screen;
        var menu = mock(StaffLinkMenu.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.isFilterPanelOpen()).thenReturn(true);
        when(screen.isDetailPanelOpen()).thenReturn(true);
        when(screen.getDetailIndex()).thenReturn(4);
        when(menu.getSelectedMedium()).thenReturn(LinkMedium.ITEM);
        var filters = new java.util.ArrayList<>(java.util.Collections.nCopies(18, LinkFilterSlot.EMPTY));
        filters.set(4, LinkFilterSlot.ofItem(new ItemStack(Items.IRON_INGOT)).withLimits(10, 20));
        when(menu.getFilterMirror()).thenReturn(filters);
        when(menu.getSelectedConfig()).thenReturn(new StaffLinkRoute(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                0, false, LinkFlow.RELEASE, LinkMedium.ITEM, 16, 5, null, 0, List.of()));
        var context = new AtomicBoolean(true);
        when(screen.jeiDropGuard()).thenReturn(context::get);
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            var targets = handler(StaffLinkScreen.class).getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true);
            assertEquals(3, targets.size());
            context.set(false);
            for (var target : targets) {
                assertEquals(0, target.getArea().getWidth());
                target.accept(new ItemStack(Items.STONE));
            }
            verify(menu, never()).setFilterSlot(anyInt(), any());
            verify(menu, never()).setFilterSlotConditionControl(anyInt(), anyBoolean(), any());
        }
    }

    @Test
    void gridReplacementPreservesBlacklistAndBothConditions() {
        var client = mock(Minecraft.class);
        var screen = mock(StaffLinkScreen.class);
        client.screen = screen;
        var menu = mock(StaffLinkMenu.class);
        when(screen.getMenu()).thenReturn(menu);
        doCallRealMethod().when(screen).setFilterMarkerFromJei(anyInt(), any());
        when(screen.isFilterPanelOpen()).thenReturn(true);
        when(screen.jeiDropGuard()).thenReturn(() -> true);
        var old = LinkFilterSlot.ofItem(new ItemStack(Items.IRON_INGOT)).withExclude(true).withLimits(10, 20);
        when(menu.getFilterMirror()).thenReturn(java.util.Collections.nCopies(18, old));
        when(menu.getSelectedConfig()).thenReturn(new StaffLinkRoute(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                0, false, LinkFlow.RELEASE, LinkMedium.ITEM, 16, 5, null, 0, List.of(old)));
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            var targets = handler(StaffLinkScreen.class).getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true);
            targets.get(5).accept(new ItemStack(Items.STONE));
            ArgumentCaptor<LinkFilterSlot> slot = ArgumentCaptor.forClass(LinkFilterSlot.class);
            verify(menu).setFilterSlot(eq(5), slot.capture());
            assertTrue(slot.getValue().item().is(Items.STONE));
            assertTrue(slot.getValue().exclude());
            assertEquals(old.outCond(), slot.getValue().outCond());
            assertEquals(old.inCond(), slot.getValue().inCond());
        }
    }

    @Test
    void disabledDetailConditionsExposeOnlyTheMarkerTarget() {
        var screen = mock(StaffLinkScreen.class);
        var menu = mock(StaffLinkMenu.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.isFilterPanelOpen()).thenReturn(true);
        when(screen.isDetailPanelOpen()).thenReturn(true);
        when(screen.getDetailIndex()).thenReturn(0);
        when(screen.jeiDropGuard()).thenReturn(() -> true);
        when(menu.getSelectedMedium()).thenReturn(LinkMedium.ITEM);
        when(menu.getFilterMirror()).thenReturn(java.util.Collections.nCopies(18,
                LinkFilterSlot.ofItem(new ItemStack(Items.IRON_INGOT))));
        when(menu.getSelectedConfig()).thenReturn(new StaffLinkRoute(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                0, false, LinkFlow.RELEASE, LinkMedium.ITEM, 16, 5, null, 0, List.of()));
        assertEquals(1, handler(StaffLinkScreen.class).getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true).size());
    }

    @Test
    void detailControlsAcceptOnlyTheirEnabledDirectionAndRetestItAtRelease() {
        var client = mock(Minecraft.class);
        var screen = mock(StaffLinkScreen.class);
        client.screen = screen;
        var menu = mock(StaffLinkMenu.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.isFilterPanelOpen()).thenReturn(true);
        when(screen.isDetailPanelOpen()).thenReturn(true);
        when(screen.getDetailIndex()).thenReturn(0);
        when(screen.jeiDropGuard()).thenReturn(() -> true);
        when(menu.getSelectedMedium()).thenReturn(LinkMedium.ITEM);
        var filters = new java.util.ArrayList<>(java.util.Collections.nCopies(18,
                LinkFilterSlot.ofItem(new ItemStack(Items.IRON_INGOT)).withLimits(10, 20)));
        when(menu.getFilterMirror()).thenReturn(filters);
        when(menu.getSelectedConfig()).thenReturn(new StaffLinkRoute(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                0, false, LinkFlow.RELEASE, LinkMedium.ITEM, 16, 5, null, 0, filters));
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            var targets = handler(StaffLinkScreen.class).getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true);
            targets.get(1).accept(new ItemStack(Items.STONE));
            targets.get(2).accept(new ItemStack(Items.STONE));
            var accepted = ArgumentCaptor.forClass(LinkFilterSlot.class);
            verify(menu).setFilterSlotConditionControl(eq(0), eq(false), accepted.capture());
            assertTrue(accepted.getValue().item().is(Items.STONE));
            verify(menu).setFilterSlotConditionControl(eq(0), eq(true), any());
            clearInvocations(menu);
            filters.set(0, filters.get(0).withConditions(LinkFilterCondition.OFF, LinkFilterCondition.OFF));
            targets.get(1).accept(new ItemStack(Items.STONE));
            targets.get(2).accept(new ItemStack(Items.STONE));
            verify(menu, never()).setFilterSlotConditionControl(anyInt(), anyBoolean(), any());
            filters.set(0, filters.get(0).withLimits(10, 20));
            when(menu.getSelectedMedium()).thenReturn(LinkMedium.CHEMICAL);
            assertEquals(1, handler(StaffLinkScreen.class).getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true).size());
        }
    }

    @Test
    void retainedWirelessTargetsUseTheRealNetworkRouteBatchAndPopoverGuard() throws Exception {
        var client = mock(Minecraft.class);
        var screen = mock(StaffLinkScreen.class, CALLS_REAL_METHODS);
        var menu = mock(StaffLinkMenu.class);
        doReturn(menu).when(screen).getMenu();
        var host = mock(com.sorrowmist.useless.client.gui.apple.AppleHost.class);
        member(screen, "menu", menu);
        member(screen, "appleHost", host);
        member(screen, "filterPanelOpen", true);
        var network = new AtomicReference<>(java.util.UUID.randomUUID());
        var originalNetwork = network.get();
        var anchor = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        var batch = new AtomicReference<>(List.of(anchor));
        var routeIndex = new java.util.concurrent.atomic.AtomicInteger();
        when(menu.getNetworkId()).thenAnswer(i -> network.get());
        when(menu.getSelectedAnchor()).thenReturn(anchor);
        when(menu.getSelectedRoute()).thenAnswer(i -> routeIndex.get());
        when(menu.getEditTargets()).thenAnswer(i -> batch.get());
        when(menu.getSelectedMedium()).thenReturn(LinkMedium.ITEM);
        when(menu.isFilterActive()).thenReturn(true);
        when(menu.getFilterMirror()).thenReturn(java.util.Collections.nCopies(18, LinkFilterSlot.EMPTY));
        when(menu.getSelectedConfig()).thenReturn(new StaffLinkRoute(anchor, 0, false,
                LinkFlow.RELEASE, LinkMedium.ITEM, 16, 5, null, 0, List.of()));
        client.screen = screen;
        try (var mc = mockStatic(Minecraft.class)) {
            mc.when(Minecraft::getInstance).thenReturn(client);
            var target = handler(StaffLinkScreen.class).getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true).get(0);
            target.accept(new ItemStack(Items.STONE));
            verify(menu).setFilterSlot(eq(0), any());
            clearInvocations(menu);
            network.set(java.util.UUID.randomUUID());
            target.accept(new ItemStack(Items.STONE));
            assertEquals(0, target.getArea().getWidth());
            network.set(originalNetwork);
            routeIndex.set(1);
            target.accept(new ItemStack(Items.STONE));
            routeIndex.set(0);
            batch.set(List.of(anchor, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 0, 0))));
            target.accept(new ItemStack(Items.STONE));
            batch.set(List.of(anchor));
            when(host.isPopoverOpen()).thenReturn(true);
            target.accept(new ItemStack(Items.STONE));
            verify(menu, never()).setFilterSlot(anyInt(), any());
        }
    }

    @Test
    void replacedGroupAtTheSameIndexCannotReceiveARetainedTarget() throws Exception {
        var screen = mock(ChainGroupScreen.class, CALLS_REAL_METHODS);
        var layout = new com.sorrowmist.useless.data.BeefToolLayout(0, List.of(), List.of(),
                List.of(List.of("minecraft:dirt"), List.of("minecraft:granite")));
        member(screen, "layout", layout);
        member(screen, "host", mock(com.sorrowmist.useless.client.gui.apple.AppleHost.class));
        member(screen, "selectedGroup", 0);
        var guard = screen.jeiDropGuard(0, true);
        assertTrue(guard.getAsBoolean());
        layout.chainGroups().remove(0);
        assertFalse(guard.getAsBoolean());
        var newGuard = screen.jeiDropGuard(0, true);
        assertTrue(newGuard.getAsBoolean());
        member(screen, "selectedGroup", 1);
        assertFalse(newGuard.getAsBoolean());
    }

    private static void member(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    void chainIgnoresNonBlocksAndScrolledOutGroups() {
        var screen = mock(ChainGroupScreen.class);
        when(screen.chainGroupCount()).thenReturn(10);
        when(screen.selectedGroupIndex()).thenReturn(-1);
        when(screen.isGroupRowVisible(anyInt())).thenAnswer(i -> (int) i.getArgument(0) >= 2);
        when(screen.jeiDropGuard(anyInt(), anyBoolean())).thenReturn(() -> true);
        var handler = handler(ChainGroupScreen.class);
        assertTrue(handler.getTargetsTyped(screen, item(new ItemStack(Items.DIAMOND)), true).isEmpty());
        assertEquals(8, handler.getTargetsTyped(screen, item(new ItemStack(Items.STONE)), true).size());
    }
}
