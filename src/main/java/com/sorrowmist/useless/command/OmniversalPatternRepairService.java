package com.sorrowmist.useless.command;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternRepair;
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
import java.util.Set;

/**
 * 扫描并修复 AE 网格上失效的万象样板。
 *
 * <p>失效判定与重建都委托给 {@link OmniversalPatternRepair}：无法解码的样板、以及仍能解码
 * 但输入槽语义已与当前配方不符的样板都会按当前配方重建，然后就地写回原槽位。</p>
 *
 * <p>这是样板修复的唯一入口：机器在载入存档时不再自动重建样板，需要玩家主动执行命令。
 * 扫描覆盖 AE 网络上已通电的机器；玩家背包与终端内尚未上传的样板不在扫描范围内。</p>
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
                if (!OmniversalPatternRepair.needsRepair(stack, level)) continue;
                invalid++;

                ItemStack replacement = OmniversalPatternRepair.rebuild(stack, level);
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