package com.sorrowmist.useless.compat.neoecoae.compact;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「紧凑主机 → ECO 主机类」的映射表，供 AE2 的网格类索引使用。
 *
 * <p><b>为什么需要它</b>：AE2 的 {@code Grid} 用
 * {@code gridNode.getOwner().getClass()}（<b>精确运行时类</b>）给机器建索引，
 * 而 {@code IGrid.getMachines(Class)} 是精确 key 查询。ECO 通篇依赖
 * {@code grid.getMachines(ECOComputationSystemBlockEntity.class)} /
 * {@code getMachines(ECOCraftingSystemBlockEntity.class)} 来找自己的主机，
 * 我们的紧凑主机是它们的子类 → 查询永远落空（CPU 因此永远不会被 CraftingService 登记）。</p>
 *
 * <p>映射表让 {@code Grid.add/remove} 把紧凑主机登记在 <b>父类</b> 名下，
 * 于是从 AE2 与 ECO 的视角看，紧凑主机就是一个标准 ECO 主机。</p>
 *
 * <p><b>本类刻意不引用任何 ECO 类型</b>：它会被 AE2 的 mixin 引用，
 * 必须能在没有 ECO 的环境下安全加载（那时映射表是空的，等于什么都没做）。</p>
 */
public final class CompactMachineClassMap {

    private static final Map<Class<?>, Class<?>> PRESENTED = new ConcurrentHashMap<>();

    private CompactMachineClassMap() {}

    /** 注册「子类在网格里应该以哪个父类的身份出现」。 */
    public static void register(Class<?> compactClass, Class<?> presentedClass) {
        PRESENTED.put(compactClass, presentedClass);
    }

    /**
     * @param ownerClass {@code node.getOwner().getClass()}
     * @return 该宿主在 AE2 机器索引里应该使用的 key（非紧凑方块原样返回）
     */
    public static Class<?> presentedClass(@Nullable Class<?> ownerClass) {
        if (ownerClass == null || PRESENTED.isEmpty()) {
            return ownerClass;
        }
        Class<?> presented = PRESENTED.get(ownerClass);
        return presented == null ? ownerClass : presented;
    }

    /** 该宿主是否是紧凑方块（用于让 AE2 的 CPU 列表在节点入网时刷新）。 */
    public static boolean isCompactHost(@Nullable Object owner) {
        return owner != null && !PRESENTED.isEmpty() && PRESENTED.containsKey(owner.getClass());
    }
}
