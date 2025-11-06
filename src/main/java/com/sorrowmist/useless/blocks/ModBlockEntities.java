package com.sorrowmist.useless.blocks;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.blocks.oregenerator.OreGeneratorBlock;
import com.sorrowmist.useless.blocks.oregenerator.OreGeneratorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, UselessMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<OreGeneratorBlockEntity>> ORE_GENERATOR =
            BLOCK_ENTITIES.register("ore_generator",
                    () -> BlockEntityType.Builder.of(OreGeneratorBlockEntity::new,
                            OreGeneratorBlock.ORE_GENERATOR_BLOCK.get()).build(null));

    // 在 ModBlockEntities.java 中添加注册
// 在现有的 ModBlockEntities.java 文件中添加以下内容：

    public static final RegistryObject<BlockEntityType<AdvancedAlloyFurnaceBlockEntity>> ADVANCED_ALLOY_FURNACE =
            BLOCK_ENTITIES.register("advanced_alloy_furnace",
                    () -> BlockEntityType.Builder.of(AdvancedAlloyFurnaceBlockEntity::new,
                            AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK.get()).build(null));
}