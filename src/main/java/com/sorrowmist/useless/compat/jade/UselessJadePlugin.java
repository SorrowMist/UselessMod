package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("jade")
public class UselessJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AdvancedAlloyFurnaceJadeProvider.INSTANCE, AdvancedAlloyFurnaceBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AdvancedAlloyFurnaceJadeProvider.INSTANCE, AdvancedAlloyFurnaceBlock.class);
    }
}
