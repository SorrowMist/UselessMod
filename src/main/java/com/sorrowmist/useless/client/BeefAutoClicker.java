package com.sorrowmist.useless.client;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
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
 * 造化杖「连点模式」的客户端实现。
 *
 * <p>模式开启且手持造化杖时，每个客户端 tick 按配置次数重复触发右键。
 * 判定顺序完全复刻 {@code Minecraft#startUseItem}：先看视线命中（实体 → 方块），
 * 命中被消费就结束这一次；否则再走物品自身的 {@code use}。
 * 这样连点触发的是<b>真正的右键交互</b>，造化杖自己的行为链、以及其它模组的
 * 右键功能都会照常生效（例如同时开启催熟模式即可自动反复催熟）。
 *
 * <p>速率由客户端配置 {@code beef_autoclick_clicks_per_tick} 控制（默认 4 次/tick）。
 * 之所以不复用原版的 {@code rightClickDelay}，是因为它会把速率压到约 4 次/秒，
 * 达不到「尽可能快」的要求。
 */
public final class BeefAutoClicker {

    private BeefAutoClicker() {
    }

    /** 客户端 tick 入口。 */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        // 开着界面 / 游戏暂停时不连点，避免误操作
        if (mc.screen != null || mc.isPaused()) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof EndlessBeafItem) || !EndlessBeafItem.isAutoClickEnabled(mainHand)) {
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
     * @return 本次是否真的触发了一次交互（false 表示当前状态不适合连点，应停止本 tick 的循环）
     */
    private static boolean performRightClick(Minecraft mc, LocalPlayer player) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return false;
        }

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

        // 3. 兜底：走物品自身的 use（例如对空气右键、或方块交互未消费时）
        InteractionResult result = mc.gameMode.useItem(player, hand);
        if (result.consumesAction()) {
            player.swing(hand);
        }
        return true;
    }
}
