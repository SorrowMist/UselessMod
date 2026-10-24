package com.sorrowmist.useless.init;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.OreGeneratorBlockEntity;
import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.compat.draconicevolution.DraconicOpStorageCompat;
import com.sorrowmist.useless.compat.fluxnetworks.FluxNetworksEnergyCompat;
import com.sorrowmist.useless.compat.forge1201.OptionalIntegration;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import com.sorrowmist.useless.compat.forge1201.capability.RegisterCapabilitiesEvent;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;
import net.minecraftforge.fml.ModList;

import appeng.capabilities.Capabilities;

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
                ForgeCapabilities.ITEM_HANDLER,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getItemHandler(side)
        );

        event.registerBlockEntity(
                ForgeCapabilities.ENERGY,
                MULTIBLOCK_ALLOY_FURNACE_CORE.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage());
        event.registerBlockEntity(
                ForgeCapabilities.ITEM_HANDLER,
                ME_PATTERN_ASSEMBLY.get(),
                (blockEntity, side) -> blockEntity.getPatterns());
        event.registerBlockEntity(
                appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST,
                ME_PATTERN_ASSEMBLY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                ForgeCapabilities.ITEM_HANDLER,
                OMNIVERSAL_MOLD_HUB.get(),
                (blockEntity, side) -> blockEntity.getMolds());
        event.registerBlockEntity(
                ForgeCapabilities.ITEM_HANDLER,
                PASSIVE_CRAFTING_HATCH.get(),
                (blockEntity, side) -> blockEntity.getPatterns());
        event.registerBlockEntity(
                appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST,
                ORE_GENERATOR.get(),
                (blockEntity, context) -> blockEntity);

        // 注册高级合金炉的能量处理能力
        event.registerBlockEntity(
                ForgeCapabilities.ENERGY,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage()
        );

        // 注册高级合金炉的流体处理能力（支持方向感知）
        event.registerBlockEntity(
                ForgeCapabilities.FLUID_HANDLER,
                ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> blockEntity.getCombinedFluidHandler(side)
        );

        // 注册高级合金炉的AE网络节点能力（用于连接AE网络）
        event.registerBlockEntity(
                appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST,
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
        if (ModList.get().isLoaded("pneumaticcraft")) {
            invokeOptionalCapabilityLoader(
                    "com.sorrowmist.useless.compat.pneumaticcraft.ThermostatHeatCompat", event);
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
        if (!OptionalIntegration.hasClass(className)) {
            UselessMod.LOGGER.debug("Optional capability loader {} is not present in this build", className);
            return;
        }
        try {
            Class<?> loader = Class.forName(className, true, ModBlockEntities.class.getClassLoader());
            loader.getMethod("registerCapabilities", RegisterCapabilitiesEvent.class).invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError exception) {
            RateLimitedLogger.error(UselessMod.LOGGER, "Failed to register optional capabilities from {}", className, exception);
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

    /**
     * 「温度调节器」方块的恒温源状态。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermostatBlockEntity>> TEMPERATURE_REGULATOR =
            BLOCK_ENTITY_TYPES.register("temperature_regulator",
                    () -> BlockEntityType.Builder.of(ThermostatBlockEntity::new,
                            ModBlocks.TEMPERATURE_REGULATOR.get()).build(null));


}
