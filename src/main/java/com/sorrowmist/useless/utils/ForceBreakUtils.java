package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Collections;
import java.util.List;

/**
 * 强制破坏工具类，包含强制破坏单个方块的逻辑
 */
public class ForceBreakUtils {

    /**
     * 强制破坏单个方块
     * @param event 方块破坏事件
     * @param level 世界
     * @param pos 方块位置
     * @param state 方块状态
     * @param player 玩家
     * @param stack 工具
     * @return 是否成功破坏
     */
    public static boolean forceBreakBlock(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        // 检查方块是否含水（更通用的方法，适用于原版和模组方块）
        // 获取方块的流体状态
        net.minecraft.world.level.material.FluidState fluidState = level.getFluidState(pos);
        // 检查流体是否是水，并且是水源方块（level == 8表示水源）
        if (fluidState.getType() == net.minecraft.world.level.material.Fluids.WATER && fluidState.isSource()) {
            // 含水方块，直接返回false，不取消事件，让原版破坏逻辑处理
            return false;
        }
        
        // 特殊处理混沌水晶：绕过击败混沌龙检查
        if (handleChaosCrystal(event, level, pos, state, player, stack)) {
            return true;
        }

        List<ItemStack> drops;
        
        // 检查是否为精准采集模式
        boolean isSilkTouch = isSilkTouchMode(stack);
        
        if (isSilkTouch) {
            // 精准采集模式：优先获取物品本身（包含NBT）
            ItemStack silkTouchDrop = createSilkTouchDrop(level, pos, state);
            drops = Collections.singletonList(silkTouchDrop);
        } else {
            // 非精准采集模式：使用正常逻辑获取掉落物
            // 1. 尝试获取方块的掉落物（使用正常逻辑，包括战利品表和时运）
            drops = BlockBreakUtils.getBlockDrops(state, level, pos, player, stack);

            // 2. 如果没有有效的掉落物，强制掉落一个方块
            if (drops == null || drops.isEmpty() || BlockBreakUtils.hasInvalidDrops(drops)) {
                // 强制掉落一个目标方块
                ItemStack forcedDrop = new ItemStack(state.getBlock().asItem(), 1);
                drops = Collections.singletonList(forcedDrop);
            }
        }

        // 3. 处理掉落物：优先存入AE网络，然后是玩家背包，最后是掉落
        EndlessBeafItem.handleDrops(drops, player);

        // 4. 生成剩余的掉落物（如果有）
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                // 掉落在玩家脚下
                ItemEntity itemEntity = new ItemEntity(level,
                        player.getX(), player.getY(), player.getZ(),
                        drop.copy());
                level.addFreshEntity(itemEntity);
            }
        }

        // 5. 强制破坏方块
        event.setCanceled(true);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        // 6. 播放破坏音效
        BlockBreakUtils.playBreakSoundWithCooldown(level, pos, state, player);

        return true;
    }

    /**
     * 检查工具是否处于精准采集模式
     */
    private static boolean isSilkTouchMode(ItemStack stack) {
        if (stack.hasTag()) {
            return stack.getTag().getBoolean("SilkTouchMode");
        }
        return false;
    }

    /**
     * 创建包含NBT数据的方块物品
     */
    private static ItemStack createSilkTouchDrop(Level level, BlockPos pos, BlockState state) {
        // 创建方块物品
        ItemStack stack = new ItemStack(state.getBlock().asItem(), 1);
        
        // 尝试获取并复制方块实体的NBT数据
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            CompoundTag blockEntityTag = blockEntity.saveWithoutMetadata();
            if (!blockEntityTag.isEmpty()) {
                // 创建方块物品的NBT标签
                CompoundTag itemTag = stack.getOrCreateTag();
                itemTag.put("BlockEntityTag", blockEntityTag);
            }
        }
        
        return stack;
    }

    /**
     * 处理混沌水晶的特殊破坏逻辑
     */
    private static boolean handleChaosCrystal(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        try {
            // 使用反射检查并处理混沌水晶
            Class<?> chaosCrystalClass = Class.forName("com.brandon3055.draconicevolution.blocks.ChaosCrystal");
            Class<?> tileChaosCrystalClass = Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileChaosCrystal");

            // 检查当前方块是否是混沌水晶
            if (chaosCrystalClass.isInstance(state.getBlock())) {
                // 获取方块实体
                BlockEntity tileEntity = level.getBlockEntity(pos);
                if (tileChaosCrystalClass.isInstance(tileEntity)) {
                    // 直接调用setDefeated方法，这会正确设置guardianDefeated并触发状态更新
                    try {
                        java.lang.reflect.Method setDefeatedMethod = tileChaosCrystalClass.getMethod("setDefeated");
                        setDefeatedMethod.invoke(tileEntity);
                    } catch (Exception e) {
                        // 如果setDefeated方法不可用，尝试直接设置字段
                        java.lang.reflect.Field guardianDefeatedField = tileChaosCrystalClass.getDeclaredField("guardianDefeated");
                        guardianDefeatedField.setAccessible(true);
                        Object managedBool = guardianDefeatedField.get(tileEntity);

                        Class<?> managedBoolClass = managedBool.getClass();
                        java.lang.reflect.Method setMethod = managedBoolClass.getMethod("set", boolean.class);
                        setMethod.invoke(managedBool, true);
                    }

                    // 调用tick方法确保状态更新
                    java.lang.reflect.Method tickMethod = tileChaosCrystalClass.getMethod("tick");
                    tickMethod.invoke(tileEntity);
                }

                // 如果是混沌水晶，直接生成掉落物并破坏方块，绕过detonate方法的重生逻辑
                try {
                    // 获取DEConfig.chaosDropCount的值
                    Class<?> deConfigClass = Class.forName("com.brandon3055.draconicevolution.DEConfig");
                    java.lang.reflect.Field chaosDropCountField = deConfigClass.getDeclaredField("chaosDropCount");
                    chaosDropCountField.setAccessible(true);
                    int chaosDropCount = chaosDropCountField.getInt(null);

                    // 获取DEContent.CHAOS_SHARD
                    Class<?> deContentClass = Class.forName("com.brandon3055.draconicevolution.init.DEContent");
                    java.lang.reflect.Field chaosShardField = deContentClass.getDeclaredField("CHAOS_SHARD");
                    chaosShardField.setAccessible(true);
                    Object chaosShardObject = chaosShardField.get(null);

                    // 确保CHAOS_SHARD是一个RegistryObject
                    Class<?> registryObjectClass = Class.forName("net.minecraftforge.registries.RegistryObject");
                    if (registryObjectClass.isInstance(chaosShardObject)) {
                        // 调用get方法获取实际物品
                        java.lang.reflect.Method getMethod = registryObjectClass.getMethod("get");
                        Object chaosShardItem = getMethod.invoke(chaosShardObject);

                        // 创建掉落物
                        ItemStack chaosShardStack = new ItemStack((net.minecraft.world.item.Item) chaosShardItem, chaosDropCount);

                        // 直接掉落物品
                        Block.popResource(level, pos, chaosShardStack);

                        // 清除周围的水晶部分
                        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(pos.below(2), Blocks.AIR.defaultBlockState(), 3);

                        // 直接设置方块为空气，绕过onRemove方法的detonate调用
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

                        // 播放破坏音效
                        BlockBreakUtils.playBreakSoundWithCooldown(level, pos, state, player);

                        return true;
                    }
                } catch (Exception e) {
                    // 如果任何步骤失败，回退到正常的强制挖掘逻辑
                    UselessMod.LOGGER.debug("Failed to handle Chaos Crystal drops: {}", e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            // 如果Draconic Evolution模组没有安装，忽略此处理
        } catch (Exception e) {
            // 记录其他异常但不崩溃
            UselessMod.LOGGER.debug("Error handling Chaos Crystal: {}", e.getMessage());
        }

        return false;
    }
}
