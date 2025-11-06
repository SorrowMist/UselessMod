package com.sorrowmist.useless.blocks;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.EnumColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class GlowPlasticBlock extends Block implements IColoredBlock {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessMod.MODID);

    public static final Map<EnumColor, DeferredBlock<GlowPlasticBlock>> GLOW_PLASTIC_BLOCKS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredItem<Item>> GLOW_PLASTIC_BLOCK_ITEMS = new LinkedHashMap<>();

    static {
        UnaryOperator<BlockBehaviour.Properties> glowPlasticProperties = properties -> properties;

        // 为每种颜色注册方块和物品
        for (EnumColor color : EnumColor.valuesInOrder()) {
            String registryName = color.getRegistryPrefix() + "_glow_plastic";

            // 注册方块
            DeferredBlock<GlowPlasticBlock> block = BLOCKS.register(
                    registryName,
                    () -> new GlowPlasticBlock(color, glowPlasticProperties)
            );
            GLOW_PLASTIC_BLOCKS.put(color, block);

            // 注册对应的物品
            DeferredItem<Item> item = ITEMS.register(
                    registryName,
                    () -> new BlockItem(block.get(), new Item.Properties())
            );
            GLOW_PLASTIC_BLOCK_ITEMS.put(color, item);
        }
    }

    private final EnumColor color;

    public GlowPlasticBlock(EnumColor color, UnaryOperator<Properties> propertyModifier) {
        super(applyLightLevelAdjustments(propertyModifier.apply(Properties.of()
                .mapColor(color.getMapColor())
                .strength(5F, 6F)
                .requiresCorrectToolForDrops())));
        this.color = color;
    }

    private static Properties applyLightLevelAdjustments(Properties properties) {
        return properties.lightLevel(state -> 15);
    }

    @Override
    public EnumColor getColor() {
        return color;
    }

}