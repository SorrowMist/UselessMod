package com.sorrowmist.useless.mixin.compact;

import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import com.google.common.collect.SetMultimap;
import com.sorrowmist.useless.compat.neoecoae.compact.CompactMachineClassMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 AE2 的机器索引也能按「ECO 父类」查到紧凑方块主机。
 *
 * <p>{@code appeng.me.Grid} 用宿主<b>精确运行时类</b>建索引：
 * <pre>
 *   void add(GridNode n, ...) { this.machines.put(n.getOwner().getClass(), n); }
 *   public &lt;T&gt; Set&lt;T&gt; getMachines(Class&lt;T&gt; c) { ... this.machines.get(c) ... }   // 精确 key
 * </pre>
 * ECO 通篇用 {@code getMachines(ECOComputationSystemBlockEntity.class)} /
 * {@code getMachines(ECOCraftingSystemBlockEntity.class)} 找自己的主机，子类永远查不到。
 * 本 mixin 在 {@code add}/{@code remove} 里登记父类 key；非 {@link PatternContainer} 仍保留自己的 key，
 * 让需要精确查询紧凑宿主类的逻辑继续可用。</p>
 *
 * <p>为什么是「追加」而不是「替换」：样板管理终端是靠
 * {@code grid.getMachineClasses()} + {@code PatternContainer.class.isAssignableFrom(...)}
 * 做发现的（见 ECO 的 {@code PatternCatalog}），那里需要看到紧凑主机<b>自己</b>的类；
 * 聚合样板总线本身就是 {@link PatternContainer}，因此这类对象只登记父类 key，避免样板管理终端
 * 按两个 machine class 重复遍历同一个容器。</p>
 *
 * <p>⚠️ 参数类型必须与字节码里的调用点完全一致（{@code (Object,Object)}）——
 * 写成 {@code Class}/{@code IGridNode} 会被 mixin 判定为非法签名并<b>整个混入静默失效</b>，
 * 所以这里的两个参数都声明为 {@code Object} 再在内部强转。</p>
 */
@Mixin(targets = "appeng.me.Grid", remap = false)
public abstract class GridMachineClassMixin {

    @Redirect(
            method = "add",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/SetMultimap;put(Ljava/lang/Object;Ljava/lang/Object;)Z",
                    remap = false))
    private boolean uselessMod$indexByPresentedClass(SetMultimap<Class<?>, IGridNode> machines,
                                                     Object ownerClass,
                                                     Object node) {
        Class<?> owner = (Class<?>) ownerClass;
        Class<?> presented = CompactMachineClassMap.presentedClass(owner);
        if (presented != owner && PatternContainer.class.isAssignableFrom(owner)) {
            return machines.put(presented, (IGridNode) node);
        }
        boolean added = machines.put(owner, (IGridNode) node);
        if (presented != owner) {
            machines.put(presented, (IGridNode) node);
        }
        return added;
    }

    @Redirect(
            method = "remove",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/SetMultimap;remove(Ljava/lang/Object;Ljava/lang/Object;)Z",
                    remap = false))
    private boolean uselessMod$removeByPresentedClass(SetMultimap<Class<?>, IGridNode> machines,
                                                     Object ownerClass,
                                                     Object node) {
        Class<?> owner = (Class<?>) ownerClass;
        Class<?> presented = CompactMachineClassMap.presentedClass(owner);
        if (presented != owner && PatternContainer.class.isAssignableFrom(owner)) {
            return machines.remove(presented, (IGridNode) node);
        }
        boolean removed = machines.remove(owner, (IGridNode) node);
        if (presented != owner) {
            machines.remove(presented, (IGridNode) node);
        }
        return removed;
    }
}
