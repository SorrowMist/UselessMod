package com.sorrowmist.useless.core.component;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

public record OmniversalPatternData(
        int version,
        ResourceLocation recipeId,
        String recipeFingerprint,
        boolean requiresMold,
        Optional<AEItemKey> displayMold,
        List<AEItemKey> displayMolds,
        List<TagInputSlot> tagInputSlots,
        List<FluidTagInputSlot> fluidTagInputSlots,
        List<MoldTagInputSlot> moldTagInputSlots,
        List<Integer> itemIdInputSlots,
        List<Integer> itemIdOutputSlots) {
    private static final int LEGACY_DEFAULT_VERSION = 1;
    public static final int SEMANTIC_FINGERPRINT_VERSION = 2;
    public static final int MULTI_MOLD_VERSION = 3;
    public static final int TAG_INPUT_VERSION = 4;
    public static final int FLUID_TAG_INPUT_VERSION = 5;
    public static final int MOLD_TAG_INPUT_VERSION = 6;
    public static final int CURRENT_VERSION = MOLD_TAG_INPUT_VERSION;

    public record TagInputSlot(int slot, TagKey<Item> tag) {
        public static final Codec<TagInputSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(TagInputSlot::slot),
                TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(TagInputSlot::tag)
        ).apply(instance, TagInputSlot::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, TagInputSlot> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, TagInputSlot::slot,
                        ByteBufCodecs.fromCodecWithRegistries(TagKey.codec(Registries.ITEM)), TagInputSlot::tag,
                        TagInputSlot::new);

        public TagInputSlot {
            if (slot < 0) {
                throw new IllegalArgumentException("Tag input slot cannot be negative: " + slot);
            }
            if (tag == null) {
                throw new NullPointerException("tag");
            }
        }
    }

    public record FluidTagInputSlot(int slot, TagKey<Fluid> tag) {
        public static final Codec<FluidTagInputSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(FluidTagInputSlot::slot),
                TagKey.codec(Registries.FLUID).fieldOf("tag").forGetter(FluidTagInputSlot::tag)
        ).apply(instance, FluidTagInputSlot::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FluidTagInputSlot> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, FluidTagInputSlot::slot,
                        ByteBufCodecs.fromCodecWithRegistries(TagKey.codec(Registries.FLUID)), FluidTagInputSlot::tag,
                        FluidTagInputSlot::new);

        public FluidTagInputSlot {
            if (slot < 0) {
                throw new IllegalArgumentException("Fluid tag input slot cannot be negative: " + slot);
            }
            if (tag == null) {
                throw new NullPointerException("tag");
            }
        }
    }

    /** A tag-backed mold requirement, indexed by the independent mold slot in the recipe. */
    public record MoldTagInputSlot(int moldSlot, TagKey<Item> tag) {
        public static final Codec<MoldTagInputSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("mold_slot").forGetter(MoldTagInputSlot::moldSlot),
                TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(MoldTagInputSlot::tag)
        ).apply(instance, MoldTagInputSlot::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MoldTagInputSlot> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, MoldTagInputSlot::moldSlot,
                        ByteBufCodecs.fromCodecWithRegistries(TagKey.codec(Registries.ITEM)), MoldTagInputSlot::tag,
                        MoldTagInputSlot::new);

        public MoldTagInputSlot {
            if (moldSlot < 0) {
                throw new IllegalArgumentException("Mold tag input slot cannot be negative: " + moldSlot);
            }
            if (tag == null) {
                throw new NullPointerException("tag");
            }
        }
    }

    public static final Codec<OmniversalPatternData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Version one was omitted by its original codec, so its decode
            // default must remain stable after introducing newer versions.
            Codec.INT.optionalFieldOf("version", LEGACY_DEFAULT_VERSION).forGetter(OmniversalPatternData::version),
            ResourceLocation.CODEC.fieldOf("recipe_id").forGetter(OmniversalPatternData::recipeId),
            Codec.STRING.fieldOf("recipe_fingerprint").forGetter(OmniversalPatternData::recipeFingerprint),
            Codec.BOOL.optionalFieldOf("requires_mold", false).forGetter(OmniversalPatternData::requiresMold),
            AEItemKey.CODEC.optionalFieldOf("display_mold").forGetter(OmniversalPatternData::displayMold),
            AEItemKey.CODEC.listOf().optionalFieldOf("display_molds", List.of()).forGetter(OmniversalPatternData::displayMolds),
            TagInputSlot.CODEC.listOf().optionalFieldOf("tag_inputs", List.of()).forGetter(OmniversalPatternData::tagInputSlots),
            FluidTagInputSlot.CODEC.listOf().optionalFieldOf("fluid_tag_inputs", List.of()).forGetter(OmniversalPatternData::fluidTagInputSlots),
            MoldTagInputSlot.CODEC.listOf().optionalFieldOf("mold_tag_inputs", List.of()).forGetter(OmniversalPatternData::moldTagInputSlots),
            Codec.INT.listOf().optionalFieldOf("item_id_inputs", List.of()).forGetter(OmniversalPatternData::itemIdInputSlots),
            Codec.INT.listOf().optionalFieldOf("item_id_outputs", List.of()).forGetter(OmniversalPatternData::itemIdOutputSlots)
    ).apply(instance, OmniversalPatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OmniversalPatternData> STREAM_CODEC = StreamCodec.of(
            OmniversalPatternData::write,
            OmniversalPatternData::read);

    public OmniversalPatternData {
        displayMold = displayMold == null ? Optional.empty() : displayMold;
        displayMolds = List.copyOf(displayMolds == null ? List.of() : displayMolds);
        tagInputSlots = List.copyOf(tagInputSlots == null ? List.of() : tagInputSlots);
        fluidTagInputSlots = List.copyOf(fluidTagInputSlots == null ? List.of() : fluidTagInputSlots);
        moldTagInputSlots = List.copyOf(moldTagInputSlots == null ? List.of() : moldTagInputSlots);
        itemIdInputSlots = List.copyOf(itemIdInputSlots == null ? List.of() : itemIdInputSlots);
        itemIdOutputSlots = List.copyOf(itemIdOutputSlots == null ? List.of() : itemIdOutputSlots);
    }

    /** Compatibility constructor for metadata written before tag input slots were introduced. */
    public OmniversalPatternData(
            int version,
            ResourceLocation recipeId,
            String recipeFingerprint,
            boolean requiresMold,
            Optional<AEItemKey> displayMold,
            List<AEItemKey> displayMolds,
            List<Integer> itemIdInputSlots,
            List<Integer> itemIdOutputSlots) {
        this(version, recipeId, recipeFingerprint, requiresMold, displayMold, displayMolds,
                List.of(), List.of(), List.of(), itemIdInputSlots, itemIdOutputSlots);
    }

    /** Compatibility constructor for metadata written after item tags but before fluid tags. */
    public OmniversalPatternData(
            int version,
            ResourceLocation recipeId,
            String recipeFingerprint,
            boolean requiresMold,
            Optional<AEItemKey> displayMold,
            List<AEItemKey> displayMolds,
            List<TagInputSlot> tagInputSlots,
            List<Integer> itemIdInputSlots,
            List<Integer> itemIdOutputSlots) {
        this(version, recipeId, recipeFingerprint, requiresMold, displayMold, displayMolds,
                tagInputSlots, List.of(), List.of(), itemIdInputSlots, itemIdOutputSlots);
    }

    /** Compatibility constructor for metadata written after fluid tags but before mold tags. */
    public OmniversalPatternData(
            int version,
            ResourceLocation recipeId,
            String recipeFingerprint,
            boolean requiresMold,
            Optional<AEItemKey> displayMold,
            List<AEItemKey> displayMolds,
            List<TagInputSlot> tagInputSlots,
            List<FluidTagInputSlot> fluidTagInputSlots,
            List<Integer> itemIdInputSlots,
            List<Integer> itemIdOutputSlots) {
        this(version, recipeId, recipeFingerprint, requiresMold, displayMold, displayMolds,
                tagInputSlots, fluidTagInputSlots, List.of(), itemIdInputSlots, itemIdOutputSlots);
    }

    /** Compatibility constructor for metadata written before display_molds was introduced. */
    public OmniversalPatternData(
            int version,
            ResourceLocation recipeId,
            String recipeFingerprint,
            boolean requiresMold,
            Optional<AEItemKey> displayMold,
            List<Integer> itemIdInputSlots,
            List<Integer> itemIdOutputSlots) {
        this(version, recipeId, recipeFingerprint, requiresMold, displayMold,
                List.of(), List.of(), List.of(),
                itemIdInputSlots, itemIdOutputSlots);
    }

    public AlloyFurnaceRecipeIdentity identity() {
        return new AlloyFurnaceRecipeIdentity(recipeId, recipeFingerprint);
    }

    private static void write(RegistryFriendlyByteBuf buffer, OmniversalPatternData data) {
        buffer.writeVarInt(data.version);
        ResourceLocation.STREAM_CODEC.encode(buffer, data.recipeId);
        buffer.writeUtf(data.recipeFingerprint);
        buffer.writeBoolean(data.requiresMold);
        buffer.writeBoolean(data.displayMold.isPresent());
        data.displayMold.ifPresent(mold -> mold.writeToPacket(buffer));
        if (data.version >= MULTI_MOLD_VERSION) {
            writeMolds(buffer, data.displayMolds);
        }
        if (data.version >= TAG_INPUT_VERSION) {
            writeTagInputs(buffer, data.tagInputSlots);
        }
        if (data.version >= FLUID_TAG_INPUT_VERSION) {
            writeFluidTagInputs(buffer, data.fluidTagInputSlots);
        }
        if (data.version >= MOLD_TAG_INPUT_VERSION) {
            writeMoldTagInputs(buffer, data.moldTagInputSlots);
        }
        writeInts(buffer, data.itemIdInputSlots);
        writeInts(buffer, data.itemIdOutputSlots);
    }

    private static OmniversalPatternData read(RegistryFriendlyByteBuf buffer) {
        int version = buffer.readVarInt();
        ResourceLocation recipeId = ResourceLocation.STREAM_CODEC.decode(buffer);
        String fingerprint = buffer.readUtf();
        boolean requiresMold = buffer.readBoolean();
        Optional<AEItemKey> displayMold = buffer.readBoolean()
                ? Optional.of(AEItemKey.fromPacket(buffer))
                : Optional.empty();
        List<AEItemKey> displayMolds = version >= MULTI_MOLD_VERSION
                ? readMolds(buffer) : List.of();
        List<TagInputSlot> tagInputSlots = version >= TAG_INPUT_VERSION
                ? readTagInputs(buffer) : List.of();
        List<FluidTagInputSlot> fluidTagInputSlots = version >= FLUID_TAG_INPUT_VERSION
                ? readFluidTagInputs(buffer) : List.of();
        List<MoldTagInputSlot> moldTagInputSlots = version >= MOLD_TAG_INPUT_VERSION
                ? readMoldTagInputs(buffer) : List.of();
        return new OmniversalPatternData(
                version, recipeId, fingerprint, requiresMold, displayMold, displayMolds,
                tagInputSlots, fluidTagInputSlots, moldTagInputSlots, readInts(buffer), readInts(buffer));
    }

    private static void writeMolds(RegistryFriendlyByteBuf buffer, List<AEItemKey> molds) {
        buffer.writeVarInt(molds.size());
        for (AEItemKey mold : molds) mold.writeToPacket(buffer);
    }

    private static List<AEItemKey> readMolds(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new DecoderException("Omniversal pattern mold list exceeds 256 entries");
        }
        java.util.ArrayList<AEItemKey> result = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(AEItemKey.fromPacket(buffer));
        return List.copyOf(result);
    }

    private static void writeTagInputs(RegistryFriendlyByteBuf buffer, List<TagInputSlot> slots) {
        buffer.writeVarInt(slots.size());
        for (TagInputSlot slot : slots) TagInputSlot.STREAM_CODEC.encode(buffer, slot);
    }

    private static List<TagInputSlot> readTagInputs(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new DecoderException("Omniversal pattern tag input list exceeds 256 entries");
        }
        java.util.ArrayList<TagInputSlot> result = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(TagInputSlot.STREAM_CODEC.decode(buffer));
        return List.copyOf(result);
    }

    private static void writeFluidTagInputs(RegistryFriendlyByteBuf buffer, List<FluidTagInputSlot> slots) {
        buffer.writeVarInt(slots.size());
        for (FluidTagInputSlot slot : slots) FluidTagInputSlot.STREAM_CODEC.encode(buffer, slot);
    }

    private static List<FluidTagInputSlot> readFluidTagInputs(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new DecoderException("Omniversal pattern fluid tag input list exceeds 256 entries");
        }
        java.util.ArrayList<FluidTagInputSlot> result = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(FluidTagInputSlot.STREAM_CODEC.decode(buffer));
        return List.copyOf(result);
    }

    private static void writeMoldTagInputs(RegistryFriendlyByteBuf buffer, List<MoldTagInputSlot> slots) {
        buffer.writeVarInt(slots.size());
        for (MoldTagInputSlot slot : slots) MoldTagInputSlot.STREAM_CODEC.encode(buffer, slot);
    }

    private static List<MoldTagInputSlot> readMoldTagInputs(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new DecoderException("Omniversal pattern mold tag list exceeds 256 entries");
        }
        java.util.ArrayList<MoldTagInputSlot> result = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(MoldTagInputSlot.STREAM_CODEC.decode(buffer));
        return List.copyOf(result);
    }

    private static void writeInts(RegistryFriendlyByteBuf buffer, List<Integer> values) {
        buffer.writeVarInt(values.size());
        for (int value : values) buffer.writeVarInt(value);
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new DecoderException("Omniversal pattern slot list exceeds 256 entries");
        }
        java.util.ArrayList<Integer> result = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(buffer.readVarInt());
        return List.copyOf(result);
    }
}
