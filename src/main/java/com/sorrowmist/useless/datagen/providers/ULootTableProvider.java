package com.sorrowmist.useless.datagen.providers;

import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ULootTableProvider extends LootTableProvider {

    private static final List<SubProviderEntry> SUB_PROVIDERS = List.of(
            new SubProviderEntry(BlockLootProvider::new, LootContextParamSets.BLOCK)
    );

    public ULootTableProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(), SUB_PROVIDERS, registries);
    }

    private static class BlockLootProvider extends BlockLootSubProvider {

        BlockLootProvider(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            this.dropSelf(ModBlocks.ORE_GENERATOR_BLOCK.get());
            this.dropSelf(ModBlocks.TELEPORT_BLOCK.get());
            this.dropSelf(ModBlocks.TELEPORT_BLOCK_2.get());
            this.dropSelf(ModBlocks.TELEPORT_BLOCK_3.get());
            this.dropSelf(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
            this.dropSelf(ModBlocks.SUPERVISOR.get());
            this.dropSelf(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get());
            this.dropSelf(ModBlocks.ME_PATTERN_ASSEMBLY.get());
            this.dropSelf(ModBlocks.OMNIVERSAL_MOLD_HUB.get());
            this.dropSelf(ModBlocks.PASSIVE_CRAFTING_HATCH.get());
            this.dropSelf(ModBlocks.OMNIVERSAL_FURNACE_CASING.get());

            for (var coil : ModBlocks.USELESS_COILS.values()) {
                this.dropSelf(coil.get());
            }

            for (var blockMap : GlowPlasticBlock.ALL_BLOCK_MAPS) {
                for (var block : blockMap.values()) {
                    this.dropSelf(block.get());
                }
            }

            for (var entry : ModBlocks.USELESS_GLASS_BLOCKS.entrySet()) {
                this.dropSelf(entry.getValue().get());
            }
        }

        @Override
        protected @NotNull Iterable<Block> getKnownBlocks() {
            Stream.Builder<Block> blocks = Stream.builder();
            for (var blockMap : GlowPlasticBlock.ALL_BLOCK_MAPS) {
                blockMap.values().forEach(block -> blocks.add(block.get()));
            }
            ModBlocks.USELESS_GLASS_BLOCKS.values().forEach(block -> blocks.add(block.get()));
            ModBlocks.USELESS_COILS.values().forEach(block -> blocks.add(block.get()));
            blocks.add(ModBlocks.ORE_GENERATOR_BLOCK.get());
            blocks.add(ModBlocks.TELEPORT_BLOCK.get());
            blocks.add(ModBlocks.TELEPORT_BLOCK_2.get());
            blocks.add(ModBlocks.TELEPORT_BLOCK_3.get());
            blocks.add(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
            blocks.add(ModBlocks.SUPERVISOR.get());
            blocks.add(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get());
            blocks.add(ModBlocks.ME_PATTERN_ASSEMBLY.get());
            blocks.add(ModBlocks.OMNIVERSAL_MOLD_HUB.get());
            blocks.add(ModBlocks.PASSIVE_CRAFTING_HATCH.get());
            blocks.add(ModBlocks.OMNIVERSAL_FURNACE_CASING.get());
            return blocks.build().toList();
        }
    }
}
