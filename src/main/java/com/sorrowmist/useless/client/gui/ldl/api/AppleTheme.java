package com.sorrowmist.useless.client.gui.ldl.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.client.gui.apple.AppleStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工具包 API 的布局 / 颜色令牌（主题）。默认值与 {@link AppleStyle} 完全一致。
 *
 * <p>可选样式 JSON（只改布局数值和 API 用到的语义色，不必改代码）：按顺序叠加
 * <ol>
 *   <li>资源包 {@code assets/useless_mod/apple_ui/theme.json}</li>
 *   <li>{@code config/useless_mod/apple_theme.json}（玩家/整合包本地微调）</li>
 * </ol>
 * 键名 = 本类 public 字段名，例如 {@code {"gap": 6, "cardPad": 6, "primary": "#007AFF"}}。
 * 颜色写 "#RRGGBB" / "#AARRGGBB" 或整数。首次用到时读取一次；改完后重进游戏，或在代码里调用 {@link #reload()}（目前没有挂到 F3+T）。</p>
 */
public final class AppleTheme {
    // ---- 间距 / 尺寸（GUI 像素）
    public int gap = 4;            // 卡片之间、行内控件之间
    public int pad = AppleStyle.PAD;           // 窗口内边距
    public int cardPad = 6;        // 卡片内边距
    public int titleBar = AppleStyle.TITLE_BAR;
    public int rowH = AppleStyle.ROW_H;        // 设置行高
    public int controlH = AppleStyle.CONTROL_H;
    public int buttonH = 17;
    public int switchW = 24, switchH = 14;
    public int slot = 18;
    public int iconTile = 13;
    public int progressH = 5;
    public int energyBarH = 6;
    public int pillW = 68;
    // ---- 语义色（API 控件引用；底层控件仍用 AppleStyle）
    public int primary = AppleStyle.BLUE;
    public int success = AppleStyle.GREEN_TEXT;
    public int warning = AppleStyle.ORANGE;
    public int danger = AppleStyle.RED_TEXT;
    public int energy = AppleStyle.GREEN;
    public int heat = AppleStyle.ORANGE;
    public int fluid = AppleStyle.TEAL;
    public int mold = AppleStyle.PURPLE;
    public int label = AppleStyle.LABEL;
    public int secondary = AppleStyle.SECONDARY;

    private static AppleTheme current;

    public static AppleTheme get() {
        if (current == null) reload();
        return current;
    }

    /** 重新读取（资源重载时调用）。读不到 / 解析失败一律回退默认值。 */
    public static void reload() {
        AppleTheme t = new AppleTheme();
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                var res = mc.getResourceManager().getResource(new ResourceLocation("useless_mod", "apple_ui/theme.json"));
                if (res.isPresent()) try (Reader r = res.get().openAsReader()) { t.apply(JsonParser.parseReader(r).getAsJsonObject()); }
            }
        } catch (Exception ignored) {}
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve("useless_mod/apple_theme.json");
            if (Files.isRegularFile(p)) try (Reader r = Files.newBufferedReader(p)) { t.apply(JsonParser.parseReader(r).getAsJsonObject()); }
        } catch (Exception ignored) {}
        current = t;
    }

    void apply(JsonObject o) throws IllegalAccessException {
        for (Field f : AppleTheme.class.getFields()) {
            if (!o.has(f.getName()) || f.getType() != int.class) continue;
            var v = o.get(f.getName());
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                String s = v.getAsString().replace("#", "");
                long c = Long.parseLong(s, 16);
                f.setInt(this, (int) (s.length() <= 6 ? (0xFF000000L | c) : c));
            } else f.setInt(this, v.getAsInt());
        }
    }
}
