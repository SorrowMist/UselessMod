package com.sorrowmist.useless.client.gui.apple;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ApplePageTabsTest {
    @Test
    void pageNumbersMoveWithSelectionAndDoNotExcludeLargeInventories() {
        for (int page = 0; page < 120; page++) {
            var tabs = ApplePageTabs.tabs(74, page, 120, 120);
            int selected = page;
            assertEquals(3, tabs.size());
            assertTrue(tabs.stream().anyMatch(t -> t.page() == selected));
            int right = 1;
            for (var tab : tabs) {
                assertEquals(right, tab.left());
                assertTrue(tab.width() > 0);
                right += tab.width();
            }
            assertEquals(73, right);
        }
    }

    @Test
    void recoveryTabKeepsReadableWidthAndItsCurrentRecoveryPage() {
        var tabs = ApplePageTabs.tabs(74, 122, 125, 120);
        assertEquals(3, tabs.size());
        var recovery = tabs.get(2);
        assertTrue(recovery.recovery());
        assertEquals(122, recovery.page());
        assertEquals(42, recovery.width());
        assertEquals(120, ApplePageTabs.tabs(74, 10, 125, 120).get(2).page());
    }

    @Test
    void clickTargetsUseTheSameTabRectanglesAsDrawing() {
        AtomicInteger target = new AtomicInteger(-1);
        ApplePageTabs pager = new ApplePageTabs(0, 0, 74, 17, () -> 0, () -> 4, () -> 3, target::set);
        for (var tab : ApplePageTabs.tabs(74, 0, 4, 3)) {
            target.set(-1);
            assertTrue(pager.onClick(tab.left() + .5, 5));
            assertEquals(tab.page() == 0 ? -1 : tab.page(), target.get());
        }
        assertFalse(pager.onClick(0, 5));
        assertFalse(pager.onClick(73, 5));
    }
}
