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

import java.util.ArrayList;
import java.util.List;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UselessMod.MODID);

    /**
     * 由条件加载的兼容模块（例如 ECO 已安装时的紧凑方块）追加的创造栏条目。
     * 这些物品只在对应前置存在时才会被注册，因此这里必须允许运行时追加。
     */
    public static final List<DeferredItem<? extends net.minecraft.world.item.Item>> EXTRA_TAB_ITEMS =
            new ArrayList<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TAB.register(
            "main",
            () -> CreativeModeTab.builder()
                                 .icon(() -> new ItemStack(ModItems.ADVANCED_ALLOY_FURNACE_BLOCK.get()))
                                 .title(Component.translatable("itemGroup." + UselessMod.MODID + ".main"))
                                 .displayItems((pParameters, pOutput) -> {
                                     pOutput.accept(getItemStack(pParameters));
                                     
                                     for (var itemMap : GlowPlasticBlock.ALL_BLOCK_ITEM_MAPS) {
                                         for (DeferredItem<?> item : itemMap.values()) {
                                             pOutput.accept(item.get());
                                         }
                                     }

                                     ModItems.CREATIVE_MAIN_TAB_ITEMS.forEach(pOutput::accept);
                                     EXTRA_TAB_ITEMS.forEach(item -> pOutput.accept(item.get()));
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
