package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.AppleStepper;
import com.sorrowmist.useless.client.gui.apple.AppleTextField;
import com.sorrowmist.useless.client.gui.apple.core.Widget;
import com.sorrowmist.useless.client.gui.apple.core.WidgetGroup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppleInputTest {
    private Font fixedWidthFont() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0);
            int length = Math.min(text.length(), Math.max(0, call.<Integer>getArgument(1) / 6));
            return text.substring(0, length);
        });
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> {
            String text = call.getArgument(0);
            int length = Math.min(text.length(), Math.max(0, call.<Integer>getArgument(1) / 6));
            return call.<Boolean>getArgument(2) ? text.substring(text.length() - length) : text.substring(0, length);
        });
        return font;
    }

    @Test
    void shortRightAlignedTextKeepsTheEntireDecoratedHitBox() {
        AppleEditBox box = new AppleEditBox(fixedWidthFont(), 90, 23, 7, 14, Component.empty());
        box.setDecorationBounds(10, 20, 86, 14);
        assertTrue(box.clicked(10, 20));
        assertTrue(box.clicked(95.99, 33.99));
        assertFalse(box.clicked(9.99, 20));
        assertFalse(box.clicked(96, 20));
        assertFalse(box.clicked(10, 34));
        box.active = false;
        assertFalse(box.clicked(20, 24));
        box.active = true;
        box.setVisible(false);
        assertFalse(box.isMouseOver(20, 24));
        assertFalse(box.clicked(20, 24));
    }

    @Test
    void decorationAndTextMoveTogetherBeforeTheFirstRenderAndAfterResize() {
        AppleEditBox box = new AppleEditBox(fixedWidthFont(), 0, 0, 50, 12, Component.empty());
        WidgetGroup root = new WidgetGroup(100, 200, 300, 300);
        root.addWidget(new AppleTextField(10, 20, 80, 14, box));
        root.updateLayout();
        assertEquals(116, box.getX());
        assertEquals(224, box.getY());
        assertTrue(box.clicked(110, 220));
        root.setSelfPosition(20, 30);
        root.updateLayout();
        assertEquals(36, box.getX());
        assertEquals(54, box.getY());
        assertTrue(box.clicked(30, 50));
        assertFalse(box.clicked(110, 220));
    }

    @Test
    void clickUsesTheSameScrolledTextWindowAsRendering() {
        AppleEditBox box = new AppleEditBox(fixedWidthFont(), 10, 20, 18, 12, Component.empty());
        box.setBordered(false);
        box.setValue("0123456789");
        GuiGraphics graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new com.mojang.blaze3d.vertex.PoseStack());
        box.renderWidget(graphics, 0, 0, 0);
        box.onClick(10, 20);
        assertEquals(7, box.getCursorPosition());
        box.onClick(28, 20);
        assertEquals(10, box.getCursorPosition());
    }

    @Test
    void largeTemperatureTextUsesScaledMetricsForScrollingClickingAndDragging() {
        AppleEditBox box = new AppleEditBox(fixedWidthFont(), 10, 20, 27, 18, Component.empty()).textScale(1.5f);
        box.setBordered(false);
        box.setValue("0123456789");
        GuiGraphics graphics = mock(GuiGraphics.class);
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        when(graphics.pose()).thenReturn(pose);
        box.renderWidget(graphics, 0, 0, 0);
        assertEquals(new org.joml.Matrix4f(), pose.last().pose());
        box.onClick(10, 20);
        assertEquals(7, box.getCursorPosition());
        box.setFocused(true);
        box.onDrag(28, 20, 18, 0);
        assertEquals(9, box.getCursorPosition());
        assertEquals("78", box.getHighlighted());
        assertThrows(IllegalArgumentException.class, () -> box.textScale(0));
        assertThrows(IllegalArgumentException.class, () -> box.textScale(Float.NaN));
    }

    @Test
    void draggingSelectsTheCorrectRepeatedCharacters() {
        AppleEditBox box = new AppleEditBox(fixedWidthFont(), 10, 20, 60, 12, Component.empty());
        box.setBordered(false);
        box.setValue("11111");
        box.onClick(16, 20);
        box.setFocused(true);
        box.onDrag(34, 20, 18, 0);
        assertEquals(4, box.getCursorPosition());
        assertEquals("111", box.getHighlighted());
    }

    @Test
    void enlargedTextReservesUnitSpaceWithoutShrinkingTheDecoratedHitTarget() {
        var box = new AppleEditBox(fixedWidthFont(), 0, 0, 220, 28, Component.empty());
        box.textScale(1.5f);
        var field = new AppleTextField(10, 20, 220, 28, box, 8).trailingInset(14);
        field.updateLayout();
        assertEquals(18, box.getX());
        // 12px scaled glyphs centered in 28px, plus the shared 1px baseline offset.
        assertEquals(29, box.getY());
        assertEquals(190, box.getWidth());
        assertTrue(box.isMouseOver(229, 30));
        assertFalse(box.isMouseOver(231, 30));
    }

    @Test
    void leadingIconAlwaysReservesTextSpaceWithoutReducingTheHitTarget() {
        var box = new AppleEditBox(fixedWidthFont(), 0, 0, 80, 14, Component.empty());
        var field = new AppleTextField(10, 20, 80, 14, box).icon(AppleUi.Icon.SEARCH);
        field.updateLayout();
        assertEquals(27, box.getX());
        assertEquals(57, box.getWidth());
        assertTrue(box.clicked(10, 20));
        assertTrue(box.clicked(89, 33));
        field.setSelfPosition(30, 40);
        field.updateLayout();
        assertEquals(47, box.getX());
        assertTrue(box.clicked(30, 40));
        assertFalse(box.clicked(10, 20));
    }

    @Test
    void decoratedInputPaintsOneBackgroundAndKeepsCursorAndHitTargetAligned() {
        var box = new AppleEditBox(fixedWidthFont(), 0, 0, 157, 16, Component.empty());
        var field = new AppleTextField(168, 111, 157, 16, box);
        field.updateLayout();
        GuiGraphics graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new com.mojang.blaze3d.vertex.PoseStack());
        try (var assets = mockStatic(DesignAssets.class)) {
            field.drawInBackground(graphics, -1, -1, 0);
            box.renderWidget(graphics, -1, -1, 0);
            assets.verify(() -> DesignAssets.draw(graphics, "field_default_200", 168, 111, 157, 16));
            assets.verifyNoMoreInteractions(); // The native input must not paint another offset field.
        }
        assertTrue(box.clicked(168, 111));
        assertTrue(box.clicked(324.9, 126.9));
        assertFalse(box.clicked(325, 111));
        box.setValue("minecraft:stone");
        box.setFocused(true);
        field.setSelfPosition(168, 237);
        field.updateLayout();
        assertEquals("minecraft:stone", box.getValue());
        assertTrue(box.isFocused());
        assertTrue(box.clicked(168, 237));
        assertFalse(box.clicked(168, 111));
    }

    @Test
    void grayDisabledFieldsKeepTheirBackgroundWithoutAcceptingInput() {
        var box = new AppleEditBox(fixedWidthFont(), 0, 0, 100, 16, Component.empty());
        box.active = false;
        box.setEditable(false);
        var field = new AppleTextField(10, 20, 100, 16, box).grayWhenDisabled();
        GuiGraphics graphics = mock(GuiGraphics.class);
        try (var assets = mockStatic(DesignAssets.class)) {
            field.drawInBackground(graphics, -1, -1, 0);
            assets.verify(() -> DesignAssets.draw(graphics, "field_default_200", 10, 20, 100, 16));
        }
        assertFalse(box.clicked(20, 25));
        assertFalse(box.canConsumeInput());
        var stepper = new AppleStepper(10, 20, 128, 16, box,
                () -> fail("disabled minus"), () -> fail("disabled plus"))
                .trailingButtons().grayWhenDisabled().enabledWhen(() -> false);
        try (var assets = mockStatic(DesignAssets.class)) {
            stepper.drawInBackground(graphics, -1, -1, 0);
            assets.verify(() -> DesignAssets.draw(graphics, "field_default_200", 10, 20, 88, 16));
        }
        assertFalse(stepper.mouseClicked(130, 25, 0));
        assertFalse(box.active);
        assertFalse(box.clicked(20, 25));
    }

    @Test
    void hiddenStepperHidesAndUnfocusesItsSeparatelyRegisteredEditBox() {
        EditBox box = mock(EditBox.class);
        box.visible = true;
        WidgetGroup root = new WidgetGroup(0, 0, 100, 100);
        root.addWidget(new AppleStepper(0, 0, 64, 15, box, () -> {}, () -> {}).shownWhen(() -> false));
        root.updateLayout();
        assertFalse(box.visible);
        verify(box).setFocused(false);
    }

    @Test
    void layoutVisitsHiddenChildrenEvenThoughRenderingDoesNot() {
        AtomicInteger updates = new AtomicInteger();
        Widget hidden = new Widget(0, 0, 10, 10) {
            @Override public void updateLayout() { updates.incrementAndGet(); }
        };
        hidden.setVisible(false);
        WidgetGroup root = new WidgetGroup(0, 0, 100, 100);
        root.addWidget(hidden);
        root.updateLayout();
        assertEquals(1, updates.get());
    }
}
