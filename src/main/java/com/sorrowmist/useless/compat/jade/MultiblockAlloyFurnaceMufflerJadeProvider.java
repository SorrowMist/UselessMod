package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 消声仓受阻提示：悬停万象合金炉核心或其消声仓时，若排烟空间被占据则给出提示。
 *
 * <p>受阻状态只在服务端逐 tick 判定、不随方块实体同步，因此经 {@link IServerDataProvider} 下发。</p>
 */
public enum MultiblockAlloyFurnaceMufflerJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = UselessMod.id("multiblock_alloy_furnace_muffler");
    private static final String TAG_OBSTRUCTED = "UselessModMufflerObstructed";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().getBoolean(TAG_OBSTRUCTED)) {
            return;
        }
        tooltip.add(Component.translatable("gui.useless_mod.multiblock_alloy_furnace.muffler_obstructed")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("gui.useless_mod.multiblock_alloy_furnace.muffler_obstructed.desc",
                MufflerHatchBlockEntity.EXHAUST_HEIGHT).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        boolean obstructed = switch (accessor.getBlockEntity()) {
            case MultiblockAlloyFurnaceCoreBlockEntity core -> core.isFormed() && core.isMufflerObstructed();
            case MufflerHatchBlockEntity muffler -> muffler.isObstructed();
            case null, default -> false;
        };
        if (obstructed) {
            data.putBoolean(TAG_OBSTRUCTED, true);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}