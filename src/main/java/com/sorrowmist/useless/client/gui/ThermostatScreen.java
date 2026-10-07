package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.network.ThermostatSetPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * 「温度调节器」方块的配置界面。
 *
 * <p>由权杖 <b>Alt + 右键</b> 打开（见 {@code ClientEventBusSubscriber#tryThermostatGesture}）。
 * 刻意<b>不做成容器界面</b>：没有物品槽、也不需要 JEI 侧栏，普通 {@link Screen} 就够。</p>
 *
 * <h2>外观</h2>
 *
 * <p>沿用模组统一的 AE 风格：{@link MachineScreenStyle#drawPanel} 画底板、
 * {@link PressableAE2Button} 画按钮、{@link AE2StyleTextField} 画输入框，与
 * {@code ModeWheelScreen} 同一套写法。</p>
 *
 * <p><b>底板在 {@link #renderBackground} 里画，不要在 {@code render} 里再调一次。</b>
 * 原版 {@code Screen.render()} 内部本来就会调 {@code renderBackground()}
 * （已反编译确认），多调一次等于把遮罩叠两层、整个界面发黑。</p>
 *
 * <h2>值的权威</h2>
 *
 * <p>服务端是唯一权威：界面读客户端方块实体的同步值，改动通过
 * {@link ThermostatSetPacket} 提交，服务端校验后再同步回来。</p>
 */
public final class ThermostatScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 136;

    /** 快速步进档位：覆盖「微调」与「大幅改」两种用法。 */
    private static final int[] QUICK_STEPS = {-1000, -100, -10, 10, 100, 1000};

    private final BlockPos pos;
    private final List<PressableAE2Button> buttons = new ArrayList<>();

    private int leftPos;
    private int topPos;
    private AE2StyleTextField temperatureField;
    private PressableAE2Button toggleButton;

    /** 最近一次由服务端同步下来的温度，用来判断输入框是否需要刷新。 */
    private long lastSyncedTemperature = Long.MIN_VALUE;
    /** 最近一次写进输入框的文本；与它不同即视为「玩家改过」。 */
    private String syncedTemperatureText = "";
    /** 正在由代码改写输入框时抑制 responder，避免把同步当成玩家输入。 */
    private boolean updatingField;
    /** 输入框里的内容与同步值不一致。 */
    private boolean fieldDirty;

    public ThermostatScreen(BlockPos pos) {
        super(Component.translatable("gui.useless_mod.thermostat.title"));
        this.pos = pos;
    }

    @Nullable
    private ThermostatBlockEntity thermostat() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getBlockEntity(pos) instanceof ThermostatBlockEntity thermostat
                ? thermostat : null;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        leftPos = (width - PANEL_WIDTH) / 2;
        topPos = (height - PANEL_HEIGHT) / 2;

        temperatureField = new AE2StyleTextField(font,
                leftPos + PANEL_WIDTH - 8 - 80, topPos + 22, 80, 14,
                Component.translatable("gui.useless_mod.thermostat.temperature"));
        temperatureField.setMaxLength(24);
        // 与「数量 / 产出速率」同一套：吃 K / M / G / T / P / E 后缀，1M = 1,000,000 K。
        temperatureField.setFilter(ScaledEnergyAmount::isValidInput);
        temperatureField.setHint(Component.translatable("gui.useless_mod.thermostat.temperature"));
        temperatureField.setResponder(value -> {
            if (!updatingField) {
                fieldDirty = !value.equals(syncedTemperatureText);
            }
        });
        addRenderableWidget(temperatureField);

        int buttonWidth = 36;
        int gap = 3;
        int totalWidth = QUICK_STEPS.length * buttonWidth + (QUICK_STEPS.length - 1) * gap;
        int startX = leftPos + (PANEL_WIDTH - totalWidth) / 2;
        for (int index = 0; index < QUICK_STEPS.length; index++) {
            int step = QUICK_STEPS[index];
            addButton(startX + index * (buttonWidth + gap), topPos + 42, buttonWidth, 14,
                    Component.literal((step > 0 ? "+" : "") + step),
                    () -> adjust(step));
        }

        toggleButton = addButton(leftPos + 8, topPos + 60, PANEL_WIDTH - 16, 16,
                toggleLabel(), this::toggleEnabled);

        addButton(leftPos + (PANEL_WIDTH - 70) / 2, topPos + PANEL_HEIGHT - 22, 70, 16,
                Component.translatable("gui.useless_mod.thermostat.done"), this::onClose);

        syncField(true);
    }

    private PressableAE2Button addButton(int x, int y, int width, int height,
                                         Component message, Runnable action) {
        PressableAE2Button button = addRenderableWidget(new PressableAE2Button(
                x, y, width, height, message, ignored -> action.run()));
        buttons.add(button);
        return button;
    }

    @Override
    public void tick() {
        super.tick();
        syncField(false);
        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }
    }

    /**
     * 把服务端的温度同步进输入框。
     *
     * <p>正在输入（聚焦）或玩家已改过（{@code fieldDirty}）时不动它，否则打字会被打断。</p>
     */
    private void syncField(boolean force) {
        if (temperatureField == null) {
            return;
        }
        long temperature = currentTemperature();
        if ((force || (!temperatureField.isFocused() && !fieldDirty))
                && temperature != lastSyncedTemperature) {
            setFieldValue(temperature);
        }
    }

    private void setFieldValue(long temperature) {
        syncedTemperatureText = String.valueOf(temperature);
        lastSyncedTemperature = Math.max(0L, temperature);
        updatingField = true;
        temperatureField.setValue(syncedTemperatureText);
        updatingField = false;
        fieldDirty = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // super.render 内部会先调 renderBackground（遮罩 + 底板）再画控件，顺序正好。
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, title, leftPos + 8, topPos + 8,
                MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.thermostat.kelvin"),
                leftPos + 8, topPos + 26, MachineScreenStyle.TEXT_COLOR, false);

        long kelvin = parseTemperature();
        graphics.drawString(font, Component.translatable("gui.useless_mod.thermostat.celsius",
                        String.format("%.2f", kelvin - 273.15D)),
                leftPos + 8, topPos + 84, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.thermostat.hint"),
                leftPos + 8, topPos + 96, MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    /**
     * 遮罩 + 底板。
     *
     * <p><b>只在这里画，不要同时在 {@code render} 里再画一遍</b>——原版
     * {@code Screen.render()} 会自己调用本方法，重复绘制会让整个界面叠两层暗色。</p>
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && temperatureField != null && temperatureField.isFocused()) {
            commit(parseTemperature(), isEnabled());
            setFocused(null);
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && temperatureField != null
                && (temperatureField.keyPressed(keyCode, scanCode, modifiers)
                    || temperatureField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PressableAE2Button widget : buttons) {
            widget.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // 关界面前把输入框里没提交的值也落下去，免得玩家以为改了却没生效。
        if (fieldDirty) {
            commit(parseTemperature(), isEnabled());
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停：让玩家当场看到相邻机器的温度变化。
        return false;
    }

    private void adjust(int step) {
        long current = parseTemperature();
        // 防溢出：接近 long 上限时 +1000 会绕回负数。
        long next = step > 0
                ? (current > Long.MAX_VALUE - step ? Long.MAX_VALUE : current + step)
                : Math.max(0L, current + step);
        setFieldValue(next);
        commit(next, isEnabled());
    }

    private void toggleEnabled() {
        commit(parseTemperature(), !isEnabled());
    }

    private boolean isEnabled() {
        ThermostatBlockEntity thermostat = thermostat();
        return thermostat != null && thermostat.isEnabled();
    }

    private void commit(long temperature, boolean enabled) {
        ThermostatBlockEntity thermostat = thermostat();
        if (thermostat != null
                && thermostat.getTemperature() == temperature
                && thermostat.isEnabled() == enabled) {
            return;
        }
        PacketDistributor.sendToServer(new ThermostatSetPacket(pos, temperature, enabled));
    }

    /**
     * 读输入框里的温度（开尔文）。
     *
     * <p>吃 {@code K / M / G / T / P / E} 后缀（与「数量 / 产出速率」同一套
     * {@link ScaledEnergyAmount}），上限就是 {@code long} 的上限；
     * 解析不了就回落到当前生效值。</p>
     */
    private long parseTemperature() {
        if (temperatureField == null) {
            return currentTemperature();
        }
        OptionalLong parsed = ScaledEnergyAmount.parse(temperatureField.getValue(), Long.MAX_VALUE);
        return parsed.isPresent() ? parsed.getAsLong() : currentTemperature();
    }

    private long currentTemperature() {
        ThermostatBlockEntity thermostat = thermostat();
        return thermostat == null
                ? ThermostatBlockEntity.DEFAULT_TEMPERATURE : thermostat.getTemperature();
    }

    private Component toggleLabel() {
        return Component.translatable(isEnabled()
                ? "gui.useless_mod.thermostat.enabled"
                : "gui.useless_mod.thermostat.disabled");
    }
}
