package com.sorrowmist.useless.client.render.ctm;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.ModelEvent;

import java.util.Map;
import java.util.function.Function;

public final class CtmModelRegistrar {
    private static final String FURNACE_TEXTURE_ROOT =
            "block/multiblock_alloy_furnace/";
    private static final String COIL_TEXTURE_ROOT = FURNACE_TEXTURE_ROOT + "coils/";
    private static final String PLASTIC_TEXTURE = "block/plastic_glow_block";
    private static final String PLASTIC_CTM_TEXTURE = "block/plastic_glow_block_ctm";

    private CtmModelRegistrar() {
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // ModifyBakingResult runs before atlas upload. Keep texture IDs until BakingCompleted.
        Function<ResourceLocation, ResourceLocation> textureGetter = Function.identity();
        ResourceLocation furnaceBase = sprite(textureGetter,
                FURNACE_TEXTURE_ROOT + "furnace_casing");
        ResourceLocation furnaceCtm = sprite(textureGetter,
                FURNACE_TEXTURE_ROOT + "furnace_casing_ctm");
        ResourceLocation uselessCoilActive = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useless_coil_active");
        ResourceLocation uselessCoilActiveCtm = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useless_coil_active_ctm");
        ResourceLocation usefulCoilActive = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useful_coil_active");
        ResourceLocation usefulCoilActiveCtm = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useful_coil_active_ctm");

        wrapBlock(event.getModels(), ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get(),
                furnaceBase, furnaceCtm);
        wrapBlock(event.getModels(), ModBlocks.OMNIVERSAL_FURNACE_CASING.get(),
                furnaceBase, furnaceCtm);
        wrapBlock(event.getModels(), ModBlocks.ME_PATTERN_ASSEMBLY.get(),
                furnaceBase, furnaceCtm);
        wrapBlock(event.getModels(), ModBlocks.OMNIVERSAL_MOLD_HUB.get(),
                furnaceBase, furnaceCtm);
        wrapBlock(event.getModels(), ModBlocks.PASSIVE_CRAFTING_HATCH.get(),
                furnaceBase, furnaceCtm);

        ModBlocks.USELESS_COILS.forEach((tier, holder) -> {
            String name = UselessCoilBlock.registryName(tier);
            boolean useful = tier == UselessCoilBlock.USEFUL_TIER;
            wrapBlock(event.getModels(), holder.get(),
                    sprite(textureGetter, COIL_TEXTURE_ROOT + name),
                    sprite(textureGetter, COIL_TEXTURE_ROOT + name + "_ctm"),
                    useful ? usefulCoilActive : uselessCoilActive,
                    useful ? usefulCoilActiveCtm : uselessCoilActiveCtm);
        });

        ResourceLocation plasticBase = sprite(textureGetter, PLASTIC_TEXTURE);
        ResourceLocation plasticCtm = sprite(textureGetter, PLASTIC_CTM_TEXTURE);
        GlowPlasticBlock.PLASTIC_CTM_BLOCKS.values().forEach(block ->
                wrapBlock(event.getModels(), block.get(), plasticBase, plasticCtm));
        GlowPlasticBlock.GLOW_PLASTIC_CTM_BLOCKS.values().forEach(block ->
                wrapBlock(event.getModels(), block.get(), plasticBase, plasticCtm));
    }

    public static void bakingCompleted(ModelEvent.BakingCompleted event) {
        var atlas = event.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        event.getModels().values().stream().filter(CtmBakedModel.class::isInstance)
                .map(CtmBakedModel.class::cast).distinct().forEach(model -> model.bindSprites(atlas::getSprite));
    }

    private static ResourceLocation sprite(
            Function<ResourceLocation, ResourceLocation> textureGetter, String path) {
        ResourceLocation texture = new ResourceLocation(
                UselessMod.MODID, path);
        return textureGetter.apply(texture);
    }

    private static void wrapBlock(
            Map<ResourceLocation, BakedModel> models, Block block,
            ResourceLocation baseSprite, ResourceLocation ctmSprite) {
        wrapBlock(models, block, baseSprite, ctmSprite, null, null);
    }

    private static void wrapBlock(
            Map<ResourceLocation, BakedModel> models, Block block,
            ResourceLocation baseSprite, ResourceLocation ctmSprite,
            ResourceLocation overlayBaseSprite, ResourceLocation overlayCtmSprite) {
        for (var state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            models.computeIfPresent(location, (ignored, model) ->
                    model instanceof CtmBakedModel
                            ? model
                            : new CtmBakedModel(model, baseSprite, ctmSprite,
                                    overlayBaseSprite, overlayCtmSprite));
        }
    }
}
