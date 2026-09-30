package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.config.BeefToolProtectionManager;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.data.BeefToolLayoutManager;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Ctrl / Shift + 右键生物：切换生物保护名单。
 *
 * <p>客户端只上报「实体 id + 是否按种类」；目标解析、距离与前置条件校验全部在服务端完成，
 * 避免被构造包写入任意实体。</p>
 *
 * <p>为什么不在 {@code PlayerInteractEvent.EntityInteract} 里做：Ctrl 不会被
 * {@code ServerboundInteractPacket} 传输（它只带 {@code isShiftKeyDown()}），
 * 服务端根本无法区分 Ctrl 与普通右键；而且原版交互（村民交易 GUI、剪羊毛）会在事件之后执行。
 * 客户端改在 {@code InputEvent.MouseButton.Pre} 拦截，取消后连交互包都不会发出。</p>
 */
public record ProtectEntityPacket(int entityId, boolean byType) implements CustomPacketPayload {

    public static final Type<ProtectEntityPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "protect_entity"));

    public static final StreamCodec<FriendlyByteBuf, ProtectEntityPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.entityId);
                buffer.writeBoolean(packet.byType);
            },
            buffer -> new ProtectEntityPacket(buffer.readVarInt(), buffer.readBoolean()));

    public static void handle(ProtectEntityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }

            // 1) 必须手持造化杖
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) {
                return;
            }
            ItemStack stack = toolEntry.get().getKey();

            // 2) 杀戮光环、范围伤害或保护名单模式至少开一个
            if (!EndlessBeafItem.isProtectGestureAvailable(stack)) {
                return;
            }

            // 3) 目标存在、是生物、不是玩家自己、在可交互距离内
            Entity raw = level.getEntity(packet.entityId());
            if (raw == null) {
                return;
            }
            Entity target = EndlessBeafItem.unwrapPartEntity(raw);
            if (!(target instanceof LivingEntity living) || living instanceof Player) {
                return;
            }
            if (living == player || !player.canInteractWithEntity(living, 2.0)) {
                return;
            }

            // 4) 切换名单条目
            BeefToolLayout layout = BeefToolLayoutManager.loadOrCreate(player);
            boolean byType = packet.byType();
            List<String> list = byType ? layout.protectedTypes() : layout.protectedEntities();
            int max = byType ? BeefToolLayout.MAX_PROTECTED_TYPES
                    : BeefToolLayout.MAX_PROTECTED_ENTITIES;
            String key = byType ? EndlessBeafItem.getEntityId(living) : living.getUUID().toString();

            boolean added;
            if (list.contains(key)) {
                list.remove(key);
                added = false;
            } else {
                if (list.size() >= max) {
                    return;
                }
                list.add(key);
                added = true;
            }

            try {
                BeefToolLayoutManager.validateForSave(layout);
            } catch (BeefToolLayout.LayoutException exception) {
                return;
            }
            BeefToolLayoutManager.save(player, layout);
            BeefToolProtectionManager.invalidate(player.getUUID());
            PacketDistributor.sendToPlayer(player, new BeefToolLayoutSyncPacket(layout.toJson()));

            // 5) 反馈：动作栏消息 + 提示音（沿用 StaffLinkBinding 的紫水晶 chime/break 范式）
            String messageKey = byType
                    ? (added ? "gui.useless_mod.beef_protect.add_type"
                             : "gui.useless_mod.beef_protect.remove_type")
                    : (added ? "gui.useless_mod.beef_protect.add_entity"
                             : "gui.useless_mod.beef_protect.remove_entity");
            Component name = byType ? living.getType().getDescription() : living.getDisplayName();
            player.displayClientMessage(Component.translatable(messageKey, name), true);
            level.playSound(null, player.blockPosition(),
                    added ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 0.7F, added ? 1.4F : 0.8F);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
