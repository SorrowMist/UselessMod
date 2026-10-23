package com.sorrowmist.useless.tests;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.gui.PortConfigScreen;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Opens the real registered factory and native Configured widgets in an isolated client. */
@Mod.EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class ConfigUiClientCheck {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft", "textures/block/stone.png");
    private static int ticks;
    private static int step;
    private static boolean languageLoaded;
    private static Screen parent;
    private static Screen selection;
    private static Screen common;
    private static Object config;
    private static Object root;
    private static String query;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("useless.configUiCheck") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || ++ticks % 10 != 0) return;
        if (step == 0 && !(mc.screen instanceof TitleScreen)) return;
        try {
            if (!languageLoaded) {
                languageLoaded = true;
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                mc.reloadResourcePacks();
                return;
            }
            if (step == 0) {
                parent = mc.screen;
                var factory = ModList.get().getModContainerById(UselessMod.MODID).orElseThrow()
                        .getCustomExtension(ConfigScreenFactory.class).orElseThrow();
                selection = factory.screenFunction().apply(mc, parent);
                if (!ModList.get().isLoaded("configured")) {
                    check(selection instanceof PortConfigScreen, "Missing standalone fallback factory");
                    mc.setScreen(selection);
                    step = 100;
                    return;
                }
                check(selection.getClass().getName().startsWith("com.mrcrayfish.configured."),
                        "Installed Configured must own the mod-list config factory");
                mc.setScreen(selection);
            } else if (step == 1) {
                capture("01-config-selection");
                selectConfig("-common.toml");
                common = screen(root);
                mc.setScreen(common);
            } else if (step == 2) {
                capture("02-config-categories");
                check(!entries(common).isEmpty(), "Categories must be generated from the registered Forge spec");
                common = screen(folder(root, Boolean.class));
                mc.setScreen(common);
                query = invoke(entry(common, "BooleanItem"), "getLabel").toString();
                ((EditBox) field(common, "searchTextField")).setValue(query);
            } else if (step == 3) {
                capture("03-config-search");
                var results = ((net.minecraft.client.gui.components.AbstractSelectionList<?>) field(common, "list")).children();
                check(!results.isEmpty() && results.size() <= entries(common).size(), "Search must return matching entries");
                ((EditBox) field(common, "searchTextField")).setValue("");
                var booleanScreen = screen(folder(root, Boolean.class));
                mc.setScreen(booleanScreen);
                var row = entry(booleanScreen, "BooleanItem");
                Object holder = field(row, "holder");
                Object before = invoke(holder, "get");
                ((Button) field(row, "button")).onPress();
                check(!before.equals(invoke(holder, "get")), "Boolean control must toggle its draft value");
                ((Button) field(row, "resetButton")).onPress();
                check(invoke(holder, "getDefault").equals(invoke(holder, "get")), "Per-value reset must restore defaults");
            } else if (step == 4) {
                capture("04-boolean-controls");
                mc.setScreen(selection);
                selectConfig("-client.toml");
                var numeric = screen(folder(root, Number.class));
                mc.setScreen(numeric);
                var row = entries(numeric).stream().filter(v -> {
                    try { return invoke(field(v, "holder"), "get") instanceof Number; }
                    catch (RuntimeException ignored) { return false; }
                }).findFirst().orElseThrow();
                Object holder = field(row, "holder");
                Object before = invoke(holder, "get");
                var editor = (EditBox) field(row, "textField");
                editor.setValue("not-a-number");
                check(before.equals(invoke(holder, "get")), "Invalid input must not replace the numeric draft");
                editor.setValue(before.toString());
            } else if (step == 5) {
                capture("05-number-controls");
                mc.setScreen(selection);
                // Only this test process: provide world-config defaults without opening a user world.
                ConfigManager.SERVER_SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
                selectConfig("-server.toml");
                var lists = screen(folder(root, List.class));
                mc.setScreen(lists);
                ((Button) field(entry(lists, "ListItem"), "button")).onPress();
                check(mc.screen.getClass().getSimpleName().equals("EditListScreen"), "List control must open a list editor");
            } else if (step == 6) {
                capture("06-list-editor");
                mc.setScreen(selection);
                selectConfig("-common.toml");
                common = screen(root);
                mc.setScreen(common);
                ((Button) field(common, "restoreButton")).onPress();
                check(mc.screen.getClass().getSimpleName().contains("Confirmation"), "Restore-all must require confirmation");
            } else if (step == 7) {
                capture("07-restore-confirmation");
                mc.setScreen(common);
                ((Button) field(entry(common, "FolderItem"), "button")).onPress();
                var edits = mc.screen;
                ((Button) field(entry(edits, "BooleanItem"), "button")).onPress();
                back(edits);
                check(mc.screen == common, "Category navigation must return to its root without losing the draft");
                back(common);
                check(mc.screen.getClass().getSimpleName().contains("Confirmation"), "Closing unsaved edits must show confirmation");
            } else if (step == 8) {
                capture("08-unsaved-confirmation");
                System.out.println("CONFIG_UI_CHECK_OK configured factory, categories, search, boolean/reset, numeric validation, lists, confirmations");
                mc.stop();
            } else if (step == 100) {
                capture("09-standalone-fallback");
                System.out.println("CONFIG_UI_CHECK_OK standalone fallback without Configured");
                mc.stop();
            }
            step++;
        } catch (Throwable failure) {
            throw new IllegalStateException("Config UI client check failed at step " + step, failure);
        }
    }

    private static Object invoke(Object object, String name) {
        try {
            var method = object.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(object);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(name, failure);
        }
    }

    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(name, failure);
            }
        }
        throw new IllegalStateException("Missing UI field " + name);
    }

    private static List<?> entries(Object screen) { return (List<?>) field(screen, "entries"); }

    private static void back(Screen screen) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getY() >= screen.height - 35)
                .max(java.util.Comparator.comparingInt(Button::getX)).orElseThrow().onPress();
    }

    private static void selectConfig(String suffix) {
        var configs = (Map<?, ?>) field(selection, "configMap");
        config = configs.values().stream().flatMap(v -> ((Collection<?>) v).stream())
                .filter(v -> invoke(v, "getFileName").toString().endsWith(suffix)).findFirst().orElseThrow();
        root = invoke(config, "getRoot");
    }

    private static Object entry(Object screen, String kind) {
        return entries(screen).stream().filter(v -> v.getClass().getSimpleName().equals(kind)).findFirst().orElseThrow();
    }

    private static Object folder(Object entry, Class<?> valueType) {
        for (Object child : (List<?>) invoke(entry, "getChildren")) {
            if ((boolean) invoke(child, "isLeaf") && valueType.isInstance(invoke(invoke(child, "getValue"), "get"))) return entry;
        }
        for (Object child : (List<?>) invoke(entry, "getChildren")) {
            if (!(boolean) invoke(child, "isLeaf")) {
                Object found = folder(child, valueType);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Screen screen(Object folder) throws ReflectiveOperationException {
        check(folder != null, "Expected typed configuration category");
        var constructor = Class.forName("com.mrcrayfish.configured.client.screen.ConfigScreen").getDeclaredConstructor(
                Screen.class, Component.class, Class.forName("com.mrcrayfish.configured.api.IModConfig"),
                ResourceLocation.class, Class.forName("com.mrcrayfish.configured.api.IConfigEntry"));
        constructor.setAccessible(true);
        return (Screen) constructor.newInstance(selection, Component.literal("Useless Mod"), config, BACKGROUND, folder);
    }

    private static void capture(String name) throws java.io.IOException {
        var directory = Path.of(System.getProperty("useless.configUiScreenshots"));
        Files.createDirectories(directory);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(directory.resolve(name + ".png"));
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
