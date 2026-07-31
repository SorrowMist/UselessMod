package com.sorrowmist.useless.client.render.ctm;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;
import java.util.function.Function;

public final class CtmModelRegistrar {
    private static final String FURNACE_TEXTURE_ROOT =
            "block/multiblock_alloy_furnace/";
    private static final String COIL_TEXTURE_ROOT = FURNACE_TEXTURE_ROOT + "coils/";

    private CtmModelRegistrar() {
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Function<Material, TextureAtlasSprite> textureGetter = event.getTextureGetter();
        TextureAtlasSprite furnaceBase = sprite(textureGetter,
                FURNACE_TEXTURE_ROOT + "furnace_casing");
        TextureAtlasSprite furnaceCtm = sprite(textureGetter,
                FURNACE_TEXTURE_ROOT + "furnace_casing_ctm");
        TextureAtlasSprite uselessCoilActive = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useless_coil_active");
        TextureAtlasSprite uselessCoilActiveCtm = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useless_coil_active_ctm");
        TextureAtlasSprite usefulCoilActive = sprite(textureGetter,
                COIL_TEXTURE_ROOT + "useful_coil_active");
        TextureAtlasSprite usefulCoilActiveCtm = sprite(textureGetter,
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
    }

    private static TextureAtlasSprite sprite(
            Function<Material, TextureAtlasSprite> textureGetter, String path) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                UselessMod.MODID, path);
        return textureGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, texture));
    }

    private static void wrapBlock(
            Map<ModelResourceLocation, BakedModel> models, Block block,
            TextureAtlasSprite baseSprite, TextureAtlasSprite ctmSprite) {
        wrapBlock(models, block, baseSprite, ctmSprite, null, null);
    }

    private static void wrapBlock(
            Map<ModelResourceLocation, BakedModel> models, Block block,
            TextureAtlasSprite baseSprite, TextureAtlasSprite ctmSprite,
            TextureAtlasSprite overlayBaseSprite, TextureAtlasSprite overlayCtmSprite) {
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
