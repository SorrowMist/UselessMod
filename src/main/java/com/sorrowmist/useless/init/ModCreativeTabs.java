package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import static com.sorrowmist.useless.api.enums.EnumColor.BLACK;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UselessMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TAB.register(
            "main",
            () -> CreativeModeTab.builder()
                                 .icon(() -> new ItemStack(GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(BLACK)))
                                 .title(Component.translatable("itemGroup." + UselessMod.MODID + ".main"))
                                 .displayItems((pParameters, pOutput) -> {
                                     pOutput.accept(getItemStack(pParameters));
                                     
                                     for (DeferredItem<?> item : GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.values()) {
                                         pOutput.accept(item.get());
                                     }

                                     ModItems.CREATIVE_MAIN_TAB_ITEMS.forEach(pOutput::accept);
                                 })
                                 .build()
    );

    private static @NotNull ItemStack getItemStack(CreativeModeTab.ItemDisplayParameters pParameters) {
        HolderLookup.Provider lookup = pParameters.holders();
        ItemStack endlessBeef = new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
        EnchantmentUtil.applyEnchantment(endlessBeef, lookup, Enchantments.SILK_TOUCH, 1);
        EnchantmentUtil.applyEnchantment(endlessBeef, lookup, Enchantments.LOOTING, ConfigManager.getLootingLevel());
        return endlessBeef;
    }
}
