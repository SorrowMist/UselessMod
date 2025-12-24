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
            // 含水方块，直接返回false，让原版破坏逻辑处理
            return false;
        }
        
        // 特殊处理混沌水晶：绕过击败混沌龙检查
        if (handleChaosCrystal(event, level, pos, state, player, stack)) {
            return true;
        }

        List<ItemStack> drops;
        
        // 1. 检查是否为精准采集模式
        boolean isSilkTouch = isSilkTouchMode(stack);
        
        if (isSilkTouch) {
            // 精准采集模式：优先获取物品本身（包含NBT）
            ItemStack silkTouchDrop = createSilkTouchDrop(level, pos, state);
            drops = Collections.singletonList(silkTouchDrop);
            // 注意：在精准采集模式下，箱子内容物会随箱子一起销毁，不会产生额外掉落物
        } else {
            // 非精准采集模式：使用正常逻辑获取掉落物
            // 2. 尝试获取方块的掉落物（使用正常逻辑，包括战利品表和时运）
            drops = BlockBreakUtils.getBlockDrops(state, level, pos, player, stack);

            // 3. 如果没有有效的掉落物，强制掉落一个方块
            if (drops == null || drops.isEmpty() || BlockBreakUtils.hasInvalidDrops(drops)) {
                // 强制掉落一个目标方块
                ItemStack forcedDrop = new ItemStack(state.getBlock().asItem(), 1);
                drops = Collections.singletonList(forcedDrop);
            }
        }

        // 4. 强制破坏方块 - 最后破坏方块，这样确保获取了需要的物品
        event.setCanceled(true);
        
        if (isSilkTouch) {
            // 在精准采集模式下，先清除原方块实体的内容物，以防止内容物掉落
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                // 尝试获取并清空容器内容物，防止其作为掉落物生成
                try {
                    net.minecraftforge.items.IItemHandler itemHandler = 
                        blockEntity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
                    if (itemHandler != null) {
                        // 尝试将处理程序转换为可修改的类型
                        if (itemHandler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiableHandler) {
                            for (int i = 0; i < modifiableHandler.getSlots(); i++) {
                                modifiableHandler.setStackInSlot(i, ItemStack.EMPTY); // 清空每个槽位
                            }
                        }
                    }
                } catch (Exception e) {
                    // 如果处理失败，记录错误但继续执行
                    UselessMod.LOGGER.debug("Failed to clear container contents: {}", e.getMessage());
                }
                
                // 额外添加SophisticatedStorage模组的扩展槽位清理
                try {
                    // 检查是否是SophisticatedStorage的存储方块实体
                    if (blockEntity.getClass().getName().contains("sophisticatedstorage")) {
                        // 方法1：尝试通过反射清空升级槽位
                        try {
                            // 尝试获取storageWrapper并清空升级槽位
                            java.lang.reflect.Field storageWrapperField = blockEntity.getClass().getDeclaredField("storageWrapper");
                            storageWrapperField.setAccessible(true);
                            Object storageWrapper = storageWrapperField.get(blockEntity);
                            
                            // 尝试获取upgradeHandler
                            java.lang.reflect.Method getUpgradeHandlerMethod = storageWrapper.getClass().getMethod("getUpgradeHandler");
                            Object upgradeHandler = getUpgradeHandlerMethod.invoke(storageWrapper);
                            
                            // 清空升级槽位
                            if (upgradeHandler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiableUpgradeHandler) {
                                for (int i = 0; i < modifiableUpgradeHandler.getSlots(); i++) {
                                    modifiableUpgradeHandler.setStackInSlot(i, ItemStack.EMPTY);
                                }
                            }
                        } catch (Exception e) {
                            // 如果反射方法失败，尝试方法2：直接操作NBT数据
                            UselessMod.LOGGER.debug("Reflection method failed, trying NBT method: {}", e.getMessage());
                        }
                        
                        // 方法2：直接操作NBT数据清理升级槽位
                        CompoundTag blockEntityTag = blockEntity.saveWithoutMetadata();
                        if (blockEntityTag.contains("storageWrapper")) {
                            CompoundTag storageWrapperTag = blockEntityTag.getCompound("storageWrapper");
                            if (storageWrapperTag.contains("contents")) {
                                CompoundTag contentsTag = storageWrapperTag.getCompound("contents");
                                if (contentsTag.contains("upgradeInventory")) {
                                    // 清空升级槽位数据
                                    CompoundTag upgradeInventoryTag = new CompoundTag();
                                    upgradeInventoryTag.putInt("Size", 0);
                                    upgradeInventoryTag.put("Items", new net.minecraft.nbt.ListTag());
                                    contentsTag.put("upgradeInventory", upgradeInventoryTag);
                                    
                                    // 更新storageWrapper标签
                                    storageWrapperTag.put("contents", contentsTag);
                                    
                                    // 更新方块实体标签
                                    blockEntityTag.put("storageWrapper", storageWrapperTag);
                                    
                                    // 将修改后的NBT数据加载回方块实体
                                    blockEntity.load(blockEntityTag);
                                }
                            }
                        }
                        
                        // 强制更新方块实体的NBT数据，确保清理后的状态被保存
                        blockEntity.setChanged();
                    }
                } catch (Exception e) {
                    // 如果处理SophisticatedStorage升级槽位失败，记录错误但继续执行
                    UselessMod.LOGGER.debug("Failed to clear SophisticatedStorage upgrade slots: {}", e.getMessage());
                }
            }
        }
        
        // 破坏方块，但不产生额外的掉落物
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.sendBlockUpdated(pos, state, Blocks.AIR.defaultBlockState(), 3);
        
        // 3. 处理掉落物：优先存入AE网络，然后是玩家背包，最后是掉落
        EndlessBeafItem.handleDrops(drops, player, stack);

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
            // 确保所有数据都已同步到NBT
            if (blockEntity instanceof net.minecraft.world.level.block.entity.BlockEntity) {
                net.minecraft.world.level.block.entity.BlockEntity be = (net.minecraft.world.level.block.entity.BlockEntity) blockEntity;
                be.setChanged(); // 标记为已更改，确保数据被保存
            }
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
                    java.lang.reflect.Field chaosShardField = deConfigClass.getDeclaredField("CHAOS_SHARD");
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