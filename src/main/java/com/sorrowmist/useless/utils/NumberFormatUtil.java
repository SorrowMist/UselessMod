// NumberFormatUtil.java
package com.sorrowmist.useless.utils;

public class NumberFormatUtil {

    /**
     * 格式化物品数量显示（带小数简写）
     * 例：1200 → 1.2K，2500000 → 2.5M，1e12 → 1T
     * 优化：整数部分为三位数时不显示小数，小数部分为0时不显示小数
     */
    public static String formatItemCount(long number) {
        if (number >= 1_000_000_000_000_000_000L) {
            double value = number / 1_000_000_000_000_000_000.0;
            return formatNumberWithCondition(value, "E");
        } else if (number >= 1_000_000_000_000_000L) {
            double value = number / 1_000_000_000_000_000.0;
            return formatNumberWithCondition(value, "P");
        } else if (number >= 1_000_000_000_000L) {
            double value = number / 1_000_000_000_000.0;
            return formatNumberWithCondition(value, "T");
        } else if (number >= 1_000_000_000L) {
            double value = number / 1_000_000_000.0;
            return formatNumberWithCondition(value, "G");
        } else if (number >= 1_000_000L) {
            double value = number / 1_000_000.0;
            return formatNumberWithCondition(value, "M");
        } else if (number >= 1_000L) {
            double value = number / 1_000.0;
            return formatNumberWithCondition(value, "K");
        } else {
            return String.valueOf(number);
        }
    }

    /**
     * 格式化流体数量显示 (1000mB = 1B, 1000B = 1KB)
     * 优化：整数部分为三位数时不显示小数，小数部分为0时不显示小数
     */
    public static String formatFluidAmount(long amount) {
        if (amount >= 1_000_000_000_000_000_000L) {
            double value = amount / 1_000_000_000_000_000_000.0;
            return formatNumberWithCondition(value, "PB");
        } else if (amount >= 1_000_000_000_000_000L) {
            double value = amount / 1_000_000_000_000_000.0;
            return formatNumberWithCondition(value, "TB");
        } else if (amount >= 1_000_000_000_000L) {
            double value = amount / 1_000_000_000_000.0;
            return formatNumberWithCondition(value, "GB");
        } else if (amount >= 1_000_000_000L) {
            double value = amount / 1_000_000_000.0;
            return formatNumberWithCondition(value, "MB");
        } else if (amount >= 1_000_000L) {
            double value = amount / 1_000_000.0;
            return formatNumberWithCondition(value, "KB");
        } else if (amount >= 1_000L) {
            double value = amount / 1_000.0;
            return formatNumberWithCondition(value, "B");
        } else {
            return amount + "mB";
        }
    }

    /**
     * 格式化能量显示（FE）
     * 优化：整数部分为三位数时不显示小数，小数部分为0时不显示小数
     */
    public static String formatEnergy(long energy) {
        if (energy >= 1_000_000_000_000_000_000L) {
            double value = energy / 1_000_000_000_000_000_000.0;
            return formatNumberWithCondition(value, "EFE");
        } else if (energy >= 1_000_000_000_000_000L) {
            double value = energy / 1_000_000_000_000_000.0;
            return formatNumberWithCondition(value, "PFE");
        } else if (energy >= 1_000_000_000_000L) {
            double value = energy / 1_000_000_000_000.0;
            return formatNumberWithCondition(value, "TFE");
        } else if (energy >= 1_000_000_000L) {
            double value = energy / 1_000_000_000.0;
            return formatNumberWithCondition(value, "GFE");
        } else if (energy >= 1_000_000L) {
            double value = energy / 1_000_000.0;
            return formatNumberWithCondition(value, "MFE");
        } else if (energy >= 1_000L) {
            double value = energy / 1_000.0;
            return formatNumberWithCondition(value, "KFE");
        } else {
            return energy + " FE";
        }
    }

    /**
     * 根据条件格式化数字：整数部分为三位数时不显示小数，小数部分为0时不显示小数
     */
    private static String formatNumberWithCondition(double value, String unit) {
        int intPart = (int) value;
        double decimalPart = value - intPart;

        // 如果整数部分 >= 100（三位数），或者小数部分为0，则只显示整数
        if (intPart >= 100 || Math.abs(decimalPart) < 0.0001) {
            return intPart + unit;
        } else {
            // 显示一位小数
            return String.format("%.1f%s", value, unit);
        }
    }
}