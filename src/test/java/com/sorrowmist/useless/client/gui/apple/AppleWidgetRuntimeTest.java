package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.Widget;
import com.sorrowmist.useless.client.gui.apple.core.WidgetGroup;
import com.sorrowmist.useless.client.gui.apple.ldl.LDLibWidgetRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AppleWidgetRuntimeTest {
    static Stream<AppleWidgetRuntime> runtimes() {
        return Stream.of(new AppleWidgetRuntime.Vanilla(), new LDLibWidgetRuntime());
    }

    @ParameterizedTest
    @MethodSource("runtimes")
    void dispatchesTopmostVisibleActiveWidgetAtAbsoluteCoordinates(AppleWidgetRuntime runtime) {
        WidgetGroup root = new WidgetGroup(0, 0, 200, 100);
        Hit lower = new Hit(10, 20);
        Hit upper = new Hit(10, 20);
        root.addWidget(lower);
        root.addWidget(upper);
        root.setSelfPosition(100, 50);
        assertTrue(runtime.click(root, 115, 75, 0));
        assertEquals(0, lower.clicks);
        assertEquals(1, upper.clicks);
        upper.setVisible(false);
        assertTrue(runtime.click(root, 115, 75, 0));
        assertEquals(1, lower.clicks);
        upper.setVisible(true);
        upper.setActive(false);
        assertTrue(runtime.click(root, 115, 75, 0));
        assertEquals(2, lower.clicks);
        assertFalse(runtime.click(root, 15, 25, 0));
    }

    @ParameterizedTest
    @MethodSource("runtimes")
    void followsLayoutChangesAndRebuiltChildren(AppleWidgetRuntime runtime) {
        WidgetGroup root = new WidgetGroup(0, 0, 100, 100);
        Hit original = new Hit(4, 6);
        root.addWidget(original);
        assertTrue(runtime.click(root, 5, 7, 0));
        root.setSelfPosition(30, 40);
        original.setSelfPosition(8, 10);
        assertFalse(runtime.click(root, 5, 7, 0));
        assertTrue(runtime.click(root, 39, 51, 0));
        root.clearAllWidgets();
        Hit replacement = new Hit(0, 0);
        root.addWidget(replacement);
        assertTrue(runtime.click(root, 31, 41, 0));
        assertEquals(2, original.clicks);
        assertEquals(1, replacement.clicks);
        assertTrue(runtime.drag(root, 31, 41, 0, 1, 1));
        assertTrue(runtime.scroll(root, 31, 41, 1));
        assertTrue(runtime.release(root, 31, 41, 0));
        runtime.tick(root);
        assertEquals(1, replacement.ticks);
    }

    @ParameterizedTest
    @MethodSource("runtimes")
    void modalCloseConsumesTheEntireGestureWithoutReleasingOrDraggingUnderlyingSlots(AppleWidgetRuntime runtime) {
        AppleHost host = new AppleHost(200, 120, runtime);
        Hit underlying = host.add(new Hit(10, 20));
        host.layout(400, 300, 100, 50);
        java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
        host.openPopover(new ApplePopover(50, 50, 30, 30).onClose(closed::incrementAndGet), true, this);
        assertTrue(host.mouseClicked(115, 75, 0));
        assertFalse(host.isPopoverOpen());
        assertEquals(1, closed.get());
        assertTrue(host.mouseDragged(115, 75, 0, 2, 2));
        assertTrue(host.mouseReleased(115, 75, 0));
        assertEquals(0, underlying.clicks);
        assertEquals(0, underlying.drags);
        assertEquals(0, underlying.releases);
        assertTrue(host.mouseClicked(115, 75, 0));
        assertEquals(1, underlying.clicks);
    }

    @ParameterizedTest
    @MethodSource("runtimes")
    void emptyHostedLayersDoNotRequireAModularUiAndLeaveVanillaDraggingAvailable(AppleWidgetRuntime runtime) {
        WidgetGroup root = new WidgetGroup(100, 50, 200, 120);
        assertFalse(runtime.drag(root, 115, 75, 0, 2, 2));
        // A full-size empty overlay is always present in AppleHost, even with no controls.
        root.addWidget(new WidgetGroup(0, 0, 200, 120));
        assertFalse(runtime.drag(root, 115, 75, 0, 2, 2));
        assertFalse(runtime.release(root, 115, 75, 0));
        assertFalse(runtime.scroll(root, 115, 75, 1));
        AppleHost host = new AppleHost(200, 120, runtime);
        host.layout(400, 300, 100, 50);
        assertFalse(host.mouseDragged(115, 75, 0, 2, 2));
        assertFalse(host.mouseReleased(115, 75, 0));
    }

    @ParameterizedTest
    @MethodSource("runtimes")
    void unconsumedDragsFallThroughAndConsumingTopmostWidgetsStopDispatch(AppleWidgetRuntime runtime) {
        WidgetGroup root = new WidgetGroup(100, 50, 200, 120);
        java.util.concurrent.atomic.AtomicInteger visits = new java.util.concurrent.atomic.AtomicInteger();
        root.addWidget(new Widget(10, 20, 20, 20) {
            @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
                visits.incrementAndGet();
                return false;
            }
        });
        assertFalse(runtime.drag(root, 115, 75, 0, 2, 2));
        assertEquals(1, visits.get());
        Hit top = new Hit(10, 20);
        root.addWidget(top);
        assertTrue(runtime.drag(root, 115, 75, 0, 2, 2));
        assertEquals(1, top.drags);
        assertEquals(1, visits.get());
        top.setVisible(false);
        assertFalse(runtime.drag(root, 115, 75, 0, 2, 2));
        assertEquals(1, top.drags);
        assertEquals(2, visits.get());
        root.clearAllWidgets();
        assertFalse(runtime.drag(root, 115, 75, 0, 2, 2));
    }

    @Test
    void nativeDragDispatcherSkipsInactiveChildrenBeforeInvokingTheirHandlers() {
        LDLibWidgetRuntime runtime = new LDLibWidgetRuntime();
        WidgetGroup root = new WidgetGroup(0, 0, 100, 100);
        Hit active = new Hit(10, 20);
        Hit inactive = new Hit(10, 20);
        inactive.setActive(false);
        root.addWidget(active);
        root.addWidget(inactive);
        assertTrue(runtime.drag(root, 15, 25, 0, 1, 1));
        assertEquals(1, active.drags);
        assertEquals(0, inactive.drags);
        active.setVisible(false);
        assertFalse(runtime.drag(root, 15, 25, 0, 1, 1));
    }

    private static final class Hit extends Widget {
        int clicks, ticks, drags, releases;
        Hit(int x, int y) { super(x, y, 20, 20); }
        @Override public boolean mouseClicked(double x, double y, int b) {
            if (!isMouseOverElement(x, y)) return false;
            clicks++;
            return true;
        }
        @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { drags++; return isMouseOverElement(x, y); }
        @Override public boolean mouseReleased(double x, double y, int b) { releases++; return isMouseOverElement(x, y); }
        @Override public boolean mouseWheelMove(double x, double y, double d) { return isMouseOverElement(x, y); }
        @Override public void updateScreen() { ticks++; }
    }
}
