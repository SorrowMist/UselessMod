package com.sorrowmist.useless.compat.thermal;

import cofh.core.util.helpers.AugmentDataHelper;
import cofh.thermal.lib.common.item.AugmentItem;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredItem;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;
import com.sorrowmist.useless.init.ModCreativeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ThermalItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessMod.MODID);

    static {
        float[] modifiers = {10.0F, 25.0F, 50.0F, 100.0F, 250.0F, 500.0F, 1000.0F, 2500.0F, 5000.0F};
        for (int index = 0; index < modifiers.length; index++) {
            float modifier = modifiers[index];
            DeferredItem<UpgradeAugmentItem> item = ITEMS.register("useless_integral_component_tier_" + (index + 1),
                    () -> new UpgradeAugmentItem(new Item.Properties().rarity(Rarity.EPIC),
                            AugmentDataHelper.builder().type("Upgrade").mod("BaseMod", modifier).build()));
            ModCreativeTabs.EXTRA_TAB_ITEMS.add(item);
        }
        float[] parallel = {1.0F, 16.0F, 256.0F};
        for (int index = 0; index < parallel.length; index++) {
            float modifier = parallel[index];
            DeferredItem<AugmentItem> item = ITEMS.register("augment_parallel_" + (index + 1),
                    () -> new AugmentItem(new Item.Properties(),
                            AugmentDataHelper.builder().type("Machine").mod("MachineParallel", modifier).build()));
            ModCreativeTabs.EXTRA_TAB_ITEMS.add(item);
        }
    }

    private ThermalItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
