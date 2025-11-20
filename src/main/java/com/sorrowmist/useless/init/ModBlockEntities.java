package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.oregenerator.OreGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UselessMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreGeneratorBlockEntity>> ORE_GENERATOR =
            BLOCK_ENTITY_TYPES.register("ore_generator",
                    () -> BlockEntityType.Builder.of(OreGeneratorBlockEntity::new,
                            ModBlocks.ORE_GENERATOR_BLOCK.get()).build(null));
}
