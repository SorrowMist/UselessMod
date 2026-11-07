package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.forge1201.OptionalIntegration;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端在魔典预览的五芒星上右键后，将「要摆放哪座仪式」上报给服务端。
 *
 * <p>这三个字段均不可信，不代表玩家确实拥有预览或放置权限。兼容入口必须按服务端的
 * 仪式数据、射线、距离、区块、手持工具及 Forge 事件重新验证，之后才能取料。</p>
 */
public class RitualSatchelPlacePacket implements CustomPacketPayload {

    public static final Type<RitualSatchelPlacePacket> TYPE = new Type<>(
            new ResourceLocation(UselessMod.MODID, "ritual_satchel_place"));
    public static final StreamCodec<FriendlyByteBuf, RitualSatchelPlacePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeResourceLocation(pkt.multiblockId);
                buf.writeBlockPos(pkt.anchor);
                buf.writeEnum(pkt.facing);
            },
            buf -> new RitualSatchelPlacePacket(
                    buf.readResourceLocation(), buf.readBlockPos(), buf.readEnum(Rotation.class))
    );

    private final ResourceLocation multiblockId;
    private final BlockPos anchor;
    private final Rotation facing;

    public RitualSatchelPlacePacket(ResourceLocation multiblockId, BlockPos anchor, Rotation facing) {
        this.multiblockId = java.util.Objects.requireNonNull(multiblockId);
        this.anchor = java.util.Objects.requireNonNull(anchor).immutable();
        this.facing = java.util.Objects.requireNonNull(facing);
    }

    public static void handle(RitualSatchelPlacePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                    || !player.mayBuild() || player.isShiftKeyDown()) return;
            // 两个可选模组都存在才解析兼容类；客户端预览不能授权服务端操作。
            if (!ModList.get().isLoaded("occultism") || !ModList.get().isLoaded("modonomicon")) return;

            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) return;

            ItemStack stack = toolEntry.get().getKey();
            if (stack.isEmpty() || player.getItemInHand(toolEntry.get().getValue()) != stack
                    || !(stack.getItem() instanceof EndlessBeafItem)) return;
            if (!ComponentAccess.getOrDefault(stack, UComponents.BeefRitualSatchelComponent.get(), false)) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            OptionalIntegration.call("occultism",
                    "com.sorrowmist.useless.compat.occultism.RitualSatchelCompat","placeFromAe",Void.class,null,
                    new Class<?>[]{ServerLevel.class,ServerPlayer.class,ItemStack.class,ResourceLocation.class,BlockPos.class,Rotation.class},
                    serverLevel,player,stack,msg.multiblockId,msg.anchor,msg.facing);

            player.containerMenu.broadcastChanges();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
