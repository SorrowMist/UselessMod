package com.sorrowmist.useless.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** A public-API selector compatible with JEI 15.48, without newer cycle-button APIs. */
final class HostileNetworksDataSelector implements IRecipeWidget, IJeiInputHandler {
    private static final int WIDTH = 204;
    private static final int HEIGHT = 16;
    private final List<Entry> choices;
    private final Consumer<Entry> display;
    private int index;

    HostileNetworksDataSelector(List<Entry> choices, Consumer<Entry> display) {
        if (choices.isEmpty()) throw new IllegalArgumentException("Missing HNN variants");
        this.choices = List.copyOf(choices);
        this.display = display;
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(4, 74); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(4, 74, WIDTH, HEIGHT); }

    @Override public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        // JEI has already translated drawing to getPosition().
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xFF454545);
        graphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF222222);
        var label = Component.literal("< ").append(HostileNetworksJeiRecipes.dataLabel(
                HostileNetworksJeiRecipes.info(choices.get(index)))).append(" >");
        graphics.drawCenteredString(Minecraft.getInstance().font, label, WIDTH / 2, 4, 0xFFFFFF);
    }

    @Override public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (mouseX >= 0 && mouseX < WIDTH && mouseY >= 0 && mouseY < HEIGHT) {
            tooltip.add(Component.translatable("jei.useless_mod.hnn.select_data", choices.size()));
        }
    }

    @Override public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE || input.getKey().getValue() != 0) return false;
        if (!input.isSimulate()) select((input.getModifiers() & 1) != 0 ? -1 : 1);
        return true;
    }

    @Override public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        select(delta > 0 ? -1 : 1);
        return true;
    }

    private void select(int change) {
        index = Math.floorMod(index + change, choices.size());
        display.accept(choices.get(index));
    }
}
