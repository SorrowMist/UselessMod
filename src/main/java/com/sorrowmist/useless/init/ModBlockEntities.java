package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.OreGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UselessMod.MODID);

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 注册高级合金炉的物品处理能力（支持方向感知）
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getItemHandler(side)
        );

        // 注册高级合金炉的能量处理能力
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage()
        );

        // 注册高级合金炉的流体处理能力（复合处理器，同时管理输入和输出槽位）
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getCombinedFluidHandler()
        );

        // 注册高级合金炉的AE网络节点能力（用于连接AE网络）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, context) -> blockEntity
        );
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreGeneratorBlockEntity>> ORE_GENERATOR =
            BLOCK_ENTITY_TYPES.register("ore_generator",
                                        () -> BlockEntityType.Builder.of(OreGeneratorBlockEntity::new,
                                                                         ModBlocks.ORE_GENERATOR_BLOCK.get()
                                        ).build(null)
            );


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedAlloyFurnaceBlockEntity>> ADVANCED_ALLOY_FURNACE =
            BLOCK_ENTITY_TYPES.register("advanced_alloy_furnace",
                                        () -> BlockEntityType.Builder.of(AdvancedAlloyFurnaceBlockEntity::new,
                                                                         ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()
                                        ).build(null)
            );


}
