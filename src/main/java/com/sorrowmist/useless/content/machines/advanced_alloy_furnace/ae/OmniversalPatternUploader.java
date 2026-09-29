package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.networking.IGrid;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.AEBasePart;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把编码终端刚产出的万象样板移交给同一张 AE 网格上的多方块合金炉样板总成。
 *
 * <p>移交目标限定为 {@link MePatternAssemblyBlockEntity}，而非同样实现
 * {@code PatternContainer} 的单方块高级合金炉。区分依据是该总成是多方块合金炉在网格上
 * 唯一挂节点的部件，只有其背后的多方块核心声明了 bigint 批次语义；万象样板在该处才能以
 * 超过 long 的次数派发。单方块高级合金炉的万象样板不受本类影响，仍由玩家自行放置。</p>
 *
 * <p>目标发现方式与 AE2 样板访问终端一致：以 {@code getMachineClasses()} 过滤出
 * 目标宿主类，再按该类取已通电机器。写入前先以 {@code simulateAdd} 确认可容纳，
 * 避免在多个总成上留下部分插入。</p>
 */
public final class OmniversalPatternUploader {

    private OmniversalPatternUploader() {
    }

    /**
     * 尝试把样板移交给网格上的多方块合金炉样板总成。
     *
     * <p>本方法只负责移交，不产生任何玩家可见效果；调用方依据返回值决定是否清空终端槽位，
     * 并按 {@link PatternUploadNotice} 提示发起编码的玩家。</p>
     *
     * @param host    编码终端的逻辑宿主，用于解析所在网格
     * @param pattern 刚编码完成的万象样板
     * @return 接收样板的总成坐标；不存在可接收的总成时返回 {@code null}，样板应保留在终端内
     */
    public static @Nullable BlockPos upload(IPatternTerminalLogicHost host, ItemStack pattern) {
        if (pattern.isEmpty()) {
            return null;
        }
        IGrid grid = resolveGrid(host);
        if (grid == null) {
            OmniversalPatternDiagnostics.uploadSkipped("the encoding terminal is not attached to a grid");
            return null;
        }
        List<MePatternAssemblyBlockEntity> targets = collectAssemblies(grid);
        if (targets.isEmpty()) {
            OmniversalPatternDiagnostics.uploadSkipped("no online multiblock pattern assembly");
            return null;
        }
        for (MePatternAssemblyBlockEntity target : targets) {
            var terminalInventory = target.getTerminalPatternInventory();
            if (!terminalInventory.simulateAdd(pattern).isEmpty()) {
                continue;
            }
            if (terminalInventory.addItems(pattern).isEmpty()) {
                BlockPos targetPos = target.getBlockPos().immutable();
                OmniversalPatternDiagnostics.uploaded(targetPos.toShortString());
                return targetPos;
            }
        }
        OmniversalPatternDiagnostics.uploadSkipped("every multiblock pattern assembly is full");
        return null;
    }

    /**
     * 编码终端部件自身持有网格节点，但 {@link IPatternTerminalLogicHost} 不暴露该节点，
     * 因此需要向下转型到 AE2 的部件基类。无线终端等其它宿主不满足该条件时返回 {@code null}，
     * 此时样板保留在终端内，编码流程本身不受影响。
     */
    private static @Nullable IGrid resolveGrid(IPatternTerminalLogicHost host) {
        return host instanceof AEBasePart part ? part.getMainNode().getGrid() : null;
    }

    /**
     * 收集网格上已通电的多方块样板总成，并按终端排序值升序排列。
     *
     * <p>排序使「首个可容纳目标」的选择在多次上传之间保持稳定：机器索引本身是无序集合，
     * 不排序时同一网格上的多个总成会随节点遍历顺序变化而轮流成为目标。</p>
     */
    private static List<MePatternAssemblyBlockEntity> collectAssemblies(IGrid grid) {
        Set<MePatternAssemblyBlockEntity> assemblies = new LinkedHashSet<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (MePatternAssemblyBlockEntity.class.isAssignableFrom(machineClass)) {
                assemblies.addAll(grid.getActiveMachines(MePatternAssemblyBlockEntity.class));
            }
        }
        List<MePatternAssemblyBlockEntity> result = new ArrayList<>(assemblies);
        result.sort(Comparator.comparingLong(MePatternAssemblyBlockEntity::getTerminalSortOrder));
        return result;
    }
}
