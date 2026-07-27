package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.content.blocks.OreGeneratorBlock;
import com.sorrowmist.useless.content.blocks.TeleportPadBlock;
import com.sorrowmist.useless.content.blocks.UselessGlassBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MePatternAssemblyBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockPartBlock;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalMoldHubBlock;
import com.sorrowmist.useless.content.blocks.multiblock.PassiveCraftingHatchBlock;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter2;
import com.sorrowmist.useless.world.teleport.UselessDimTeleporter3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessMod.MODID);

    public static final DeferredBlock<Block> TELEPORT_BLOCK = BLOCKS.register(
            "teleport_block",
            () -> new TeleportPadBlock(
                    UselessDimTeleporter::new,
                    BlockBehaviour.Properties.of()
                            .strength(2.0f, 65536.0f)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<Block> TELEPORT_BLOCK_2 = BLOCKS.register(
            "teleport_block_2",
            () -> new TeleportPadBlock(
                    UselessDimTeleporter2::new,
                    BlockBehaviour.Properties.of()
                            .strength(2.0f, 65536.0f)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<Block> TELEPORT_BLOCK_3 = BLOCKS.register(
            "teleport_block_3",
            () -> new TeleportPadBlock(
                    UselessDimTeleporter3::new,
                    BlockBehaviour.Properties.of()
                            .strength(2.0f, 65536.0f)
                            .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredBlock<OreGeneratorBlock> ORE_GENERATOR_BLOCK = BLOCKS.register(
            "ore_generator_block", OreGeneratorBlock::new
    );

    public static final DeferredBlock<AdvancedAlloyFurnaceBlock> ADVANCED_ALLOY_FURNACE_BLOCK = BLOCKS.register(
            "advanced_alloy_furnace_block",
            AdvancedAlloyFurnaceBlock::new
    );

    public static final DeferredBlock<MultiblockAlloyFurnaceCoreBlock> MULTIBLOCK_ALLOY_FURNACE_CORE = BLOCKS.register(
            "multiblock_alloy_furnace_core",
            () -> new MultiblockAlloyFurnaceCoreBlock(BlockBehaviour.Properties.of()
                    .strength(5.0F, 32768.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<MePatternAssemblyBlock> ME_PATTERN_ASSEMBLY = BLOCKS.register(
            "me_pattern_assembly",
            () -> new MePatternAssemblyBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 1200.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<OmniversalMoldHubBlock> OMNIVERSAL_MOLD_HUB = BLOCKS.register(
            "omniversal_mold_hub",
            () -> new OmniversalMoldHubBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 1200.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<PassiveCraftingHatchBlock> PASSIVE_CRAFTING_HATCH = BLOCKS.register(
            "passive_crafting_hatch",
            () -> new PassiveCraftingHatchBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 1200.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<MultiblockPartBlock> OMNIVERSAL_FURNACE_CASING = BLOCKS.register(
            "omniversal_furnace_casing",
            () -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 1200.0F).requiresCorrectToolForDrops()));

    public static final Map<Integer, DeferredBlock<UselessCoilBlock>> USELESS_COILS = new LinkedHashMap<>();

    static {
        for (int tier = UselessCoilBlock.MIN_TIER; tier <= UselessCoilBlock.MAX_TIER; tier++) {
            int capturedTier = tier;
            USELESS_COILS.put(tier, BLOCKS.register(UselessCoilBlock.registryName(tier),
                    () -> new UselessCoilBlock(capturedTier, BlockBehaviour.Properties.of()
                            .strength(3.0F + capturedTier * 0.25F, 1200.0F + capturedTier * 600.0F)
                            .requiresCorrectToolForDrops())));
        }
    }

    // 无用玻璃方块 - 防爆，Shift+右键快速破坏
    public static final Map<String, DeferredBlock<UselessGlassBlock>> USELESS_GLASS_BLOCKS = new LinkedHashMap<>();

    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_1 = registerGlassBlock(
            "useless_glass_tier_1", 1.0f, 1200.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_2 = registerGlassBlock(
            "useless_glass_tier_2", 1.5f, 1200.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_3 = registerGlassBlock(
            "useless_glass_tier_3", 2.0f, 2400.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_4 = registerGlassBlock(
            "useless_glass_tier_4", 2.5f, 2400.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_5 = registerGlassBlock(
            "useless_glass_tier_5", 3.0f, 3600.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_6 = registerGlassBlock(
            "useless_glass_tier_6", 3.5f, 3600.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_7 = registerGlassBlock(
            "useless_glass_tier_7", 4.0f, 4800.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_8 = registerGlassBlock(
            "useless_glass_tier_8", 4.5f, 6000.0f);
    static final DeferredBlock<UselessGlassBlock> USELESS_GLASS_TIER_9 = registerGlassBlock(
            "useless_glass_tier_9", 5.0f, 7200.0f);

    private ModBlocks() {}

    private static DeferredBlock<UselessGlassBlock> registerGlassBlock(String name, float hardness, float resistance) {
        DeferredBlock<UselessGlassBlock> block = BLOCKS.register(name, () -> new UselessGlassBlock(
                BlockBehaviour.Properties.of().strength(hardness, resistance)) {}
        );
        USELESS_GLASS_BLOCKS.put(name, block);
        return block;
    }
}
