package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.utils.ThermalDependencyHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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
                    .displayItems(((pParameters, pOutput) -> {
                        // 基础物品 - 始终添加
                        pOutput.accept(ModItems.ENDLESS_BEAF_ITEM.get());
                        pOutput.accept(ModItems.TELEPORT_BLOCK_ITEM.get());
                        pOutput.accept(ModItems.TELEPORT_BLOCK_ITEM_2.get());
//                        pOutput.accept(ModItems.ORE_GENERATOR_BLOCK_ITEM.get());
//
//                        pOutput.accept(AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK_ITEM.get());
//
                        // 发光塑料块 - 始终添加
                        for (DeferredItem<Item> item : GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.values()) {
                            pOutput.accept(item.get());
                        }

                        // 只在热力系列安装时添加 ThermalMore 整合的物品
                        if (ThermalDependencyHelper.isAnyThermalModLoaded()) {
//                            // 安全地添加 ThermalMore 物品
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_1);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_2);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_3);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_4);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_5);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_6);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_7);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_8);
//                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_9);
//                            safelyAddItem(pOutput, ThermalParallelItems.AUGMENT_PARALLEL_1);
//                            safelyAddItem(pOutput, ThermalParallelItems.AUGMENT_PARALLEL_2);
//                            safelyAddItem(pOutput, ThermalParallelItems.AUGMENT_PARALLEL_3);
                        } else {
                            UselessMod.LOGGER.debug("热力系列模组未安装，跳过 Thermal 相关物品添加到创造标签");
                        }
                    }))
                    .build());
}