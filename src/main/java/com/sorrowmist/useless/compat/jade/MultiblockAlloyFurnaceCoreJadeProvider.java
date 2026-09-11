package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.items.MultiblockAlloyFurnaceCoreBlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

public enum MultiblockAlloyFurnaceCoreJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = UselessMod.id("multiblock_alloy_furnace_core_name");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.remove(JadeIds.CORE_OBJECT_NAME);
        tooltip.add(0, MultiblockAlloyFurnaceCoreBlockItem.rainbowName(
                Component.translatable("block.useless_mod.multiblock_alloy_furnace_core")),
                JadeIds.CORE_OBJECT_NAME);
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
