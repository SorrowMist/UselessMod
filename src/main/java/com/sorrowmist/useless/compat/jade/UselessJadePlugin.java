package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.content.blocks.TemperatureRegulatorBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MufflerHatchBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("jade")
public class UselessJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AdvancedAlloyFurnaceJadeProvider.INSTANCE, AdvancedAlloyFurnaceBlockEntity.class);
        registration.registerBlockDataProvider(MultiblockAlloyFurnaceMufflerJadeProvider.INSTANCE,
                MultiblockAlloyFurnaceCoreBlockEntity.class);
        registration.registerBlockDataProvider(MultiblockAlloyFurnaceMufflerJadeProvider.INSTANCE,
                MufflerHatchBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AdvancedAlloyFurnaceJadeProvider.INSTANCE, AdvancedAlloyFurnaceBlock.class);
        registration.registerBlockComponent(MultiblockAlloyFurnaceCoreJadeProvider.INSTANCE,
                MultiblockAlloyFurnaceCoreBlock.class);
        registration.registerBlockComponent(MultiblockAlloyFurnaceMufflerJadeProvider.INSTANCE,
                MultiblockAlloyFurnaceCoreBlock.class);
        registration.registerBlockComponent(MultiblockAlloyFurnaceMufflerJadeProvider.INSTANCE,
                MufflerHatchBlock.class);
        registration.registerBlockComponent(ThermostatJadeProvider.INSTANCE,
                TemperatureRegulatorBlock.class);
    }
}
