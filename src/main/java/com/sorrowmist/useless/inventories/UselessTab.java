package com.sorrowmist.useless.inventories;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.blocks.oregenerator.OreGeneratorBlock;
import com.sorrowmist.useless.blocks.CreatorDollBlock;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock2;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock3;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.registry.ModComponents;
import com.sorrowmist.useless.registry.ModIngots;
import com.sorrowmist.useless.registry.ModMolds;
import com.sorrowmist.useless.registry.ThermalMoreItems;
import com.sorrowmist.useless.registry.ThermalParallelItems;
import com.sorrowmist.useless.utils.ThermalDependencyHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class UselessTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UselessMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> USELESS_TAB =
            CREATIVE_TAB.register("useless_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(EndlessBeafItem.ENDLESS_BEAF_ITEM.get()))
                    .title(Component.literal("无用之物"))
                    .displayItems(((pParameters, pOutput) -> {
                        // 基础物品 - 始终添加
                        pOutput.accept(EndlessBeafItem.ENDLESS_BEAF_ITEM.get());
                        pOutput.accept(TeleportBlock.TELEPORT_BLOCK_ITEM.get());
                        pOutput.accept(OreGeneratorBlock.ORE_GENERATOR_BLOCK_ITEM.get());
                        pOutput.accept(TeleportBlock2.TELEPORT_BLOCK_ITEM_2.get());
                        pOutput.accept(TeleportBlock3.TELEPORT_BLOCK_ITEM_3.get());

                        pOutput.accept(AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK_ITEM.get());


                        // 发光塑料块 - 始终添加
                        for (RegistryObject<Item> item : GlowPlasticBlock.GLOW_PLASTIC_BLOCK_ITEMS.values()) {
                            pOutput.accept(item.get());
                        }

                        // 添加制作者皮肤玩偶方块物品
                        pOutput.accept(CreatorDollBlock.CREATOR_DOLL_BLOCK_ITEM.get());

                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_1.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_2.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_3.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_4.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_5.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_6.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_7.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_8.get());
                        pOutput.accept(ModIngots.USELESS_INGOT_TIER_9.get());
                        
                        // 添加新的锭到创造标签
                        pOutput.accept(ModIngots.POSSIBLE_USEFUL_INGOT.get());
                        pOutput.accept(ModIngots.USEFUL_INGOT.get());

                        // 添加金属模具到创造标签
                        pOutput.accept(ModMolds.METAL_MOLD_PLATE.get());
                        pOutput.accept(ModMolds.METAL_MOLD_ROD.get());
                        pOutput.accept(ModMolds.METAL_MOLD_GEAR.get());
                        pOutput.accept(ModMolds.METAL_MOLD_WIRE.get());
                        pOutput.accept(ModMolds.METAL_MOLD_BLOCK.get());
                        
                        // 添加无用齿轮到创造标签
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_1.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_2.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_3.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_4.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_5.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_6.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_7.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_8.get());
                        pOutput.accept(ModComponents.USELESS_GEAR_TIER_9.get());
                        
                        // 添加无用玻璃到创造标签
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_1.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_2.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_3.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_4.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_5.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_6.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_7.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_8.get());
                        pOutput.accept(ModComponents.USELESS_GLASS_TIER_9.get());

                        // 只在热力系列安装时添加 ThermalMore 整合的物品
                        if (ThermalDependencyHelper.isAnyThermalModLoaded()) {
                            // 安全地添加 ThermalMore 物品
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_1);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_2);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_3);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_4);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_5);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_6);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_7);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_8);
                            safelyAddItem(pOutput, ThermalMoreItems.USELESS_INTEGRAL_COMPONENT_TIER_9);
                            safelyAddItem(pOutput, ThermalParallelItems.AUGMENT_PARALLEL_1);
                            safelyAddItem(pOutput, ThermalParallelItems.AUGMENT_PARALLEL_2);
                            safelyAddItem(pOutput, ThermalParallelItems.AUGMENT_PARALLEL_3);
                        } else {
                            UselessMod.LOGGER.debug("热力系列模组未安装，跳过 Thermal 相关物品添加到创造标签");
                        }
                    }))
                    .build());

    /**
     * 安全地添加物品到创造标签
     * 防止在物品未注册时出现异常
     */
    private static void safelyAddItem(CreativeModeTab.Output output, RegistryObject<? extends Item> itemRegistryObject) {
        try {
            if (itemRegistryObject != null && itemRegistryObject.isPresent()) {
                Item item = itemRegistryObject.get();
                if (item != null) {
                    output.accept(item);
                }
            }
        } catch (Exception e) {
            UselessMod.LOGGER.warn("无法添加物品到创造标签: {}", itemRegistryObject.getId(), e);
        }
    }
}