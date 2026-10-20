package com.sorrowmist.useless.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sorrowmist.useless.command.OmniversalPatternRepairService.RepairReport;
import com.sorrowmist.useless.command.OmniversalPatternRepairService.RepairResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 本模组的 Brigadier 命令树。 */
public final class UselessCommands {
    /** 批量改写机器内容是破坏性操作，需权限等级 4。 */
    private static final int REPAIR_PERMISSION_LEVEL = 4;

    private UselessCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("uselessmod")
                .then(Commands.literal("patterns")
                        .then(Commands.literal("repair")
                                .requires(source -> source.hasPermission(REPAIR_PERMISSION_LEVEL))
                                .executes(context -> run(context.getSource(), false))
                                .then(Commands.literal("preview")
                                        .executes(context -> run(context.getSource(), true))))));
    }

    private static int run(CommandSourceStack source, boolean preview) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            // 修复依赖玩家身上的无线终端，控制台与命令方块无从解析网格。
            source.sendFailure(Component.translatable("commands.useless_mod.patterns.repair.player_only"));
            return 0;
        }

        RepairResult result = OmniversalPatternRepairService.repair(player, preview);
        if (result.isFailure()) {
            source.sendFailure(result.failure());
            for (Component detail : result.details()) {
                source.sendFailure(detail);
            }
            return 0;
        }

        RepairReport report = result.report();
        if (report == null) return 0;

        source.sendSuccess(() -> Component.translatable(
                preview
                        ? "commands.useless_mod.patterns.repair.summary.preview"
                        : "commands.useless_mod.patterns.repair.summary",
                report.scanned(), report.invalid(), report.repaired(), report.unresolved()), false);

        for (Component detail : result.details()) {
            source.sendSuccess(() -> detail, false);
        }
        return Math.max(report.repaired(), 1);
    }
}
