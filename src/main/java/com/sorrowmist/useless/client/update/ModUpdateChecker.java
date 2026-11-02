package com.sorrowmist.useless.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * 客户端侧的模组版本检查。
 *
 * <p>实现方式参考 Modern Industrialization 的 {@code VersionEvents}
 * （AztechMC/Modern-Industrialization，MIT）：在客户端登录事件后另起线程拉取 CurseForge
 * 文件列表，再与当前模组版本比较，仅在发现新版本时向聊天栏发送一条可点击的消息。</p>
 *
 * <p>与参考实现的差异（均为本项目需要处理的问题）：</p>
 * <ul>
 *   <li>CurseForge 官方接口 {@code api.curseforge.com} 必须携带 API Key，因此与参考实现一样
 *       使用免 Key 的镜像 {@code api.cfwidget.com}；该镜像的 {@code download} 字段只反映
 *       项目里最近上传的文件，本项目同时维护 1.20.1 与 1.21.1 两条线，直接用它会把 1.20.1
 *       的版本当成 1.21.1 的更新，所以一律从 {@code files} 中按 Minecraft 版本与加载器过滤。</li>
 *   <li>只在客户端执行并带本地缓存，专用服务器不需要出站网络，也不会因为玩家频繁进出世界
 *       反复请求第三方接口。</li>
 *   <li>失败一律静默：联网异常、响应格式变化都只记一条 debug 日志，不影响进服。</li>
 * </ul>
 */
public final class ModUpdateChecker {
    /** CurseForge 项目页地址，消息里的“CurseForge”可点击跳转。 */
    private static final String PROJECT_URL = "https://www.curseforge.com/minecraft/mc-mods/some-useless-things";

    /** 免 Key 的 CurseForge 数据镜像，字段结构见 {@link #pickLatest(String)}。 */
    private static final String API_URL = "https://api.cfwidget.com/minecraft/mc-mods/some-useless-things";

    /** 请求超时。进服路径上的网络调用宁可放弃，也不能长时间占用线程。 */
    private static final int CONNECT_TIMEOUT_MILLIS = 8000;
    private static final int READ_TIMEOUT_MILLIS = 12000;

    /** 同一份检查结果的有效期，避免玩家反复进出世界时重复请求。 */
    private static final long CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L;

    private static final String CACHE_FILE_NAME = "useless_mod-update-check.properties";
    private static final String THREAD_NAME = "Useless Mod Update Checker";

    /** 已有检查线程在运行，避免短时间内连续登录并发请求第三方接口。 */
    private static volatile boolean checkInFlight = false;

    private ModUpdateChecker() {
    }

    /**
     * 在客户端进入世界后发起一次异步检查。
     *
     * <p>调用方为客户端登录事件；任何异常都在内部消化，不会向调用方抛出。</p>
     */
    public static void checkOnLogin(LocalPlayer player) {
        if (player == null || checkInFlight) {
            return;
        }
        checkInFlight = true;

        // 每次进入世界都检查一次；缓存只用于在有效期内复用结果，不用于抑制提示。
        String currentVersion = currentModVersion();
        if (currentVersion == null || !ConfigManager.isUpdateCheckEnabled()) {
            checkInFlight = false;
            return;
        }

        Thread thread = new Thread(() -> runCheck(player, currentVersion), THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    private static void runCheck(LocalPlayer player, String currentVersion) {
        try {
            String minecraftVersion = currentMinecraftVersion();
            if (minecraftVersion == null) {
                return;
            }

            String latest = readCachedLatest(minecraftVersion);
            if (latest == null) {
                // 缓存未命中或已过期：真正联网一次，随后写回缓存。
                latest = fetchLatest(minecraftVersion);
                if (latest == null) {
                    return;
                }
                writeCachedLatest(minecraftVersion, latest);
            }

            notifyIfOutdated(player, currentVersion, latest);
        } catch (Exception e) {
            UselessMod.LOGGER.debug("Mod update check failed", e);
        } finally {
            checkInFlight = false;
        }
    }

    /**
     * 拉取并解析 CurseForge 文件列表，返回当前 Minecraft 版本下最新的发布版本号。
     *
     * @return 归一化后的版本号（例如 {@code 2.4.6.2}）；没有匹配文件时返回 {@code null}
     */
    private static String fetchLatest(String minecraftVersion) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "UselessMod/" + currentModVersion());
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                UselessMod.LOGGER.debug("Mod update check returned HTTP {}", status);
                return null;
            }
            try (Reader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return pickLatest(JsonParser.parseReader(reader), minecraftVersion);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 从镜像响应中挑出与当前 Minecraft 版本匹配的最新发布版本。
     *
     * <p>过滤条件与参考实现一致：{@code versions} 数组必须包含当前 MC 版本，且文件类型不是
     * alpha；额外的加载器判断是为了在同一个 MC 版本下同时存在多个加载器时不会取错。
     * 排序依据是上传时间，版本号本身只在展示与比较时使用。</p>
     */
    private static String pickLatest(JsonElement rootElement, String minecraftVersion) {
        if (!rootElement.isJsonObject()) {
            return null;
        }
        JsonElement filesElement = rootElement.getAsJsonObject().get("files");
        if (filesElement == null || !filesElement.isJsonArray()) {
            return null;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (JsonElement fileElement : filesElement.getAsJsonArray()) {
            if (!fileElement.isJsonObject()) {
                continue;
            }
            JsonObject file = fileElement.getAsJsonObject();

            String type = stringField(file, "type");
            if (type != null && type.equalsIgnoreCase("alpha")) {
                continue;
            }

            JsonElement versionsElement = file.get("versions");
            if (versionsElement == null || !versionsElement.isJsonArray()) {
                continue;
            }
            JsonArray versions = versionsElement.getAsJsonArray();
            if (!containsVersion(versions, minecraftVersion)) {
                continue;
            }
            // 镜像没有独立的加载器字段，加载器名混在 versions 数组里；包含至少一个已知加载器
            // 才视为可用的发布文件，避免把纯资源包之类的条目算进来。
            if (!containsAnyLoader(versions)) {
                continue;
            }

            String display = stringField(file, "display");
            if (display == null) {
                continue;
            }
            String version = normalizeVersion(display);
            if (version == null) {
                continue;
            }

            Date uploadedAt = parseUploadedAt(stringField(file, "uploaded_at"));
            candidates.add(new Candidate(version, uploadedAt));
        }

        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparing(Candidate::uploadedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return candidates.get(0).version();
    }

    private static boolean containsVersion(JsonArray versions, String minecraftVersion) {
        for (JsonElement element : versions) {
            if (element.isJsonPrimitive() && element.getAsString().equals(minecraftVersion)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyLoader(JsonArray versions) {
        for (JsonElement element : versions) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = element.getAsString().toLowerCase(Locale.ROOT);
            if (value.equals("neoforge") || value.equals("forge") || value.equals("fabric")) {
                return true;
            }
        }
        return false;
    }

    private static String stringField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static Date parseUploadedAt(String uploadedAt) {
        if (uploadedAt == null) {
            return null;
        }
        try {
            return Date.from(Instant.parse(uploadedAt));
        } catch (DateTimeParseException ignored) {
            // 时间格式变化时退化为“无时间戳”，该条目仍可参与比较但排在最后。
            return null;
        }
    }

    /**
     * 把 CurseForge 的展示名归一化成可比较的版本号。
     *
     * <p>例如 {@code "Useless Mod 1.21.1-2.4.6.2"} 与 {@code "Useless Mod 20.2.4.11"} 都会去掉
     * 前缀，再剥掉形如 {@code 1.21.1-} 的 Minecraft 版本前缀，只保留模组自身的版本号。
     * 两种情况共存时不能直接比较，因此前缀剥离规则固定在 {@link #normalizeVersion(String)}。</p>
     */
    private static String normalizeVersion(String display) {
        String text = display.trim();
        int spaceIndex = text.lastIndexOf(' ');
        if (spaceIndex >= 0) {
            text = text.substring(spaceIndex + 1).trim();
        }
        // 剥掉 Minecraft 版本前缀，形如 1.21.1-2.4.6.2 → 2.4.6.2
        if (text.startsWith("1.")) {
            int dashIndex = text.indexOf('-');
            if (dashIndex > 0 && dashIndex < text.length() - 1) {
                text = text.substring(dashIndex + 1).trim();
            }
        }
        return text.isEmpty() ? null : text;
    }

    /**
     * 比较版本并向玩家发送提示。
     *
     * <p>只在聊天栏出现；点击“CurseForge”打开项目页。用 {@link DefaultArtifactVersion} 而不是
     * 字符串比较，避免 2.4.10 与 2.4.9 这类位数不同的版本被判错。</p>
     */
    private static void notifyIfOutdated(LocalPlayer player, String currentVersion, String latestVersion) {
        DefaultArtifactVersion current;
        DefaultArtifactVersion latest;
        try {
            current = new DefaultArtifactVersion(currentVersion);
            latest = new DefaultArtifactVersion(latestVersion);
        } catch (Exception e) {
            UselessMod.LOGGER.debug("Unparsable version: current={}, latest={}", currentVersion, latestVersion);
            return;
        }
        if (latest.compareTo(current) <= 0) {
            return;
        }

        Style linkStyle = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PROJECT_URL))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.useless_mod.update.hover")))
                .applyFormat(ChatFormatting.UNDERLINE)
                .applyFormat(ChatFormatting.GREEN);

        // 聊天栏同时有其它模组的消息，必须写明是哪个模组有新版本，否则玩家无法判断来源。
        MutableComponent message = Component.translatable(
                "message.useless_mod.update.available",
                currentModDisplayName(),
                latestVersion,
                Component.translatable("message.useless_mod.update.link").setStyle(linkStyle));

        Minecraft.getInstance().execute(() -> {
            // 线程启动到回调执行之间玩家可能已经退出，回调前必须再确认一次仍是同一个玩家。
            if (Minecraft.getInstance().player == player) {
                player.displayClientMessage(message, false);
            }
        });
    }

    /** 模组的展示名，用于提示中指明来源；元数据缺失时退回 modid。 */
    private static String currentModDisplayName() {
        return ModList.get().getModContainerById(UselessMod.MODID)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(UselessMod.MODID);
    }

    private static String currentModVersion() {
        return ModList.get().getModContainerById(UselessMod.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static String currentMinecraftVersion() {
        return ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static Path cacheFile() {
        return FMLPaths.CONFIGDIR.get().resolve(CACHE_FILE_NAME);
    }

    /**
     * 读取缓存中的最新版本号；缓存不存在、损坏或超过有效期时返回 {@code null}。
     *
     * <p>缓存按 Minecraft 版本分别记录，玩家切换不同的实例版本时不会互相干扰。</p>
     */
    private static String readCachedLatest(String minecraftVersion) {
        Path file = cacheFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            return null;
        }

        long checkedAt;
        try {
            checkedAt = Long.parseLong(properties.getProperty("checked_at." + minecraftVersion, "0"));
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (System.currentTimeMillis() - checkedAt > CACHE_TTL_MILLIS) {
            return null;
        }
        String latest = properties.getProperty("latest." + minecraftVersion);
        return latest == null || latest.isBlank() ? null : latest;
    }

    private static void writeCachedLatest(String minecraftVersion, String latestVersion) {
        Path file = cacheFile();
        Properties properties = new Properties();
        try {
            // 先读回已有内容，保留其它 Minecraft 版本的记录。
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            properties.setProperty("latest." + minecraftVersion, latestVersion);
            properties.setProperty("checked_at." + minecraftVersion, Long.toString(System.currentTimeMillis()));
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Useless Mod update check cache");
            }
        } catch (IOException e) {
            // 缓存写失败只影响下次是否会重复请求，不影响本次提示。
            UselessMod.LOGGER.debug("Failed to write update check cache", e);
        }
    }

    /** 参与比较的候选文件；{@code uploadedAt} 可能为 {@code null}，排序时排在最后。 */
    private record Candidate(String version, Date uploadedAt) {
    }
}