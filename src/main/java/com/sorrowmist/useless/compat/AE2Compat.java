package com.sorrowmist.useless.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class AE2Compat {

    public static long tryExtractFromLinkedGrid(
            ItemStack tool, Player player, ItemStack requested, Actionable mode) {
        LinkedGridAccess access = resolveLinkedGrid(tool, player);
        AEItemKey key = AEItemKey.of(requested);
        if (access == null || key == null || requested.isEmpty()) return 0L;
        return access.grid.getStorageService().getInventory().extract(
                key, requested.getCount(), mode, access.source);
    }

    public static long tryInsertIntoLinkedGrid(
            ItemStack tool, Player player, ItemStack stack, Actionable mode) {
        LinkedGridAccess access = resolveLinkedGrid(tool, player);
        AEItemKey key = AEItemKey.of(stack);
        if (access == null || key == null || stack.isEmpty()) return 0L;
        return access.grid.getStorageService().getInventory().insert(
                key, stack.getCount(), mode, access.source);
    }

    /**
     * 尝试将物品存入工具绑定的 AE2 网络
     *
     * @return 实际存入的数量
     */
    public static int tryInsertToLinkedGrid(ItemStack tool, Player player, ItemStack drop) {
        // 1. 获取绑定坐标组件
        GlobalPos linkedPos = tool.get(UComponents.WIRELESS_LINK_TARGET.get());
        if (linkedPos == null) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.ae2.not_bound"), true);
            return 0;
        }
        BlockPos pos = linkedPos.pos();

        // 2. 服务器安全检查
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        // 3. 获取目标维度世界并检查区块是否加载
        ServerLevel targetLevel = server.getLevel(linkedPos.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(pos)) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.ae2.chunk_not_loaded"), true);
            return 0;
        }

        // 4. 获取 BlockEntity 并验证类型
        BlockEntity be = targetLevel.getBlockEntity(pos);
        if (!(be instanceof WirelessAccessPointBlockEntity wap)) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.ae2.invalid_target"), true);
            return 0;
        }

        // 5. 检查无线访问点是否在线
        if (!wap.getMainNode().isOnline()) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.ae2.offline"), true);
            return 0;
        }

        // 6. 获取网格及存储服务
        IGrid grid = wap.getGrid();
        if (grid == null) return 0;

        IStorageService storage = grid.getStorageService();
        IActionSource source = IActionSource.ofPlayer(player);
        AEItemKey key = AEItemKey.of(drop);

        if (key == null) return 0;

        // 7. 执行存入操作
        long inserted = storage.getInventory().insert(
                key,
                drop.getCount(),
                Actionable.MODULATE,
                source
        );

        return (int) inserted;
    }

    @Nullable
    private static LinkedGridAccess resolveLinkedGrid(ItemStack tool, Player player) {
        GlobalPos linkedPos = tool.get(UComponents.WIRELESS_LINK_TARGET.get());
        MinecraftServer server = player.getServer();
        if (linkedPos == null || server == null) return null;
        ServerLevel targetLevel = server.getLevel(linkedPos.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(linkedPos.pos())) return null;
        if (!(targetLevel.getBlockEntity(linkedPos.pos()) instanceof WirelessAccessPointBlockEntity accessPoint)
                || !accessPoint.getMainNode().isOnline()) return null;
        IGrid grid = accessPoint.getGrid();
        return grid == null ? null : new LinkedGridAccess(grid, IActionSource.ofPlayer(player));
    }

    private record LinkedGridAccess(IGrid grid, IActionSource source) {
    }
}
