package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.sorrowmist.useless.api.EnumColor.BLACK;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UselessMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TAB.register(
            "main",
            () -> CreativeModeTab.builder()
                                 .icon(() -> new ItemStack(GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(BLACK)))
                                 .title(Component.translatable("itemGroup." + UselessMod.MODID + ".main"))
                                 .displayItems((pParameters, pOutput) -> {
                                     pOutput.accept(ModItems.ENDLESS_BEAF_ITEM.get().getDefaultInstance());
                                     
                                     for (DeferredItem<?> item : GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.values()) {
                                         pOutput.accept(item.get());
                                     }

                                     ModItems.CREATIVE_MAIN_TAB_ITEMS.forEach(pOutput::accept);
                                 })
                                 .build()
    );
}
