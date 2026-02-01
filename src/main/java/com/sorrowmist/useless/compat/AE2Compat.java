package com.sorrowmist.useless.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import com.sorrowmist.useless.api.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AE2Compat {

    /**
     * 尝试将物品存入工具绑定的 AE2 网络
     *
     * @return 实际存入的数量
     */
    public static int tryInsertToLinkedGrid(ItemStack tool, Player player, ItemStack drop) {
        // 1. 获取绑定坐标组件
        GlobalPos linkedPos = tool.get(UComponents.WIRELESS_LINK_TARGET.get());
        if (linkedPos == null) {
            player.displayClientMessage(Component.literal("未绑定无线访问点"), true);
            return 0;
        }
        BlockPos pos = linkedPos.pos();

        // 2. 服务器安全检查
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        // 3. 获取目标维度世界并检查区块是否加载
        ServerLevel targetLevel = server.getLevel(linkedPos.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(pos)) {
            player.displayClientMessage(Component.literal("目标区域未加载"), true);
            return 0;
        }

        // 4. 获取 BlockEntity 并验证类型
        BlockEntity be = targetLevel.getBlockEntity(pos);
        if (!(be instanceof WirelessAccessPointBlockEntity wap)) {
            player.displayClientMessage(Component.literal("链接的目标不是有效的无线访问点"), true);
            return 0;
        }

        // 5. 检查无线访问点是否在线
        if (!wap.getMainNode().isOnline()) {
            player.displayClientMessage(Component.literal("无线访问点已掉线（无电力或无频道）"), true);
            return 0;
        }

        // 6. 获取网格及存储服务
        IGrid grid = wap.getGrid(); // 或者使用 wap.getMainNode().getGrid();
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
}