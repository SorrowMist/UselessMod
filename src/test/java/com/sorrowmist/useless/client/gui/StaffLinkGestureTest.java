package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.sorrowmist.useless.client.gui.StaffLinkViewLayout.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The screen must keep a pointer gesture with the owner that received the press. In particular,
 * dragging a row is a local MANUAL gesture and must not turn into a field drag, a hosted-widget
 * drag, or a container-slot drag just because the pointer moved over one of those things.
 */
class StaffLinkGestureTest {
    private static final int LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void textOwnerRoutesDragAndReleaseToThePressedField() throws Exception {
        Harness h = new Harness();
        h.putGesture(LEFT, "TEXT", h.field);

        assertTrue(h.screen.mouseDragged(10.5, 20.5, LEFT, 1.25, 2.5));
        verify(h.field).mouseDragged(10.5, 20.5, LEFT, 1.25, 2.5);
        verify(h.host, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(h.menu, never()).getCarried();

        when(h.host.mouseReleased(11.5, 21.5, LEFT)).thenReturn(false);
        assertTrue(h.screen.mouseReleased(11.5, 21.5, LEFT));
        verify(h.field).mouseReleased(11.5, 21.5, LEFT);
        verify(h.child, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
        verify(h.menu, never()).getCarried();
        assertTrue(h.gestures.isEmpty(), "release consumes the recorded owner");
    }

    @Test
    void hostedOwnerRoutesDragAndReleaseToTheAppleHost() throws Exception {
        Harness h = new Harness();
        h.putGesture(LEFT, "HOSTED", null);

        assertTrue(h.screen.mouseDragged(30, 40, LEFT, 2, 3));
        verify(h.host).mouseDragged(30, 40, LEFT, 2, 3);
        verify(h.field, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(h.menu, never()).getCarried();

        when(h.host.mouseReleased(50, 60, LEFT)).thenReturn(true);
        assertTrue(h.screen.mouseReleased(50, 60, LEFT));
        verify(h.host).mouseReleased(50, 60, LEFT);
        verify(h.child, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
        verify(h.menu, never()).getCarried();
        assertTrue(h.gestures.isEmpty());
    }

    @Test
    void manualOwnerIsSwallowedWithoutFieldOrContainerRouting() throws Exception {
        Harness h = new Harness();
        h.putGesture(LEFT, "MANUAL", null);

        assertTrue(h.screen.mouseDragged(70, 80, LEFT, 4, 5));
        verify(h.host, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(h.field, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(h.menu, never()).getCarried();

        assertTrue(h.screen.mouseReleased(70, 80, LEFT));
        // Release still sends the ordinary visual-state cleanup to the host, but never to super.
        verify(h.host).mouseReleased(70, 80, LEFT);
        verify(h.child, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
        verify(h.field, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
        verify(h.menu, never()).getCarried();
        assertTrue(h.gestures.isEmpty());
    }

    @Test
    void inventoryOwnerRoutesDragAndReleaseToTheContainerScreen() throws Exception {
        Harness h = new Harness();
        h.putGesture(LEFT, "INVENTORY", null);

        assertTrue(h.screen.mouseDragged(12, 22, LEFT, 1, 2));
        assertTrue(h.slot.activeChecks > 0, "the container-slot lookup was not reached");
        verify(h.menu, atLeastOnce()).getCarried();
        verify(h.host, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());

        assertTrue(h.screen.mouseReleased(12, 22, LEFT));
        // Screen/ContainerEventHandler dispatch during super.mouseReleased reaches the child.
        verify(h.child).mouseReleased(12, 22, LEFT);
        verify(h.menu, atLeastOnce()).getCarried();
        assertTrue(h.gestures.isEmpty());
    }

    @Test
    void pendingRowDragTakesPrecedenceOverAStaleTextOwner() throws Exception {
        Harness h = new Harness();
        UUID network = UUID.randomUUID();
        GlobalPos anchor = anchor(0);
        h.putGesture(LEFT, "TEXT", h.field);
        h.drag.begin(network, anchor, 50, anchorRowY(0) + 4);

        assertTrue(h.screen.mouseDragged(55, anchorRowY(0) + 8, LEFT, 5, 4));
        assertTrue(h.drag.moved());
        verify(h.field, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(h.host, never()).mouseDragged(anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble());
        verify(h.menu, never()).getCarried();
    }

    @Test
    void cancelledRowDragReleasesWithoutSelectionOrReorder() throws Exception {
        Harness h = new Harness();
        UUID network = UUID.randomUUID();
        GlobalPos a = anchor(0);
        GlobalPos b = anchor(1);
        when(h.menu.getNetworkId()).thenReturn(network);
        when(h.menu.getAnchors()).thenReturn(List.of(a, b));
        h.putGesture(LEFT, "MANUAL", null);
        h.drag.begin(network, a, 50, anchorRowY(0) + 4);

        double x = CONFIG_LEFT; // outside the anchor list
        double y = anchorRowY(2) + 4;
        assertTrue(h.screen.mouseDragged(x, y, LEFT, x - 50, y - (anchorRowY(0) + 4)));
        assertTrue(h.drag.moved());

        assertTrue(h.screen.mouseReleased(x, y, LEFT));
        assertFalse(h.drag.pending());
        verify(h.menu, never()).moveAnchorTo(any(), anyInt());
        verify(h.menu, never()).setSelection(any(), anyInt());
        verify(h.menu, never()).clearMultiSelection();
        verify(h.menu, never()).applyRouteSingle(any(), any());
        verify(h.menu, never()).defaultRouteFor(any(), anyInt());
        // Cancellation returns before hosted/field release and before super.mouseReleased.
        verify(h.host, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
        verify(h.child, never()).mouseReleased(anyDouble(), anyDouble(), anyInt());
        verify(h.menu, never()).getCarried();
        assertTrue(h.gestures.isEmpty());
    }

    @Test
    void rowVanishingMidDragCancelsWithoutSelectingTheStaleAnchor() throws Exception {
        Harness h = new Harness();
        UUID network = UUID.randomUUID();
        GlobalPos a = anchor(0);
        GlobalPos b = anchor(1);
        AtomicReference<List<GlobalPos>> anchors = new AtomicReference<>(List.of(a, b));
        when(h.menu.getNetworkId()).thenReturn(network);
        when(h.menu.getAnchors()).thenAnswer(call -> anchors.get());
        h.putGesture(LEFT, "MANUAL", null);
        h.drag.begin(network, a, 50, anchorRowY(0) + 4);

        // Jitter below the drag threshold: an ordinary click must still be validated at release.
        double x = 51;
        double y = anchorRowY(0) + 5;
        assertTrue(h.screen.mouseDragged(x, y, LEFT, 1, 1));
        assertFalse(h.drag.moved());
        anchors.set(List.of(b)); // server snapshot removed the pressed row

        assertTrue(h.screen.mouseReleased(x, y, LEFT));
        assertFalse(h.drag.pending());
        verify(h.menu, never()).moveAnchorTo(any(), anyInt());
        verify(h.menu, never()).setSelection(any(), anyInt());
        verify(h.menu, never()).clearMultiSelection();
        verify(h.menu, never()).applyRouteSingle(any(), any());
        verify(h.menu, never()).defaultRouteFor(any(), anyInt());
        verify(h.menu, never()).getCarried();
    }

    private static GlobalPos anchor(int x) {
        return GlobalPos.of(Level.OVERWORLD, new BlockPos(x, 64, 0));
    }

    /**
     * A real-screen mock keeps the production mouseDragged/mouseReleased bodies, while the fields
     * normally built in init() are supplied with mocks that expose whether a route leaked.
     */
    private static final class Harness {
        final StaffLinkScreen screen = mock(StaffLinkScreen.class, CALLS_REAL_METHODS);
        final StaffLinkMenu menu = mock(StaffLinkMenu.class);
        final AppleHost host = mock(AppleHost.class);
        final EditBox field = mock(EditBox.class);
        final GuiEventListener child = mock(GuiEventListener.class);
        final ProbeSlot slot = new ProbeSlot();
        final Map<Integer, Object> gestures = new HashMap<>();
        final StaffLinkAnchorDrag drag = new StaffLinkAnchorDrag();

        Harness() throws ReflectiveOperationException {
            field.visible = true;
            field.active = true;
            when(field.canConsumeInput()).thenReturn(true);

            // ContainerScreen.mouseDragged/mouseReleased look for a slot and then consult getCarried().
            NonNullList<Slot> slots = NonNullList.create();
            slots.add(slot);
            setField(menu, "slots", slots);
            when(menu.getCarried()).thenReturn(ItemStack.EMPTY);

            // ContainerEventHandler.mouseReleased asks children() for the widget under the pointer.
            when(child.isMouseOver(anyDouble(), anyDouble())).thenReturn(true);
            when(child.mouseReleased(anyDouble(), anyDouble(), anyInt())).thenReturn(true);

            setField(screen, "menu", menu);
            setField(screen, "appleHost", host);
            setField(screen, "pointerGestures", gestures);
            setField(screen, "anchorDrag", drag);
            for (String name : List.of("flowButton", "mediumButton", "enabledButton", "sideButton",
                    "prevNetworkButton", "nextNetworkButton", "newNetworkButton", "dissolveButton",
                    "selectAllButton", "clearSelectionButton", "applyAllButton", "saveNameButton")) {
                setField(screen, name, mock(PressableAE2Button.class));
            }
            PressableAE2Button[] routeButtons = new PressableAE2Button[StaffLinkNetwork.ROUTE_COUNT];
            Arrays.setAll(routeButtons, ignored -> mock(PressableAE2Button.class));
            setField(screen, "routeButtons", routeButtons);
            doReturn(List.of(child)).when(screen).children();
        }

        void putGesture(int button, String ownerName, EditBox gestureField) throws ReflectiveOperationException {
            Class<?> ownerType = Class.forName(StaffLinkScreen.class.getName() + "$PointerOwner");
            Object owner = Arrays.stream(ownerType.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals(ownerName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown pointer owner " + ownerName));

            Class<?> gestureType = Class.forName(StaffLinkScreen.class.getName() + "$PointerGesture");
            Constructor<?> constructor = gestureType.getDeclaredConstructor(ownerType, EditBox.class);
            constructor.setAccessible(true);
            gestures.put(button, constructor.newInstance(owner, gestureField));
        }
    }

    private static final class ProbeSlot extends Slot {
        int activeChecks;

        ProbeSlot() {
            super(mock(Container.class), 0, 10, 20);
        }

        @Override
        public boolean isActive() {
            activeChecks++;
            return true;
        }
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // The field may live in a superclass (menu, children, slots, ...).
            }
        }
        throw new NoSuchFieldException(name);
    }
}
