package com.sorrowmist.useless.compat.neoecoae.compact;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactBlockItem;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactC9Block;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactF9Block;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactL9Block;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactTicker;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactC9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactL9BlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.exposed.ExposedPatternBusBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationDriveBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationHostBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationParallelCoreBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowCraftingHostBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowCraftingParallelCoreBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowCraftingWorkerBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowInterfaceBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowPatternBusBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowThreadingCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 无用型紧凑三方块（C9 / F9 / L9）的注册入口。
 *
 * <p>本类只能在确认 neoecoae 已加载之后才会被类加载，因此可以安全地直接引用 ECO 的类型。
 * 三个方块都是「单方块等价于整套满配多方块」的壳：自己不带任何机器逻辑，全部把调用转给 ECO。</p>
 *
 * <p>注意：ECO 用 Registrate 注册内容，而 Registrate 只是它的 {@code implementation} 依赖，
 * 不在本工程的编译类路径上（{@code BlockEntry} / {@code ItemEntry} 不可访问）。
 * 因此这里一律不改用 ECO 的注册条目对象，改走注册表按 id 查询。</p>
 */
public final class NeoEcoCompactRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UselessMod.MODID);

    private NeoEcoCompactRegistry() {}

    private static BlockBehaviour.Properties shellProperties() {
        return BlockBehaviour.Properties.of()
                .strength(6.0F, 1200.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    // ---------------------------------------------------------------- 方块

    public static final DeferredBlock<CompactC9Block> COMPACT_C9 =
            BLOCKS.register("useless_compact_c9", () -> new CompactC9Block(shellProperties()));

    public static final DeferredBlock<CompactF9Block> COMPACT_F9 =
            BLOCKS.register("useless_compact_f9", () -> new CompactF9Block(shellProperties()));

    public static final DeferredBlock<CompactL9Block> COMPACT_L9 =
            BLOCKS.register("useless_compact_l9", () -> new CompactL9Block(shellProperties()));

    public static final DeferredItem<BlockItem> COMPACT_C9_ITEM = ITEMS.register(
            "useless_compact_c9", () -> new CompactBlockItem(COMPACT_C9.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> COMPACT_F9_ITEM = ITEMS.register(
            "useless_compact_f9", () -> new CompactBlockItem(COMPACT_F9.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> COMPACT_L9_ITEM = ITEMS.register(
            "useless_compact_l9", () -> new CompactBlockItem(COMPACT_L9.get(), new Item.Properties()));

    // ------------------------------------------------------ 宿主方块实体类型
    // 注意：lambda 里对自身字段一律用「类名限定」写法，避免 javac 的「初始化程序自引用」。

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompactC9BlockEntity>> COMPACT_C9_BE =
            BLOCK_ENTITY_TYPES.register("useless_compact_c9", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CompactC9BlockEntity(
                            NeoEcoCompactRegistry.COMPACT_C9_BE.get(), pos, state),
                    COMPACT_C9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompactF9BlockEntity>> COMPACT_F9_BE =
            BLOCK_ENTITY_TYPES.register("useless_compact_f9", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CompactF9BlockEntity(
                            NeoEcoCompactRegistry.COMPACT_F9_BE.get(), pos, state),
                    COMPACT_F9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompactL9BlockEntity>> COMPACT_L9_BE =
            BLOCK_ENTITY_TYPES.register("useless_compact_l9", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CompactL9BlockEntity(
                            NeoEcoCompactRegistry.COMPACT_L9_BE.get(), pos, state),
                    COMPACT_L9.get()).build(null));

    // ------------------------------------------------------- 影子组件实体类型
    // 影子组件永远不会由世界创建，这些类型只用于手动 new 出「不落世界的 ECO 组件」。

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowComputationHostBlockEntity>>
            SHADOW_COMPUTATION_HOST = BLOCK_ENTITY_TYPES.register("compact_shadow_computation_host",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowComputationHostBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_COMPUTATION_HOST.get(), pos, state),
                    COMPACT_C9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowThreadingCoreBlockEntity>>
            SHADOW_THREADING_CORE = BLOCK_ENTITY_TYPES.register("compact_shadow_threading_core",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowThreadingCoreBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_THREADING_CORE.get(), pos, state),
                    COMPACT_C9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowComputationDriveBlockEntity>>
            SHADOW_COMPUTATION_DRIVE = BLOCK_ENTITY_TYPES.register("compact_shadow_computation_drive",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowComputationDriveBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_COMPUTATION_DRIVE.get(), pos, state),
                    COMPACT_C9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowComputationParallelCoreBlockEntity>>
            SHADOW_COMPUTATION_PARALLEL_CORE =
            BLOCK_ENTITY_TYPES.register("compact_shadow_computation_parallel_core",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new ShadowComputationParallelCoreBlockEntity(
                                    NeoEcoCompactRegistry.SHADOW_COMPUTATION_PARALLEL_CORE.get(), pos, state),
                            COMPACT_C9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowCraftingHostBlockEntity>>
            SHADOW_CRAFTING_HOST = BLOCK_ENTITY_TYPES.register("compact_shadow_crafting_host",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowCraftingHostBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_CRAFTING_HOST.get(), pos, state),
                    COMPACT_F9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowCraftingWorkerBlockEntity>>
            SHADOW_CRAFTING_WORKER = BLOCK_ENTITY_TYPES.register("compact_shadow_crafting_worker",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowCraftingWorkerBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_CRAFTING_WORKER.get(), pos, state),
                    COMPACT_F9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowCraftingParallelCoreBlockEntity>>
            SHADOW_CRAFTING_PARALLEL_CORE = BLOCK_ENTITY_TYPES.register("compact_shadow_crafting_parallel_core",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowCraftingParallelCoreBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_CRAFTING_PARALLEL_CORE.get(), pos, state),
                    COMPACT_F9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShadowPatternBusBlockEntity>>
            SHADOW_PATTERN_BUS = BLOCK_ENTITY_TYPES.register("compact_shadow_pattern_bus",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ShadowPatternBusBlockEntity(
                            NeoEcoCompactRegistry.SHADOW_PATTERN_BUS.get(), pos, state),
                    COMPACT_F9.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExposedPatternBusBlockEntity>>
            COMPACT_EXPOSED_PATTERN_BUS = BLOCK_ENTITY_TYPES.register("compact_exposed_pattern_bus",
            () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ExposedPatternBusBlockEntity(
                            NeoEcoCompactRegistry.COMPACT_EXPOSED_PATTERN_BUS.get(), pos, state),
                    COMPACT_F9.get()).build(null));

    // ---------------------------------------------------------------- 生命周期

    public static void init(IEventBus modEventBus) {
        // AE2 按宿主的「精确运行时类」建机器索引，而 ECO 用父类去查主机；
        // 先把「紧凑主机 → ECO 主机类」登记好，Grid 的 mixin 会据此改写索引 key。
        CompactMachineClassMap.register(CompactC9BlockEntity.class, ECOComputationSystemBlockEntity.class);
        CompactMachineClassMap.register(CompactF9BlockEntity.class, ECOCraftingSystemBlockEntity.class);
        CompactMachineClassMap.register(CompactL9BlockEntity.class, ECOStorageSystemBlockEntity.class);
        // 影子接口与聚合总线也各自带着「假身份」的网格节点：ECO 的界面与样板终端是按
        // ECOMachineInterfaceBlockEntity / ECOCraftingPatternBusBlockEntity 这两个类去查网格的。
        CompactMachineClassMap.register(ShadowInterfaceBlockEntity.class, ECOMachineInterfaceBlockEntity.class);
        CompactMachineClassMap.register(ExposedPatternBusBlockEntity.class, ECOCraftingPatternBusBlockEntity.class);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(NeoEcoCompactRegistry::onCommonSetup);
        modEventBus.addListener(NeoEcoCompactRegistry::onRegisterCapabilities);
    }

    /**
     * AE2 只在方块暴露了 {@code IN_WORLD_GRID_NODE_HOST} 能力时才认它是网格节点宿主。
     * ECO 在自己的 {@code NEBlockEntityEntry#onRegisterCapabilies} 里做了同样的事；
     * 漏掉这一步会让紧凑方块「有节点但永远接不上相邻线缆」。
     */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, COMPACT_C9_BE.get(),
                (blockEntity, side) -> (IInWorldGridNodeHost) blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, COMPACT_F9_BE.get(),
                (blockEntity, side) -> (IInWorldGridNodeHost) blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, COMPACT_L9_BE.get(),
                (blockEntity, side) -> (IInWorldGridNodeHost) blockEntity);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            wire(COMPACT_C9.get(), COMPACT_C9_BE.get(), CompactC9BlockEntity.class, COMPACT_C9_ITEM.get());
            wire(COMPACT_F9.get(), COMPACT_F9_BE.get(), CompactF9BlockEntity.class, COMPACT_F9_ITEM.get());
            wire(COMPACT_L9.get(), COMPACT_L9_BE.get(), CompactL9BlockEntity.class, COMPACT_L9_ITEM.get());
        });
    }

    /**
     * 复刻 ECO 自己的 {@code NEBlockEntityEntry#onCommonSetup}：把方块和它的方块实体类型绑起来。
     * 影子组件不需要绑定，它们只被我们手动实例化。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wire(AEBaseEntityBlock block, BlockEntityType<?> type, Class<?> blockEntityClass, Item item) {
        block.setBlockEntity(
                blockEntityClass,
                type,
                null,
                (level, pos, state, be) -> {
                    if (be instanceof CompactTicker ticker) {
                        ticker.tick(level, pos, state);
                    }
                }
        );
        AEBaseBlockEntity.registerBlockEntityItem(type, item);
    }

    // ------------------------------------------------------------------ 便利

    /** 按 id 取 ECO 的物品，避免触碰 Registrate 的 {@code ItemEntry} 类型。 */
    public static Item neoEcoItem(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("neoecoae", path));
    }

    public static ItemStack neoEcoStack(String path) {
        Item item = neoEcoItem(path);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** 影子组件用的方块状态：紧凑方块自己的默认状态（影子组件从不写入世界，状态只用于占位）。 */
    public static net.minecraft.world.level.block.state.BlockState placeholderState(
            DeferredBlock<? extends net.minecraft.world.level.block.Block> block) {
        return block.get().defaultBlockState();
    }

    public static BlockPos hostAnchor(BlockPos pos) {
        return pos;
    }
}
