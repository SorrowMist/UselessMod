package com.sorrowmist.useless.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

final class ScaledEnergyAmount {
    private static final String[] SUFFIXES = {"", "K", "M", "G", "T", "P", "E"};

    private ScaledEnergyAmount() {
    }

    static String format(long amount) {
        BigDecimal value = BigDecimal.valueOf(Math.max(0L, amount));
        int suffix = 0;
        while (suffix < SUFFIXES.length - 1
                && value.compareTo(BigDecimal.valueOf(1_000L)) >= 0) {
            value = value.movePointLeft(3);
            suffix++;
        }
        return value.setScale(2, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString() + SUFFIXES[suffix];
    }

    static OptionalLong parse(String text, long maximum) {
        if (text == null) return OptionalLong.empty();
        String value = text.trim();
        if (value.isEmpty()) return OptionalLong.empty();

        int suffix = suffixIndex(value.charAt(value.length() - 1));
        String number = suffix == 0 ? value : value.substring(0, value.length() - 1);
        if (number.isEmpty() || number.equals(".")) return OptionalLong.empty();

        try {
            BigDecimal scaled = new BigDecimal(number).movePointRight(suffix * 3);
            if (scaled.signum() < 0) return OptionalLong.empty();
            long clampedMaximum = Math.max(0L, maximum);
            if (scaled.compareTo(BigDecimal.valueOf(clampedMaximum)) >= 0) {
                return OptionalLong.of(clampedMaximum);
            }
            return OptionalLong.of(scaled.setScale(0, RoundingMode.DOWN).longValueExact());
        } catch (ArithmeticException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    static boolean isValidInput(String text) {
        if (text.isEmpty()) return true;
        int end = suffixIndex(text.charAt(text.length() - 1)) == 0
                ? text.length() : text.length() - 1;
        boolean decimalPoint = false;
        for (int index = 0; index < end; index++) {
            char character = text.charAt(index);
            if (character == '.' && !decimalPoint) {
                decimalPoint = true;
            } else if (character < '0' || character > '9') {
                return false;
            }
        }
        return end == text.length() || end > 0;
    }

    private static int suffixIndex(char suffix) {
        char normalized = Character.toUpperCase(suffix);
        for (int index = 1; index < SUFFIXES.length; index++) {
            if (SUFFIXES[index].charAt(0) == normalized) return index;
        }
        return 0;
    }
}
