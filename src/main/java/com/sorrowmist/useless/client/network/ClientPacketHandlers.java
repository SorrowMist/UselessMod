package com.sorrowmist.useless.client.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.gui.ModeWheelScreen;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import com.sorrowmist.useless.network.BeefInvulnerabilitySyncPacket;
import com.sorrowmist.useless.network.BeefToolLayoutResultPacket;
import com.sorrowmist.useless.network.BeefToolLayoutSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端专用的载荷（Packet）处理逻辑。
 *
 * <p>所有会直接引用 {@code net.minecraft.client.*} 的代码都必须放在这里，而不能留在
 * {@code com.sorrowmist.useless.network} 的公共载荷类中：在专用服务器（DEDICATED_SERVER）上，
 * 那些类会在 {@code RegisterPayloadHandlersEvent} 阶段被加载，JVM 校验器随后会尝试解析
 * 客户端类型（例如 {@code Minecraft.player} 的字段类型 {@code LocalPlayer}），
 * 触发 RuntimeDistCleaner 抛出
 * “Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER”
 * 并导致启动崩溃。载荷类只保留“分发检查 + 委托”，客户端类只会在客户端真正执行时被加载。
 *
 * <p>注意：这里的静态方法签名中不要出现客户端类型（参数/返回值），否则校验器在服务端
 * 仍需加载它们。参数一律使用公共的载荷类型。
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    /** 同步被保护玩家的血量与状态（吃掉死亡/无敌帧，保持存活表现）。 */
    public static void handleBeefInvulnerabilitySync(BeefInvulnerabilitySyncPacket msg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!Float.isFinite(msg.health()) || msg.health() <= 0.0F) {
            UselessMod.LOGGER.warn("Ignoring invalid protected-player health sync: entityId={}, health={}",
                    msg.entityId(), msg.health());
            return;
        }

        Player player = null;
        if (minecraft.player != null && minecraft.player.getId() == msg.entityId()) {
            player = minecraft.player;
        } else {
            Entity entity = minecraft.level.getEntity(msg.entityId());
            if (entity instanceof Player target) {
                player = target;
            }
        }
        if (player == null) {
            UselessMod.LOGGER.debug("Could not resolve protected player for health sync: entityId={}", msg.entityId());
            return;
        }

        player.dead = false;
        player.deathTime = 0;
        player.hurtTime = 0;
        player.hurtDuration = 0;
        player.setHealth(msg.health());
        player.setPose(Pose.STANDING);
        player.clearFire();
        player.fallDistance = 0.0F;
    }

    /** 把 AE 任务进度刷新到当前打开的高级合金炉界面。 */
    public static void handleAETaskProgress(AETaskProgressPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null
                && mc.player.containerMenu instanceof MultiblockAlloyFurnaceMenu menu
                && menu.getBlockPos().equals(msg.pos())) {
            menu.updateTaskProgress(msg.tasks());
        }
        if (mc.level != null) {
            var blockEntity = mc.level.getBlockEntity(msg.pos());
            if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                furnace.updateClientTaskProgress(msg.tasks());
            } else if (blockEntity instanceof MultiblockAlloyFurnaceCoreBlockEntity furnace) {
                furnace.updateClientTaskProgress(msg.tasks());
            }
        }
    }

    /** 把服务端校验后的工具配置同步到模式轮盘界面。 */
    public static void handleBeefToolLayoutSync(BeefToolLayoutSyncPacket packet) {
        try {
            BeefToolLayout layout = BeefToolLayout.fromJson(packet.json());
            if (Minecraft.getInstance().screen instanceof ModeWheelScreen screen) {
                screen.receiveLayout(layout);
            }
        } catch (BeefToolLayout.LayoutException ignored) {
            if (Minecraft.getInstance().screen instanceof ModeWheelScreen screen) {
                screen.receiveLayoutError(BeefToolLayout.Error.INVALID_TEXT);
            }
        }
    }

    /** 把服务端返回的错误码同步到模式轮盘界面。 */
    public static void handleBeefToolLayoutResult(BeefToolLayoutResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof ModeWheelScreen screen) {
            screen.receiveLayoutError(packet.error());
        }
    }
}
