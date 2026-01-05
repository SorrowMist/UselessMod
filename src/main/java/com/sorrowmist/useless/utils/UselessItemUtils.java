package com.sorrowmist.useless.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;
import java.util.stream.Collectors;

public class UselessItemUtils {
    // 音效冷却记录
    private static final Map<UUID, Long> lastSoundTime = new HashMap<>();
    private static final long SOUND_COOLDOWN = 50;

    // 获取方块掉落物
    public static List<ItemStack> getBlockDrops(BlockState state, Level level, BlockPos pos, Player player,
                                                ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel)) return Collections.emptyList();

        try {
            LootParams.Builder lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos));

            return state.getDrops(lootParams)
                        .stream()
                        .filter(drop -> !drop.isEmpty())
                        .collect(Collectors.toList());

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // 将物品放入玩家背包
    private static boolean addItemToPlayerInventory(Player player, ItemStack stack) {
        return player.getInventory().add(stack);
    }

    // 带冷却播放方块破坏音效
    public static void playBreakSoundWithCooldown(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) return;

        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastSoundTime.get(playerId);

        if (lastTime == null || currentTime - lastTime >= SOUND_COOLDOWN) {
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS,
                            0.7F, 1.0F
            );
            lastSoundTime.put(playerId, currentTime);
        }
    }

    // 检查掉落物是否全部无效
    private static boolean hasInvalidDrops(List<ItemStack> drops) {
        return drops.stream().allMatch(drop -> drop.isEmpty());
    }

    // 处理普通方块的自动收集
    private static void handleNormalBlockBreak(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state,
                                               Player player, ItemStack stack, List<ItemStack> drops) {
        boolean allCollected = true;
        for (ItemStack drop : drops) {
            if (!addItemToPlayerInventory(player, drop.copy())) {
                allCollected = false;
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), drop.copy()));
            }
        }

        if (allCollected) {
            event.setCanceled(true);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } else {
            handleFallbackBlockBreak(level, pos, state, player, stack);
            return;
        }

        playBreakSoundWithCooldown(level, pos, state, player);
    }

    // 回退到原版破坏逻辑的处理
    private static void handleFallbackBlockBreak(Level level, BlockPos pos, BlockState state, Player player,
                                                 ItemStack tool) {
        // 记录破坏前已有的物品实体
        List<ItemEntity> existingItems = level.getEntitiesOfClass(ItemEntity.class,
                                                                  new AABB(pos).inflate(3.0)
        );
        Set<UUID> existingItemIds = new HashSet<>();
        for (ItemEntity item : existingItems) {
            existingItemIds.add(item.getUUID());
        }

        // 让方块正常破坏（不取消事件）
        // 使用延迟任务来收集新生成的掉落物
        Objects.requireNonNull(level.getServer()).execute(() -> {
            // 等待一小段时间让掉落物生成
            try {
                Thread.sleep(10); // 10ms通常足够
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 获取新生成的物品实体
            List<ItemEntity> newItems = level.getEntitiesOfClass(ItemEntity.class,
                                                                 new AABB(pos).inflate(3.0)
            );

            for (ItemEntity itemEntity : newItems) {
                // 只处理新生成的物品
                if (!existingItemIds.contains(itemEntity.getUUID())) {
                    ItemStack itemStack = itemEntity.getItem().copy();

                    if (!itemStack.isEmpty()) {
                        // 尝试将物品添加到玩家背包
                        if (player.getInventory().add(itemStack)) {
                            // 如果成功添加到背包，移除原物品实体
                            itemEntity.discard();
                        }
                        // 如果背包满了，物品会保留在原地
                    }
                }
            }
        });

        // 播放破坏音效
        playBreakSoundWithCooldown(level, pos, state, player);
    }

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
        if (!(Math.random() < 0.05)) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Level level = killedEntity.level();

        if (!level.isClientSide) {
            // 显示提示消息
            sendFestiveMessage(player);
            // 直接修改掉落物堆叠数量
            Collection<ItemEntity> drops = event.getDrops();
            List<ItemEntity> newDrops = new ArrayList<>();

            for (ItemEntity itemEntity : drops) {
                if (!itemEntity.getItem().isDamageableItem()) {
                    ItemStack itemStack = itemEntity.getItem();
                    // 直接将堆叠数量乘以20
                    int originalCount = itemStack.getCount();
                    itemStack.setCount(originalCount * 20);

                    // 重新创建ItemEntity以确保更新
                    ItemEntity newItem = new ItemEntity(
                            level,
                            itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                            itemStack
                    );
                    newItem.setDeltaMovement(
                            -0.3 + level.random.nextDouble() * 0.6,
                            0.3 + level.random.nextDouble() * 0.3,
                            -0.3 + level.random.nextDouble() * 0.6
                    );
                    newDrops.add(newItem);
                } else {
                    newDrops.add(itemEntity);
                }
            }

            // 清空原掉落物列表并添加新的
            drops.clear();
            drops.addAll(newDrops);
        }
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
