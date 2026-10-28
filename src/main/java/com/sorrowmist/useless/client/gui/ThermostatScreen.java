package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.client.gui.ldl.api.*;
import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.network.ThermostatSetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.sorrowmist.useless.client.gui.ldl.api.Ui.*;

/**
 * 声明式恒温器面板，按 HTML 参考的 260×220 布局。服务端仍是温度和开关的唯一权威；
 * 数字输入的同步、回车、步进和关闭提交由 ApplePanelScreen / NumberField 统一处理。
 */
public final class ThermostatScreen extends ApplePanelScreen {
    private static final int[] QUICK_STEPS = {-1000, -100, -10, 10, 100, 1000};
    private final BlockPos pos;
    private final NumberField temperature;

    public ThermostatScreen(BlockPos pos) {
        super(Component.translatable("gui.useless_mod.thermostat.title"), 260, 220);
        this.pos = pos;
        temperature = numberField(this::currentTemperature, value -> commit(value, isEnabled()))
                .format(String::valueOf).boxHeight(14)
                .hint(Component.translatable("gui.useless_mod.thermostat.temperature"));
    }

    @Override protected int bodyX() { return 12; }
    @Override protected int bodyY() { return 40; }
    @Override protected AppleWindow createWindow() { return super.createWindow().icon(AppleUi.Icon.THERMO, AppleStyle.ORANGE); }

    @Override
    protected UiNode titleAccessory() {
        return statusPill(either(this::isEnabled, "gui.useless_mod.thermostat.enabled_short",
                        "gui.useless_mod.thermostat.disabled_short"),
                () -> isEnabled() ? AppleStyle.GREEN : AppleStyle.TERTIARY).size(68, 12).nudge(-6, 0);
    }

    @Override
    protected UiNode body() {
        Absolute content = absolute(236, 168)
                .at(0, 0, 236, 109, card())
                .at(10, 8, 216, 9, caption(tr("gui.useless_mod.thermostat.kelvin")))
                .at(9, 22, 218, 27, new Leaf(218, 27, (x, y, w, h) ->
                        new AppleTextField(x, y, w, h, temperature.box(), 6).trailingInset(14)).id("temp"))
                .at(214, 30, 10, 9, footnote(lit("K")))
                .at(9, 53, 94, 9, footnote(() -> Component.translatable("gui.useless_mod.thermostat.celsius",
                        String.format(Locale.ROOT, "%.2f", temperature.value() - 273.15D))))
                .at(104, 53, 123, 9, footnote(tr("gui.useless_mod.thermostat.hint")).wrap())
                .at(10, 70, 216, 9, caption(tr("gui.useless_mod.thermostat.quick_steps")))
                .at(0, 117, 236, 22, card())
                .at(8, 121, 14, 14, new Leaf(14, 14, (x, y, w, h) -> new AppleIconTile(x, y, w,
                        () -> AppleUi.Icon.THERMO, () -> isEnabled() ? AppleStyle.GREEN : AppleStyle.TERTIARY).filled()))
                .at(28, 123, 164, 9, Ui.body(this::toggleLabel))
                .at(204, 122, 22, 13, toggle(this::isEnabled, this::toggleEnabled).id("toggle"))
                .at(198, 147, 38, 20, button(tr("gui.useless_mod.thermostat.done"), this::onClose).primary().id("done"));
        for (int i = 0; i < QUICK_STEPS.length; i++) {
            int step = QUICK_STEPS[i];
            content.at(9 + i * 37, 82, 33, 18,
                    button(lit((step > 0 ? "+" : "") + step), () -> temperature.add(step))
                            .kind(step > 0 ? ApplePushButton.Kind.TINTED : ApplePushButton.Kind.SECONDARY).id("step" + i));
        }
        return content;
    }

    @Override
    protected void afterLayout(AppleHost h) {
        ((AppleEditBox) temperature.box()).textScale(1.8f);
        setInitialFocus(temperature.box());
    }

    @Nullable
    private ThermostatBlockEntity thermostat() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(pos) instanceof ThermostatBlockEntity t ? t : null;
    }

    private void toggleEnabled() { commit(temperature.value(), !isEnabled()); }
    private boolean isEnabled() { ThermostatBlockEntity t = thermostat(); return t != null && t.isEnabled(); }
    private long currentTemperature() {
        ThermostatBlockEntity t = thermostat();
        return t == null ? ThermostatBlockEntity.DEFAULT_TEMPERATURE : t.getTemperature();
    }

    private void commit(long value, boolean enabled) {
        // The client BE can lag behind an earlier in-flight edit. Even if it
        // currently equals this value, send the newest intent (e.g. 1121 -> 1111 -> 1121).
        AppleNet.send(new ThermostatSetPacket(pos, value, enabled));
    }

    private Component toggleLabel() {
        return Component.translatable(isEnabled() ? "gui.useless_mod.thermostat.enabled" : "gui.useless_mod.thermostat.disabled");
    }
}
