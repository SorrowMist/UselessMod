package com.sorrowmist.useless.client.gui.apple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppleTextLayoutTest {
    @Test
    void compactStatusBadgeLeavesThreePixelsAboveAndBelowItsText() {
        var m = AppleTextLayout.measure(120, 14, 104, 9, 0, 8, 3);
        assertEquals(8f / 9, m.scale(), 0.0001f);
        assertEquals(3, m.textY(), 0.0001f);
        assertEquals(3, 14 - m.textY() - 9 * m.scale(), 0.0001f);
        assertTrue(m.startX(120, 104) >= 8);
    }

    @Test
    void iconAndTextHaveExactlyTheSameVerticalCenter() {
        for (int h : new int[]{12, 14, 16, 18, 20, 21, 24}) {
            var m = AppleTextLayout.measure(180, h, 90, 9, 8, 8, 3);
            assertEquals(h / 2f, m.textY() + 9 * m.scale() / 2, 0.0001f);
            assertEquals(h / 2f, m.iconY() + m.iconSize() / 2f, 0.0001f);
        }
    }

    @Test
    void moderatelyLongActionShrinksBeforeTruncating() {
        var m = AppleTextLayout.measure(112, 20, 100, 9, 8, 8, 3);
        assertEquals(0.84f, m.scale(), 0.0001f);
        assertTrue(m.textLimit() >= 99);
        assertEquals(8, m.startX(112, 100), 0.0001f);
        assertEquals(4, m.gap());
    }

    @Test
    void veryLongTextKeepsReadableScaleAndTruncationBudget() {
        var m = AppleTextLayout.measure(100, 20, 300, 9, 8, 8, 3);
        assertEquals(0.8f, m.scale());
        assertEquals(90, m.textLimit());
        assertTrue(m.startX(100, m.textLimit()) >= 8);
        assertTrue(m.contentWidth(m.textLimit()) <= 84);
    }

    @Test
    void iconOnlyButtonDoesNotReserveAnInvisibleTextGap() {
        var m = AppleTextLayout.measure(24, 20, 0, 9, 8, 4, 3);
        assertEquals(0, m.gap());
        assertEquals(8, m.contentWidth(0));
        assertEquals(8, m.startX(24, 0));
    }

    @Test
    void shortLabelsRemainAtFullScaleInNormalHeightButtons() {
        var m = AppleTextLayout.measure(140, 20, 64, 9, 8, 8, 3);
        assertEquals(1f, m.scale());
        assertEquals(32, m.startX(140, 64));
    }

    @Test
    void emptyOrZeroHeightContentNeverProducesNegativeMetrics() {
        var m = AppleTextLayout.measure(0, 0, 100, 9, 8, 8, 3);
        assertEquals(0, m.scale());
        assertEquals(0, m.textLimit());
        assertEquals(0, m.iconSize());
    }

    @Test
    void badgeUsesFourteenPixelHeightWithoutChangingItsAllocatedWidth() {
        var badge = new AppleBadge(10, 20, 164, () -> null, () -> 0);
        assertEquals(14, badge.getSizeHeight());
        assertEquals(164, badge.getSizeWidth());
    }
}
