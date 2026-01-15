package com.sorrowmist.useless.datagen;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.datagen.providers.ULootTableProvider;
import com.sorrowmist.useless.datagen.providers.recipes.CraftingRecipes;
import com.sorrowmist.useless.datagen.providers.tags.UBlockTagsProvider;
import com.sorrowmist.useless.datagen.providers.tags.UItemTagsProvider;
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
        var existingFileHelper = event.getExistingFileHelper();

        var blockTagsProvider = pack.addProvider(
                output -> new UBlockTagsProvider(output, registries, existingFileHelper));
        pack.addProvider(output -> new UItemTagsProvider(output,
                                                         registries,
                                                         blockTagsProvider.contentsGetter()
        ));
        pack.addProvider(output -> new ULootTableProvider(output, registries));
//        pack.addProvider(output -> new ModBiomeProvider(output, registries));
        pack.addProvider(output -> new CraftingRecipes(output, registries));
    }
}
