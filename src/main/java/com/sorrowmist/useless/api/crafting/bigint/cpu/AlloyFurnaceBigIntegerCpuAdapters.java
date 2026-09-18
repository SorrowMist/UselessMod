package com.sorrowmist.useless.api.crafting.bigint.cpu;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CPU 适配器的显式注册表。
 *
 * <h2>什么时候注册</h2>
 *
 * <p>在你自己的 mod 初始化阶段注册一次即可，推荐放在
 * {@code FMLCommonSetupEvent#enqueueWork}（或等价的公共初始化钩子）里：</p>
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onCommonSetup(FMLCommonSetupEvent event) {
 *     event.enqueueWork(() ->
 *             AlloyFurnaceBigIntegerCpuAdapters.register(new MyCpuAdapter()));
 * }
 * }</pre>
 *
 * <p>注册表是全局的、进程级的，不随存档或世界重载清空。若你的 mod 支持运行期禁用，
 * 用 {@link #unregister} 摘掉即可。</p>
 *
 * <h2>线程</h2>
 *
 * <p>注册表本身是线程安全的（{@code ConcurrentHashMap}），但适配器<b>回调</b>只会在服务器线程发生。
 * 请不要在注册/注销的同时假设回调已停止 —— 注销只影响之后的查找。</p>
 */
public final class AlloyFurnaceBigIntegerCpuAdapters {
    private static final Map<ResourceLocation, AlloyFurnaceBigIntegerCpuAdapter> ADAPTERS =
            new ConcurrentHashMap<>();

    private AlloyFurnaceBigIntegerCpuAdapters() {
    }

    /**
     * 用适配器自己声明的 {@link AlloyFurnaceBigIntegerCpuAdapter#id()} 注册。
     *
     * @throws IllegalArgumentException id 已被别的适配器占用
     */
    public static void register(@NotNull AlloyFurnaceBigIntegerCpuAdapter adapter) {
        Objects.requireNonNull(adapter, "BigInteger crafting CPU adapter must not be null");
        register(adapter.id(), adapter);
    }

    /**
     * 显式指定 id 注册，并校验它与适配器声明的一致（防止复制粘贴写错）。
     *
     * @throws IllegalArgumentException id 与 {@code adapter.id()} 不一致，或该 id 已被占用
     */
    public static void register(@NotNull ResourceLocation id,
                                @NotNull AlloyFurnaceBigIntegerCpuAdapter adapter) {
        Objects.requireNonNull(id, "BigInteger crafting CPU adapter id must not be null");
        Objects.requireNonNull(adapter, "BigInteger crafting CPU adapter must not be null");
        if (!id.equals(adapter.id())) {
            throw new IllegalArgumentException(
                    "Registered id " + id + " does not match adapter id " + adapter.id());
        }
        AlloyFurnaceBigIntegerCpuAdapter previous = ADAPTERS.putIfAbsent(id, adapter);
        if (previous != null && previous != adapter) {
            throw new IllegalArgumentException("BigInteger crafting CPU adapter id is already registered: " + id);
        }
    }

    /**
     * 注销一个适配器。
     *
     * @param id 要摘掉的适配器 id；不存在时静默返回
     */
    public static void unregister(@Nullable ResourceLocation id) {
        if (id != null) {
            ADAPTERS.remove(id);
        }
    }

    /**
     * @param id 适配器 id
     * @return 命中的适配器；未注册或 {@code id} 为 {@code null} 时为空
     */
    public static @NotNull Optional<AlloyFurnaceBigIntegerCpuAdapter> find(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(ADAPTERS.get(id));
    }

    /** @return 当前已注册的全部适配器（只读视图） */
    public static @NotNull Map<ResourceLocation, AlloyFurnaceBigIntegerCpuAdapter> registered() {
        return Collections.unmodifiableMap(ADAPTERS);
    }
}
