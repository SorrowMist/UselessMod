package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.AppleEditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NumberFieldTest {
    private NumberField field(AtomicLong server, List<Long> commits) {
        NumberField field = new NumberField(server::get, commits::add);
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0);
            return text.substring(0, Math.min(text.length(), Math.max(0, call.<Integer>getArgument(1) / 6)));
        });
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> {
            String text = call.getArgument(0);
            int count = Math.min(text.length(), Math.max(0, call.<Integer>getArgument(1) / 6));
            return call.<Boolean>getArgument(2) ? text.substring(text.length() - count) : text.substring(0, count);
        });
        field.create(font, 0, 0, 80, Component.empty());
        return field;
    }

    @Test
    void abbreviatedDisplayDoesNotLoseExactServerPrecision() {
        List<Long> commits = new ArrayList<>();
        NumberField field = field(new AtomicLong(1_234_567), commits);
        assertEquals("1.23M", field.box.getValue());
        assertEquals(1_234_567L, field.value());
        field.submit();
        assertEquals(List.of(1_234_567L), commits);
    }

    @Test
    void focusedOrDirtyTextSurvivesServerSynchronizationUntilCommitted() {
        AtomicLong server = new AtomicLong(300);
        NumberField field = field(server, new ArrayList<>());
        field.box.setFocused(true);
        field.box.setValue("123");
        server.set(400);
        field.sync(false);
        assertEquals("123", field.box.getValue());
        field.box.setFocused(false);
        field.sync(false);
        assertEquals("123", field.box.getValue());
        assertTrue(field.dirty());
        field.commitIfDirty();
        assertFalse(field.dirty());
        field.sync(false);
        assertEquals("400", field.box.getValue());
    }

    @Test
    void readOnlyPageCannotSubmitOrStepThroughKeyboardOrClose() {
        List<Long> commits = new ArrayList<>();
        AtomicBoolean editable = new AtomicBoolean(false);
        NumberField field = field(new AtomicLong(20), commits).editableWhen(editable::get);
        field.box.setValue("999");
        field.submit(999);
        field.add(1);
        field.commitIfDirty();
        assertTrue(commits.isEmpty());
        editable.set(true);
        field.submit(21);
        assertEquals(List.of(21L), commits);
    }

    @Test
    void stepsSaturateAtBoundsAndDoNotWrapLongValues() {
        List<Long> commits = new ArrayList<>();
        NumberField field = field(new AtomicLong(Long.MAX_VALUE - 5), commits);
        field.add(1000);
        assertEquals(Long.MAX_VALUE, field.value());
        field.add(Long.MIN_VALUE);
        assertEquals(0, field.value());
        field.min(1).max(100);
        field.submit(-5);
        field.submit(200);
        assertEquals(List.of(Long.MAX_VALUE, 0L, 1L, 100L), commits);
    }

    @Test
    void rapidEditsReturningToStaleServerValueStillSubmitBothIntents() {
        List<Long> commits = new ArrayList<>();
        NumberField field = field(new AtomicLong(1121), commits).format(String::valueOf);
        field.box.setValue("1111");
        field.submit();
        field.add(10);
        assertEquals(List.of(1111L, 1121L), commits);
        assertEquals(1121L, field.value());
    }

    @Test
    void invalidInputFallsBackToServerAndSubmittedValuesClampToRange() {
        List<Long> commits = new ArrayList<>();
        NumberField field = field(new AtomicLong(42), commits).min(1).max(100);
        field.box.setValue(".");
        assertEquals(42, field.value());
        field.submit(500);
        assertEquals(100, field.value());
        assertEquals(List.of(100L), commits);
    }
}
