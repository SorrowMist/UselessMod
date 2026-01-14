package com.sorrowmist.useless.datagen;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.datagen.providers.recipes.CraftingRecipes;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = UselessMod.MODID)
public class DataGenerators {

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        var registries = event.getLookupProvider();
        var pack = generator.getVanillaPack(true);

        // 仅添加配方生成器
        pack.addProvider(output -> new CraftingRecipes(output, registries));
    }
}
