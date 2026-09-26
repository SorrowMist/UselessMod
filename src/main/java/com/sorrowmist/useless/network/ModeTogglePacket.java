package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.event.EventHandler;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class ModeTogglePacket implements CustomPacketPayload {

    public static final Type<ModeTogglePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "mode_toggle"));
    public static final StreamCodec<FriendlyByteBuf, ModeTogglePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeEnum(pkt.modeType);
                buf.writeBoolean(pkt.enabled);
            },
            buf -> new ModeTogglePacket(buf.readEnum(ModeType.class), buf.readBoolean())
    );
    private final ModeType modeType;
    private final boolean enabled;
    public ModeTogglePacket(ModeType modeType, boolean enabled) {
        this.modeType = modeType;
        this.enabled = enabled;
    }

    public static void handle(ModeTogglePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) return; // 没找到工具直接返回

            var entry = toolEntry.get();
            ItemStack stack = entry.getKey();

            // 根据模式类型处理不同的组件
            switch (msg.modeType) {
                case CHAIN_MINING -> {
                    stack.set(UComponents.EnhancedChainMiningComponent.get(), msg.enabled);
                    // 切换连锁模式时清空缓存，避免使用旧模式的缓存数据
                    MiningDispatcher.clearPlayerCache(player);
                }
                case FORCE_MINING -> {
                    MiningDispatcher.clearPlayerCache(player);
                    stack.set(UComponents.ForceMiningComponent.get(), msg.enabled);
                }
                case AUTO_SMELT -> {
                    stack.set(UComponents.AutoSmeltComponent.get(), msg.enabled);
                }
                case AE_STORAGE_PRIORITY -> {
                    stack.set(UComponents.AEStoragePriorityComponent.get(), msg.enabled);
                }
                case AE_NETWORK_CONNECT -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.AeNetworkConnectComponent.get(), msg.enabled);
                        if (msg.enabled) {
                            disableRightClickConflicts(stack);
                        }
                    }
                }
                case WRENCH_TAG -> {
                    if (BeefToolVariants.isBaseVariant(stack)
                            && BeefToolVariants.isWrenchTagEnabled(stack) != msg.enabled) {
                        ItemStack replacement = BeefToolVariants.withWrenchTag(stack, msg.enabled);
                        player.setItemInHand(entry.getValue(), replacement);
                        stack = replacement;
                    }
                }
                case CONSTRUCTION_WAND -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.ConstructionWandEnabledComponent.get(), msg.enabled);
                        if (msg.enabled) {
                            disableAeNetworkConnect(stack);
                            // 建筑魔杖的「潜行右键 = 撤销」会抢走绑定容器的那次交互。
                            disableStaffLink(stack);
                        }
                    }
                }
                case FORCE_KILL -> {
                    stack.set(UComponents.ForceKillEnabledComponent.get(), msg.enabled);
                }
                case BEEF_MALUM_SPIRIT -> {
                    if (ModList.get().isLoaded("malum") && stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefMalumSpiritEnabledComponent.get(), msg.enabled);
                    }
                }
                case BEEF_MYSTICAL_AGRICULTURE -> {
                    if (ModList.get().isLoaded("mysticalagriculture")
                            && stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefMysticalAgricultureEnabledComponent.get(), msg.enabled);
                    }
                }
                case BEEF_BEHEADING -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefBeheadingEnabledComponent.get(), msg.enabled);
                    }
                }
                case BEEF_TIME_ACCELERATION -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), msg.enabled);
                        if (msg.enabled) {
                            disableRitualSatchel(stack);
                            // 时间加速同样绑定「潜行右键」。
                            disableStaffLink(stack);
                        }
                    }
                }
                case BEEF_INVULNERABILITY -> {
                    stack.set(UComponents.BeefInvulnerabilityEnabledComponent.get(), msg.enabled);
                    if (!msg.enabled) {
                        stack.set(UComponents.BeefAdvancedStealthEnabledComponent.get(), false);
                    }
                    EventHandler.updateBeefInvulnerability(player, true);
                }
                case BEEF_ADVANCED_STEALTH -> {
                    stack.set(UComponents.BeefAdvancedStealthEnabledComponent.get(), msg.enabled);
                    if (msg.enabled) {
                        stack.set(UComponents.BeefInvulnerabilityEnabledComponent.get(), true);
                    }
                    EventHandler.updateBeefInvulnerability(player, true);
                }
                case BEEF_CAPTURE -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefCaptureEnabledComponent.get(), msg.enabled);
                    }
                }
                case BEEF_TELEPORT -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setTeleportEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_AOE_DAMAGE -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefAoeDamageEnabledComponent.get(), msg.enabled);
                    }
                }
                case BEEF_MAGNET -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefMagnetEnabledComponent.get(), msg.enabled);
                    }
                }
                case BEEF_FARMLAND_MODE -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefFarmlandModeComponent.get(), msg.enabled);
                    }
                }
                case BEEF_CROP_HARVEST -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefCropHarvestComponent.get(), msg.enabled);
                        if (msg.enabled) {
                            disableAeNetworkConnect(stack);
                            disableRitualSatchel(stack);
                        }
                    }
                }
                case BEEF_SHEARS -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setShearsEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_FLINT_AND_STEEL -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setFlintAndSteelEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_RITUAL_SATCHEL -> {
                    // 该能力依赖 occultism 的仪式挎包机制，未加载时直接忽略，避免写入无人读取的组件
                    if (ModList.get().isLoaded("occultism") && stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.BeefRitualSatchelComponent.get(), msg.enabled);
                        if (msg.enabled) {
                            // 仪式摆放会占用右键，启用时关闭其它右键模式
                            disableAeNetworkConnect(stack);
                            disableRightClickConflicts(stack);
                        }
                    }
                }
                case BEEF_RIPEN -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setRipenEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_FORCE_GROW -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setForceGrowEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_AUTO_CLICK -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setAutoClickEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_WIRELESS_LOGISTICS -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setStaffLinkEnabled(stack, msg.enabled);
                        if (msg.enabled) {
                            // 潜行右键已被建筑魔杖（撤销）与时间加速占用，AE 连接又同属「网络」语义，
                            // 全部关掉，保证这次潜行右键只会落到绑定容器上。
                            disableStaffLinkConflicts(stack);
                        }
                    }
                }
            }

            // 显式同步物品到客户端
            player.containerMenu.broadcastChanges();
        });
    }

    /**
     * AE 连接模式与「顺手收菜 / 时间加速 / 建筑魔杖」互斥：
     * 打开它时把这三个占用右键的开关关掉。
     */
    private static void disableRightClickConflicts(ItemStack stack) {
        stack.set(UComponents.BeefCropHarvestComponent.get(), false);
        stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
        stack.set(UComponents.ConstructionWandEnabledComponent.get(), false);
        disableStaffLink(stack);
    }

    /** 无线物流与 AE 连接同为「网络」类模式，互斥以免右键意图混淆。 */
    private static void disableStaffLink(ItemStack stack) {
        if (stack.getOrDefault(UComponents.StaffLinkEnabledComponent.get(), false)) {
            stack.set(UComponents.StaffLinkEnabledComponent.get(), false);
        }
    }

    /** 开启无线物流时，关掉所有会抢占右键或语义重叠的其它模式（不含它自己）。 */
    private static void disableStaffLinkConflicts(ItemStack stack) {
        stack.set(UComponents.BeefCropHarvestComponent.get(), false);
        stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
        stack.set(UComponents.ConstructionWandEnabledComponent.get(), false);
        disableAeNetworkConnect(stack);
        disableRitualSatchel(stack);
    }

    /** 反过来：打开其它占用右键的模式时，关掉 AE 连接模式。 */
    private static void disableAeNetworkConnect(ItemStack stack) {
        if (stack.getOrDefault(UComponents.AeNetworkConnectComponent.get(), false)) {
            stack.set(UComponents.AeNetworkConnectComponent.get(), false);
        }
    }

    /** 匠心仪式挎包同样占用右键，开启其它右键模式时要把它关掉。 */
    private static void disableRitualSatchel(ItemStack stack) {
        if (stack.getOrDefault(UComponents.BeefRitualSatchelComponent.get(), false)) {
            stack.set(UComponents.BeefRitualSatchelComponent.get(), false);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ModeType {
        CHAIN_MINING,
        FORCE_MINING,
        AUTO_SMELT,
        AE_STORAGE_PRIORITY,
        AE_NETWORK_CONNECT,
        WRENCH_TAG,
        CONSTRUCTION_WAND,
        FORCE_KILL,
        BEEF_MALUM_SPIRIT,
        BEEF_MYSTICAL_AGRICULTURE,
        BEEF_BEHEADING,
        BEEF_INVULNERABILITY,
        BEEF_CAPTURE,
        BEEF_TIME_ACCELERATION,
        BEEF_TELEPORT,
        BEEF_AOE_DAMAGE,
        BEEF_MAGNET,
        BEEF_ADVANCED_STEALTH,
        BEEF_FARMLAND_MODE,
        BEEF_CROP_HARVEST,
        BEEF_SHEARS,
        BEEF_FLINT_AND_STEEL,
        BEEF_RITUAL_SATCHEL,
        // 新增值必须追加在末尾：writeEnum 按 ordinal 编码
        BEEF_RIPEN,
        BEEF_AUTO_CLICK,
        BEEF_FORCE_GROW,
        // 无线物流：潜行右键容器绑定/解绑，界面里配置搬运规则
        BEEF_WIRELESS_LOGISTICS
    }
}
