package com.sorrowmist.useless.compat.forge1201.recipe;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** AE2 15's native key/stack formats include third-party key types and long quantities. */
public final class AE2Codecs {
    private AE2Codecs() {}
    public static final StreamCodec<FriendlyByteBuf, GenericStack> GENERIC_STACK_STREAM = StreamCodec.of(
            (buf, value) -> GenericStack.writeBuffer(value, buf), GenericStack::readBuffer);
    public static final Codec<GenericStack> GENERIC_STACK = CompoundTag.CODEC.comapFlatMap(tag -> {
        try {
            GenericStack value = GenericStack.readTag(tag);
            return value == null ? DataResult.error(() -> "Unknown AE2 stack/key type: " + tag) : DataResult.success(value);
        } catch (RuntimeException exception) { return DataResult.error(() -> "Invalid AE2 stack: " + exception.getMessage()); }
    }, GenericStack::writeTag);
    public static final Codec<AEKey> AE_KEY = CompoundTag.CODEC.comapFlatMap(tag -> {
        try {
            AEKey value = AEKey.fromTagGeneric(tag);
            return value == null ? DataResult.error(() -> "Unknown AE2 key type: " + tag) : DataResult.success(value);
        } catch (RuntimeException exception) { return DataResult.error(() -> "Invalid AE2 key: " + exception.getMessage()); }
    }, AEKey::toTagGeneric);
    public static final Codec<appeng.api.stacks.AEItemKey> ITEM_KEY = AE_KEY.comapFlatMap(
            key -> key instanceof appeng.api.stacks.AEItemKey item ? DataResult.success(item) : DataResult.error(() -> "Expected an AE2 item key"),
            item -> item);
}
