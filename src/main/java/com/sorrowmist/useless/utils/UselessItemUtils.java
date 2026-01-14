package com.sorrowmist.useless.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UselessItemUtils {
    public static void applyEndlessBeafEffects(Player player) {
        if (player == null) return;

        // 饱和效果
        MobEffectInstance saturation = player.getEffect(MobEffects.SATURATION);
        if (saturation == null || saturation.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20000, 0, true, false, true));
        }

        // 生命恢复
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen == null || regen.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20000, 5, true, false, true));
        }

        // 夜视
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision == null || nightVision.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20000, 0, true, false, true));
        }

        // 抗火
        MobEffectInstance fireResistance = player.getEffect(MobEffects.FIRE_RESISTANCE);
        if (fireResistance == null || fireResistance.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20000, 0, true, false, true));
        }

        // 水下呼吸
        MobEffectInstance waterBreathing = player.getEffect(MobEffects.WATER_BREATHING);
        if (waterBreathing == null || waterBreathing.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20000, 0, true, false, true));
        }

        // 抗性提升
        MobEffectInstance damageResistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (damageResistance == null || damageResistance.getDuration() < 200) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20000, 5, true, false, true));
        }
    }

    public static void onLivingDrops(LivingDropsEvent event, ItemStack stack, Player player) {
        if (player == null) return;

        // 5% 概率
        if (!(Math.random() < 0.05)) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Level level = killedEntity.level();

        if (level.isClientSide()) return;

        sendFestiveMessage(player);

        Collection<ItemEntity> drops = event.getDrops();
        List<ItemEntity> remainingDrops = new ArrayList<>(); // 保留原样掉落的（可损坏物品）

        for (ItemEntity itemEntity : drops) {
            ItemStack dropStack = itemEntity.getItem();

            if (dropStack.isDamageableItem()) {
                // 可损坏物品（如剑、弓、护甲）保持原版掉落行为
                remainingDrops.add(itemEntity);
            } else {
                // 非可损坏物品：数量 ×20，直接尝试进玩家背包
                ItemStack amplifiedStack = dropStack.copy();
                amplifiedStack.setCount(dropStack.getCount() * 20);

                // 原版 API：优先进背包，满了自动掉落在玩家脚下
                player.getInventory().placeItemBackInInventory(amplifiedStack);
            }
        }

        // 清空原掉落物，重新添加只需掉在地上的部分（主要是可损坏物品）
        drops.clear();
        drops.addAll(remainingDrops);
    }

    // 显示触发提示
    private static void sendFestiveMessage(Player player) {
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.useless_mod.festive_triggered"),
                    true
            );
        }
    }
}
