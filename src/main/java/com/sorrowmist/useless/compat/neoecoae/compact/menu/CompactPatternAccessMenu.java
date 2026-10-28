package com.sorrowmist.useless.compat.neoecoae.compact.menu;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType.BlockUIHolder;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Opens ECO's own crafting interface layout and keeps its native menu holder and slot protocol. */
public final class CompactPatternAccessMenu {
    private static final UIFactory<BlockUIHolder> FACTORY = new UIFactory<>(
            new ResourceLocation(UselessMod.MODID, "compact_crafting_interface")) {
        @Override
        protected com.lowdragmc.lowdraglib.gui.modular.ModularUI createUITemplate(BlockUIHolder holder, Player player) {
            if (!(player.level().getBlockEntity(holder.pos) instanceof CompactF9BlockEntity host)
                    || !canAccess(player, host)) return null;
            return CraftingInterfaceUI.create(host.compactCraftingInterface(), player).nativeUI(holder);
        }

        @Override
        protected BlockUIHolder readHolderFromSyncData(FriendlyByteBuf buffer) {
            return new BlockUIHolder(net.minecraft.client.Minecraft.getInstance().player, buffer.readBlockPos());
        }

        @Override
        protected void writeHolderToSyncData(FriendlyByteBuf buffer, BlockUIHolder holder) {
            buffer.writeBlockPos(holder.pos);
        }
    };

    private CompactPatternAccessMenu() {}

    public static void register() {
        UIFactory.register(FACTORY);
    }

    public static void open(ServerPlayer player, CompactF9BlockEntity host) {
        if (canAccess(player, host)) FACTORY.openUI(new BlockUIHolder(player, host.getBlockPos()), player);
    }

    static boolean canAccess(Player player, CompactF9BlockEntity host) {
        BlockPos pos = host.getBlockPos();
        return !host.isRemoved() && host.getLevel() == player.level()
                && player.level().hasChunkAt(pos)
                && player.level().getBlockEntity(pos) == host
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
