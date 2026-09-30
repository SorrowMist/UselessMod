package com.sorrowmist.useless.compat.exdeorum;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * 造化杖对 Ex Deorum 三种工具效果的兼容门面。
 *
 * <p>本类刻意不引用 exdeorum 的任何类型：{@link ExDeorumCompatImpl} 的方法体与栈映射表引用了
 * {@code HammerRecipe}、{@code CrookRecipe} 等类，一旦本类直接承载这些实现，JVM 在链接本类时
 * 即须解析这些外部类型，未安装 Ex Deorum 时会在调用守卫方法的那一行抛出
 * {@link NoClassDefFoundError}，使「先判断是否加载」的守卫本身失效。</p>
 *
 * <p>因此实现被隔离在 {@code ExDeorumCompatImpl}，仅在该模组确已加载后经反射加载，
 * 与本模组既有的 {@code MekanismCompatLoader} 采用同一策略。入口方法的异常一律回退为
 * 原始掉落，保证兼容层故障不影响正常挖掘。</p>
 */
public final class ExDeorumCompat {

    public static final String MOD_ID = "exdeorum";

    /** 实现类名，与门面同包，独立成类以避免外部类型进入本类的常量池。 */
    private static final String IMPL_CLASS_NAME =
            "com.sorrowmist.useless.compat.exdeorum.ExDeorumCompatImpl";

    private ExDeorumCompat() {
    }

    /** Ex Deorum 是否已加载。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 按当前启用的 Ex Deorum 模式改写掉落。
     *
     * <p>未安装 Ex Deorum 时直接返回入参；实现类加载或调用失败时同样返回入参，
     * 使兼容层故障退化为「不做改写」，而非丢失整批掉落。</p>
     */
    public static List<ItemStack> rewriteDrops(ServerLevel level, BlockState state, List<ItemStack> original,
                                               ItemStack tool, boolean hammer, boolean compressedHammer,
                                               boolean crook, BlockPos pos, Player player) {
        if (!isLoaded()) {
            return original;
        }
        try {
            Class<?> impl = Class.forName(IMPL_CLASS_NAME, true, ExDeorumCompat.class.getClassLoader());
            Object rewritten = impl
                    .getMethod("rewriteDrops", ServerLevel.class, BlockState.class, List.class, ItemStack.class,
                            boolean.class, boolean.class, boolean.class, BlockPos.class, Player.class)
                    .invoke(null, level, state, original, tool, hammer, compressedHammer, crook, pos, player);
            return (List<ItemStack>) rewritten;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            return original;
        }
    }
}
