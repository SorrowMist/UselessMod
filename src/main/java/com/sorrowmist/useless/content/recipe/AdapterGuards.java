package com.sorrowmist.useless.content.recipe;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 配方适配器的统一容错守卫。
 *
 * <p>可选的兼容模组可能在运行期以不兼容的版本出现：类被移到别的包、方法签名改变、静态初始化
 * 失败等。这些问题的表现是 {@link NoClassDefFoundError}、{@link NoSuchMethodError} 一类的
 * {@link LinkageError}，它们不属于 {@link RuntimeException}，普通 catch 兜不住。适配器调用
 * 发生在配方目录构建与机器查找配方的热路径上，一旦向上冒泡就会终止服务器 tick 循环，表现为
 * 「游戏打不开」。</p>
 *
 * <p>本类提供两种降级口径：</p>
 * <ul>
 *   <li>{@link #call}：结构性调用（取配方类、取模具、枚举合成配方、查找匹配配方）。失败说明该
 *       适配器与当前模组版本不匹配，停用其对应动作，后续不再调用。</li>
 *   <li>{@link #callEach}：逐条数据的转换。普通运行期异常通常只是某一条源配方无法转换，记为
 *       一次警告后跳过该条，不影响同一适配器的其它配方；只有链接错误才停用整个动作。</li>
 * </ul>
 *
 * <p>停用与日志都按「适配器类 + 动作」记录，因此同一问题不会在每条配方上重复打印；逐条降级
 * 只在首次发生时写一行摘要，后续只累加计数，日志量不随坏配方条数增长。</p>
 */
public final class AdapterGuards {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 诊断信息里最多展开的 cause 层数。 */
    private static final int MAX_CAUSE_DEPTH = 4;

    /** 单条异常消息的最大字符数，超出部分截断。 */
    private static final int MAX_MESSAGE_LENGTH = 240;

    /** 已停用的「适配器 + 动作」组合。 */
    private static final Set<FailureKey> DISABLED = ConcurrentHashMap.newKeySet();

    /** 逐条降级的跳过计数，用于「一次摘要日志」后不再逐条打印。 */
    private static final Map<FailureKey, AtomicInteger> SKIPPED_ITEMS = new ConcurrentHashMap<>();

    /** 适配器类名 → 配方来源 id（即兼容模组 id），用于把失败归类到具体模组。 */
    private static final Map<String, String> ADAPTER_SOURCES = new ConcurrentHashMap<>();

    /** 注册阶段因版本错配被整体跳过的模组 id。 */
    private static final Set<String> MOD_FAILURES = ConcurrentHashMap.newKeySet();

    private AdapterGuards() {
    }

    /**
     * 执行一次结构性适配器调用，失败即停用该动作。
     *
     * @param adapter  适配器实例，用于日志与停用标记
     * @param action   动作名，如 {@code getRecipeClass}、{@code findMatchingRecipes}
     * @param supplier 实际调用
     * @param fallback 失败或已停用时返回的降级值
     * @param <T>      返回值类型
     * @return 调用结果；异常或已停用时返回 {@code fallback}
     */
    public static <T> T call(@Nullable Object adapter, String action, Supplier<T> supplier, T fallback) {
        if (adapter == null) return fallback;

        FailureKey key = key(adapter, action);
        if (DISABLED.contains(key)) return fallback;

        try {
            return supplier.get();
        } catch (RuntimeException | LinkageError exception) {
            disable(key, exception);
            return fallback;
        }
    }

    /** 执行一次无返回值的结构性适配器调用。 */
    public static void run(@Nullable Object adapter, String action, Runnable body) {
        call(adapter, action, () -> {
            body.run();
            return null;
        }, null);
    }

    /**
     * 执行一次逐条数据级别的适配器调用。
     *
     * <p>普通运行期异常只降级当前这一条，不永久停用该适配器——否则一条坏配方就会让同类全部
     * 配方消失。链接错误则说明版本不匹配，与 {@link #call} 一样停用该动作。</p>
     */
    public static <T> T callEach(@Nullable Object adapter, String action, Supplier<T> supplier, T fallback) {
        return callEach(adapter, action, null, supplier, fallback);
    }

    /**
     * 执行一次逐条数据级别的适配器调用，并在首次降级时把该条目的标识写进摘要日志。
     *
     * @param itemDescription 惰性求值的条目标识（如配方 id）；只在需要记录摘要时求值，
     *                        求值本身抛异常也不影响降级
     */
    public static <T> T callEach(@Nullable Object adapter, String action,
                                 @Nullable Supplier<String> itemDescription,
                                 Supplier<T> supplier, T fallback) {
        if (adapter == null) return fallback;

        FailureKey key = key(adapter, action);
        if (DISABLED.contains(key)) return fallback;

        try {
            return supplier.get();
        } catch (LinkageError error) {
            disable(key, error);
            return fallback;
        } catch (RuntimeException exception) {
            skipOneItem(key, itemDescription, exception);
            return fallback;
        }
    }

    /** 该适配器在指定动作上是否已被停用。 */
    public static boolean isDisabled(@Nullable Object adapter, String action) {
        return adapter != null && DISABLED.contains(key(adapter, action));
    }

    /**
     * 该适配器在指定动作上累计跳过的条目数，供诊断与回归测试使用。
     */
    public static int skippedItems(@Nullable Object adapter, String action) {
        if (adapter == null) return 0;
        AtomicInteger counter = SKIPPED_ITEMS.get(key(adapter, action));
        return counter == null ? 0 : counter.get();
    }

    /**
     * 登记适配器所属的配方来源 id（即兼容模组 id）。
     *
     * <p>必须在第一次调用该适配器之前登记，否则首次失败时无法把这次停用归到具体模组上。</p>
     */
    public static void rememberSource(@Nullable Object adapter, @Nullable String sourceId) {
        if (adapter == null || sourceId == null || sourceId.isBlank()) return;
        String normalized = RecipeSourceIds.normalize(sourceId);
        if (RecipeSourceIds.UNKNOWN.equals(normalized)) return;
        ADAPTER_SOURCES.put(adapter.getClass().getName(), normalized);
    }

    /**
     * 记录某个模组在注册阶段整体失败。
     *
     * <p>适配器在实例化阶段就因版本错配抛出链接错误时，根本走不到 {@link #rememberSource}，
     * 因此注册方需要显式把模组 id 记进来，登录提示才能覆盖这一类失败。</p>
     */
    public static void recordModFailure(@Nullable String modId) {
        if (modId == null || modId.isBlank()) return;
        String normalized = RecipeSourceIds.normalize(modId);
        if (RecipeSourceIds.UNKNOWN.equals(normalized)) return;
        MOD_FAILURES.add(normalized);
    }

    /**
     * 返回本轮有适配器操作被停用的模组 id 列表，按 id 排序。
     *
     * <p>供登录提示使用：提示文案只需要知道「哪些模组不适配」，不需要逐个适配器说明。</p>
     */
    public static List<String> disabledMods() {
        return MOD_FAILURES.stream().sorted().toList();
    }

    /**
     * 返回本轮有适配器操作被停用的模组显示名列表，按对应 id 排序。
     *
     * <p>显示名取自模组元数据，供面向玩家的提示使用；查不到对应模组时退回其 id，
     * 以免提示里出现空白项。</p>
     */
    public static List<String> disabledModNames() {
        List<String> names = new ArrayList<>();
        for (String modId : disabledMods()) {
            names.add(modDisplayName(modId));
        }
        return names;
    }

    private static String modDisplayName(String modId) {
        try {
            ModList modList = ModList.get();
            if (modList == null) return modId;
            return modList.getModContainerById(modId)
                    .map(container -> container.getModInfo().getDisplayName())
                    .orElse(modId);
        } catch (RuntimeException | LinkageError exception) {
            // 模组元数据查询本身失败不应影响登录提示，退回 id 即可。
            return modId;
        }
    }

    /** 显式重新初始化及隔离测试使用；正常运行中与全局适配器注册表保持相同的进程生命周期。 */
    public static void clearFailures() {
        DISABLED.clear();
        SKIPPED_ITEMS.clear();
        MOD_FAILURES.clear();
        ADAPTER_SOURCES.clear();
    }

    /**
     * 执行一段不归属单个适配器的结构性动作，失败只降级本次调用、不永久停用。
     *
     * <p>用于整表级别的流程，例如配方目录构建、配方索引重建、按输入查找配方。这些流程会间接
     * 触达大量兼容模组的类，任何一处抛出 {@link LinkageError} 都会沿调用栈上冒到服务器 tick。
     * 与 {@link #call} 的区别是不按「适配器 + 动作」记账：这类流程失败后应当在下一次调用时重试，
     * 而不是永久跳过。</p>
     *
     * @param scope    日志作用域，用于标识失败发生的阶段
     * @param supplier 实际调用
     * @param fallback 失败时返回的降级值
     * @param <T>      返回值类型
     * @return 调用结果；失败时返回 {@code fallback}
     */
    public static <T> T guard(String scope, Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (RuntimeException | LinkageError exception) {
            logGuardFailure(scope, exception);
            return fallback;
        }
    }

    private static void logGuardFailure(String scope, Throwable exception) {
        // 作用域写进消息本身，限流按「作用域 + 级别」各自成键，避免不同阶段互相吞掉告警。
        if (exception instanceof LinkageError) {
            RateLimitedLogger.error(LOGGER,
                    "Recipe subsystem failed during " + scope + "; this step is skipped for now. "
                            + "An optional mod is likely installed in an incompatible version. Cause: {}",
                    describe(exception));
        } else {
            RateLimitedLogger.error(LOGGER,
                    "Recipe subsystem failed during " + scope + "; this step is skipped for now. Cause: {}",
                    describe(exception));
        }
    }

    private static FailureKey key(Object adapter, String action) {
        return new FailureKey(adapter.getClass().getName(), action);
    }

    private static void disable(FailureKey key, Throwable exception) {
        if (!DISABLED.add(key)) return;
        String sourceId = ADAPTER_SOURCES.get(key.adapterClass());
        if (sourceId != null) MOD_FAILURES.add(sourceId);
        if (exception instanceof LinkageError) {
            // 版本不匹配属于「模组组合问题」而非本模组的缺陷，提示用户升级/降级对应模组即可。
            LOGGER.error("Recipe adapter {} is incompatible with the installed mod version; "
                            + "its {} will be skipped for this session. "
                            + "Check the mod version listed in the compatibility notes. Cause: {}",
                    key.adapterClass(), key.action(), describe(exception));
        } else {
            LOGGER.warn("Recipe adapter {} failed during {}; "
                            + "it will be skipped for this session. Cause: {}",
                    key.adapterClass(), key.action(), describe(exception));
        }
    }

    /**
     * 记录一次逐条降级：首次只写一行摘要，之后仅累加计数。
     *
     * <p>坏配方往往成批出现（例如某个模组换了数据格式）。逐条打印会让日志被同一类信息淹没，
     * 反而看不出「哪个适配器、哪一条最先坏」；这里把「哪个适配器、什么动作、第一条的标识、
     * 原因」压成一行，后续失败不再重复。</p>
     */
    private static void skipOneItem(FailureKey key, @Nullable Supplier<String> itemDescription,
                                    RuntimeException exception) {
        AtomicInteger counter = SKIPPED_ITEMS.computeIfAbsent(key, ignored -> new AtomicInteger());
        int skipped = counter.incrementAndGet();
        if (skipped > 1) return;
        RateLimitedLogger.warn(LOGGER,
                "Recipe adapter " + key.adapterClass() + " skipped an unconvertible item during "
                        + key.action() + "; further skips are counted silently. First item: {}. Cause: {}",
                safeDescription(itemDescription), describe(exception));
    }

    private static String safeDescription(@Nullable Supplier<String> itemDescription) {
        if (itemDescription == null) return "<unknown>";
        try {
            String description = itemDescription.get();
            return description == null || description.isBlank() ? "<unknown>" : description;
        } catch (RuntimeException | LinkageError exception) {
            // 条目标识本身也可能来自不兼容模组，取不到就退回占位符。
            return "<unavailable: " + exception.getClass().getSimpleName() + ">";
        }
    }

    /**
     * 把异常整理成一行诊断信息：异常类型、消息与根因链，不附带堆栈。
     *
     * <p>适配器失败发生在配方目录构建与机器查找配方的热路径上，同一条问题会被反复触发。
     * 原样打印堆栈会让日志被上百行重复的调用链淹没，反而看不清「哪个类缺失、哪个方法不存在」。
     * 这里只保留定位问题所需的类型名、消息与至多 {@value #MAX_CAUSE_DEPTH} 层根因，堆栈交由
     * 调试级别另行开启。</p>
     *
     * <p>适配器与兼容模组加载器内部的失败日志也通过本方法输出异常，避免把完整堆栈写进
     * log 文件。</p>
     */
    public static String describe(@Nullable Throwable exception) {
        if (exception == null) return "<none>";
        StringBuilder builder = new StringBuilder();
        Throwable current = exception;
        for (int depth = 0; current != null; depth++) {
            if (depth >= MAX_CAUSE_DEPTH) {
                builder.append(" <- ...");
                break;
            }
            if (depth > 0) builder.append(" <- ");
            builder.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                builder.append(": ").append(abbreviate(message));
            }
            Throwable cause = current.getCause();
            // 自引用或成环的 cause 链会让循环无法收敛，遇到即止。
            current = cause == current ? null : cause;
        }
        return builder.toString();
    }

    private static String abbreviate(String message) {
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    private record FailureKey(String adapterClass, String action) {
    }
}
