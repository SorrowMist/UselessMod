package com.sorrowmist.useless.core.component;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record OmniversalPatternData(
        int version,
        ResourceLocation recipeId,
        String recipeFingerprint,
        boolean requiresMold,
        Optional<AEItemKey> displayMold,
        List<Integer> itemIdInputSlots,
        List<Integer> itemIdOutputSlots) {
    private static final int LEGACY_DEFAULT_VERSION = 1;
    public static final int SEMANTIC_FINGERPRINT_VERSION = 2;
    public static final int CURRENT_VERSION = SEMANTIC_FINGERPRINT_VERSION;

    public static final Codec<OmniversalPatternData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Version one was omitted by its original codec, so its decode
            // default must remain stable after introducing newer versions.
            Codec.INT.optionalFieldOf("version", LEGACY_DEFAULT_VERSION).forGetter(OmniversalPatternData::version),
            ResourceLocation.CODEC.fieldOf("recipe_id").forGetter(OmniversalPatternData::recipeId),
            Codec.STRING.fieldOf("recipe_fingerprint").forGetter(OmniversalPatternData::recipeFingerprint),
            Codec.BOOL.optionalFieldOf("requires_mold", false).forGetter(OmniversalPatternData::requiresMold),
            AEItemKey.CODEC.optionalFieldOf("display_mold").forGetter(OmniversalPatternData::displayMold),
            Codec.INT.listOf().optionalFieldOf("item_id_inputs", List.of()).forGetter(OmniversalPatternData::itemIdInputSlots),
            Codec.INT.listOf().optionalFieldOf("item_id_outputs", List.of()).forGetter(OmniversalPatternData::itemIdOutputSlots)
    ).apply(instance, OmniversalPatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OmniversalPatternData> STREAM_CODEC = StreamCodec.of(
            OmniversalPatternData::write,
            OmniversalPatternData::read);

    public OmniversalPatternData {
        displayMold = displayMold == null ? Optional.empty() : displayMold;
        itemIdInputSlots = List.copyOf(itemIdInputSlots == null ? List.of() : itemIdInputSlots);
        itemIdOutputSlots = List.copyOf(itemIdOutputSlots == null ? List.of() : itemIdOutputSlots);
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
        return new OmniversalPatternData(
                version, recipeId, fingerprint, requiresMold, displayMold, readInts(buffer), readInts(buffer));
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
