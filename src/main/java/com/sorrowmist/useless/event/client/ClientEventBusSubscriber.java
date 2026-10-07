package com.sorrowmist.useless.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.client.BeefAutoClicker;
import com.sorrowmist.useless.client.gui.MiningStatusGui;
import com.sorrowmist.useless.client.gui.PlasticThermostatScreen;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.blocks.TeleportPadBlock;
import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.BeefToolProtectionManager;
import com.sorrowmist.useless.event.EventHandler;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.ForceBreakKeyPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.ProtectEntityPacket;
import com.sorrowmist.useless.network.ShapeSwitchPacket;
import com.sorrowmist.useless.network.StaffLinkCyclePacket;
import com.sorrowmist.useless.network.StaffLinkOpenPacket;
import com.sorrowmist.useless.network.TabKeyPressedPacket;
import com.sorrowmist.useless.network.TeleportKeyPacket;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientEventBusSubscriber {
    // 跟踪Tab键的前一状态
    private static boolean lastTabPressed = false;

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        // 附魔切换
        event.register(KeyBindings.SWITCH_SILK_TOUCH_KEY.get());
        event.register(KeyBindings.SWITCH_FORTUNE_KEY.get());

        // 模式开关
        event.register(KeyBindings.TOGGLE_CHAIN_MODE_KEY.get());
        event.register(KeyBindings.SWITCH_FORCE_MINING_KEY.get());
        event.register(KeyBindings.SWITCH_FARMLAND_MODE_KEY.get());
        event.register(KeyBindings.TOGGLE_CROP_HARVEST_KEY.get());
        // 这两个按键此前只被 onKeyInput / tooltip 使用，却漏了注册，导致按键实际不生效
        event.register(KeyBindings.TOGGLE_SHEARS_KEY.get());
        event.register(KeyBindings.TOGGLE_FLINT_AND_STEEL_KEY.get());
        // 连点模式（默认未绑定）
        event.register(KeyBindings.TOGGLE_AUTO_CLICK_KEY.get());
        // 杀戮光环（默认未绑定）
        event.register(KeyBindings.TOGGLE_KILL_AURA_KEY.get());

        // 触发按键
        event.register(KeyBindings.TRIGGER_CHAIN_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY.get());

        // 短距传送（造化杖）
        event.register(KeyBindings.SHORT_TELEPORT_KEY.get());

        // UI
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY.get());
        event.register(KeyBindings.OPEN_WIRELESS_LOGISTICS_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.screen != null) return;

        // 无线物流：打开配置界面（仅手持造化杖时）
        if (KeyBindings.OPEN_WIRELESS_LOGISTICS_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(new StaffLinkOpenPacket());
            }
        }

        // 检测连锁键状态变化。Tab 键与 FTB 的连锁键并列，任一按下即视为连锁激活；
        // 后者直接读取 FTB 自己的绑定，不在此处另行注册。
        boolean currentTabPressed = KeyBindings.isChainMiningKeyDown();
        if (currentTabPressed != lastTabPressed) {
            PacketDistributor.sendToServer(new TabKeyPressedPacket(currentTabPressed));
            lastTabPressed = currentTabPressed;
        }

        if (KeyBindings.SWITCH_FORTUNE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantMode.FORTUNE));
            }
        }

        if (KeyBindings.SWITCH_SILK_TOUCH_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantMode.SILK_TOUCH));
            }
        }

        if (KeyBindings.TOGGLE_CHAIN_MODE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换连锁挖矿模式
                boolean currentEnabled = mainHandItem.getOrDefault(UComponents.EnhancedChainMiningComponent.get(),
                                                                   false
                );
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.CHAIN_MINING, !currentEnabled));
            }
        }

        if (KeyBindings.SWITCH_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换强制挖掘模式
                boolean currentEnabled = mainHandItem.getOrDefault(UComponents.ForceMiningComponent.get(), false);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.FORCE_MINING, !currentEnabled));
            }
        }

        if (KeyBindings.SWITCH_FARMLAND_MODE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换右键泥土的结果：草径(铲子) <-> 耕地(锄头)
                boolean currentFarmland = EndlessBeafItem.isFarmlandMode(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_FARMLAND_MODE, !currentFarmland));
            }
        }

        if (KeyBindings.TOGGLE_CROP_HARVEST_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换「顺手收菜」（右键成熟作物：收获并保留种子于耕地）
                boolean currentHarvest = EndlessBeafItem.isCropHarvestEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_CROP_HARVEST, !currentHarvest));
            }
        }

        if (KeyBindings.TOGGLE_SHEARS_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换剪刀功能（剪羊毛 / 剪掉落，并对外声明剪刀能力）
                boolean currentShears = EndlessBeafItem.isShearsEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_SHEARS, !currentShears));
            }
        }

        if (KeyBindings.TOGGLE_FLINT_AND_STEEL_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换打火石功能（点亮营火/蜡烛，或在点击面点火）
                boolean currentFlintAndSteel = EndlessBeafItem.isFlintAndSteelEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_FLINT_AND_STEEL,
                                !currentFlintAndSteel));
            }
        }

        if (KeyBindings.TOGGLE_AUTO_CLICK_KEY.get().consumeClick()) {
            // 连点开关是客户端会话级状态，不绑定物品：空手 / 任意物品下都能开关，
            // 也不需要发包（真正的连点循环纯在客户端跑）。
            BeefAutoClicker.toggle();
        }

        if (KeyBindings.TOGGLE_KILL_AURA_KEY.get().consumeClick()) {
            // 主手 / 副手 / 快捷栏 / 背包里任意一把杖都能开关，与服务端 findKillAuraToggleTarget 一致
            ItemStack staff = UselessItemUtils.findKillAuraToggleTarget(player);
            if (!staff.isEmpty()) {
                boolean currentAura = EndlessBeafItem.isKillAuraEnabled(staff);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_KILL_AURA, !currentAura));
            }
        }

        // 检测R键按下（触发强制破坏）
        if (KeyBindings.TRIGGER_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem
                    && mainHandItem.getOrDefault(UComponents.ForceMiningComponent.get(), false)) {
                // R键按下，发送强制破坏请求，同时传入当前Tab键状态
                boolean tabPressed = KeyBindings.isChainMiningKeyDown();
                PacketDistributor.sendToServer(new ForceBreakKeyPacket(tabPressed));
            }
        }

        // 连点模式：开关为客户端会话级状态（BeefAutoClicker），开启后按配置速率重复触发右键
        BeefAutoClicker.tick(mc);
    }

    /**
     * Shift + 滚轮切换连锁形状 / 无线物流网络。
     *
     * <p>只在「手持造化杖 + 没开任何界面」时才吃掉这次滚动，免得抢走其它模组的 Shift 滚轮用法
     * （物品栏滚动之类）。</p>
     *
     * <p>优先级必须高于 FTB Ultimine：FTB 通过 Architectury 的
     * {@code ClientRawInputEvent.MOUSE_SCROLLED} 注册了同一个 NeoForge 鼠标滚动事件，并在
     * 「连锁键按下 + Shift + 滚轮」时以 {@code EventResult.interruptFalse()} 取消该事件。若本方法
     * 用默认优先级，事件已被 FTB 取消，形状切换包永远不会发出，表现为「按住连锁键滚轮毫无反应」。
     * 同理，形状分支不能依赖 {@code event.isCanceled()}：该标志在 FTB 先行处理时必然为真，
     * 据此提前返回等于把功能再次关掉。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDeltaY() == 0.0) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!player.isShiftKeyDown()) return;

        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof EndlessBeafItem)) return;

        int delta = event.getScrollDeltaY() > 0.0 ? 1 : -1;

        // 按住连锁键时 Shift + 滚轮切换连锁形状。该分支先于无线物流判定：
        // 两者虽然共用 Shift + 滚轮，但触发前提互斥（连锁键按下 / 未按下），不会同时命中。
        if (KeyBindings.isChainMiningKeyDown()) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new ShapeSwitchPacket(delta));
            return;
        }

        // 无线物流分支仍避开已被其它模组处理的滚动，保持原有克制。
        if (event.isCanceled()) return;

        if (!EndlessBeafItem.isStaffLinkEnabled(mainHandItem)) return;

        event.setCanceled(true);
        PacketDistributor.sendToServer(new StaffLinkCyclePacket(delta));
    }

    /**
     * 短距传送的鼠标触发入口。
     *
     * <p>该绑定挂在恒为非激活的冲突上下文上，不参与按键分发，因此按下时原版 keyUse
     * 仍能取得点击，方块交互不受影响；此处只上报传送请求，不取消该事件。
     * 是否触发取决于绑定自身配置的主键与修饰键，玩家在按键设置中的改动即时生效。</p>
     */
    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // 保护手势必须最先判定：命中即取消并 return，短距传送分支不会执行。
        // 两者共用 Shift + 右键，命中生物时约定「保护优先」。
        if (tryProtectGesture(mc, event)) return;
        // 权杖 Alt + 右键塑料方块：打开恒温配置界面。它同样要取消事件，否则原版右键照发。
        if (tryThermostatGesture(mc, event)) return;

        if (event.getAction() != GLFW.GLFW_PRESS) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;

        KeyMapping mapping = KeyBindings.SHORT_TELEPORT_KEY.get();
        InputConstants.Key pressedKey = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (!pressedKey.equals(mapping.getKey())) return;
        if (!mapping.getKeyModifier().isActive(mapping.getKeyConflictContext())) return;

        tryTriggerShortTeleport(mc, player, true);
    }

    /**
     * 短距传送的键盘触发入口。
     *
     * <p>该绑定不参与 {@code KeyMapping} 分发，触发判定只能由原始输入事件完成。
     * 若仅保留鼠标分支，玩家把主键改绑为键盘按键后不会产生任何触发：鼠标事件
     * 携带的按键恒为鼠标键，与绑定主键不相等。此处按物理按下边沿补上键盘分支，
     * 与鼠标分支共用同一套前置校验。键盘主键不与方块交互共用同一次点击，
     * 故不让位于准星命中的方块。</p>
     */
    @SubscribeEvent
    public static void onKeyInputEvent(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() == GLFW.GLFW_KEY_UNKNOWN) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;

        KeyMapping mapping = KeyBindings.SHORT_TELEPORT_KEY.get();
        InputConstants.Key pressedKey = InputConstants.Type.KEYSYM.getOrCreate(event.getKey());
        if (!pressedKey.equals(mapping.getKey())) return;
        if (!mapping.getKeyModifier().isActive(mapping.getKeyConflictContext())) return;

        tryTriggerShortTeleport(mc, player, false);
    }

    /**
     * 校验短距闪现的前置条件并上报按键包。
     *
     * @param respectBlockPriority 是否让位于共用同一次点击的方块交互与时间加速；
     *                             鼠标右键需要让位，键盘主键不需要
     * @return true 表示本次按键已上报
     */
    private static boolean tryTriggerShortTeleport(Minecraft mc, LocalPlayer player, boolean respectBlockPriority) {
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof EndlessBeafItem)) return false;
        if (!EndlessBeafItem.isTeleportEnabled(mainHandItem)) return false;

        if (respectBlockPriority) {
            // 时间加速同样绑定 Shift + 右键，两者不可同时触发：启用时交由方块交互处理。
            if (BeefTimeAcceleration.shouldBlockOtherRightClick(mainHandItem, player)) return false;
            // 仅当准星命中的方块自身要独占该组合键时让位，普通方块不拦截闪现。
            if (isBlockInteractionPriority(mc)) return false;
        }

        PacketDistributor.sendToServer(new TeleportKeyPacket());
        return true;
    }

    /**
     * Ctrl / Shift + 右键生物：切换生物保护名单。
     *
     * <p>为什么用 {@code MouseButton.Pre} 而不是 {@code PlayerInteractEvent.EntityInteract}：
     * <ul>
     *   <li>Ctrl 不会被 {@code ServerboundInteractPacket} 传输（它只带 {@code isShiftKeyDown()}），
     *       服务端根本区分不出 Ctrl 与普通右键，只能用 {@code getModifiers()} 在客户端判断；</li>
     *   <li>取消本事件发生在 {@code KeyMapping.click} 之前，{@code keyUse} 从不被按下，
     *       于是 {@code startUseItem()} 不会被调用 —— 交互包不会发出，村民交易 GUI、
     *       剪羊毛等原版行为都不会发生；</li>
     *   <li>GLFW 鼠标按键没有键盘那样的 repeat，每次物理按下只触发一次，因此不需要去抖。</li>
     * </ul>
     *
     * @return true 表示本次右键已被保护手势消费，调用方应立即 return
     */
    private static boolean tryProtectGesture(Minecraft mc, InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return false;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null) return false;

        // Ctrl 优先：两个修饰键同时按下时按「种类」处理
        boolean byType = (event.getModifiers() & InputConstants.MOD_CONTROL) != 0;
        boolean byEntity = !byType && player.isShiftKeyDown();
        if (!byType && !byEntity) return false;

        // 必须手持造化杖（主手优先，其次副手）
        ItemStack staff = player.getMainHandItem();
        if (!(staff.getItem() instanceof EndlessBeafItem)) {
            staff = player.getOffhandItem();
            if (!(staff.getItem() instanceof EndlessBeafItem)) return false;
        }
        // 杀戮光环、范围伤害或保护名单模式至少开一个，否则完全让给原版交互
        if (!EndlessBeafItem.isProtectGestureAvailable(staff)) return false;

        // 「生物加速」模式开启时，Shift+右键生物让给生物加速（与闪现键让位同款）。
        // 用 byEntity 判定：Ctrl 按种类的手势不受影响。漏了这一步，生物加速永远不会触发——
        // 本事件被取消发生在 KeyMapping.click 之前，keyUse 不按下，交互包根本不会发出。
        if (byEntity && EndlessBeafItem.isEntityTimeAccelerationEnabled(staff)) return false;

        // 用当前帧重算准星命中，避免用到上一 tick 的旧 hitResult
        mc.gameRenderer.pick(1.0F);
        if (!(mc.hitResult instanceof EntityHitResult hit)) return false;

        Entity target = EndlessBeafItem.unwrapPartEntity(hit.getEntity());
        if (!(target instanceof LivingEntity) || target == player) return false;

        event.setCanceled(true);
        PacketDistributor.sendToServer(new ProtectEntityPacket(target.getId(), byType));
        return true;
    }

    /**
     * 权杖 <b>Alt + 右键</b> 塑料方块：打开恒温源配置界面。
     *
     * <p><b>Alt 判定用 GLFW 位域而不是 {@code InputConstants}。</b> 本版本的
     * {@code InputConstants} 只有 {@code MOD_CONTROL}，没有 {@code MOD_ALT}，所以直接读
     * {@link InputEvent.MouseButton.Pre#getModifiers()} 里的 {@code GLFW_MOD_ALT}。</p>
     *
     * <p>取消本事件发生在 {@code KeyMapping.click} 之前，{@code keyUse} 从不被按下，
     * 于是原版右键交互包不会发出——权杖的 {@code useOn} 链、方块自身的 {@code useItemOn}、
     * 以及 {@code UselessMod#onRightClickBlock} 都不会跑。这与保护手势同一套机制。</p>
     *
     * @return true 表示本次右键已被恒温手势消费，调用方应立即 return
     */
    private static boolean tryThermostatGesture(Minecraft mc, InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return false;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        if ((event.getModifiers() & GLFW.GLFW_MOD_ALT) == 0) return false;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null) return false;

        // 必须手持造化杖（主手优先，其次副手），与保护手势同一把尺子。
        ItemStack staff = player.getMainHandItem();
        if (!(staff.getItem() instanceof EndlessBeafItem)) {
            staff = player.getOffhandItem();
            if (!(staff.getItem() instanceof EndlessBeafItem)) return false;
        }

        // 用当前帧重算准星命中，避免用到上一 tick 的旧 hitResult。
        mc.gameRenderer.pick(1.0F);
        if (!(mc.hitResult instanceof BlockHitResult hit)) return false;
        if (!(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof GlowPlasticBlock)) return false;

        event.setCanceled(true);
        mc.setScreen(new PlasticThermostatScreen(hit.getBlockPos()));
        return true;
    }

    /**
     * 判断准星命中的方块是否要独占该组合键。
     *
     * <p>仅三类既有交互需要让位：维度传送方块（潜行右键编辑配置）、合金炉核心
     * （潜行右键自动搭建）与无线接入点（潜行右键绑定目标）。其余方块不消费该组合键，
     * 闪现照常执行。未命中方块时按未命中处理。</p>
     */
    private static boolean isBlockInteractionPriority(Minecraft mc) {
        Level level = mc.level;
        if (level == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        if (level.getBlockState(pos).getBlock() instanceof TeleportPadBlock) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MultiblockAlloyFurnaceCoreBlockEntity) {
            return true;
        }
        return blockEntity != null
                && blockEntity.getClass().getName().contains("WirelessAccessPoint");
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "chain_mining_status"),
                MiningStatusGui::render
        );
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        lastTabPressed = false;
        // 连点开关是客户端会话级状态，登录时统一置关，避免跨会话残留
        BeefAutoClicker.reset();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearClientBeefProtectionState();
    }

    @SubscribeEvent
    public static void onClientPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        lastTabPressed = false;
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            EventHandler.setClientBeefAdvancedStealthState(player.getId(), false);
        }
    }

    private static void clearClientBeefProtectionState() {
        EventHandler.clearClientBeefAdvancedStealthStates();
        BeefToolProtectionManager.clearAll();
        lastTabPressed = false;
    }
}
