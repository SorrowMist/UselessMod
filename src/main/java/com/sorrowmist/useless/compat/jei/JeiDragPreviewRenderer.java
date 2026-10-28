package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Replays JEI's active drag renderer after screens that paint a modal overlay after vanilla rendering. */
public final class JeiDragPreviewRenderer {
    private record Access(Method getRuntime, Method getIngredientList, Method getBookmarks,
                          Field contents, Field ghostManager, Field ghostDrag,
                          Field bookmarkContents, Field bookmarkGhostManager, Field bookmarkDragManager,
                          Method canStart, Method drawGhost, Method drawBookmark) {}

    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();
    private static volatile Access access;

    private JeiDragPreviewRenderer() {}

    public static boolean drawActivePreview(GuiGraphics graphics, int mouseX, int mouseY) {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("jei")) return false;
        try {
            Access access = access();
            Object runtime = access.getRuntime().invoke(null);
            if (runtime == null) return false;

            boolean drawn = false;
            Object ingredientList = access.getIngredientList().invoke(runtime);
            Object contents = access.contents().get(ingredientList);
            Object manager = access.ghostManager().get(contents);
            Object ghostDrag = access.ghostDrag().get(manager);
            if (ghostDrag != null && (boolean) access.canStart().invoke(null, ghostDrag,
                    (double) mouseX, (double) mouseY)) {
                access.drawGhost().invoke(ghostDrag, graphics, mouseX, mouseY);
                drawn = true;
            }

            Object bookmarks = access.getBookmarks().invoke(runtime);
            Object bookmarkContents = access.bookmarkContents().get(bookmarks);
            Object bookmarkGhostManager = access.bookmarkGhostManager().get(bookmarkContents);
            Object bookmarkGhostDrag = access.ghostDrag().get(bookmarkGhostManager);
            if (bookmarkGhostDrag != null && (boolean) access.canStart().invoke(null, bookmarkGhostDrag,
                    (double) mouseX, (double) mouseY)) {
                access.drawGhost().invoke(bookmarkGhostDrag, graphics, mouseX, mouseY);
                drawn = true;
            }

            Object bookmarkDragManager = access.bookmarkDragManager().get(bookmarks);
            if ((boolean) access.drawBookmark().invoke(bookmarkDragManager, graphics, mouseX, mouseY)) {
                drawn = true;
            }
            return drawn;
        } catch (InvocationTargetException error) {
            // JEI renderer failures must retain their original crash report, not masquerade as API drift.
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new IllegalStateException("JEI preview renderer failed", cause);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                UselessMod.LOGGER.warn("Could not draw the active JEI ingredient preview above the wireless filter panel", error);
            }
            return false;
        }
    }

    private static Access access() throws ReflectiveOperationException {
        Access cached = access;
        if (cached != null) return cached;

        ClassLoader loader = JeiDragPreviewRenderer.class.getClassLoader();
        Class<?> plugin = Class.forName("com.sorrowmist.useless.compat.jei.JEIPlugin", true, loader);
        Class<?> runtime = Class.forName("mezz.jei.api.runtime.IJeiRuntime", true, loader);
        Class<?> ghostDragType = Class.forName("mezz.jei.gui.ghost.GhostIngredientDrag", true, loader);
        Class<?> ghostManagerType = Class.forName("mezz.jei.gui.ghost.GhostIngredientDragManager", true, loader);
        Class<?> bookmarkManagerType = Class.forName("mezz.jei.gui.overlay.bookmarks.BookmarkDragManager", true, loader);
        Class<?> ingredientListType = Class.forName("mezz.jei.gui.overlay.IngredientListOverlay", true, loader);
        Class<?> contentsType = Class.forName("mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation", true, loader);
        Class<?> bookmarkOverlayType = Class.forName("mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", true, loader);

        Access resolved = new Access(
                accessible(plugin.getMethod("getRuntime")),
                accessible(runtime.getMethod("getIngredientListOverlay")),
                accessible(runtime.getMethod("getBookmarkOverlay")),
                field(ingredientListType, "contents"),
                field(contentsType, "ghostIngredientDragManager"),
                field(ghostManagerType, "ghostIngredientDrag"),
                field(bookmarkOverlayType, "contents"),
                field(contentsType, "ghostIngredientDragManager"),
                field(bookmarkOverlayType, "bookmarkDragManager"),
                accessible(ghostDragType.getMethod("canStart", ghostDragType, double.class, double.class)),
                accessible(ghostDragType.getMethod("drawItem", GuiGraphics.class, int.class, int.class)),
                accessible(bookmarkManagerType.getMethod("drawDraggedItem", GuiGraphics.class, int.class, int.class)));
        access = resolved;
        return resolved;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }
}
