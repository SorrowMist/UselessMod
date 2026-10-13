package com.sorrowmist.useless.command;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 扫描并修复 AE 网格上失效的万象样板。
 *
 * <p>失效的权威判定与样板 tooltip 红字同源：{@link PatternDetailsHelper#decodePattern} 的
 * 结果不是 {@link OmniversalPatternDetails}（含返回 {@code null} 或抛异常）。判定为失效的
 * 样板会按当前配方目录重新匹配配方并重编码，然后就地写回原槽位。</p>
 *
 * <p>只覆盖 AE 网络上已通电的机器：单方块高级合金炉与多方块 ME 样板总成。玩家背包与终端
 * 内尚未上传的样板不在扫描范围内。</p>
 */
public final class OmniversalPatternRepairService {
    private static final int MAX_DETAILS = 3;

    private OmniversalPatternRepairService() {
    }

    /** 一次修复的统计。{@code repaired} 在预览模式下语义为「可修复数」。 */
    public record RepairReport(int scanned, int invalid, int repaired, int unresolved, int skipped) {
    }

    /**
     * 修复结果。成功时携带 {@link RepairReport}，失败时携带一句主消息与至多若干条细节。
     */
    public record RepairResult(@Nullable RepairReport report, @Nullable Component failure,
                               List<Component> details) {
        public static RepairResult success(RepairReport report, List<Component> details) {
            return new RepairResult(report, null, List.copyOf(details));
        }

        public static RepairResult failure(Component failure, List<Component> details) {
            return new RepairResult(null, failure, List.copyOf(details));
        }

        public boolean isFailure() {
            return failure != null;
        }
    }

    /**
     * 扫描并（可选）修复玩家所在网格上的失效万象样板。
     *
     * @param preview 为 {@code true} 时只扫描报告，不写回任何槽位
     */
    public static RepairResult repair(ServerPlayer player, boolean preview) {
        GridResolution resolution = resolvePlayerGrid(player);
        if (resolution.grid() == null) {
            return resolution.foundTerminal()
                    ? RepairResult.failure(
                            Component.translatable("commands.useless_mod.patterns.repair.grid_unavailable"),
                            resolution.errors())
                    : RepairResult.failure(
                            Component.translatable("commands.useless_mod.patterns.repair.unbound"),
                            resolution.errors());
        }

        Level level = player.level();
        List<PatternContainer> machines = collectMachines(resolution.grid());
        if (machines.isEmpty()) {
            return RepairResult.failure(
                    Component.translatable("commands.useless_mod.patterns.repair.no_machines"), List.of());
        }

        int scanned = 0;
        int invalid = 0;
        int repaired = 0;
        int unresolved = 0;
        int skipped = 0;
        List<Component> details = new ArrayList<>();

        for (PatternContainer machine : machines) {
            InternalInventory inventory = machine.getTerminalPatternInventory();
            if (inventory == null) continue;

            boolean dirty = false;
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || !stack.is(ModItems.OMNIVERSAL_PATTERN.get())) continue;

                OmniversalPatternData data = stack.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
                if (data == null) {
                    skipped++;
                    continue;
                }

                scanned++;
                if (isValid(stack, level)) continue;
                invalid++;

                ItemStack replacement = reencode(stack, data, level);
                if (replacement.isEmpty()) {
                    unresolved++;
                    if (details.size() < MAX_DETAILS) {
                        details.add(Component.translatable(
                                "commands.useless_mod.patterns.repair.detail",
                                describe(machine, slot), data.recipeId().toString()));
                    }
                    continue;
                }

                // 幂等：重编码结果与旧栈完全一致时不写回，也就不计入 repaired。
                if (ItemStack.isSameItemSameComponents(stack, replacement)) continue;

                if (!preview) {
                    inventory.setItemDirect(slot, replacement);
                    dirty = true;
                }
                repaired++;
            }

            if (dirty && machine instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                // 单方块炉的 setItemDirect 不会触发刷新，需显式重建。
                // 多方块总成由 RecoverableItemStackHandler 的 inventoryChanged 回调自动刷新。
                furnace.onPatternsRebuilt();
            }
        }

        return RepairResult.success(
                new RepairReport(scanned, invalid, repaired, unresolved, skipped), details);
    }

    /** 判定一张万象样板当前是否可正常解码。 */
    private static boolean isValid(ItemStack stack, Level level) {
        try {
            IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, level);
            return decoded instanceof OmniversalPatternDetails;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 按当前配方目录重解析并重编码；任一步失败返回 {@link ItemStack#EMPTY}。
     *
     * <p>绑定配方若仍能由 {@code recipeId} 唯一定位，就以该配方**当前的输入输出**重建
     * payload——配方被改动（输入变化）而失效的样板只能这样修复。只有 recipeId 无法唯一
     * 定位时才退回用旧 payload 做内容匹配，复用旧 payload 重编码。</p>
     */
    private static ItemStack reencode(ItemStack stack, OmniversalPatternData data, Level level) {
        Optional<AlloyFurnaceRecipeCatalog.Entry> byRecipeId = resolveEntryByRecipeId(data, level);
        if (byRecipeId.isPresent()) {
            ItemStack rebuilt = OmniversalPatternEncoding.reencodeFromRecipe(byRecipeId.get(), level);
            if (!rebuilt.isEmpty()) return rebuilt;
        }

        AEItemKey definition = AEItemKey.of(stack);
        if (definition == null) return ItemStack.EMPTY;

        AEProcessingPattern source;
        try {
            source = new AEProcessingPattern(definition);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }

        Optional<AlloyFurnaceRecipeCatalog.Entry> entry = resolveEntry(data, source, level);
        return entry.map(value -> OmniversalPatternEncoding.reencodeExisting(stack, value, level))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * 仅按 {@code recipeId} 定位当前配方：忽略指纹与旧 payload，返回唯一匹配项。
     *
     * <p>优先在样板记录的 {@code sourceId} 范围内查找，找不到再退到全目录；命中数不为 1
     * （无匹配或存在歧义）时返回空，交由内容匹配回退处理。</p>
     */
    private static Optional<AlloyFurnaceRecipeCatalog.Entry> resolveEntryByRecipeId(
            OmniversalPatternData data, Level level) {
        var recipeId = data.recipeId();
        if (recipeId == null) return Optional.empty();

        List<AlloyFurnaceRecipeCatalog.Entry> matched = new ArrayList<>();
        for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(level, data.sourceId())) {
            if (recipeId.equals(entry.identity().recipeId())) matched.add(entry);
        }
        if (matched.isEmpty()) {
            for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(level)) {
                if (recipeId.equals(entry.identity().recipeId())) matched.add(entry);
            }
        }
        return matched.size() == 1 ? Optional.of(matched.getFirst()) : Optional.empty();
    }

    /**
     * 按稳健性递减的顺序回查当前配方目录。
     *
     * <p>首选按 {@code sourceId + identity} 精确匹配（旧版样板必须走 legacy 指纹算法）；
     * 其次退到不带画像的身份匹配；最后按样板内容模糊匹配候选，并以确定性评分取首个。</p>
     */
    private static Optional<AlloyFurnaceRecipeCatalog.Entry> resolveEntry(
            OmniversalPatternData data, AEProcessingPattern source, Level level) {
        Optional<AlloyFurnaceRecipeCatalog.Entry> entry =
                data.version() < OmniversalPatternData.TAG_INPUT_VERSION
                        ? AlloyFurnaceRecipeCatalog.resolveLegacyPattern(
                                level, data.sourceId(), data.identity(), source, data.version())
                        : AlloyFurnaceRecipeCatalog.resolvePattern(
                                level, data.sourceId(), data.identity(), source);
        if (entry.isPresent()) return entry;

        entry = AlloyFurnaceRecipeCatalog.resolve(level, data.sourceId(), data.identity());
        if (entry.isPresent()) return entry;

        entry = AlloyFurnaceRecipeCatalog.resolve(level, data.identity());
        if (entry.isPresent()) return entry;

        // allowMissingOutputs=true 是该目录为 omniversal converter 预留的内容匹配模式。
        return selectBest(AlloyFurnaceRecipeCatalog.findPatternCandidates(level, source, true));
    }

    /** 模具数多者优先，其次按 recipeId 字符串序，保证多次运行结果稳定。 */
    private static Optional<AlloyFurnaceRecipeCatalog.Entry> selectBest(
            List<AlloyFurnaceRecipeCatalog.Entry> candidates) {
        AlloyFurnaceRecipeCatalog.Entry best = null;
        for (AlloyFurnaceRecipeCatalog.Entry candidate : candidates) {
            if (candidate == null || candidate.recipe() == null) continue;
            if (best == null || compareEntries(candidate, best) < 0) best = candidate;
        }
        return Optional.ofNullable(best);
    }

    private static int compareEntries(AlloyFurnaceRecipeCatalog.Entry left,
                                      AlloyFurnaceRecipeCatalog.Entry right) {
        int moldComparison = Integer.compare(
                right.recipe().molds().size(), left.recipe().molds().size());
        if (moldComparison != 0) return moldComparison;
        return left.identity().recipeId().toString()
                .compareTo(right.identity().recipeId().toString());
    }

    /** 收集网格上已通电的两类目标机器，并按终端排序值升序排列以保证报告可复现。 */
    private static List<PatternContainer> collectMachines(IGrid grid) {
        Set<PatternContainer> machines = new LinkedHashSet<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (AdvancedAlloyFurnaceBlockEntity.class.isAssignableFrom(machineClass)) {
                machines.addAll(grid.getActiveMachines(AdvancedAlloyFurnaceBlockEntity.class));
            }
        }
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (MePatternAssemblyBlockEntity.class.isAssignableFrom(machineClass)) {
                machines.addAll(grid.getActiveMachines(MePatternAssemblyBlockEntity.class));
            }
        }
        List<PatternContainer> result = new ArrayList<>(machines);
        result.sort(Comparator.comparingLong(PatternContainer::getTerminalSortOrder));
        return result;
    }

    private static Component describe(PatternContainer machine, int slot) {
        if (machine instanceof BlockEntity blockEntity && blockEntity.getLevel() != null) {
            BlockPos pos = blockEntity.getBlockPos();
            return Component.literal(blockEntity.getLevel().dimension().location()
                    + " [" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "] #" + slot);
        }
        return Component.literal("#" + slot);
    }

    /**
     * 从玩家背包、副手、护甲与 curios 饰品槽中找出第一个可解析的无线终端并返回其网格。
     *
     * <p>不做多网格合并：遇到第一个能给出非空 {@link IGrid} 的终端即返回。</p>
     */
    private static GridResolution resolvePlayerGrid(ServerPlayer player) {
        List<Component> errors = new ArrayList<>();
        boolean foundTerminal = false;
        Level level = player.level();

        for (ItemStack stack : candidateStacks(player)) {
            if (stack.isEmpty()) continue;

            if (stack.has(UComponents.WIRELESS_LINK_TARGET.get())) {
                AE2Compat.LinkedGridAccess access = AE2Compat.resolveLinkedGrid(stack, player);
                if (access != null && access.grid() != null) {
                    return new GridResolution(access.grid(), true, errors);
                }
            }

            // ae2wtlib 的 ItemWT 继承 WirelessTerminalItem，instanceof 天然覆盖，
            // 无需编译期依赖或反射。
            if (stack.getItem() instanceof WirelessTerminalItem terminal) {
                foundTerminal = true;
                IGrid grid = terminal.getLinkedGrid(stack, level, errors::add);
                if (grid != null) {
                    return new GridResolution(grid, true, errors);
                }
            }
        }

        return new GridResolution(null, foundTerminal, errors);
    }

    private static List<ItemStack> candidateStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        player.getInventory().items.forEach(stacks::add);
        player.getInventory().offhand.forEach(stacks::add);
        player.getInventory().armor.forEach(stacks::add);

        if (ModList.get().isLoaded("curios")) {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var equipped = handler.getEquippedCurios();
                for (int slot = 0; slot < equipped.getSlots(); slot++) {
                    stacks.add(equipped.getStackInSlot(slot));
                }
            });
        }
        return stacks;
    }

    private record GridResolution(@Nullable IGrid grid, boolean foundTerminal, List<Component> errors) {
    }
}