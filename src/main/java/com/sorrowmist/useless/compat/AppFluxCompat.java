package com.sorrowmist.useless.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * AppliedFlux (AppFlux) 兼容层。
 * <p>
 * AppFlux 将 FE 能量以 {@link FluxKey} 的形式存入 AE 网络存储（通量元件），
 * FluxKey 的数量即 FE 数量，直接通过 {@link MEStorage#extract} 抽取。
 */
public final class AppFluxCompat {

    private static final String MOD_ID = "appflux";

    @Nullable
    private static Boolean isLoaded;

    private AppFluxCompat() {}

    public static boolean isLoaded() {
        if (isLoaded == null) {
            isLoaded = ModList.get().isLoaded(MOD_ID);
        }
        return isLoaded;
    }

    /**
     * 从 AE 网络存储中抽取 AppFlux 存储的 FE 能量。
     * AppFlux 未安装时静默返回 0。
     *
     * @param amount 期望抽取的 FE 数量
     * @return 实际抽取的 FE 数量
     */
    public static long extractFe(IGrid grid, long amount, IActionSource source) {
        if (amount <= 0 || !isLoaded()) {
            return 0;
        }
        return Impl.extractFe(grid, amount, source);
    }

    /**
     * AppFlux 类引用隔离在内部类中，未安装时不会触发类加载。
     */
    private static final class Impl {

        static long extractFe(IGrid grid, long amount, IActionSource source) {
            MEStorage storage = grid.getStorageService().getInventory();
            return storage.extract(FluxKey.of(EnergyType.FE), amount, Actionable.MODULATE, source);
        }
    }
}
