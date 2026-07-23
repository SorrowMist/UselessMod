package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.OmniversalPatternEncoderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OmniversalPatternEncoderScreen extends AbstractContainerScreen<OmniversalPatternEncoderMenu> {
    private static final int VISIBLE_CANDIDATES = 7;
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public OmniversalPatternEncoderScreen(OmniversalPatternEncoderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> select(OmniversalPatternEncoderMenu.PREVIOUS_CANDIDATE))
                .bounds(leftPos + 8, topPos + 46, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> select(OmniversalPatternEncoderMenu.NEXT_CANDIDATE))
                .bounds(leftPos + 44, topPos + 46, 18, 18).build());
    }

    private void select(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0x404040, false);
        var candidates = menu.getCandidates();
        int first = firstVisibleCandidate();
        int visible = Math.min(VISIBLE_CANDIDATES, candidates.size() - first);
        for (int index = 0; index < visible; index++) {
            int candidateIndex = first + index;
            var entry = candidates.get(candidateIndex);
            int color = candidateIndex == menu.getSelectedCandidate() ? 0x2E7D32 : 0x404040;
            String text = entry.identity().recipeId().toString();
            if (font.width(text) > 100) text = font.plainSubstrByWidth(text, 97) + "...";
            graphics.drawString(font, text, 68, 18 + index * 14, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localX >= 68 && localX < 172 && localY >= 18) {
            int index = (int) ((localY - 18) / 14);
            int candidateIndex = firstVisibleCandidate() + index;
            if (index >= 0 && index < VISIBLE_CANDIDATES
                    && candidateIndex < menu.getCandidates().size()) {
                select(OmniversalPatternEncoderMenu.SELECT_CANDIDATE_BASE + candidateIndex);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int firstVisibleCandidate() {
        return Math.max(0, menu.getSelectedCandidate() / VISIBLE_CANDIDATES * VISIBLE_CANDIDATES);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
