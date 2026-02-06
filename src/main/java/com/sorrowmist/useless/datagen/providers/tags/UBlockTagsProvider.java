package com.sorrowmist.useless.datagen.providers.tags;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class UBlockTagsProvider extends BlockTagsProvider {

    public UBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                              ExistingFileHelper existingFileHelper) {
        super(output, registries, UselessMod.MODID, existingFileHelper);
    }

    @Override
    public @NotNull String getName() {
        return "Tags (Block)";
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        this.addMinecraftTags();
    }

    private void addMinecraftTags() {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .replace(false)
            .add(ModBlocks.ORE_GENERATOR_BLOCK.get())
            .add(ModBlocks.TELEPORT_BLOCK.get())
            .add(ModBlocks.TELEPORT_BLOCK_2.get())
            .add(ModBlocks.TELEPORT_BLOCK_3.get())
            .add(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());

        for (var entry : GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.entrySet()) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(entry.getValue().get());
        }
    }
}
