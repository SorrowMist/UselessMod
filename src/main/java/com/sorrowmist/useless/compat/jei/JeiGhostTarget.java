package com.sorrowmist.useless.compat.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** JEI retains targets for a whole drag: never redirect an old target after reflow or state changes. */
final class JeiGhostTarget {
    private JeiGhostTarget() {}

    static <I> IGhostIngredientHandler.Target<I> guarded(Screen screen, Supplier<Rect2i> area,
                                                        BooleanSupplier context, Consumer<I> accept) {
        Rect2i original = area.get();
        int width = screen.width, height = screen.height;
        return new IGhostIngredientHandler.Target<>() {
            private boolean valid() {
                if (Minecraft.getInstance().screen != screen || screen.width != width || screen.height != height
                        || !context.getAsBoolean()) return false;
                Rect2i current = area.get();
                return original.getX() == current.getX() && original.getY() == current.getY()
                        && original.getWidth() == current.getWidth() && original.getHeight() == current.getHeight();
            }

            @Override
            public Rect2i getArea() {
                return valid() ? new Rect2i(original.getX(), original.getY(), original.getWidth(), original.getHeight())
                        : new Rect2i(0, 0, 0, 0);
            }

            @Override
            public void accept(I value) {
                if (valid()) accept.accept(value);
            }
        };
    }
}
