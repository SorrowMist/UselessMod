package com.sorrowmist.useless.world.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.Objects;

public record DimensionGenerationConfig(
        ResourceLocation borderBlockId,
        ResourceLocation fillBlockId,
        ResourceLocation centerBlockId,
        int platformLayers,
        int platformStartY,
        boolean generateBedrock,
        boolean bedrockAtBottom) {
    public static final ResourceLocation DEFAULT_BORDER_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "aqua_glow_plastic");
    public static final ResourceLocation DEFAULT_FILL_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "white_glow_plastic");
    public static final ResourceLocation DEFAULT_CENTER_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "light_gray_glow_plastic");

    public static final Codec<DimensionGenerationConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("border_block", DEFAULT_BORDER_BLOCK)
                    .forGetter(DimensionGenerationConfig::borderBlockId),
            ResourceLocation.CODEC.optionalFieldOf("fill_block", DEFAULT_FILL_BLOCK)
                    .forGetter(DimensionGenerationConfig::fillBlockId),
            ResourceLocation.CODEC.optionalFieldOf("center_block", DEFAULT_CENTER_BLOCK)
                    .forGetter(DimensionGenerationConfig::centerBlockId),
            Codec.INT.optionalFieldOf("platform_layers", 69)
                    .forGetter(DimensionGenerationConfig::platformLayers),
            Codec.INT.optionalFieldOf("platform_start_y", -64)
                    .forGetter(DimensionGenerationConfig::platformStartY),
            Codec.BOOL.optionalFieldOf("generate_bedrock", true)
                    .forGetter(DimensionGenerationConfig::generateBedrock),
            Codec.BOOL.optionalFieldOf("bedrock_at_bottom", false)
                    .forGetter(DimensionGenerationConfig::bedrockAtBottom)
    ).apply(instance, DimensionGenerationConfig::new));

    public DimensionGenerationConfig {
        borderBlockId = Objects.requireNonNull(borderBlockId);
        fillBlockId = Objects.requireNonNull(fillBlockId);
        centerBlockId = Objects.requireNonNull(centerBlockId);
    }

    public static DimensionGenerationConfig defaults() {
        return new DimensionGenerationConfig(
                DEFAULT_BORDER_BLOCK,
                DEFAULT_FILL_BLOCK,
                DEFAULT_CENTER_BLOCK,
                69,
                -64,
                true,
                false);
    }

    public boolean isValid() {
        return platformLayers >= 1 && platformLayers <= 256
                && platformStartY >= -64 && platformStartY <= 256;
    }

    public boolean hasValidBlockIds() {
        return isValidBlockId(borderBlockId)
                && isValidBlockId(fillBlockId)
                && isValidBlockId(centerBlockId);
    }

    public boolean hasAllowedBlockIds() {
        return isAllowedBlockId(borderBlockId)
                && isAllowedBlockId(fillBlockId)
                && isAllowedBlockId(centerBlockId);
    }

    public static boolean isValidBlockId(ResourceLocation id) {
        if (id == null) return false;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block != Blocks.AIR && block.asItem() != Items.AIR;
    }

    public static boolean isAllowedBlockId(ResourceLocation id) {
        return isValidBlockId(id)
                && ConfigManager.isUselessDimensionFloorBlockAllowed(id);
    }

    public DimensionGenerationConfig normalized() {
        return new DimensionGenerationConfig(
                borderBlockId,
                fillBlockId,
                centerBlockId,
                Mth.clamp(platformLayers, 1, 256),
                Mth.clamp(platformStartY, -64, 256),
                generateBedrock,
                bedrockAtBottom);
    }

    public Block borderBlock() {
        return resolveBlock(borderBlockId, Blocks.BLUE_WOOL);
    }

    public Block fillBlock() {
        return resolveBlock(fillBlockId, Blocks.WHITE_WOOL);
    }

    public Block centerBlock() {
        return resolveBlock(centerBlockId, Blocks.GRAY_WOOL);
    }

    public ItemStack borderBlockItem() {
        return blockItem(borderBlockId);
    }

    public ItemStack fillBlockItem() {
        return blockItem(fillBlockId);
    }

    public ItemStack centerBlockItem() {
        return blockItem(centerBlockId);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(borderBlockId);
        buffer.writeResourceLocation(fillBlockId);
        buffer.writeResourceLocation(centerBlockId);
        buffer.writeVarInt(platformLayers);
        buffer.writeInt(platformStartY);
        buffer.writeBoolean(generateBedrock);
        buffer.writeBoolean(bedrockAtBottom);
    }

    public static DimensionGenerationConfig read(FriendlyByteBuf buffer) {
        return new DimensionGenerationConfig(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("border_block", borderBlockId.toString());
        tag.putString("fill_block", fillBlockId.toString());
        tag.putString("center_block", centerBlockId.toString());
        tag.putInt("platform_layers", platformLayers);
        tag.putInt("platform_start_y", platformStartY);
        tag.putBoolean("generate_bedrock", generateBedrock);
        tag.putBoolean("bedrock_at_bottom", bedrockAtBottom);
        return tag;
    }

    public static DimensionGenerationConfig load(CompoundTag tag) {
        DimensionGenerationConfig defaults = defaults();
        return new DimensionGenerationConfig(
                readId(tag, "border_block", defaults.borderBlockId),
                readId(tag, "fill_block", defaults.fillBlockId),
                readId(tag, "center_block", defaults.centerBlockId),
                tag.contains("platform_layers") ? tag.getInt("platform_layers") : defaults.platformLayers,
                tag.contains("platform_start_y") ? tag.getInt("platform_start_y") : defaults.platformStartY,
                tag.contains("generate_bedrock") ? tag.getBoolean("generate_bedrock") : defaults.generateBedrock,
                tag.contains("bedrock_at_bottom") ? tag.getBoolean("bedrock_at_bottom") : defaults.bedrockAtBottom)
                .normalized();
    }

    public static ResourceLocation blockId(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
    }

    private static ResourceLocation readId(CompoundTag tag, String key, ResourceLocation fallback) {
        ResourceLocation value = ResourceLocation.tryParse(tag.getString(key));
        return value == null ? fallback : value;
    }

    private static Block resolveBlock(ResourceLocation id, Block fallback) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? fallback : block;
    }

    private static ItemStack blockItem(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR || block.asItem() == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(block.asItem());
    }
}
