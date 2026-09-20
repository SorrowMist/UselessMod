package com.sorrowmist.useless.world.dimension;

/*
 * The boundary, road, and center-marker option model follows concepts from
 * GT New Horizons/PersonalSpace:
 * https://github.com/GTNewHorizons/PersonalSpace
 * PersonalSpace is licensed under LGPL-3.0; see LICENSES/PersonalSpace-LGPL-3.0.txt.
 * The record, codec, persistence, validation, and Useless Mod integration are
 * original code.
 */

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;

/** Settings used by all three Useless Dimension chunk generators. */
public record DimensionGenerationConfig(
        ResourceLocation borderBlockId,
        ResourceLocation fillBlockId,
        ResourceLocation centerBlockId,
        int platformLayers,
        int platformStartY,
        boolean generateBedrock,
        boolean bedrockAtBottom,
        Features features) {

    public static final ResourceLocation DEFAULT_BORDER_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "aqua_glow_plastic");
    public static final ResourceLocation DEFAULT_FILL_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "white_glow_plastic");
    public static final ResourceLocation DEFAULT_CENTER_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "light_gray_glow_plastic");
    public static final ResourceLocation DEFAULT_BOUNDARY_BLOCK_A =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "black_glow_plastic");
    public static final ResourceLocation DEFAULT_BOUNDARY_BLOCK_B =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "yellow_glow_plastic");
    public static final ResourceLocation DEFAULT_ROAD_SURFACE_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "black_plastic_ctm");
    public static final ResourceLocation DEFAULT_ROAD_MARKING_BLOCK =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "yellow_plastic_ctm");

    /** 平台模式：马路、多联。 */
    public enum Mode {
        /** 马路模式：区块之间按边界间隔生成马路。 */
        ROAD,
        /** 多联模式：按边界间隔把相邻区块合并成一个平台。 */
        MULTI;

        public static Mode fromOrdinal(int ordinal) {
            return ordinal == MULTI.ordinal() ? MULTI : ROAD;
        }

        /** 切换到下一个模式：马路与多联之间循环。 */
        public Mode next() {
            return this == ROAD ? MULTI : ROAD;
        }

        /** 按名称解析模式；无法识别（含旧版的纯方块模式）时回落到马路模式。 */
        public static Mode byName(String name) {
            return "MULTI".equalsIgnoreCase(name) ? MULTI : ROAD;
        }
    }

    /** Additional surface feature settings. */
    public record Features(
            ResourceLocation boundaryBlockAId,
            ResourceLocation boundaryBlockBId,
            int boundaryIntervalX,
            int boundaryIntervalZ,
            int roadWidth,
            Mode mode,
            ResourceLocation roadBlockAId,
            ResourceLocation roadBlockBId,
            ResourceLocation roadBlockCId,
            boolean centerMarkerEnabled,
            ResourceLocation centerMarkerBlockId) {

        private static final Codec<Features> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("boundary_block_a", DEFAULT_BOUNDARY_BLOCK_A)
                        .forGetter(Features::boundaryBlockAId),
                ResourceLocation.CODEC.optionalFieldOf("boundary_block_b", DEFAULT_BOUNDARY_BLOCK_B)
                        .forGetter(Features::boundaryBlockBId),
                Codec.INT.optionalFieldOf("boundary_interval_x", 1)
                        .forGetter(Features::boundaryIntervalX),
                Codec.INT.optionalFieldOf("boundary_interval_z", 1)
                        .forGetter(Features::boundaryIntervalZ),
                Codec.INT.optionalFieldOf("road_width", 0)
                        .forGetter(Features::roadWidth),
                Codec.STRING.optionalFieldOf("mode", Mode.ROAD.name())
                        .forGetter(features -> features.mode().name()),
                ResourceLocation.CODEC.optionalFieldOf("road_block_a", DEFAULT_ROAD_SURFACE_BLOCK)
                        .forGetter(Features::roadBlockAId),
                ResourceLocation.CODEC.optionalFieldOf("road_block_b", DEFAULT_ROAD_MARKING_BLOCK)
                        .forGetter(Features::roadBlockBId),
                ResourceLocation.CODEC.optionalFieldOf("road_block_c", DEFAULT_ROAD_MARKING_BLOCK)
                        .forGetter(Features::roadBlockCId),
                Codec.BOOL.optionalFieldOf("center_marker_enabled", false)
                        .forGetter(Features::centerMarkerEnabled),
                ResourceLocation.CODEC.optionalFieldOf("center_marker_block", DEFAULT_CENTER_BLOCK)
                        .forGetter(Features::centerMarkerBlockId)
        ).apply(instance, (boundaryA, boundaryB, intervalX, intervalZ, roadWidth, mode,
                           roadA, roadB, roadC, centerEnabled, marker) ->
                new Features(boundaryA, boundaryB, intervalX, intervalZ, roadWidth,
                        Mode.byName(mode), roadA, roadB, roadC,
                        centerEnabled, marker)));

        public Features {
            boundaryBlockAId = Objects.requireNonNull(boundaryBlockAId);
            boundaryBlockBId = Objects.requireNonNull(boundaryBlockBId);
            mode = Objects.requireNonNull(mode);
            roadBlockAId = Objects.requireNonNull(roadBlockAId);
            roadBlockBId = Objects.requireNonNull(roadBlockBId);
            roadBlockCId = Objects.requireNonNull(roadBlockCId);
            centerMarkerBlockId = Objects.requireNonNull(centerMarkerBlockId);
        }

        private static Features defaults() {
            return new Features(
                    DEFAULT_BOUNDARY_BLOCK_A,
                    DEFAULT_BOUNDARY_BLOCK_B,
                    1,
                    1,
                    0,
                    Mode.ROAD,
                    DEFAULT_ROAD_SURFACE_BLOCK,
                    DEFAULT_ROAD_MARKING_BLOCK,
                    DEFAULT_ROAD_MARKING_BLOCK,
                    false,
                    DEFAULT_CENTER_BLOCK);
        }

        private Features normalized() {
            return new Features(
                    boundaryBlockAId,
                    boundaryBlockBId,
                    Mth.clamp(boundaryIntervalX, 0, 256),
                    Mth.clamp(boundaryIntervalZ, 0, 256),
                    Mth.clamp(roadWidth, 0, 16),
                    mode,
                    roadBlockAId,
                    roadBlockBId,
                    roadBlockCId,
                    centerMarkerEnabled,
                    centerMarkerBlockId);
        }
    }

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
                    .forGetter(DimensionGenerationConfig::bedrockAtBottom),
            Features.CODEC.optionalFieldOf("features", Features.defaults())
                    .forGetter(DimensionGenerationConfig::features)
    ).apply(instance, DimensionGenerationConfig::new));

    /** Keeps callers that used the original seven-field record. */
    public DimensionGenerationConfig(ResourceLocation borderBlockId, ResourceLocation fillBlockId,
                                     ResourceLocation centerBlockId, int platformLayers,
                                     int platformStartY, boolean generateBedrock,
                                     boolean bedrockAtBottom) {
        this(borderBlockId, fillBlockId, centerBlockId, platformLayers, platformStartY,
                generateBedrock, bedrockAtBottom, Features.defaults());
    }

    public DimensionGenerationConfig {
        borderBlockId = Objects.requireNonNull(borderBlockId);
        fillBlockId = Objects.requireNonNull(fillBlockId);
        centerBlockId = Objects.requireNonNull(centerBlockId);
        features = Objects.requireNonNull(features);
    }

    public static DimensionGenerationConfig defaults() {
        return new DimensionGenerationConfig(
                DEFAULT_BORDER_BLOCK,
                DEFAULT_FILL_BLOCK,
                DEFAULT_CENTER_BLOCK,
                69,
                -64,
                true,
                false,
                Features.defaults());
    }
    public ResourceLocation boundaryBlockAId() {
        return features.boundaryBlockAId();
    }

    public ResourceLocation boundaryBlockBId() {
        return features.boundaryBlockBId();
    }

    public int boundaryIntervalX() {
        return features.boundaryIntervalX();
    }

    public int boundaryIntervalZ() {
        return features.boundaryIntervalZ();
    }

    public int roadWidth() {
        return features.roadWidth();
    }

    public Mode mode() {
        return features.mode();
    }

    public ResourceLocation roadBlockAId() {
        return features.roadBlockAId();
    }

    public ResourceLocation roadBlockBId() {
        return features.roadBlockBId();
    }

    public ResourceLocation roadBlockCId() {
        return features.roadBlockCId();
    }

    public boolean centerMarkerEnabled() {
        return features.centerMarkerEnabled();
    }

    public ResourceLocation centerMarkerBlockId() {
        return features.centerMarkerBlockId();
    }

    public boolean isValid() {
        if (platformLayers < 1 || platformLayers > 256
                || platformStartY < -64 || platformStartY > 256) return false;
        if (boundaryIntervalX() < 0 || boundaryIntervalX() > 256
                || boundaryIntervalZ() < 0 || boundaryIntervalZ() > 256
                || roadWidth() < 0 || roadWidth() > 16
                || mode() == null) return false;
        // 多联模式把边界间隔当作合并尺寸，必须大于零。
        if (mode() == Mode.MULTI
                && (boundaryIntervalX() < 1 || boundaryIntervalZ() < 1)) return false;
        return true;
    }

    public boolean hasValidBlockIds() {
        if (!isValidBlockId(borderBlockId) || !isValidBlockId(fillBlockId)
                || !isValidBlockId(centerBlockId)) {
            return false;
        }
        if (hasBoundaryFeatures()
                && (!isValidBlockId(boundaryBlockAId()) || !isValidBlockId(boundaryBlockBId()))) return false;
        if (hasRoadFeatures()
                && (!isValidBlockId(roadBlockAId())
                || (mode() == Mode.ROAD && !isValidBlockId(roadBlockBId()))
                || (mode() == Mode.ROAD && roadWidth() * 16 >= 4
                && !isValidBlockId(roadBlockCId())))) return false;
        return !hasCenterMarkerFeature() || isValidBlockId(centerMarkerBlockId());
    }

    public boolean hasAllowedBlockIds() {
        if (!isAllowedBlockId(borderBlockId) || !isAllowedBlockId(fillBlockId)
                || !isAllowedBlockId(centerBlockId)) {
            return false;
        }
        if (hasBoundaryFeatures()
                && (!isAllowedBlockId(boundaryBlockAId()) || !isAllowedBlockId(boundaryBlockBId()))) return false;
        if (hasRoadFeatures()
                && (!isAllowedBlockId(roadBlockAId())
                || (mode() == Mode.ROAD && !isAllowedBlockId(roadBlockBId()))
                || (mode() == Mode.ROAD && roadWidth() * 16 >= 4
                && !isAllowedBlockId(roadBlockCId())))) return false;
        return !hasCenterMarkerFeature() || isAllowedBlockId(centerMarkerBlockId());
    }

    /**
     * Boundary and road slots are editable only when the generator can actually
     * place the corresponding layout.
     */
    public static boolean areBoundaryAndRoadFeaturesEnabled(
            int boundaryIntervalX, int boundaryIntervalZ, int roadWidth) {
        return roadWidth > 0 && (boundaryIntervalX > 0 || boundaryIntervalZ > 0);
    }

    private boolean hasBoundaryFeatures() {
        // 多联模式不使用边界与道路布局，其方块配置不参与校验。
        if (mode() == Mode.MULTI) return false;
        return areBoundaryAndRoadFeaturesEnabled(boundaryIntervalX(), boundaryIntervalZ(), roadWidth());
    }

    private boolean hasRoadFeatures() {
        if (mode() == Mode.MULTI) return false;
        return areBoundaryAndRoadFeaturesEnabled(boundaryIntervalX(), boundaryIntervalZ(), roadWidth());
    }

    private boolean hasCenterMarkerFeature() {
        // 中心标记只在马路模式生效。
        if (mode() == Mode.MULTI) return false;
        return centerMarkerEnabled() && boundaryIntervalX() > 0 && boundaryIntervalZ() > 0;
    }

    public static boolean isValidBlockId(ResourceLocation id) {
        if (id == null) return false;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block != Blocks.AIR && block.asItem() != Items.AIR;
    }

    public static boolean isAllowedBlockId(ResourceLocation id) {
        return isValidBlockId(id) && ConfigManager.isUselessDimensionFloorBlockAllowed(id);
    }

    public DimensionGenerationConfig normalized() {
        return new DimensionGenerationConfig(
                borderBlockId,
                fillBlockId,
                centerBlockId,
                Mth.clamp(platformLayers, 1, 256),
                Mth.clamp(platformStartY, -64, 256),
                generateBedrock,
                bedrockAtBottom,
                features.normalized());
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

    public Block boundaryBlockA() {
        return resolveBlock(boundaryBlockAId(), Blocks.BLUE_WOOL);
    }

    public Block boundaryBlockB() {
        return resolveBlock(boundaryBlockBId(), Blocks.WHITE_WOOL);
    }

    public Block roadBlockA() {
        return resolveBlock(roadBlockAId(), Blocks.BLACK_WOOL);
    }

    public Block roadBlockB() {
        return resolveBlock(roadBlockBId(), Blocks.YELLOW_WOOL);
    }

    public Block roadBlockC() {
        return resolveBlock(roadBlockCId(), Blocks.YELLOW_WOOL);
    }

    public Block centerMarkerBlock() {
        return resolveBlock(centerMarkerBlockId(), Blocks.GRAY_WOOL);
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

        buffer.writeResourceLocation(boundaryBlockAId());
        buffer.writeResourceLocation(boundaryBlockBId());
        buffer.writeVarInt(boundaryIntervalX());
        buffer.writeVarInt(boundaryIntervalZ());
        buffer.writeVarInt(roadWidth());
        buffer.writeVarInt(mode().ordinal());
        buffer.writeResourceLocation(roadBlockAId());
        buffer.writeResourceLocation(roadBlockBId());
        buffer.writeResourceLocation(roadBlockCId());
        buffer.writeBoolean(centerMarkerEnabled());
        buffer.writeResourceLocation(centerMarkerBlockId());
    }

    public static DimensionGenerationConfig read(FriendlyByteBuf buffer) {
        ResourceLocation border = buffer.readResourceLocation();
        ResourceLocation fill = buffer.readResourceLocation();
        ResourceLocation center = buffer.readResourceLocation();
        int platformLayers = buffer.readVarInt();
        int platformStartY = buffer.readInt();
        boolean generateBedrock = buffer.readBoolean();
        boolean bedrockAtBottom = buffer.readBoolean();

        Features features = new Features(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                Mode.fromOrdinal(buffer.readVarInt()),
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readBoolean(),
                buffer.readResourceLocation());
        return new DimensionGenerationConfig(border, fill, center, platformLayers, platformStartY,
                generateBedrock, bedrockAtBottom, features);
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("border_block", borderBlockId.toString());
        tag.putString("fill_block", fillBlockId.toString());
        tag.putString("center_block", centerBlockId.toString());
        tag.putInt("platform_layers", platformLayers);
        tag.putInt("platform_start_y", platformStartY);
        tag.putBoolean("generate_bedrock", generateBedrock);
        tag.putBoolean("bedrock_at_bottom", bedrockAtBottom);

        CompoundTag featureTag = new CompoundTag();
        featureTag.putString("boundary_block_a", boundaryBlockAId().toString());
        featureTag.putString("boundary_block_b", boundaryBlockBId().toString());
        featureTag.putInt("boundary_interval_x", boundaryIntervalX());
        featureTag.putInt("boundary_interval_z", boundaryIntervalZ());
        featureTag.putInt("road_width", roadWidth());
        featureTag.putString("mode", mode().name());
        featureTag.putString("road_block_a", roadBlockAId().toString());
        featureTag.putString("road_block_b", roadBlockBId().toString());
        featureTag.putString("road_block_c", roadBlockCId().toString());
        featureTag.putBoolean("center_marker_enabled", centerMarkerEnabled());
        featureTag.putString("center_marker_block", centerMarkerBlockId().toString());
        tag.put("features", featureTag);
        return tag;
    }

    public static DimensionGenerationConfig load(CompoundTag tag) {
        DimensionGenerationConfig defaults = defaults();
        Features features = Features.defaults();
        if (tag.contains("features")) {
            CompoundTag featureTag = tag.getCompound("features");
            features = new Features(
                    readId(featureTag, "boundary_block_a", features.boundaryBlockAId()),
                    readId(featureTag, "boundary_block_b", features.boundaryBlockBId()),
                    featureTag.contains("boundary_interval_x")
                            ? featureTag.getInt("boundary_interval_x") : features.boundaryIntervalX(),
                    featureTag.contains("boundary_interval_z")
                            ? featureTag.getInt("boundary_interval_z") : features.boundaryIntervalZ(),
                    featureTag.contains("road_width") ? featureTag.getInt("road_width") : features.roadWidth(),
                    Mode.byName(featureTag.contains("mode")
                            ? featureTag.getString("mode") : features.mode().name()),
                    readId(featureTag, "road_block_a", features.roadBlockAId()),
                    readId(featureTag, "road_block_b", features.roadBlockBId()),
                    readId(featureTag, "road_block_c", features.roadBlockCId()),
                    featureTag.contains("center_marker_enabled")
                            && featureTag.getBoolean("center_marker_enabled"),
                    readId(featureTag, "center_marker_block", features.centerMarkerBlockId()));
        }
        return new DimensionGenerationConfig(
                readId(tag, "border_block", defaults.borderBlockId),
                readId(tag, "fill_block", defaults.fillBlockId),
                readId(tag, "center_block", defaults.centerBlockId),
                tag.contains("platform_layers") ? tag.getInt("platform_layers") : defaults.platformLayers,
                tag.contains("platform_start_y") ? tag.getInt("platform_start_y") : defaults.platformStartY,
                tag.contains("generate_bedrock") ? tag.getBoolean("generate_bedrock") : defaults.generateBedrock,
                tag.contains("bedrock_at_bottom") ? tag.getBoolean("bedrock_at_bottom") : defaults.bedrockAtBottom,
                features).normalized();
    }

    /* ================= 预设导入导出 ================= */

    /** 预设 JSON 的格式版本；导入时版本不匹配即拒绝。 */
    private static final int PRESET_VERSION = 1;
    /** 预设文本的最大字节数，避免超长内容进入剪贴板与网络包。 */
    public static final int MAX_PRESET_BYTES = 8192;

    /** 把当前配置序列化成可粘贴分享的 JSON 文本。 */
    public String toPresetJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", PRESET_VERSION);
        root.addProperty("borderBlock", borderBlockId.toString());
        root.addProperty("fillBlock", fillBlockId.toString());
        root.addProperty("centerBlock", centerBlockId.toString());
        root.addProperty("platformLayers", platformLayers);
        root.addProperty("platformStartY", platformStartY);
        root.addProperty("generateBedrock", generateBedrock);
        root.addProperty("bedrockAtBottom", bedrockAtBottom);
        root.addProperty("boundaryBlockA", boundaryBlockAId().toString());
        root.addProperty("boundaryBlockB", boundaryBlockBId().toString());
        root.addProperty("boundaryIntervalX", boundaryIntervalX());
        root.addProperty("boundaryIntervalZ", boundaryIntervalZ());
        root.addProperty("roadWidth", roadWidth());
        root.addProperty("mode", mode().name());
        root.addProperty("roadBlockA", roadBlockAId().toString());
        root.addProperty("roadBlockB", roadBlockBId().toString());
        root.addProperty("roadBlockC", roadBlockCId().toString());
        root.addProperty("centerMarkerEnabled", centerMarkerEnabled());
        root.addProperty("centerMarkerBlock", centerMarkerBlockId().toString());
        return root.toString();
    }

    /**
     * 解析导入的预设文本。任何结构缺失、类型不符或方块被禁用都会抛出异常，
     * 由调用方转成界面提示，而不是静默套用一份残缺配置。
     */
    public static DimensionGenerationConfig fromPresetJson(String text) throws PresetException {
        if (text == null || text.isBlank()) {
            throw new PresetException(PresetError.INVALID_TEXT);
        }
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PRESET_BYTES) {
            throw new PresetException(PresetError.LIMIT);
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new PresetException(PresetError.INVALID_TEXT);
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("version")) {
                throw new PresetException(PresetError.INVALID_STRUCTURE);
            }
            if (root.get("version").getAsInt() != PRESET_VERSION) {
                throw new PresetException(PresetError.UNSUPPORTED_VERSION);
            }

            Features features = new Features(
                    readPresetId(root, "boundaryBlockA"),
                    readPresetId(root, "boundaryBlockB"),
                    readPresetInt(root, "boundaryIntervalX"),
                    readPresetInt(root, "boundaryIntervalZ"),
                    readPresetInt(root, "roadWidth"),
                    Mode.byName(readPresetString(root, "mode")),
                    readPresetId(root, "roadBlockA"),
                    readPresetId(root, "roadBlockB"),
                    readPresetId(root, "roadBlockC"),
                    readPresetBool(root, "centerMarkerEnabled"),
                    readPresetId(root, "centerMarkerBlock"));

            DimensionGenerationConfig config = new DimensionGenerationConfig(
                    readPresetId(root, "borderBlock"),
                    readPresetId(root, "fillBlock"),
                    readPresetId(root, "centerBlock"),
                    readPresetInt(root, "platformLayers"),
                    readPresetInt(root, "platformStartY"),
                    readPresetBool(root, "generateBedrock"),
                    readPresetBool(root, "bedrockAtBottom"),
                    features).normalized();

            if (!config.isValid()) {
                throw new PresetException(PresetError.INVALID_STRUCTURE);
            }
            if (!config.hasAllowedBlockIds()) {
                throw new PresetException(PresetError.BLOCKED_BLOCK);
            }
            return config;
        } catch (PresetException exception) {
            throw exception;
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException
                 | ClassCastException | NumberFormatException exception) {
            throw new PresetException(PresetError.INVALID_TEXT);
        }
    }

    private static String readPresetString(JsonObject root, String key) throws PresetException {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PresetException(PresetError.INVALID_STRUCTURE);
        }
        return value.getAsString();
    }

    private static int readPresetInt(JsonObject root, String key) throws PresetException {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PresetException(PresetError.INVALID_STRUCTURE);
        }
        return value.getAsInt();
    }

    private static boolean readPresetBool(JsonObject root, String key) throws PresetException {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new PresetException(PresetError.INVALID_STRUCTURE);
        }
        return value.getAsBoolean();
    }

    private static ResourceLocation readPresetId(JsonObject root, String key) throws PresetException {
        ResourceLocation id = ResourceLocation.tryParse(readPresetString(root, key));
        if (id == null) {
            throw new PresetException(PresetError.INVALID_STRUCTURE);
        }
        return id;
    }

    /** 预设导入的失败原因，用于映射到界面提示。 */
    public enum PresetError {
        INVALID_TEXT,
        UNSUPPORTED_VERSION,
        INVALID_STRUCTURE,
        BLOCKED_BLOCK,
        LIMIT
    }

    public static final class PresetException extends Exception {
        private final PresetError error;

        public PresetException(PresetError error) {
            super(error.name());
            this.error = error;
        }

        public PresetError error() {
            return error;
        }
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
