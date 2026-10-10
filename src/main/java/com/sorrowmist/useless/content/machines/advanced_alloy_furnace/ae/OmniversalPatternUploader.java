package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.IPatternTerminalLogicHost;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
 * <p>网格上存在多台总成时按模具择优：只写入背后合金炉的模具仓真正满足这张样板的那一台。
 * 判定沿用合金炉核心做 AE 任务可用性检查时的同一逻辑，避免两处对「模具齐不齐」
 * 产生不同理解。一台都不满足时退回首个可容纳者，使样板不至于滞留在编码终端。</p>
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
        for (MePatternAssemblyBlockEntity target : preferMoldSatisfying(targets, pattern, host.getLevel())) {
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
     * 把模具仓能满足这张样板的总成排到前面，其余保持原有排序跟随其后。
     *
     * <p>样板不需要模具时直接返回原列表，不做任何反查：绝大多数样板属于这一类，
     * 而反查需要解码配方，代价远高于一次列表复制。</p>
     *
     * <p>一台总成都不满足时同样返回原列表。此时样板仍会写入首个可容纳的总成，
     * 而不是滞留在编码终端：模具可能稍后被补上，且这条路径与引入择优之前的行为一致，
     * 不会让原本可用的流程因为择优失败而回退。</p>
     */
    private static List<MePatternAssemblyBlockEntity> preferMoldSatisfying(
            List<MePatternAssemblyBlockEntity> targets, ItemStack pattern, @Nullable Level level) {
        List<Ingredient> requiredMolds = resolveRequiredMolds(pattern, level);
        if (requiredMolds.isEmpty()) {
            return targets;
        }
        List<MePatternAssemblyBlockEntity> satisfying = new ArrayList<>(targets.size());
        List<MePatternAssemblyBlockEntity> rest = new ArrayList<>(targets.size());
        for (MePatternAssemblyBlockEntity target : targets) {
            (moldSatisfied(target, requiredMolds) ? satisfying : rest).add(target);
        }
        if (satisfying.isEmpty()) {
            return targets;
        }
        satisfying.addAll(rest);
        return satisfying;
    }

    /**
     * 读出这张样板要求的模具。
     *
     * <p>反查方式与 {@code OmniversalPatternDetails.decodeUncached} 一致：按元数据版本
     * 分别走旧版兼容路径与当前路径。版本低于标签输入的那批样板用的是表示敏感指纹，
     * 必须走兼容路径才解析得出来。</p>
     *
     * <p>任何一步失败都返回空列表，使调用方退回「不择优」的行为。这里刻意不抛异常：
     * 一次上传不该因为配方目录尚未就绪或样板绑定已失效而中断。</p>
     */
    private static List<Ingredient> resolveRequiredMolds(ItemStack pattern, @Nullable Level level) {
        OmniversalPatternData data = pattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null || !data.requiresMold() || level == null) {
            return List.of();
        }
        AEItemKey definition = AEItemKey.of(pattern);
        if (definition == null) {
            return List.of();
        }
        try {
            AEProcessingPattern source = new AEProcessingPattern(definition);
            Optional<AlloyFurnaceRecipeCatalog.Entry> resolved =
                    data.version() < OmniversalPatternData.TAG_INPUT_VERSION
                            ? AlloyFurnaceRecipeCatalog.resolveLegacyPattern(
                                    level, data.sourceId(), data.identity(), source, data.version())
                            : AlloyFurnaceRecipeCatalog.resolvePattern(
                                    level, data.sourceId(), data.identity(), source);
            return resolved.map(entry -> entry.recipe().molds()).orElse(List.of());
        } catch (RuntimeException exception) {
            OmniversalPatternDiagnostics.uploadSkipped(
                    "the bound recipe could not be resolved for mold-aware selection");
            return List.of();
        }
    }

    /**
     * 判定这台总成背后的合金炉，其模具仓能否满足给定的模具需求。
     *
     * <p>判定直接委托给模具仓自身的 {@code containsMolds}，即合金炉核心做 AE 任务
     * 可用性检查时用的同一个方法。两处共用一套判定，才不会出现「上传时认为模具齐全、
     * 派发时却报缺模具」这种自相矛盾的状态。</p>
     *
     * <p>结构未成型、模具仓未连接或对应区块未加载时链路返回 {@code null}，
     * 一律视为不满足，让样板落到真正可用的那台总成上。</p>
     */
    private static boolean moldSatisfied(
            MePatternAssemblyBlockEntity target, List<Ingredient> requiredMolds) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = target.getController();
        if (controller == null) {
            return false;
        }
        OmniversalMoldHubBlockEntity hub = controller.getLinkedMoldHub();
        return hub != null && hub.containsMolds(requiredMolds);
    }

    /**
     * 解析编码终端所在的 AE 网格。
     *
     * <p>{@link IPatternTerminalLogicHost} 不暴露网格节点，因此需要向下转型到 AE2 的
     * {@link IActionHost}。该接口是网格访问的统一入口：有线编码终端部件与无线终端宿主
     * 都实现了它，故两种终端在此处一视同仁。早期实现只识别 {@code AEBasePart}，使无线
     * 终端因宿主并非部件而始终解析不到网格，样板自动上传对无线终端形同失效。</p>
     *
     * <p>宿主未连接接入点、超出范围或未实现该接口时 {@code getActionableNode()} 返回
     * {@code null}，此时本方法同样返回 {@code null}，样板保留在终端内，编码流程本身不受影响。</p>
     */
    private static @Nullable IGrid resolveGrid(IPatternTerminalLogicHost host) {
        if (host instanceof IActionHost actionHost) {
            IGridNode node = actionHost.getActionableNode();
            return node == null ? null : node.getGrid();
        }
        return null;
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