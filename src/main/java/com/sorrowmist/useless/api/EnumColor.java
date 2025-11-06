package com.sorrowmist.useless.api;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

public enum EnumColor {
    BLACK("Black", "black", new int[]{64, 64, 64}, DyeColor.BLACK),
    RED("Red", "red", new int[]{255, 56, 60}, DyeColor.RED),
    GREEN("Green", "green", new int[]{89, 193, 95}, DyeColor.GREEN),
    BROWN("Brown", "brown", new int[]{161, 118, 73}, DyeColor.BROWN),
    BLUE("Blue", "blue", new int[]{54, 107, 208}, DyeColor.BLUE),
    PURPLE("Purple", "purple", new int[]{164, 96, 217}, DyeColor.PURPLE),
    CYAN("Cyan", "cyan", new int[]{0, 243, 208}, DyeColor.CYAN),
    LIGHT_GRAY("Light Gray", "light_gray", new int[]{207, 207, 207}, DyeColor.LIGHT_GRAY),
    GRAY("Gray", "gray", new int[]{122, 122, 122}, DyeColor.GRAY),
    PINK("Pink", "pink", new int[]{255, 188, 196}, DyeColor.PINK),
    LIME("Lime", "lime", new int[]{117, 255, 137}, DyeColor.LIME),
    YELLOW("Yellow", "yellow", new int[]{255, 221, 79}, DyeColor.YELLOW),
    LIGHT_BLUE("Light Blue", "light_blue", new int[]{85, 158, 255}, DyeColor.LIGHT_BLUE),
    MAGENTA("Magenta", "magenta", new int[]{213, 94, 203}, DyeColor.MAGENTA),
    ORANGE("Orange", "orange", new int[]{255, 161, 96}, DyeColor.ORANGE),
    WHITE("White", "white", new int[]{255, 255, 255}, DyeColor.WHITE),
    DARK_RED("Dark Red", "dark_red", new int[]{201, 7, 31}, MapColor.NETHER, null),
    AQUA("Aqua", "aqua", new int[]{48, 255, 249}, MapColor.COLOR_LIGHT_BLUE, null);

    private final String englishName;
    private final String registryPrefix;
    private final int[] rgbCode;
    @Nullable
    private final DyeColor dyeColor;
    private final MapColor mapColor;

    EnumColor(String englishName, String registryPrefix, int[] rgbCode, DyeColor dyeColor) {
        this(englishName, registryPrefix, rgbCode, dyeColor.getMapColor(), dyeColor);
    }

    EnumColor(String englishName, String registryPrefix, int[] rgbCode, MapColor mapColor, @Nullable DyeColor dyeColor) {
        this.englishName = englishName;
        this.registryPrefix = registryPrefix;
        this.rgbCode = rgbCode;
        this.dyeColor = dyeColor;
        this.mapColor = mapColor;
    }

    public String getRegistryPrefix() {
        return registryPrefix;
    }

    public String getEnglishName() {
        return englishName;
    }

    public MapColor getMapColor() {
        return mapColor;
    }

    @Nullable
    public DyeColor getDyeColor() {
        return dyeColor;
    }

    /**
     * 获取RGB整数值（ARGB格式，Alpha为255）
     */
    public int getRgb() {
        return (0xFF << 24) | (rgbCode[0] << 16) | (rgbCode[1] << 8) | rgbCode[2];
    }

    /**
     * 获取RGB数组 [r, g, b]
     */
    public int[] getRgbCode() {
        return rgbCode.clone(); // 返回副本防止外部修改
    }

    /**
     * 获取RGB浮点数组 [r, g, b]，范围 0.0-1.0
     */
    public float[] getRgbFloat() {
        return new float[] {
                rgbCode[0] / 255.0f,
                rgbCode[1] / 255.0f,
                rgbCode[2] / 255.0f
        };
    }


    /**
     * 按定义顺序返回所有颜色
     */
    public static EnumColor[] valuesInOrder() {
        return values();
    }

    /**
     * 根据名称获取颜色（不区分大小写）
     */
    @Nullable
    public static EnumColor byName(String name) {
        for (EnumColor color : values()) {
            if (color.registryPrefix.equalsIgnoreCase(name) ||
                    color.englishName.equalsIgnoreCase(name)) {
                return color;
            }
        }
        return null;
    }

    /**
     * 根据索引获取颜色
     */
    public static EnumColor byIndex(int index) {
        if (index < 0 || index >= values().length) {
            return WHITE; // 默认返回白色
        }
        return values()[index];
    }

}