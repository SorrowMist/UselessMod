package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link AeChemicalCompat}。
 *
 * <p>需要 AE2 <b>与</b> Applied Mekanistics 同时在场：前者提供 ME 存储，后者提供
 * {@code MekanismKey} 这个「化学品在 ME 网络里的身份」。任一缺席时本加载器恒返回
 * {@code null}，无线物流只是解析不出化学品端点，其余功能不受影响。</p>
 */
public final class AeChemicalCompatLoader {
    private static final String AE2 = "ae2";
    private static final String APPLIED_MEKANISTICS = "appmek";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.ae.AeChemicalCompat";

    private static volatile boolean initialized;
    private static volatile AeChemicalBridge bridge;

    private AeChemicalCompatLoader() {
    }

    @Nullable
    public static AeChemicalBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(AE2) && ModList.get().isLoaded(APPLIED_MEKANISTICS)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, AeChemicalCompatLoader.class.getClassLoader());
                    bridge = (AeChemicalBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the AE chemical bridge", exception);
                }
            }
        }
        return bridge;
    }

    /** 该坐标的 AE 网络端点（化学品）；不可用时返回 {@code null}。 */
    @Nullable
    public static ChemicalHandlerView chemicalEndpoint(Level level, BlockPos pos) {
        AeChemicalBridge resolved = bridge();
        return resolved == null ? null : resolved.chemicalEndpoint(level, pos);
    }
}
