package com.sorrowmist.useless.datagen;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.datagen.providers.ULootTableProvider;
import com.sorrowmist.useless.datagen.providers.recipes.CraftingRecipes;
import com.sorrowmist.useless.datagen.providers.tags.UBlockTagsProvider;
import com.sorrowmist.useless.datagen.providers.tags.UItemTagsProvider;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = UselessMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        var registries = event.getLookupProvider();
        var pack = generator.getVanillaPack(event.includeServer());
        var existingFileHelper = event.getExistingFileHelper();

        var blockTagsProvider = pack.addProvider(
                output -> new UBlockTagsProvider(output, registries, existingFileHelper));
        pack.addProvider(output -> new UItemTagsProvider(output,
                                                         registries,
                                                         blockTagsProvider.contentsGetter()
        ));
        pack.addProvider(ULootTableProvider::new);
        pack.addProvider(CraftingRecipes::new);
    }
}
