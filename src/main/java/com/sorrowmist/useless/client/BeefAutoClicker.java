package com.sorrowmist.useless.client;

import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 「连点模式」的客户端实现。
 *
 * <p>开关是<b>客户端会话级</b>状态（{@link #isEnabled()}），<b>不绑定任何物品</b> —— 空手或拿着
 * 任意物品都能开启，开启后每个客户端 tick 按配置次数重复触发右键。判定顺序完全复刻
 * {@code Minecraft#startUseItem}：<b>先主手、后副手</b>，每只手先看视线命中（实体 → 方块），
 * 命中被消费就结束这一次；都没消费时才兜底走物品自身的 {@code use}（空手没有 {@code use}，跳过）。
 * 这样连点触发的是<b>真正的右键交互</b>，造化杖自己的行为链、以及其它模组的
 * 右键功能都会照常生效（例如同时开启催熟模式即可自动反复催熟）。</p>
 *
 * <p>速率由客户端配置 {@code beef_autoclick_clicks_per_tick} 控制（默认 4 次/tick）。
 * 之所以不复用原版的 {@code rightClickDelay}，是因为它会把速率压到约 4 次/秒，
 * 达不到「尽可能快」的要求。</p>
 */
public final class BeefAutoClicker {

    /** 与 {@code Minecraft#startUseItem} 一致的尝试顺序：主手 → 副手。 */
    private static final InteractionHand[] HANDS = InteractionHand.values();

    /** 连点开关：客户端会话级，登录时由 {@code ClientEventBusSubscriber} 重置为关。 */
    private static volatile boolean enabled;

    private BeefAutoClicker() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** 翻转开关并返回新状态。 */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /** 登录 / 断线时把开关重置为关，避免状态跨会话残留。 */
    public static void reset() {
        enabled = false;
    }

    /** 客户端 tick 入口。 */
    public static void tick(Minecraft mc) {
        if (!enabled) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        // 开着界面 / 游戏暂停时不连点，避免误操作
        if (mc.screen != null || mc.isPaused()) {
            return;
        }
        // 正在吃东西/拉弓/用刷子，或正在挖方块时让位给玩家自己
        if (player.isUsingItem() || player.isHandsBusy() || mc.gameMode.isDestroying()) {
            return;
        }

        int clicks = ConfigManager.getBeefAutoClickClicksPerTick();
        for (int i = 0; i < clicks; i++) {
            if (!performRightClick(mc, player)) {
                break;
            }
        }
    }

    /**
     * 执行一次右键交互。
     *
     * <p>与 {@code Minecraft#startUseItem} 一致：<b>依次尝试主手、副手</b>。每只手先看视线命中
     * （实体走 {@code interact}、方块走 {@code useItemOn}），命中被消费就结束；都没消费时才兜底走
     * 该手物品自身的 {@code use}。<b>空手不调用 {@code use}</b>（原版亦然），但空手依然会触发
     * 方块 / 实体的交互 —— 这正是「空手连点」的意义所在。</p>
     *
     * <p>副手一定要试：主手空着、副手拿着物品时，若只走主手就完全不会用到副手那件物品
     * （2026-10-07 用户报的「副手持有物品开启连点没生效」）。</p>
     *
     * @return 本次是否真的触发了一次交互（false 表示当前状态不适合连点，应停止本 tick 的循环）
     */
    private static boolean performRightClick(Minecraft mc, LocalPlayer player) {
        for (InteractionHand hand : HANDS) {
            ItemStack stack = player.getItemInHand(hand);

            HitResult hit = mc.hitResult;
            if (hit != null) {
                // 1. 命中实体：优先交给实体交互
                if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
                    Entity entity = entityHit.getEntity();
                    if (!mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                        return false;
                    }
                    InteractionResult result = mc.gameMode.interact(player, entity, hand);
                    if (result.consumesAction()) {
                        player.swing(hand);
                        return true;
                    }
                } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                    // 2. 命中方块：空气方块不触发（与原版一致）
                    BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
                    if (!state.isAir()) {
                        InteractionResult result = mc.gameMode.useItemOn(player, hand, blockHit);
                        if (result.consumesAction()) {
                            player.swing(hand);
                            return true;
                        }
                    }
                }
            }

            // 3. 兜底：走该手物品自身的 use（例如对空气右键、或方块交互未消费时）。空手没有 use 可用。
            if (!stack.isEmpty()) {
                InteractionResult result = mc.gameMode.useItem(player, hand);
                if (result.consumesAction()) {
                    player.swing(hand);
                    return true;
                }
            }
        }
        return true;
    }
}
