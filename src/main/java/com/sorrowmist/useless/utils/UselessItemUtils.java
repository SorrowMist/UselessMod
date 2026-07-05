package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class UselessItemUtils {
    public static void applyEndlessBeafEffects(Player player) {
        if (player == null) return;

        // 检查是否启用药水效果
        if (ConfigManager.shouldEnablePotionEffects()) {
            List<String> customEffects = ConfigManager.getCustomPotionEffects();
            
            for (String effectConfig : customEffects) {
                applyPotionEffectFromConfig(player, effectConfig);
            }
        }
    }

    private static final int POTION_DURATION = 20000;

    /**
     * 从配置字符串解析并应用药水效果
     * 格式: "modid:effect_name, amplifier"
     */
    private static void applyPotionEffectFromConfig(Player player, String effectConfig) {
        try {
            String[] parts = effectConfig.split(",");
            if (parts.length != 2) {
                return;
            }

            String effectId = parts[0];
            int amplifier = Integer.parseInt(parts[1]) - 1;

            ResourceLocation location = ResourceLocation.parse(effectId);
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(location);

            if (effect == null) {
                return;
            }

            Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            MobEffectInstance currentEffect = player.getEffect(effectHolder);
            if (currentEffect == null || currentEffect.getDuration() < 200) {
                player.addEffect(new MobEffectInstance(effectHolder, POTION_DURATION, Math.max(0, amplifier), true, false, true));
            }
        } catch (Exception e) {
            // 静默处理配置解析错误，避免每tick输出日志
        }
    }

    public static void onLivingDrops(LivingDropsEvent event, ItemStack stack, Player player) {
        if (player == null) return;

        // 根据配置的概率判断是否触发
        int chance = ConfigManager.getFestiveDropChance();
        if (!(Math.random() * 100 < chance)) {
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
                    Component.translatable("gui.useless_mod.festive_triggered"),
                    true
            );
        }
    }

    /**
     * 检查物品是否是目标工具（牛排或特定模式的omnitools扳手）
     */
    private static boolean isTargetTool(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        // 检查是否是永恒牛排工具
        if (itemStack.getItem() instanceof EndlessBeafItem) {
            return true;
        }

        // 检查是否是omnitools扳手且处于正确模式
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return itemId.getNamespace().equals("omnitools")
                && itemStack.get(UComponents.CurrentToolTypeComponent) == ToolTypeMode.OMNITOOL_MODE;
    }

    /**
     * 从玩家的主手和副手中查找目标工具
     * 返回包含目标物品和对应手的Optional
     */
    public static Optional<SimpleImmutableEntry<ItemStack, InteractionHand>> findTargetToolInHands(Player player) {
        if (player == null) {
            return Optional.empty();
        }

        ItemStack mainHandItem = player.getMainHandItem();
        ItemStack offHandItem = player.getOffhandItem();

        // 检查主手
        if (isTargetTool(mainHandItem)) {
            return Optional.of(new SimpleImmutableEntry<>(mainHandItem, InteractionHand.MAIN_HAND));
        }

        // 检查副手
        if (isTargetTool(offHandItem)) {
            return Optional.of(new SimpleImmutableEntry<>(offHandItem, InteractionHand.OFF_HAND));
        }

        return Optional.empty();
    }

    public static boolean hasTargetToolInInventory(Player player) {
        if (player == null) {
            return false;
        }

        return player.getInventory().items.stream().anyMatch(UselessItemUtils::isTargetTool);
    }
}
