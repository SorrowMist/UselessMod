package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

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
                                     pOutput.accept(getEndlessBeafItemStack());
                                     // 其他常规物品
                                     pOutput.accept(ModItems.TELEPORT_BLOCK_ITEM.get());
                                     pOutput.accept(ModItems.TELEPORT_BLOCK_ITEM_2.get());
                                     pOutput.accept(ModItems.TELEPORT_BLOCK_ITEM_3.get());
                                     pOutput.accept(ModItems.ORE_GENERATOR_BLOCK.get());

                                     for (DeferredItem<?> item : GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.values()) {
                                         pOutput.accept(item.get());
                                     }
                                 })
                                 .build()
    );

    private static @NotNull ItemStack getEndlessBeafItemStack() {
        ItemStack enchantedStack = new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
        // 构建附魔内容
        ItemEnchantments.Mutable ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        ench.set(EnchantmentUtil.getEnchantmentHolder(Enchantments.SILK_TOUCH), 1);
        ench.set(EnchantmentUtil.getEnchantmentHolder(Enchantments.LOOTING), ConfigManager.getLootingLevel());

        enchantedStack.set(DataComponents.ENCHANTMENTS, ench.toImmutable());
        return enchantedStack;
    }
}
