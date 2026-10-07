package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.event.EventHandler;
import com.sorrowmist.useless.init.ModSounds;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

            // 杀戮光环：杖子放在副手 / 快捷栏 / 主背包里也要能开关，所以单独走「全背包」查找，
            // 不能套用下面 findTargetToolInHands（只认手）的那道门。
            if (msg.modeType == ModeType.BEEF_KILL_AURA) {
                ItemStack staff = UselessItemUtils.findKillAuraToggleTarget(player);
                if (!staff.isEmpty()) {
                    EndlessBeafItem.setKillAuraEnabled(staff, msg.enabled);
                    // 光环不占右键，无需接入 disable* 互斥。
                    // 音效只发给操作者本人：这是个人 UI 反馈，不该打扰周围玩家。
                    player.playNotifySound(
                            (msg.enabled ? ModSounds.KILL_AURA_ON : ModSounds.KILL_AURA_OFF).get(),
                            SoundSource.MASTER, 1.0F, 1.0F);
                    player.containerMenu.broadcastChanges();
                }
                return;
            }

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
                case EXDEORUM_CROOK -> {
                    // 能力由 exdeorum 的配方体系提供，未加载时忽略，避免写入无人读取的组件
                    if (ModList.get().isLoaded("exdeorum") && stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.ExDeorumCrookComponent.get(), msg.enabled);
                    }
                }
                case EXDEORUM_HAMMER -> {
                    if (ModList.get().isLoaded("exdeorum") && stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.ExDeorumHammerComponent.get(), msg.enabled);
                    }
                }
                case EXDEORUM_COMPRESSED_HAMMER -> {
                    if (ModList.get().isLoaded("exdeorum") && stack.getItem() instanceof EndlessBeafItem) {
                        stack.set(UComponents.ExDeorumCompressedHammerComponent.get(), msg.enabled);
                    }
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
                            // 仪式摆放走的是「不潜行右键」手势，所以只关同样占该手势的模式。
                            // 时间加速与无线物流都是潜行手势，与它不撞，不该被连坐关掉。
                            stack.set(UComponents.BeefCropHarvestComponent.get(), false);
                            stack.set(UComponents.ConstructionWandEnabledComponent.get(), false);
                            disableAeNetworkConnect(stack);
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
                    // 连点开关已改为客户端会话级状态（BeefAutoClicker），不再写入物品组件。
                    // 保留枚举值与分支只为不破坏 ModeType 的 ordinal 编码，这里不做任何事。
                }
                case BEEF_WIRELESS_LOGISTICS -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setStaffLinkEnabled(stack, msg.enabled);
                        if (msg.enabled) {
                            // 无线物流只用「潜行右键方块」这一个手势，所以只关掉同样占用它的模式：
                            // 时间加速、建筑魔杖（潜行右键 = 撤销）、AE 连接（潜行右键访问点 = 定绑定目标）。
                            //
                            // <b>顺手收菜与匠心仪式挎包刻意不关</b>：它们都要求「不潜行」
                            // （见 EndlessBeafItem#useOn 里的 !player.isShiftKeyDown() 守卫），
                            // 与潜行手势天然错开，关掉只会让玩家白丢一个功能。
                            stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
                            stack.set(UComponents.ConstructionWandEnabledComponent.get(), false);
                            disableAeNetworkConnect(stack);
                        }
                    }
                }
                case BEEF_PROTECT_MODE -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setProtectModeEnabled(stack, msg.enabled);
                    }
                }
                case BEEF_ENTITY_TIME_ACCELERATION -> {
                    if (stack.getItem() instanceof EndlessBeafItem) {
                        EndlessBeafItem.setEntityTimeAccelerationEnabled(stack, msg.enabled);
                        // 它只作用于「潜行右键生物」，而建筑魔杖/时间加速/收菜等只管「潜行右键方块」，
                        // 作用对象不重叠，因此不接入 disable* 互斥。
                    }
                }
            }

            // 显式同步物品到客户端
            player.containerMenu.broadcastChanges();
        });
    }

    /**
     * AE 连接模式与其它右键模式互斥：打开它时把「顺手收菜 / 时间加速 / 建筑魔杖 / 无线物流」关掉。
     *
     * <p>它<b>两种手势都用</b>：不潜行右键有 AE 节点的机器是「并入网络」，潜行右键无线访问点
     * 是「给连接模式定一个绑定目标」（见 {@code EventHandler} 里那段的说明）。因此两个手势上的
     * 模式都得让位 —— 这也是本方法比下面那几个「单手势」开关管得宽的原因。</p>
     */
    private static void disableRightClickConflicts(ItemStack stack) {
        stack.set(UComponents.BeefCropHarvestComponent.get(), false);
        stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
        stack.set(UComponents.ConstructionWandEnabledComponent.get(), false);
        disableStaffLink(stack);
    }

    /**
     * 关掉无线物流模式。
     *
     * <p>它与 AE 连接撞的是<b>同一次潜行右键</b>：无线访问点既是 AE 端点（AE 连接模式下潜行右键
     * 会把它设成绑定目标并取消事件），现在也允许绑进物流网络。两者都开着时谁赢取决于事件顺序，
     * 表现就是「想绑访问点进物流网络，结果杖子连到该网络去了」。</p>
     */
    private static void disableStaffLink(ItemStack stack) {
        if (stack.getOrDefault(UComponents.StaffLinkEnabledComponent.get(), false)) {
            stack.set(UComponents.StaffLinkEnabledComponent.get(), false);
        }
    }

    /** 反过来：打开其它占用右键的模式时，关掉 AE 连接模式。 */
    private static void disableAeNetworkConnect(ItemStack stack) {
        if (stack.getOrDefault(UComponents.AeNetworkConnectComponent.get(), false)) {
            stack.set(UComponents.AeNetworkConnectComponent.get(), false);
        }
    }

    /**
     * 关掉匠心仪式挎包模式。
     *
     * <p>它走「不潜行右键」手势，因此只该被同样占该手势的模式（顺手收菜、建筑魔杖、AE 连接）关掉；
     * 潜行手势的时间加速与无线物流与它不撞。</p>
     */
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
        BEEF_WIRELESS_LOGISTICS,
        // Ex Deorum 钩子 / 锤子 / 压缩锤：按对应配方体系改写挖掘掉落
        EXDEORUM_CROOK,
        EXDEORUM_HAMMER,
        EXDEORUM_COMPRESSED_HAMMER,
        // 杀戮光环：手持造化杖每 20 tick 对「范围伤害」配置范围内生物结算一次攻击伤害
        BEEF_KILL_AURA,
        // 保护名单模式：暂停光环与范围伤害，方便从容添加保护名单
        BEEF_PROTECT_MODE,
        // 生物加速：潜行右键生物加速其「计时器」（生长/繁殖冷却/下蛋/补货/长毛…），不跑 AI 与移动
        BEEF_ENTITY_TIME_ACCELERATION
    }
}
