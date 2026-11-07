package com.sorrowmist.useless.client.update;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Forge 1.20.1 port of the upstream client update checker (including the 2.4.6.5 prefix fix).
 * Like Modern Industrialization's MIT-licensed VersionEvents, checks off-thread after login;
 * cfwidget is used because the official CurseForge API requires an API key.
 * No dedicated-server subscription and no client setup/init registration are needed.
 */
@Mod.EventBusSubscriber(modid = UselessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ModUpdateChecker {
    private static final String PROJECT_URL = "https://www.curseforge.com/minecraft/mc-mods/some-useless-things";
    private static final String API_URL = "https://api.cfwidget.com/minecraft/mc-mods/some-useless-things";
    private static final String CACHE_FILE_NAME = "useless_mod-update-check.properties";
    private static final int CONNECT_TIMEOUT_MILLIS = 8000;
    private static final int READ_TIMEOUT_MILLIS = 12000;
    private static final Executor BACKGROUND = task -> {
        Thread thread = new Thread(task, "Useless Mod Update Checker");
        thread.setDaemon(true);
        thread.start();
    };

    // Accessed only on the client thread. A connection/session survives respawn and dimension changes.
    private static LoginSession session;
    private static CompletableFuture<String> inFlight;

    private ModUpdateChecker() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            Connection connection = event.getConnection();
            if (event.getPlayer() == null || connection == null
                    || (session != null && session.connection == connection)) {
                return;
            }
            LoginSession login = new LoginSession(connection);
            session = login;
            if (!ConfigManager.isUpdateCheckerEnabled()) {
                return;
            }
            String currentVersion = modVersion(UselessMod.MODID);
            String minecraftVersion = modVersion("minecraft");
            if (UpdateVersion.normalize(currentVersion) == null || minecraftVersion == null) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            // A rapid reconnect attaches to the existing request, rather than losing the new world's
            // check to an in-flight boolean or starting a second HTTP request.
            if (inFlight == null || inFlight.isDone()) {
                inFlight = CompletableFuture.supplyAsync(
                        () -> findLatest(minecraftVersion, currentVersion), BACKGROUND);
            }
            inFlight.thenAccept(latest -> {
                if (UpdateVersion.isNewer(currentVersion, latest)) {
                    minecraft.execute(() -> notifyIfOutdated(login, currentVersion, latest));
                }
            }).exceptionally(error -> {
                UselessMod.LOGGER.debug("Mod update check callback failed", error);
                return null;
            });
        } catch (Exception error) {
            UselessMod.LOGGER.debug("Could not start mod update check", error);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // LoggingOut may contain a null player. Invalidate queued callbacks unconditionally.
        session = null;
        // Leave the daemon request running to populate the cache / serve a rapid reconnect.
    }

    private static String findLatest(String minecraftVersion, String currentVersion) {
        try {
            UpdateCheckCache cache = new UpdateCheckCache(FMLPaths.CONFIGDIR.get().resolve(CACHE_FILE_NAME));
            UpdateCheckCache.Entry cached = cache.read(minecraftVersion, System.currentTimeMillis());
            if (cached != null) {
                return cached.latestVersion();
            }
            String latest = fetchLatest(minecraftVersion, currentVersion);
            try {
                // A successful feed with no matching file is also cached; HTTP/parse failures are not.
                cache.write(minecraftVersion, latest, System.currentTimeMillis());
            } catch (IOException | IllegalArgumentException error) {
                // Cache failure must not prevent this login's notification.
                UselessMod.LOGGER.debug("Failed to write update check cache", error);
            }
            return latest;
        } catch (Exception error) {
            UselessMod.LOGGER.debug("Mod update check failed", error);
            return null;
        }
    }

    private static String fetchLatest(String minecraftVersion, String currentVersion) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "UselessMod/" + currentVersion);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Update feed returned HTTP " + status);
            }
            try (Reader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return CurseForgeUpdateFeed.pickLatest(JsonParser.parseReader(reader), minecraftVersion);
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Runs on the client thread, including the final config/connection and once-per-login checks. */
    private static void notifyIfOutdated(LoginSession expected, String currentVersion, String latestVersion) {
        Minecraft minecraft = Minecraft.getInstance();
        if (session != expected || expected.notified || minecraft.player == null
                || minecraft.getConnection() == null
                || minecraft.getConnection().getConnection() != expected.connection
                || !ConfigManager.isUpdateCheckerEnabled()
                || !UpdateVersion.isNewer(currentVersion, latestVersion)) {
            return;
        }
        Style linkStyle = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PROJECT_URL))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.useless_mod.update.hover")))
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true);
        String displayName = ModList.get().getModContainerById(UselessMod.MODID)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(UselessMod.MODID);
        Component message = Component.translatable("message.useless_mod.update.available",
                displayName, latestVersion,
                Component.translatable("message.useless_mod.update.link").setStyle(linkStyle));
        expected.notified = true;
        minecraft.player.displayClientMessage(message, false);
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static final class LoginSession {
        private final Connection connection;
        private boolean notified;

        private LoginSession(Connection connection) {
            this.connection = connection;
        }
    }
}
