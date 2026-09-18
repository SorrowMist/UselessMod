package com.sorrowmist.useless.api.crafting.bigint;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.core.definitions.AEItems;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 多方块万象合金炉 bigint 能力的<b>发现入口</b>。
 *
 * <p>用法（完整示例见 {@code wiki/ALLOY_FURNACE_BIGINT_API_ZH_CN.md}）：</p>
 *
 * <pre>{@code
 * for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
 *     AlloyFurnaceBigIntegerCapacity capacity =
 *             target.capacity(pattern, unitPrototype, requestedCount);
 *     if (!capacity.isAvailable()) {
 *         continue;
 *     }
 *     AlloyFurnaceBigIntegerBatch batch =
 *             target.admit(pattern, unitPrototype, capacity.accepted(), binding);
 *     if (batch != null && batch.commit(unitPrototype)) {
 *         // 材料所有权已转移给机器；产物会切段写回 ME 网络，并通过 binding 回调给你
 *     }
 * }
 * }</pre>
 *
 * <p><b>为什么用网格节点遍历而不是 {@code ICraftingService}</b>：AE2 19.2.17 的
 * {@code ICraftingService} 只公开了 {@code getCraftingFor(AEKey)} 与 {@code getCpus()}，
 * 没有「枚举全部供应器」的公开接口。遍历 {@code IGrid#getNodes()} 并用
 * {@link IGridNode#getOwner()} 判 {@link AlloyFurnaceBigIntegerProvider} 是唯一稳定的公开路径。</p>
 *
 * <p><b>仅服务器线程</b>。</p>
 */
public final class AlloyFurnaceBigIntegerApi {
    private AlloyFurnaceBigIntegerApi() {
    }

    /**
     * 找出网格里当前可用的全部大数目标，按 {@link AlloyFurnaceBigIntegerTarget#machineIdentity()} 去重。
     *
     * @param grid 目标网格；{@code null} 时返回空列表
     * @return 不可变的候选列表；同一台物理机器只会出现一次
     */
    public static @NotNull List<AlloyFurnaceBigIntegerTarget> findTargets(@Nullable IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        List<AlloyFurnaceBigIntegerTarget> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IGridNode node : grid.getNodes()) {
            if (!(node.getOwner() instanceof AlloyFurnaceBigIntegerProvider provider)) {
                continue;
            }
            AlloyFurnaceBigIntegerTarget target = provider.bigIntegerTarget();
            if (target != null && seen.add(target.machineIdentity())) {
                targets.add(target);
            }
        }
        return Collections.unmodifiableList(targets);
    }

    /**
     * 按物理机器身份查找单个目标。
     *
     * @param grid           目标网格
     * @param machineIdentity {@link AlloyFurnaceBigIntegerTarget#machineIdentity()} 的取值（{@code 维度@x,y,z}）
     * @return 命中的目标；机器不在线或身份不匹配时为空
     */
    public static @NotNull Optional<AlloyFurnaceBigIntegerTarget> findTarget(
            @Nullable IGrid grid, @Nullable String machineIdentity) {
        if (machineIdentity == null || machineIdentity.isBlank()) {
            return Optional.empty();
        }
        for (AlloyFurnaceBigIntegerTarget target : findTargets(grid)) {
            if (machineIdentity.equals(target.machineIdentity())) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    /**
     * @return 多方块万象合金炉接受的样板物品 id 集合（AE2 合成样板 + 本模组万象样板）。
     *
     * <p>供调用方在昂贵的解码之前先做一次廉价过滤；真正的判定仍以
     * {@link AlloyFurnaceBigIntegerTarget#capacity} 的返回值为准。</p>
     */
    public static @NotNull Set<ResourceLocation> acceptedPatternKinds() {
        Set<ResourceLocation> kinds = new LinkedHashSet<>(2);
        addKey(kinds, AEItems.CRAFTING_PATTERN.get());
        addKey(kinds, ModItems.OMNIVERSAL_PATTERN.get());
        return Collections.unmodifiableSet(kinds);
    }

    /** 注册表未就绪时 {@code getKey} 可能返回 null，这里静默跳过而不是把 null 放进集合。 */
    private static void addKey(Set<ResourceLocation> kinds, @Nullable net.minecraft.world.item.Item item) {
        if (item == null) {
            return;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key != null) {
            kinds.add(key);
        }
    }
}
