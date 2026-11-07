package com.sorrowmist.useless.datagen.providers.tags;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
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
        this.tag(ModTags.OMNIVERSAL_FURNACE_CASINGS)
            .replace(false)
            .add(ModBlocks.OMNIVERSAL_FURNACE_CASING.get())
            .add(ModBlocks.PASSIVE_CRAFTING_HATCH.get())
            .add(ModBlocks.MAINTENANCE_HATCH.get())
            .add(ModBlocks.AUTO_MAINTENANCE_HATCH.get())
            .add(ModBlocks.MUFFLER_HATCH.get());

        // 造化杖「无视等级」的例外清单：默认留空，整合包往生成的 json 里加方块即可。
        this.tag(ModTags.BEEF_TOOL_TIER_LOCKED).replace(false);

        this.addPlasticTags();
        this.addCommonColorTags();
    }

    /**
     * 四类塑料方块各自的分类标签，外加一个涵盖全部四类的总标签。
     *
     * <p>分类标签与 {@link GlowPlasticBlock} 中的四个方块映射表一一对应；
     * 总标签 {@link ModTags#PLASTIC_BLOCKS} 直接引用四个分类标签，
     * 便于一次引用全部塑料方块（如维度地板白名单仅需填写 <code>#useless_mod:plastic_blocks</code>）。
     */
    private void addPlasticTags() {
        this.tag(ModTags.PLASTIC).replace(false);
        this.tag(ModTags.GLOW_PLASTIC).replace(false);
        this.tag(ModTags.PLASTIC_CTM).replace(false);
        this.tag(ModTags.GLOW_PLASTIC_CTM).replace(false);

        for (var block : GlowPlasticBlock.PLASTIC_BLOCKS.values()) {
            this.tag(ModTags.PLASTIC).add(block.get());
        }
        for (var block : GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.values()) {
            this.tag(ModTags.GLOW_PLASTIC).add(block.get());
        }
        for (var block : GlowPlasticBlock.PLASTIC_CTM_BLOCKS.values()) {
            this.tag(ModTags.PLASTIC_CTM).add(block.get());
        }
        for (var block : GlowPlasticBlock.GLOW_PLASTIC_CTM_BLOCKS.values()) {
            this.tag(ModTags.GLOW_PLASTIC_CTM).add(block.get());
        }

        // 总标签直接引用四个分类标签，避免逐个方块重复列出。
        this.tag(ModTags.PLASTIC_BLOCKS)
            .replace(false)
            .addTag(ModTags.PLASTIC)
            .addTag(ModTags.GLOW_PLASTIC)
            .addTag(ModTags.PLASTIC_CTM)
            .addTag(ModTags.GLOW_PLASTIC_CTM);
    }

    /**
     * 保留现有塑料方块的 c:dyed 标签，Forge 1.20.1 没有对应的 Tags.Blocks 常量。
     *
     * <p>塑料方块以颜色为唯一区分维度，因此全部归入 {@code c:dyed} 总标签，
     * 并依颜色的染料映射归入对应的 {@code c:dyed/<color>} 子标签。
     * {@code dark_red} 与 {@code aqua} 不存在对应的 {@link DyeColor}，
     * 仅纳入总标签，不参与子标签归类。
     */
    private void addCommonColorTags() {
        for (var blockMap : GlowPlasticBlock.ALL_BLOCK_MAPS) {
            for (var entry : blockMap.entrySet()) {
                Block block = entry.getValue().get();
                this.tag(TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                        new net.minecraft.resources.ResourceLocation("c", "dyed"))).add(block);

                DyeColor dyeColor = entry.getKey().getDyeColor();
                if (dyeColor != null) {
                    this.tag(dyedTag(dyeColor)).add(block);
                }
            }
        }
    }

    /** 将染料颜色映射到 MinecraftForge 的 {@code c:dyed/<color>} 方块标签。 */
    private static TagKey<Block> dyedTag(DyeColor color) {
        return TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                new net.minecraft.resources.ResourceLocation("c", "dyed/" + color.getName()));
    }

    private void addMinecraftTags() {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .replace(false)
            .add(ModBlocks.ORE_GENERATOR_BLOCK.get())
            .add(ModBlocks.TELEPORT_BLOCK.get())
            .add(ModBlocks.TELEPORT_BLOCK_2.get())
            .add(ModBlocks.TELEPORT_BLOCK_3.get())
            .add(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get())
            .add(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get())
            .add(ModBlocks.ME_PATTERN_ASSEMBLY.get())
            .add(ModBlocks.OMNIVERSAL_MOLD_HUB.get())
            .add(ModBlocks.PASSIVE_CRAFTING_HATCH.get())
            .add(ModBlocks.MAINTENANCE_HATCH.get())
            .add(ModBlocks.AUTO_MAINTENANCE_HATCH.get())
            .add(ModBlocks.MUFFLER_HATCH.get())
            .add(ModBlocks.OMNIVERSAL_FURNACE_CASING.get())
            .add(ModBlocks.TEMPERATURE_REGULATOR.get());

        for (var coil : ModBlocks.USELESS_COILS.values()) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(coil.get());
        }

        for (var blockMap : GlowPlasticBlock.ALL_BLOCK_MAPS) {
            for (var block : blockMap.values()) {
                this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.get());
            }
        }
    }
}
