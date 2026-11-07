package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Register both halves for the controller and muffler: these live gates are not BE visual data. */
public enum MultiblockAlloyFurnaceMufflerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;
    private static final ResourceLocation UID = UselessMod.id("multiblock_alloy_furnace_muffler");
    private static final String OBSTRUCTED = "UselessModMufflerObstructed";
    private static final String ASH_FULL = "UselessModMufflerAshFull";
    private static final String MAINTENANCE = "UselessModMaintenanceOverdue";

    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        boolean obstructed = false, full = false, overdue = false;
        if (accessor.getBlockEntity() instanceof MultiblockAlloyFurnaceCoreBlockEntity core && core.isFormed()) {
            obstructed = core.isMufflerObstructed();
            full = core.isAshOutputBlocked();
            overdue = core.isMaintenanceOverdue();
        } else if (accessor.getBlockEntity() instanceof MufflerHatchBlockEntity muffler) {
            obstructed = muffler.isObstructed();
            full = ConfigManager.isAshFullBlockingWork() && muffler.isAshFull();
        }
        data.putBoolean(OBSTRUCTED, obstructed);
        data.putBoolean(ASH_FULL, full);
        data.putBoolean(MAINTENANCE, overdue);
    }

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (data.getBoolean(OBSTRUCTED)) {
            tooltip.add(text("muffler_obstructed").withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("gui.useless_mod.multiblock_alloy_furnace.muffler_obstructed.desc",
                    MufflerHatchBlockEntity.EXHAUST_HEIGHT).withStyle(ChatFormatting.GRAY));
        }
        if (data.getBoolean(ASH_FULL)) {
            tooltip.add(text("ash_full").withStyle(ChatFormatting.RED));
            tooltip.add(text("ash_full.desc").withStyle(ChatFormatting.GRAY));
        }
        if (data.getBoolean(MAINTENANCE)) tooltip.add(text("maintenance_overdue").withStyle(ChatFormatting.RED));
    }

    private static net.minecraft.network.chat.MutableComponent text(String suffix) {
        return Component.translatable("gui.useless_mod.multiblock_alloy_furnace." + suffix);
    }
    @Override public ResourceLocation getUid() { return UID; }
}
