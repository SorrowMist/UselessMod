package com.sorrowmist.useless.compat.forge1201.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.UUID;

public final class PortCodecs {
    private PortCodecs() {}
    public static final StreamCodec<FriendlyByteBuf,BlockPos> BLOCK_POS=StreamCodec.of(FriendlyByteBuf::writeBlockPos,FriendlyByteBuf::readBlockPos);
    public static final StreamCodec<FriendlyByteBuf,ResourceLocation> RESOURCE_LOCATION=StreamCodec.of(FriendlyByteBuf::writeResourceLocation,FriendlyByteBuf::readResourceLocation);
    public static final StreamCodec<io.netty.buffer.ByteBuf,UUID> UUID_CODEC=StreamCodec.of((buf,id)->{buf.writeLong(id.getMostSignificantBits());buf.writeLong(id.getLeastSignificantBits());},buf->new UUID(buf.readLong(),buf.readLong()));
    public static final StreamCodec<FriendlyByteBuf,ItemStack> ITEM_STACK_OPTIONAL=StreamCodec.of((buf,stack) -> {
        buf.writeBoolean(!stack.isEmpty());
        if(!stack.isEmpty()) {
            // Native writeItem/save encode count as a byte; machine recipe outputs can exceed 127.
            buf.writeVarInt(stack.getCount());
            var tag=stack.serializeNBT(); tag.putByte("Count",(byte)1); buf.writeNbt(tag);
        }
    },buf -> {
        if(!buf.readBoolean()) return ItemStack.EMPTY;
        int count=buf.readVarInt();
        if(count<=0) throw new IllegalArgumentException("Invalid item count: "+count);
        var tag=buf.readNbt();
        if(tag==null) throw new IllegalArgumentException("Missing item NBT");
        var stack=ItemStack.of(tag);
        if(stack.isEmpty()) throw new IllegalArgumentException("Invalid or unknown item in payload");
        stack.setCount(count); return stack;
    });
    public static final StreamCodec<FriendlyByteBuf,ItemStack> ITEM_STACK=ITEM_STACK_OPTIONAL;
    public static final StreamCodec<FriendlyByteBuf,FluidStack> FLUID_STACK=StreamCodec.of((b,v)->v.writeToPacket(b),FluidStack::readFromPacket);
    public static final StreamCodec<FriendlyByteBuf,GlobalPos> GLOBAL_POS=ByteBufCodecs.fromCodec(GlobalPos.CODEC);
}
