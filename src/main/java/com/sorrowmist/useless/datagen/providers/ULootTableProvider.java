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
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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

            for (var entry : GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.entrySet()) {
                this.dropSelf(entry.getValue().get());
            }

            for (var entry : ModBlocks.USELESS_GLASS_BLOCKS.entrySet()) {
                this.dropSelf(entry.getValue().get());
            }
        }

        @Override
        protected @NotNull Iterable<Block> getKnownBlocks() {
            return Stream.concat(
                    Stream.concat(
                            GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.values().stream().map(DeferredHolder::get),
                            ModBlocks.USELESS_GLASS_BLOCKS.values().stream().map(DeferredHolder::get)
                    ),
                    Stream.of(
                            ModBlocks.ORE_GENERATOR_BLOCK.get(),
                            ModBlocks.TELEPORT_BLOCK.get(),
                            ModBlocks.TELEPORT_BLOCK_2.get(),
                            ModBlocks.TELEPORT_BLOCK_3.get(),
                            ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()
                    )
            ).collect(Collectors.toList());
        }
    }
}
