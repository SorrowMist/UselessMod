package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.OreGeneratorBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.compat.draconicevolution.DraconicOpStorageCompat;
import com.sorrowmist.useless.compat.fluxnetworks.FluxNetworksEnergyCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.ModList;

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

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                MULTIBLOCK_ALLOY_FURNACE_CORE.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ME_PATTERN_ASSEMBLY.get(),
                (blockEntity, side) -> blockEntity.getPatterns());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ME_PATTERN_ASSEMBLY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                OMNIVERSAL_MOLD_HUB.get(),
                (blockEntity, side) -> blockEntity.getMolds());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                PASSIVE_CRAFTING_HATCH.get(),
                (blockEntity, side) -> blockEntity.getPatterns());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ORE_GENERATOR.get(),
                (blockEntity, context) -> blockEntity);

        // 注册高级合金炉的能量处理能力
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage()
        );

        // 注册高级合金炉的流体处理能力（支持方向感知）
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getCombinedFluidHandler(side)
        );

        // 注册高级合金炉的AE网络节点能力（用于连接AE网络）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, context) -> blockEntity
        );

        if (ModList.get().isLoaded("draconicevolution")) {
            DraconicOpStorageCompat.registerCapabilities(event);
        }
        if (ModList.get().isLoaded("mekanism")) {
            invokeOptionalCapabilityLoader(
                    "com.sorrowmist.useless.compat.mekanism.MekanismCompatLoader", event);
        }
        if (ModList.get().isLoaded(FluxNetworksEnergyCompat.MOD_ID)) {
            FluxNetworksEnergyCompat.registerCapabilities(event);
        }
    }

    /**
     * Keep optional Mekanism classes out of this always-loaded registry class.  In particular, the
     * JVM must not resolve AppMek's capability types on a server that does not install AppMek.
     */
    private static void invokeOptionalCapabilityLoader(String className, RegisterCapabilitiesEvent event) {
        try {
            Class<?> loader = Class.forName(className, true, ModBlockEntities.class.getClassLoader());
            loader.getMethod("registerCapabilities", RegisterCapabilitiesEvent.class).invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError exception) {
            UselessMod.LOGGER.error("Failed to register optional capabilities from {}", className, exception);
        }
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockAlloyFurnaceCoreBlockEntity>> MULTIBLOCK_ALLOY_FURNACE_CORE =
            BLOCK_ENTITY_TYPES.register("multiblock_alloy_furnace_core",
                    () -> BlockEntityType.Builder.of(MultiblockAlloyFurnaceCoreBlockEntity::new,
                            ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MePatternAssemblyBlockEntity>> ME_PATTERN_ASSEMBLY =
            BLOCK_ENTITY_TYPES.register("me_pattern_assembly",
                    () -> BlockEntityType.Builder.of(MePatternAssemblyBlockEntity::new,
                            ModBlocks.ME_PATTERN_ASSEMBLY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniversalMoldHubBlockEntity>> OMNIVERSAL_MOLD_HUB =
            BLOCK_ENTITY_TYPES.register("omniversal_mold_hub",
                    () -> BlockEntityType.Builder.of(OmniversalMoldHubBlockEntity::new,
                            ModBlocks.OMNIVERSAL_MOLD_HUB.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PassiveCraftingHatchBlockEntity>> PASSIVE_CRAFTING_HATCH =
            BLOCK_ENTITY_TYPES.register("passive_crafting_hatch",
                    () -> BlockEntityType.Builder.of(PassiveCraftingHatchBlockEntity::new,
                            ModBlocks.PASSIVE_CRAFTING_HATCH.get()).build(null));


}
