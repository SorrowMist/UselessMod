package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.networking.IGrid;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.AEBasePart;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import net.minecraft.core.GlobalPos;
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
 *
 * <p>写入前还要对网格上的全部总成查重：只要任一总成已存有输入、输出、模具与配方
 * 完全一致的万象样板，本次便不再写入。等价样板在总成内并存没有意义——它们会各自参与
 * 合成派发，使同一次请求被重复满足，同时白占一份样板槽位。</p>
 */
public final class OmniversalPatternUploader {

    private OmniversalPatternUploader() {
    }

    /** 一次移交尝试的结局。 */
    public enum Outcome {
        /** 样板已写入某个总成。 */
        UPLOADED,
        /** 网格上已存在等价样板，本次未写入。 */
        DUPLICATE,
        /** 没有可接收的总成：网格上没有已成型总成，或全部总成已满。 */
        SKIPPED
    }

    /**
     * @param outcome 本次移交的结局
     * @param target  接收样板的总成位置，仅 {@link Outcome#UPLOADED} 时非空
     */
    public record Result(Outcome outcome, @Nullable GlobalPos target) {
    }

    /**
     * 尝试把样板移交给网格上的多方块合金炉样板总成。
     *
     * <p>本方法只负责移交，不产生任何玩家可见效果；调用方依据结局决定是否清空终端槽位、
     * 是否退回空白样板，并按 {@link PatternUploadNotice} 提示发起编码的玩家。</p>
     *
     * @param host    编码终端的逻辑宿主，用于解析所在网格
     * @param pattern 刚编码完成的万象样板
     * @return 移交结局；{@link Outcome#SKIPPED} 时样板应保留在终端内
     */
    public static Result upload(IPatternTerminalLogicHost host, ItemStack pattern) {
        if (pattern.isEmpty()) {
            return new Result(Outcome.SKIPPED, null);
        }
        IGrid grid = resolveGrid(host);
        if (grid == null) {
            OmniversalPatternDiagnostics.uploadSkipped("the encoding terminal is not attached to a grid");
            return new Result(Outcome.SKIPPED, null);
        }
        List<MePatternAssemblyBlockEntity> targets = collectAssemblies(grid);
        if (targets.isEmpty()) {
            OmniversalPatternDiagnostics.uploadSkipped("no online multiblock pattern assembly");
            return new Result(Outcome.SKIPPED, null);
        }
        if (containsEquivalent(targets, pattern)) {
            OmniversalPatternDiagnostics.uploadDuplicate();
            return new Result(Outcome.DUPLICATE, null);
        }
        for (MePatternAssemblyBlockEntity target : targets) {
            if (target.getLevel() == null) {
                continue;
            }
            var terminalInventory = target.getTerminalPatternInventory();
            if (!terminalInventory.simulateAdd(pattern).isEmpty()) {
                continue;
            }
            if (terminalInventory.addItems(pattern).isEmpty()) {
                GlobalPos targetPos = GlobalPos.of(
                        target.getLevel().dimension(), target.getBlockPos().immutable());
                OmniversalPatternDiagnostics.uploaded(targetPos.pos().toShortString());
                return new Result(Outcome.UPLOADED, targetPos);
            }
        }
        OmniversalPatternDiagnostics.uploadSkipped("every multiblock pattern assembly is full");
        return new Result(Outcome.SKIPPED, null);
    }

    /**
     * 判定总成内是否已存有与待移交样板等价的万象样板。
     *
     * <p>判等直接比较物品与全部组件：万象样板的输入输出存于
     * {@code AEComponents.ENCODED_PROCESSING_PATTERN}，配方 id、语义指纹与模具存于
     * {@code UComponents.OMNIVERSAL_PATTERN_DATA}，二者都是值语义的 record。因此
     * 「组件全等」恰好等价于「输入、输出、模具、配方全一致」，既不需要逐个字段比对，
     * 也不会漏掉模具标签这类隐式差异。</p>
     *
     * <p>只扫描总成自身发布的样板库存，即玩家在样板访问终端中能看到的那一份，
     * 不涉及终端内尚未移交的样板。</p>
     */
    private static boolean containsEquivalent(List<MePatternAssemblyBlockEntity> targets, ItemStack pattern) {
        for (MePatternAssemblyBlockEntity target : targets) {
            var inventory = target.getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stored = inventory.getStackInSlot(slot);
                if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, pattern)) {
                    return true;
                }
            }
        }
        return false;
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