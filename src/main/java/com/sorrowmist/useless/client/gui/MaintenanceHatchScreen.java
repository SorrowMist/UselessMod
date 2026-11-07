package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import com.sorrowmist.useless.content.multiblock.MaintenanceCalibrationRules;
import com.sorrowmist.useless.network.MaintenanceCalibrationActionPacket;
import com.sorrowmist.useless.network.MaintenanceCalibrationSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/** Apple-style calibration panel. No client-side hit decisions or world-clock synchronization assumptions. */
public final class MaintenanceHatchScreen extends AppleContainerScreen<MaintenanceHatchMenu> {
    private static final int TRACK_X = 24, TRACK_Y = 100, TRACK_WIDTH = 232;
    private MaintenanceCalibrationSyncPacket snapshot;
    private boolean pending;
    private long sentAt;
    private long feedbackUntil;
    private boolean hitFeedback;

    public MaintenanceHatchScreen(MaintenanceHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
        imageHeight = 218;
    }

    @Override protected void initControls() { readSnapshot(); }

    @Override protected void buildApple(AppleHost h) {
        h.add(new AppleWindow(imageWidth, imageHeight, () -> title).icon(AppleUi.Icon.GEAR, AppleStyle.ORANGE));
        h.add(new AppleBadge(70, 36, 140, this::statusText, this::statusColor));
        for (int i = 0; i < MaintenanceCalibrationRules.ROUNDS_TOTAL; i++) {
            final int round = i;
            h.add(new AppleCard(16 + i * 84, 60, 80, 28));
            h.add(new AppleBadge(20 + i * 84, 67, 72,
                    () -> Component.literal((round < cleared() ? "✓ " : "") + (round + 1) + " / 3"),
                    () -> round < cleared() ? AppleStyle.GREEN : round == cleared() && running()
                            ? AppleStyle.BLUE : AppleStyle.TERTIARY));
        }
        h.add(new AppleCard(16, 94, 248, 56));
        h.add(new AppleCanvas(TRACK_X, TRACK_Y, TRACK_WIDTH, 40,
                (g, mx, my, x, y, w, hh) -> drawTrack(g, x, y, w)));
        h.add(new AppleLabel(20, 157, 240, AppleLabel.Kind.FOOTNOTE, this::hintText).lines(2));
        h.add(new ApplePushButton(74, 187, 132, 20, ApplePushButton.Kind.PRIMARY,
                this::buttonText, this::act).icon(AppleUi.Icon.GEAR).enabledWhen(() -> !pending));
    }

    @Override protected void tickControls() {
        readSnapshot();
        if (pending && localTick() - sentAt > 60L) pending = false;
    }

    private void readSnapshot() {
        var next = menu.getCalibration();
        if (next == null || next == snapshot) return;
        if (snapshot != null && snapshot.phase() == MaintenanceCalibrationRules.PHASE_RUNNING
                && next.roundId() != snapshot.roundId()) {
            hitFeedback = next.roundsCleared() > snapshot.roundsCleared();
            feedbackUntil = localTick() + 20L;
        }
        snapshot = next;
        pending = false;
    }

    /** Optional direct entry point; the packet handler also caches snapshots before the screen exists. */
    public void receiveSync(MaintenanceCalibrationSyncPacket packet) {
        menu.receiveCalibration(packet, localTick());
        readSnapshot();
    }

    private long localTick() { return minecraft == null || minecraft.level == null ? 0L : minecraft.level.getGameTime(); }
    private int cleared() { return snapshot == null ? 0 : snapshot.roundsCleared(); }
    private boolean running() { return snapshot != null && snapshot.phase() == MaintenanceCalibrationRules.PHASE_RUNNING; }
    private boolean completed() { return snapshot != null && snapshot.phase() == MaintenanceCalibrationRules.PHASE_COMPLETED; }
    private long elapsed() {
        return snapshot == null ? 0L : snapshot.elapsedTicks() + Math.max(0L, localTick() - menu.getCalibrationLocalTick());
    }

    private void drawTrack(GuiGraphics g, int x, int y, int width) {
        AppleUi.fillRound(g, x, y, width, 20, 4, AppleStyle.TRACK);
        if (running()) {
            float half = MaintenanceCalibrationRules.halfWidthFor(cleared());
            int left = x + Math.round((snapshot.targetCenter() - half) * width);
            int right = x + Math.round((snapshot.targetCenter() + half) * width);
            AppleUi.fillRound(g, left, y, right - left, 20, 3, AppleStyle.GREEN);
            int pointer = x + Math.round(MaintenanceCalibrationRules.pointerAt(elapsed()) * (width - 1));
            g.fill(pointer - 1, y - 3, pointer + 2, y + 23, AppleStyle.RED);
        }
        AppleTextLayout.draw(g, font, Component.literal("0%"), x, y + 28, 32, .8f, AppleStyle.SECONDARY, false);
        AppleTextLayout.draw(g, font, Component.literal("100%"), x + width - 32, y + 28, 32, .8f, AppleStyle.SECONDARY, true);
    }

    private Component statusText() {
        return text(completed() ? "completed" : running() ? "running" : "idle");
    }
    private int statusColor() { return completed() ? AppleStyle.GREEN : running() ? AppleStyle.BLUE : AppleStyle.TERTIARY; }
    private Component buttonText() { return text("button." + (completed() ? "restart" : running() ? "lock" : "start")); }
    private Component hintText() {
        if (localTick() < feedbackUntil) return text(hitFeedback ? "hit" : "miss");
        return text(completed() ? "restart_hint" : running() ? "hint" : "start_hint");
    }
    private static Component text(String suffix) { return Component.translatable("gui.useless_mod.maintenance_hatch." + suffix); }

    private void act() {
        if (pending) return;
        pending = true;
        sentAt = localTick();
        PacketDistributor.sendToServer(new MaintenanceCalibrationActionPacket(menu.containerId, menu.getBlockPos(),
                !running(), snapshot == null ? 0L : snapshot.roundId(), running() ? elapsed() : 0L));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isHovering(TRACK_X, TRACK_Y - 4, TRACK_WIDTH, 28, x, y)) {
            act();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE) { act(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
