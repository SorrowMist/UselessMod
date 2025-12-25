package com.sorrowmist.useless.items;

/*
 * This file is based on Apotheosis.
 * 
 * Copyright (c) 2023 Brennan Ward
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.client.KeyBindings;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.modes.ModeManager;
import com.sorrowmist.useless.modes.ToolMode;
import com.sorrowmist.useless.utils.BlockBreakUtils;
import com.sorrowmist.useless.utils.ForceBreakUtils;
import com.sorrowmist.useless.utils.NormalChainMiningUtils;
import com.sorrowmist.useless.utils.EnhancedChainMiningUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.SavedData;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

// AE2相关导入
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.PlayerSource;
import appeng.api.storage.StorageHelper;
import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class EndlessBeafItem extends PickaxeItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 按键状态跟踪
    private static final Map<UUID, Long> lastKeyPressTime = new HashMap<>();
// 不再需要防抖动处理，已移除 防抖动超时时间
    
    // AE2无线访问点链接相关
    private static final String TAG_ACCESS_POINT_POS = "accessPoint";
    public static final IGridLinkableHandler LINKABLE_HANDLER = new LinkableHandler();

    // 飞行状态跟踪，避免重复设置导致卡顿
    private static final Map<UUID, Boolean> playerFlightStatus = new HashMap<>();
    
    // 扩展样板供应器主从同步相关
    public static final Map<PatternProviderKey, Set<PatternProviderKey>> masterToSlaves = new HashMap<>();
    public static final Map<PatternProviderKey, PatternProviderKey> slaveToMaster = new HashMap<>();
    private static final Map<PatternProviderKey, Long> lastSyncTime = new HashMap<>();
    private static final long SYNC_INTERVAL = 1000; // 同步间隔，防止频繁同步
    private static final String SYNC_DATA_TAG = "PatternProviderSyncData";
    public static PatternProviderKey currentSelectedMaster = null; // 当前选择的主方块
    
    // 用于处理物品与无线访问点的绑定
    private static class LinkableHandler implements IGridLinkableHandler {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof EndlessBeafItem;
        }

        @Override
        public void link(ItemStack itemStack, GlobalPos pos) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                    .result()
                    .ifPresent(tag -> itemStack.getOrCreateTag().put(TAG_ACCESS_POINT_POS, tag));
        }

        @Override
        public void unlink(ItemStack itemStack) {
            itemStack.removeTagKey(TAG_ACCESS_POINT_POS);
        }
    }
    
    // 用于表示扩展样板供应器的键，包含BlockPos和Direction
    public static class PatternProviderKey {
        private final BlockPos pos;
        private final Direction direction;
        
        public PatternProviderKey(BlockPos pos, Direction direction) {
            this.pos = pos;
            this.direction = direction;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PatternProviderKey that = (PatternProviderKey) o;
            return pos.equals(that.pos) && direction == that.direction;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(pos, direction);
        }
        
        public BlockPos getPos() {
            return pos;
        }
        
        public Direction getDirection() {
            return direction;
        }
    }
    
    // 用于存储同步数据的SavedData类
    private static class PatternProviderSyncData extends SavedData {
        private final Map<PatternProviderKey, Set<PatternProviderKey>> masterToSlaves = new HashMap<>();
        private final Map<PatternProviderKey, PatternProviderKey> slaveToMaster = new HashMap<>();
        
        @Override
        public CompoundTag save(CompoundTag tag) {
            // 保存主从关系
            CompoundTag masterToSlavesTag = new CompoundTag();
            for (Map.Entry<PatternProviderKey, Set<PatternProviderKey>> entry : this.masterToSlaves.entrySet()) {
                PatternProviderKey masterKey = entry.getKey();
                Set<PatternProviderKey> slaves = entry.getValue();
                
                ListTag slaveList = new ListTag();
                for (PatternProviderKey slave : slaves) {
                    CompoundTag posTag = new CompoundTag();
                    posTag.putInt("x", slave.getPos().getX());
                    posTag.putInt("y", slave.getPos().getY());
                    posTag.putInt("z", slave.getPos().getZ());
                    posTag.putString("direction", slave.getDirection().getName());
                    slaveList.add(posTag);
                }
                
                String masterKeyStr = masterKey.getPos().getX() + "," + masterKey.getPos().getY() + "," + masterKey.getPos().getZ() + "," + masterKey.getDirection().getName();
                masterToSlavesTag.put(masterKeyStr, slaveList);
            }
            tag.put("MasterToSlaves", masterToSlavesTag);
            
            // 保存从主关系
            CompoundTag slaveToMasterTag = new CompoundTag();
            for (Map.Entry<PatternProviderKey, PatternProviderKey> entry : this.slaveToMaster.entrySet()) {
                PatternProviderKey slave = entry.getKey();
                PatternProviderKey master = entry.getValue();
                
                String slaveKeyStr = slave.getPos().getX() + "," + slave.getPos().getY() + "," + slave.getPos().getZ() + "," + slave.getDirection().getName();
                CompoundTag masterTag = new CompoundTag();
                masterTag.putInt("x", master.getPos().getX());
                masterTag.putInt("y", master.getPos().getY());
                masterTag.putInt("z", master.getPos().getZ());
                masterTag.putString("direction", master.getDirection().getName());
                slaveToMasterTag.put(slaveKeyStr, masterTag);
            }
            tag.put("SlaveToMaster", slaveToMasterTag);
            
            return tag;
        }
        
        public static PatternProviderSyncData load(CompoundTag tag) {
            PatternProviderSyncData data = new PatternProviderSyncData();
            
            // 加载主从关系
            if (tag.contains("MasterToSlaves")) {
                CompoundTag masterToSlavesTag = tag.getCompound("MasterToSlaves");
                for (String masterKeyStr : masterToSlavesTag.getAllKeys()) {
                    ListTag slaveList = masterToSlavesTag.getList(masterKeyStr, CompoundTag.TAG_COMPOUND);
                    
                    // 解析主方块位置和方向
                    String[] masterCoords = masterKeyStr.split(",");
                    if (masterCoords.length == 4) {
                        try {
                            BlockPos masterPos = new BlockPos(
                                    Integer.parseInt(masterCoords[0]),
                                    Integer.parseInt(masterCoords[1]),
                                    Integer.parseInt(masterCoords[2])
                            );
                            Direction masterDirection = Direction.byName(masterCoords[3]);
                            PatternProviderKey masterKey = new PatternProviderKey(masterPos, masterDirection);
                            
                            // 解析从方块位置和方向
                            Set<PatternProviderKey> slaves = new HashSet<>();
                            for (int i = 0; i < slaveList.size(); i++) {
                                CompoundTag posTag = slaveList.getCompound(i);
                                BlockPos slavePos = new BlockPos(
                                        posTag.getInt("x"),
                                        posTag.getInt("y"),
                                        posTag.getInt("z")
                                );
                                Direction slaveDirection = Direction.byName(posTag.getString("direction"));
                                slaves.add(new PatternProviderKey(slavePos, slaveDirection));
                            }
                            
                            data.masterToSlaves.put(masterKey, slaves);
                        } catch (NumberFormatException e) {
                            // 忽略格式错误的坐标
                        }
                    }
                }
            }
            
            // 加载从主关系
            if (tag.contains("SlaveToMaster")) {
                CompoundTag slaveToMasterTag = tag.getCompound("SlaveToMaster");
                for (String slaveKeyStr : slaveToMasterTag.getAllKeys()) {
                    CompoundTag masterTag = slaveToMasterTag.getCompound(slaveKeyStr);
                    
                    // 解析从方块位置和方向
                    String[] slaveCoords = slaveKeyStr.split(",");
                    if (slaveCoords.length == 4) {
                        try {
                            BlockPos slavePos = new BlockPos(
                                    Integer.parseInt(slaveCoords[0]),
                                    Integer.parseInt(slaveCoords[1]),
                                    Integer.parseInt(slaveCoords[2])
                            );
                            Direction slaveDirection = Direction.byName(slaveCoords[3]);
                            PatternProviderKey slaveKey = new PatternProviderKey(slavePos, slaveDirection);
                            
                            // 解析主方块位置和方向
                            BlockPos masterPos = new BlockPos(
                                    masterTag.getInt("x"),
                                    masterTag.getInt("y"),
                                    masterTag.getInt("z")
                            );
                            Direction masterDirection = Direction.byName(masterTag.getString("direction"));
                            PatternProviderKey masterKey = new PatternProviderKey(masterPos, masterDirection);
                            
                            data.slaveToMaster.put(slaveKey, masterKey);
                        } catch (NumberFormatException e) {
                            // 忽略格式错误的坐标
                        }
                    }
                }
            }
            
            return data;
        }
        
        public Map<PatternProviderKey, Set<PatternProviderKey>> getMasterToSlaves() {
            return masterToSlaves;
        }
        
        public Map<PatternProviderKey, PatternProviderKey> getSlaveToMaster() {
            return slaveToMaster;
        }
        
        public void clear() {
            masterToSlaves.clear();
            slaveToMaster.clear();
        }
    }
    


    public EndlessBeafItem(Tier pTier, int pAttackDamageModifier, float pAttackSpeedModifier, Properties pProperties) {
        super(pTier, pAttackDamageModifier, pAttackSpeedModifier, pProperties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false; // 物品不可损坏
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return 0; // 最大耐久为0，表示无限
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false; // 不显示耐久条
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        // 阻止任何耐久度设置
        super.setDamage(stack, 0);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        // 返回物品本身，使其在合成后保留在工作台中
        return stack.copy();
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        // 确保该物品有剩余物品（即本身）
        return true;
    }

    // 模式管理器实例
    private final ModeManager modeManager = new ModeManager();
    
    // 检查是否处于精准采集模式
    public boolean isSilkTouchMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.SILK_TOUCH);
    }
    
    // 设置连锁挖掘按键按下状态
    public void setChainMiningPressedState(ItemStack stack, boolean isPressed) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("ChainMiningPressed", isPressed);
        stack.setTag(tag);
    }
    
    // 获取连锁挖掘按键按下状态
    public boolean isChainMiningPressed(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("ChainMiningPressed");
    }
    
    // 检查是否应该启用连锁挖掘（根据按键状态和模式）
    public boolean shouldUseChainMining(ItemStack stack) {
        modeManager.loadFromStack(stack);
        // 简化连锁挖掘激活条件：只要按住连锁挖掘按键就启用
        return isChainMiningPressed(stack);
    }
    
    // 获取强化连锁模式
    public boolean isEnhancedChainMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.ENHANCED_CHAIN_MINING);
    }
    
    // 设置强化连锁模式
    public void setEnhancedChainMiningMode(ItemStack stack, boolean enabled) {
        modeManager.loadFromStack(stack);
        modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.ENHANCED_CHAIN_MINING, enabled);
        modeManager.saveToStack(stack);
    }
    
    // 切换强化连锁模式
    public boolean toggleEnhancedChainMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        modeManager.toggleMode(com.sorrowmist.useless.modes.ToolMode.ENHANCED_CHAIN_MINING);
        modeManager.saveToStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.ENHANCED_CHAIN_MINING);
    }
    
    // 检查是否处于强制挖掘模式
    public boolean isForceMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.FORCE_MINING);
    }
    
    // 切换强制挖掘模式
    public boolean toggleForceMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        modeManager.toggleMode(com.sorrowmist.useless.modes.ToolMode.FORCE_MINING);
        modeManager.saveToStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.FORCE_MINING);
    }
    
    // 检查是否处于AE存储优先模式
    public boolean isAEStoragePriorityMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.AE_STORAGE_PRIORITY);
    }
    
    // 切换AE存储优先模式
    public boolean toggleAEStoragePriorityMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        modeManager.toggleMode(com.sorrowmist.useless.modes.ToolMode.AE_STORAGE_PRIORITY);
        modeManager.saveToStack(stack);
        return modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.AE_STORAGE_PRIORITY);
    }
    
    // 获取链接的无线访问点位置
    @Nullable
    public static GlobalPos getLinkedPosition(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_ACCESS_POINT_POS, Tag.TAG_COMPOUND)) {
            return GlobalPos.CODEC.decode(NbtOps.INSTANCE, tag.get(TAG_ACCESS_POINT_POS))
                    .result()
                    .map(Pair::getFirst)
                    .orElse(null);
        }
        return null;
    }
    
    // 获取链接的AE网格
    @Nullable
    public static IGrid getLinkedGrid(ItemStack stack, Level level, @Nullable Player player) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        
        GlobalPos linkedPos = getLinkedPosition(stack);
        if (linkedPos == null) {
            return null;
        }
        
        net.minecraft.server.level.ServerLevel linkedLevel = serverLevel.getServer().getLevel(linkedPos.dimension());
        if (linkedLevel == null) {
            return null;
        }
        
        net.minecraft.world.level.block.entity.BlockEntity be = linkedLevel.getBlockEntity(linkedPos.pos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            return null;
        }
        
        return accessPoint.getGrid();
    }
    
    // 将物品栈存入AE网络
    private static boolean storeItemInAENetwork(ItemStack stack, Player player) {
        return storeItemInAENetwork(stack, player, null);
    }
    
    // 将物品栈存入AE网络（带工具参数）
    private static boolean storeItemInAENetwork(ItemStack stack, Player player, ItemStack toolStack) {
        if (player == null || stack.isEmpty()) {
            return false;
        }
        
        // 获取工具
        ItemStack toolItem = toolStack;
        if (toolItem == null || toolItem.isEmpty()) {
            // 如果没有提供工具参数，从玩家手中获取
            ItemStack mainHandItem = player.getMainHandItem();
            ItemStack offHandItem = player.getOffhandItem();
            
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                toolItem = mainHandItem;
            } else if (offHandItem.getItem() instanceof EndlessBeafItem) {
                toolItem = offHandItem;
            } else {
                return false;
            }
        }
        
        // 检查是否启用了AE存储优先模式
        EndlessBeafItem tool = (EndlessBeafItem) toolItem.getItem();
        boolean isAEStoragePriority = tool.isAEStoragePriorityMode(toolItem);
        if (!isAEStoragePriority) {
            UselessMod.LOGGER.debug("AE存储优先模式未启用，工具: {}", toolItem);
            return false;
        }
        
        // 尝试从链接的无线访问点获取AE网络
        try {
            // 获取链接的网格
            IGrid grid = getLinkedGrid(toolItem, player.level(), player);
            if (grid == null) {
                UselessMod.LOGGER.debug("无法获取AE网格，工具: {}", toolItem);
                return false;
            }
            
            // 获取物品存储处理程序
            MEStorage storage = grid.getStorageService().getInventory();
            if (storage == null) {
                UselessMod.LOGGER.debug("无法获取AE存储服务，工具: {}", toolItem);
                return false;
            }
            
            // 转换为AE物品栈
            AEItemKey aeKey = AEItemKey.of(stack);
            if (aeKey == null) {
                UselessMod.LOGGER.debug("无法转换为AE物品键，物品: {}", stack);
                return false;
            }
            
            // 存入AE网络
            long inserted = storage.insert(aeKey, stack.getCount(), appeng.api.config.Actionable.MODULATE, new PlayerSource(player, null));
            // 如果插入的数量等于物品栈数量，说明全部存入
            if (inserted == stack.getCount()) {
                UselessMod.LOGGER.debug("成功存入AE网络，物品: {}，数量: {}", stack, inserted);
                return true;
            } else if (inserted > 0) {
                // 更新物品栈为剩余数量
                stack.setCount((int) (stack.getCount() - inserted));
                UselessMod.LOGGER.debug("部分存入AE网络，物品: {}，存入: {}，剩余: {}", stack.getItem(), inserted, stack.getCount());
                return stack.isEmpty();
            } else {
                UselessMod.LOGGER.debug("AE网络存储失败，物品: {}，插入数量: 0", stack);
                return false;
            }
        } catch (Exception e) {
            UselessMod.LOGGER.debug("AE存储过程中出现异常: {}", e.getMessage());
            // 忽略任何异常
        }
        
        return false;
    }
    
    // 将物品列表存入AE网络或玩家背包
    public static void handleDrops(List<ItemStack> drops, Player player) {
        handleDrops(drops, player, null);
    }
    
    // 将物品列表存入AE网络或玩家背包（带工具参数）
    public static void handleDrops(List<ItemStack> drops, Player player, ItemStack toolStack) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        
        // 遍历所有掉落物品
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack dropStack = iterator.next();
            if (dropStack.isEmpty()) {
                iterator.remove();
                continue;
            }
            
            // 尝试存入AE网络
            if (storeItemInAENetwork(dropStack, player, toolStack)) {
                iterator.remove();
                continue;
            }
            
            // 尝试存入玩家背包
            if (!player.getInventory().add(dropStack)) {
                // 背包已满，保留在掉落列表中，稍后生成物品实体
                continue;
            }
            
            // 背包添加成功，从掉落列表中移除
            iterator.remove();
        }
    }
    
    // 处理增强连锁模式切换按键
    private void handleEnhancedChainMiningKey(ItemStack stack, Player player) {
        // 发送数据包到服务器，由服务器处理增强连锁模式切换
        // 客户端不再直接修改状态，而是等待服务器的响应
        com.sorrowmist.useless.networking.ModMessages.sendToServer(new com.sorrowmist.useless.networking.EnhancedChainMiningTogglePacket());
    }
    
    // 处理强制挖掘模式切换按键
    private void handleForceMiningKey(ItemStack stack, Player player) {
        // 发送数据包到服务器，由服务器处理强制挖掘模式切换
        // 客户端不再直接修改状态，而是等待服务器的响应
        com.sorrowmist.useless.networking.ModMessages.sendToServer(new com.sorrowmist.useless.networking.ForceMiningTogglePacket());
    }
    
    // 处理设置主扩展样板供应器按键（M键）
    private void handleSetMasterPatternKey(ItemStack stack, Player player) {
        // 获取玩家看向的方块
        double reachDistance = 4.5D;
        net.minecraft.world.phys.HitResult hitResult = player.pick(reachDistance, 0.0F, false);
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult blockHitResult = (net.minecraft.world.phys.BlockHitResult) hitResult;
            BlockPos pos = blockHitResult.getBlockPos();
            Direction direction = blockHitResult.getDirection();
            // 发送数据包到服务器，由服务器处理设置主方块
            com.sorrowmist.useless.networking.ModMessages.sendToServer(new com.sorrowmist.useless.networking.SetMasterPatternPacket(pos, direction));
        } else if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            // 如果点击到空气，发送数据包到服务器，由服务器处理重置主方块选择
            com.sorrowmist.useless.networking.ModMessages.sendToServer(new com.sorrowmist.useless.networking.ResetMasterPatternPacket());
        }
    }
    
    // 处理设置从扩展样板供应器按键（S键）
    private void handleSetSlavePatternKey(ItemStack stack, Player player) {
        // 获取玩家看向的方块
        double reachDistance = 4.5D;
        net.minecraft.world.phys.HitResult hitResult = player.pick(reachDistance, 0.0F, false);
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult blockHitResult = (net.minecraft.world.phys.BlockHitResult) hitResult;
            BlockPos pos = blockHitResult.getBlockPos();
            Direction direction = blockHitResult.getDirection();
            // 发送数据包到服务器，由服务器处理设置从方块
            com.sorrowmist.useless.networking.ModMessages.sendToServer(new com.sorrowmist.useless.networking.SetSlavePatternPacket(pos, direction));
        }
    }

    // 更新实际的附魔NBT
    public void updateEnchantments(ItemStack stack) {
        // 保存关键状态（使用局部变量存储，确保不会丢失）
        ModeManager modeManager = new ModeManager();
        modeManager.loadFromStack(stack);
        
        boolean enhancedChainMining = isEnhancedChainMiningMode(stack);
        boolean silkTouchMode = isSilkTouchMode(stack);
        boolean chainMiningPressed = isChainMiningPressed(stack);
        
        // 获取现有的所有附魔
        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(stack));
        // 使用配置中的抢夺等级
        enchantments.put(Enchantments.MOB_LOOTING, ConfigManager.getLootingLevel());

        if (silkTouchMode) {
            // 精准采集模式
            enchantments.remove(Enchantments.BLOCK_FORTUNE); // 移除时运
            // 确保有精准采集，使用最高等级
            int silkTouchLevel = Math.max(1, enchantments.getOrDefault(Enchantments.SILK_TOUCH, 0));
            enchantments.put(Enchantments.SILK_TOUCH, silkTouchLevel);
        } else {
            // 时运模式
            enchantments.remove(Enchantments.SILK_TOUCH); // 移除精准采集
            // 确保有时运，使用配置中的等级
            int fortuneLevel = Math.max(ConfigManager.getFortuneLevel(), enchantments.getOrDefault(Enchantments.BLOCK_FORTUNE, 0));
            enchantments.put(Enchantments.BLOCK_FORTUNE, fortuneLevel);
        }

        // 应用更新后的附魔
        EnchantmentHelper.setEnchantments(enchantments, stack);
        
        // 强制恢复关键状态标签（即使setEnchantments替换了整个NBT也能恢复）
        // 使用getOrCreateTag确保标签存在
        CompoundTag finalTag = stack.getOrCreateTag();
        finalTag.putBoolean("EnhancedChainMining", enhancedChainMining);
        finalTag.putBoolean("SilkTouchMode", silkTouchMode);
        finalTag.putBoolean("ChainMiningPressed", chainMiningPressed);
        
        // 设置模型切换谓词值
        if (silkTouchMode) {
            finalTag.putFloat("useless_mod:silk_touch_mode", 1.0f);
        } else {
            finalTag.remove("useless_mod:silk_touch_mode");
        }
        
        // 确保标签被正确应用到物品上
        stack.setTag(finalTag);
        
        // 更新工具模式标签
        updateToolModeTags(stack, modeManager);
    }
    
    // 检查是否安装了格雷科技mod
    private boolean isGTCEUInstalled() {
        return net.minecraftforge.fml.ModList.get().isLoaded("gtceu");
    }
    
    // 更新工具模式，通过NBT标签跟踪激活的工具模式
    private void updateToolModeTags(ItemStack stack, ModeManager modeManager) {
        // 在NBT中存储激活的工具模式，以便在游戏逻辑中使用
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag toolModesTag = tag.getCompound("ToolModes");
        
        // 保存工具模式状态，这些状态将在游戏逻辑中用于判断工具行为
        // 实际的标签处理将在物品交互时根据这些状态进行判断
        
        // 清除旧的工具模式标签（如果存在）
        tag.remove("ActiveToolTag");
        
        // 根据激活的模式设置活动工具标签
        if (modeManager.isModeActive(ToolMode.WRENCH_MODE)) {
            tag.putString("ActiveToolTag", "forge:tools/wrenches");
        } else if (isGTCEUInstalled() && modeManager.isModeActive(ToolMode.SCREWDRIVER_MODE)) {
            tag.putString("ActiveToolTag", "forge:tools/screwdrivers");
        } else if (isGTCEUInstalled() && modeManager.isModeActive(ToolMode.MALLET_MODE)) {
            tag.putString("ActiveToolTag", "forge:tools/mallets");
        } else if (modeManager.isModeActive(ToolMode.OMNITOOL_MODE)) {
            // 添加兼容性检查，确保omnitools模组已安装
            net.minecraft.resources.ResourceLocation omnitoolId = new net.minecraft.resources.ResourceLocation("omnitools:omni_wrench");
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(omnitoolId)) {
                tag.putString("ActiveToolTag", "forge:tools/wrenches");
            } else {
                // 如果omnitools模组未安装，移除OMNITOOL_MODE模式
                tag.remove("ActiveToolTag");
            }
        }
        
        // 将更新后的标签放回stack
        stack.setTag(tag);
    }
    
    // 根据激活的模式切换物品实例
    public ItemStack switchToolModeItem(ItemStack oldStack, ModeManager modeManager) {
        // 创建新的物品实例，根据激活的模式选择对应的子类
        ItemStack newStack = ItemStack.EMPTY;
        
        // 检查是否安装了格雷科技mod
        boolean isGTCEUInstalled = isGTCEUInstalled();
        
        // 检查激活的工具模式
        boolean hasWrenchMode = modeManager.isModeActive(ToolMode.WRENCH_MODE);
        boolean hasScrewdriverMode = isGTCEUInstalled && modeManager.isModeActive(ToolMode.SCREWDRIVER_MODE);
        boolean hasMalletMode = isGTCEUInstalled && modeManager.isModeActive(ToolMode.MALLET_MODE);
        boolean hasCrowbarMode = isGTCEUInstalled && modeManager.isModeActive(ToolMode.CROWBAR_MODE);
        boolean hasHammerMode = isGTCEUInstalled && modeManager.isModeActive(ToolMode.HAMMER_MODE);
        boolean hasOmnitoolMode = modeManager.isModeActive(ToolMode.OMNITOOL_MODE);
        
        // 如果未安装格雷科技mod，禁用相关模式
        if (!isGTCEUInstalled) {
            if (modeManager.isModeActive(ToolMode.SCREWDRIVER_MODE) || 
                modeManager.isModeActive(ToolMode.MALLET_MODE) || 
                modeManager.isModeActive(ToolMode.CROWBAR_MODE) || 
                modeManager.isModeActive(ToolMode.HAMMER_MODE)) {
                // 禁用相关模式
                modeManager.setModeActive(ToolMode.SCREWDRIVER_MODE, false);
                modeManager.setModeActive(ToolMode.MALLET_MODE, false);
                modeManager.setModeActive(ToolMode.CROWBAR_MODE, false);
                modeManager.setModeActive(ToolMode.HAMMER_MODE, false);
                modeManager.saveToStack(oldStack);
            }
        }
        
        if (hasWrenchMode) {
            // 创建扳手实例
            newStack = new ItemStack(ENDLESS_BEAF_WRENCH.get());
        } else if (hasScrewdriverMode) {
            // 创建螺丝刀实例
            newStack = new ItemStack(ENDLESS_BEAF_SCREWDRIVER.get());
        } else if (hasMalletMode) {
            // 创建锤子实例
            newStack = new ItemStack(ENDLESS_BEAF_MALLET.get());
        } else if (hasCrowbarMode) {
            // 创建撬棍实例
            newStack = new ItemStack(ENDLESS_BEAF_CROWBAR.get());
        } else if (hasHammerMode) {
            // 创建铁锤实例
            newStack = new ItemStack(ENDLESS_BEAF_HAMMER.get());
        } else if (hasOmnitoolMode) {
            // 创建Omnitool扳手实例 - 通过物品ID获取
            // 添加兼容性检查，确保omnitools模组已安装
            net.minecraft.resources.ResourceLocation omnitoolId = new net.minecraft.resources.ResourceLocation("omnitools:omni_wrench");
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(omnitoolId)) {
                newStack = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(omnitoolId));
            } else {
                // 如果omnitools模组未安装，使用基础实例
                newStack = new ItemStack(ENDLESS_BEAF_ITEM.get());
                // 禁用OMNITOOL_MODE模式
                modeManager.setModeActive(ToolMode.OMNITOOL_MODE, false);
                modeManager.saveToStack(oldStack);
            }
        } else {
            // 如果没有激活的工具模式，使用基础实例（具有所有标签）
            newStack = new ItemStack(ENDLESS_BEAF_ITEM.get());
        }
        
        // 复制原有物品的所有NBT数据到新实例
        if (oldStack.hasTag() && !newStack.isEmpty()) {
            newStack.setTag(oldStack.getTag().copy());
        }
        
        // 更新新实例的附魔NBT，确保模型切换谓词值被正确设置
        updateEnchantments(newStack);
        
        return newStack;
    }
    
    
    // 添加PlayerTickEvent监听器，用于持续检查玩家物品栏并管理飞行权限
    // 修复：当物品被移出物品栏时，确保飞行权限被正确关闭
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player == null) return;
        
        // 只在服务器端执行，避免客户端同步问题
        if (player.level().isClientSide()) return;
        
        // 检查玩家物品栏中是否有任何EndlessBeafItem变体
        boolean hasItemInInventory = player.getInventory().items.stream()
                    .anyMatch(item -> item.getItem() instanceof EndlessBeafItem || 
                    (item.hasTag() && item.getTag().contains("ToolModes")));
        
        UUID playerId = player.getUUID();
        Boolean currentFlightStatus = playerFlightStatus.getOrDefault(playerId, false);
        
        if (hasItemInInventory) {
                // 物品在物品栏中，赋予飞行权限
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, true);
                
                // 给予饱和效果（不显示粒子，但显示图标）
                MobEffectInstance baohe = player.getEffect(MobEffects.SATURATION);
                if (baohe == null || baohe.getDuration() < 20) {
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0, true, false, true));
                }

                // 给予生命恢复效果（不显示粒子，但显示图标）
                MobEffectInstance zaisheng = player.getEffect(MobEffects.REGENERATION);
                if (zaisheng == null || (zaisheng.getDuration() < 20)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 5, true, false, true));
                }

                // 给予夜视效果（不显示粒子，但显示图标）
                MobEffectInstance yeshi = player.getEffect(MobEffects.NIGHT_VISION);
                if (yeshi == null || (yeshi.getDuration() < 2000)) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20000, 0, true, false, true));
                }
                
                // 新增：给予抗火效果（不显示粒子，但显示图标）
                MobEffectInstance kanghuo = player.getEffect(MobEffects.FIRE_RESISTANCE);
                if (kanghuo == null || kanghuo.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, true, false, true));
                }
                
                // 新增：给予水下呼吸效果（不显示粒子，但显示图标）
                MobEffectInstance shuixiabreath = player.getEffect(MobEffects.WATER_BREATHING);
                if (shuixiabreath == null || shuixiabreath.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 2000, 0, true, false, true));
                }
                
                // 新增：给予抗性提升效果（不显示粒子，但显示图标）
                MobEffectInstance kangxing = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (kangxing == null || kangxing.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2000, 5, true, false, true));
                }
            } else {
                // 物品不在物品栏中，对于非创造模式玩家，关闭飞行权限
                // 只有当玩家是因为我们的工具才获得飞行权限的情况下，才会关闭飞行权限
                if (!player.isCreative() && player.getAbilities().mayfly && playerFlightStatus.getOrDefault(playerId, false)) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, false);
            }
    }

    // 切换模式的方法（供数据包调用）
    public void switchEnchantmentMode(ItemStack stack, boolean silkTouchMode) {
        // 保存当前的连锁挖掘按键状态
        boolean chainMiningPressed = isChainMiningPressed(stack);
        
        // 使用模式管理器切换模式，保持其他模式状态
        modeManager.loadFromStack(stack);
        
        // 保存当前所有模式状态
        boolean aeStoragePriority = modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.AE_STORAGE_PRIORITY);
        boolean forceMining = modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.FORCE_MINING);
        boolean enhancedChainMining = modeManager.isModeActive(com.sorrowmist.useless.modes.ToolMode.ENHANCED_CHAIN_MINING);
        
        if (silkTouchMode) {
            modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.SILK_TOUCH, true);
            modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.FORTUNE, false);
        } else {
            modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.SILK_TOUCH, false);
            modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.FORTUNE, true);
        }
        
        // 恢复其他重要模式状态
        modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.AE_STORAGE_PRIORITY, aeStoragePriority);
        modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.FORCE_MINING, forceMining);
        modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.ENHANCED_CHAIN_MINING, enhancedChainMining);
        
        modeManager.saveToStack(stack);
        
        // 更新实际的附魔NBT
        updateEnchantments(stack);
        
        // 恢复连锁挖掘按键状态
        setChainMiningPressedState(stack, chainMiningPressed);
        
        // 强制客户端更新物品渲染
        if (!stack.isEmpty()) {
            // 通过修改NBT强制更新
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong("LastModeSwitch", System.currentTimeMillis());
            stack.setTag(tag);
        }
    }
    
    // 按键触发强制破坏的方法（供数据包调用）
    public void triggerForceMining(ItemStack stack, Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        
        // 检查是否处于强制挖掘模式
        if (!isForceMiningMode(stack)) {
            return;
        }
        
        // 获取玩家视线内的方块
        double reachDistance = 4.5D; // 1.20.1中的默认交互范围
        net.minecraft.world.phys.HitResult hitResult = player.pick(reachDistance, 0.0F, false);
        
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult blockHitResult = (net.minecraft.world.phys.BlockHitResult) hitResult;
            BlockPos pos = blockHitResult.getBlockPos();
            Level level = player.level();
            BlockState state = level.getBlockState(pos);
            
            // 检查是否应该启用连锁挖掘
            if (shouldUseChainMining(stack)) {
                // 连锁挖掘模式 - 执行连锁强制挖掘
                performChainForceMining(level, pos, state, player, stack);
            } else {
                // 普通模式 - 只强制挖掘当前方块
                BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                        level, pos, state, player
                );
                performForceMining(breakEvent, level, pos, state, player, stack);
            }
        }
    }
    
    // 处理方块破坏事件，用于管理主从样板供应器关系
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        BlockPos pos = event.getPos();
        
        // 检查所有主从关系，处理与该方块相关的所有主从关系
        List<PatternProviderKey> mastersToRemove = new ArrayList<>();
        List<PatternProviderKey> slavesToRemove = new ArrayList<>();
        
        // 遍历所有方向，检查是否有任何主端或从端位于该方块的任何方向
        for (Direction direction : Direction.values()) {
            // 检查所有主端，无论方向如何
            for (PatternProviderKey key : masterToSlaves.keySet()) {
                if (key.getPos().equals(pos)) {
                    mastersToRemove.add(key);
                }
            }
            
            // 检查所有从端，无论方向如何
            for (PatternProviderKey key : slaveToMaster.keySet()) {
                if (key.getPos().equals(pos)) {
                    slavesToRemove.add(key);
                }
            }
        }
        
        // 如果没有相关的主从关系，直接返回
        if (mastersToRemove.isEmpty() && slavesToRemove.isEmpty()) {
            return;
        }
        
        // 处理所有相关的主端
        for (PatternProviderKey masterKey : mastersToRemove) {
            handleMasterBreak(levelAccessor, masterKey);
        }
        
        // 处理所有相关的从端
        for (PatternProviderKey slaveKey : slavesToRemove) {
            handleSlaveBreak(levelAccessor, slaveKey);
            clearSlavePatterns(levelAccessor, slaveKey);
        }
    }
    
    // 监听方块掉落事件，防止从端样板供应器掉落样板
    @SubscribeEvent
    public static void onBlockDrops(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        BlockPos pos = event.getPos();
        
        // 检查是否有任何从端位于该方块位置
        for (Map.Entry<PatternProviderKey, PatternProviderKey> entry : slaveToMaster.entrySet()) {
            PatternProviderKey slaveKey = entry.getKey();
            if (slaveKey.getPos().equals(pos)) {
                // 从端被破坏，确保内部样板不会掉落
                // 已经在handleSlaveBreak中清空了样板，这里可以添加额外的防护
                BlockEntity blockEntity = levelAccessor.getBlockEntity(pos);
                if (blockEntity != null) {
                    if (blockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
                        try {
                            // 再次确认清空样板，防止任何可能的掉落
                            var slaveLogic = slaveHost.getLogic();
                            var slaveInv = slaveLogic.getPatternInv();
                            slaveInv.clear();
                            slaveLogic.updatePatterns();
                        } catch (Exception e) {
                            // 忽略任何异常
                        }
                    } else if (blockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHost) {
                        try {
                            // 再次确认清空AdvancedAE高级样板供应器的样板
                            var aaeSlaveLogic = aaeSlaveHost.getLogic();
                            var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                            aaeSlaveInv.clear();
                            aaeSlaveLogic.updatePatterns();
                        } catch (Exception e) {
                            // 忽略任何异常
                        }
                    } else if (blockEntity instanceof appeng.api.parts.IPartHost slavePartHost) {
                        try {
                            // 检查指定方向的部件，再次确认清空样板
                            appeng.api.parts.IPart sidePart = slavePartHost.getPart(slaveKey.getDirection());
                            if (sidePart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                var slaveLogic = slaveHostPart.getLogic();
                                var slaveInv = slaveLogic.getPatternInv();
                                slaveInv.clear();
                                slaveLogic.updatePatterns();
                            } else if (sidePart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                                // 处理面板形式的AdvancedAE高级样板供应器
                                var aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                                var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                                aaeSlaveInv.clear();
                                aaeSlaveLogic.updatePatterns();
                            } else {
                                // 检查中心部件
                                appeng.api.parts.IPart centerPart = slavePartHost.getPart(null);
                                if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                    var slaveLogic = slaveHostPart.getLogic();
                                    var slaveInv = slaveLogic.getPatternInv();
                                    slaveInv.clear();
                                    slaveLogic.updatePatterns();
                                } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                                    // 处理中心部件形式的AdvancedAE高级样板供应器
                                    var aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                                    var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                                    aaeSlaveInv.clear();
                                    aaeSlaveLogic.updatePatterns();
                                }
                            }
                        } catch (Exception e) {
                            // 忽略任何异常
                        }
                    }
                }
                break;
            }
        }
    }
    
    // 处理主端样板供应器被破坏的逻辑
    public static void handleMasterBreak(LevelAccessor levelAccessor, PatternProviderKey masterKey) {
        // 获取所有从端
        Set<PatternProviderKey> slaves = masterToSlaves.remove(masterKey);
        if (slaves != null) {
            for (PatternProviderKey slaveKey : slaves) {
                // 清空从端的样板
                clearSlavePatterns(levelAccessor, slaveKey);
                // 移除从端到主端的映射
                slaveToMaster.remove(slaveKey);
                // 移除同步时间记录
                lastSyncTime.remove(slaveKey);
            }
        }
        
        // 如果当前选择的主方块是被破坏的主方块，重置选择
        if (currentSelectedMaster != null && currentSelectedMaster.equals(masterKey)) {
            currentSelectedMaster = null;
        }
        
        // 保存同步数据
        saveSyncDataStatic(levelAccessor);
    }
    
    // 处理从端样板供应器被破坏的逻辑
    public static void handleSlaveBreak(LevelAccessor levelAccessor, PatternProviderKey slaveKey) {
        // 获取对应的主端
        PatternProviderKey masterKey = slaveToMaster.remove(slaveKey);
        if (masterKey != null) {
            // 从主端的从端列表中移除该从端
            Set<PatternProviderKey> slaves = masterToSlaves.get(masterKey);
            if (slaves != null) {
                slaves.remove(slaveKey);
                // 如果主端没有从端了，移除主端映射
                if (slaves.isEmpty()) {
                    masterToSlaves.remove(masterKey);
                }
            }
            // 移除同步时间记录
            lastSyncTime.remove(slaveKey);
        }
        
        // 保存同步数据
        saveSyncDataStatic(levelAccessor);
    }
    
    // 清空从端样板供应器的样板
    public static void clearSlavePatterns(LevelAccessor levelAccessor, PatternProviderKey slaveKey) {
        BlockEntity slaveBlockEntity = levelAccessor.getBlockEntity(slaveKey.getPos());
        if (slaveBlockEntity == null) return;
        
        // 处理直接放置的样板供应器
        if (slaveBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
            try {
                // 获取从端的pattern inventory并清空
                var slaveLogic = slaveHost.getLogic();
                var slaveInv = slaveLogic.getPatternInv();
                slaveInv.clear();
                // 更新从端的patterns
                slaveLogic.updatePatterns();
            } catch (Exception e) {
                // 忽略任何异常
            }
        } else if (slaveBlockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHost) {
            try {
                // 获取AdvancedAE高级样板供应器的logic并清空
                var aaeSlaveLogic = aaeSlaveHost.getLogic();
                var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                aaeSlaveInv.clear();
                // 更新从端的patterns
                aaeSlaveLogic.updatePatterns();
            } catch (Exception e) {
                // 忽略任何异常
            }
        } else if (slaveBlockEntity instanceof appeng.api.parts.IPartHost slavePartHost) {
            // 处理面板形式的样板供应器
            try {
                // 检查指定方向的部件
                appeng.api.parts.IPart sidePart = slavePartHost.getPart(slaveKey.getDirection());
                if (sidePart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                    var slaveLogic = slaveHostPart.getLogic();
                    var slaveInv = slaveLogic.getPatternInv();
                    slaveInv.clear();
                    slaveLogic.updatePatterns();
                } else if (sidePart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                    // 处理面板形式的AdvancedAE高级样板供应器
                    var aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                    var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                    aaeSlaveInv.clear();
                    aaeSlaveLogic.updatePatterns();
                } else {
                    // 检查中心部件
                    appeng.api.parts.IPart centerPart = slavePartHost.getPart(null);
                    if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                        var slaveLogic = slaveHostPart.getLogic();
                        var slaveInv = slaveLogic.getPatternInv();
                        slaveInv.clear();
                        slaveLogic.updatePatterns();
                    } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                        // 处理中心部件形式的AdvancedAE高级样板供应器
                        var aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                        var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                        aaeSlaveInv.clear();
                        aaeSlaveLogic.updatePatterns();
                    }
                }
            } catch (Exception e) {
                // 忽略任何异常
            }
        }
    }
    
    // 静态版本的保存同步数据方法
    private static void saveSyncDataStatic(LevelAccessor levelAccessor) {
        if (!(levelAccessor instanceof ServerLevel serverLevel)) return;
        
        // 获取或创建同步数据
        PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                PatternProviderSyncData::load,
                PatternProviderSyncData::new,
                SYNC_DATA_TAG
        );
        
        // 清空现有数据
        syncData.clear();
        
        // 保存主从关系
        syncData.getMasterToSlaves().putAll(masterToSlaves);
        syncData.getSlaveToMaster().putAll(slaveToMaster);
        
        // 标记为已更改并保存
        syncData.setDirty();
    }
    
    // 获取样板供应器类型
    private String getPatternProviderType(BlockEntity blockEntity, appeng.api.parts.IPart part) {
        if (blockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider) {
            return "ExPatternProvider"; // ExtendedAE扩展样板供应器
        } else if (blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity) {
            return "AECraftingPatternProvider"; // AE2普通样板供应器
        } else if (blockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity) {
            return "AAEAdvPatternProvider"; // 高级AE的高级样板供应器
        } else if (part != null) {
            String partName = part.getClass().getName();
            if (partName.contains("ExPatternProvider")) {
                return "ExPatternProvider"; // 面板形式的ExtendedAE扩展样板供应器
            } else if (partName.contains("PatternProvider") && !partName.contains("AdvPatternProvider")) {
                return "AECraftingPatternProvider"; // 面板形式的AE2普通样板供应器
            } else if (partName.contains("AdvPatternProvider")) {
                return "AAEAdvPatternProvider"; // 面板形式的高级AE高级样板供应器
            }
        }
        return "Unknown"; // 未知类型
    }
    
    // 设置为主方块
    public void setAsMaster(Level world, BlockPos masterPos, Direction direction, Player player) {
        // 检查方块位置是否包含有效的样板供应器
        boolean hasValidProvider = false;
        String providerType = "Unknown";
        
        // 获取方块实体
        BlockEntity blockEntity = world.getBlockEntity(masterPos);
        if (blockEntity != null) {
            // 检查是否是直接放置的样板供应器
            if (blockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider ||
                blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                blockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity) {
                hasValidProvider = true;
                providerType = getPatternProviderType(blockEntity, null);
            } 
            // 如果不是，检查是否包含样板供应器部件
            else {
                try {
                    // 检查是否是IPartHost，包含部件
                    if (blockEntity instanceof appeng.api.parts.IPartHost partHost) {
                        // 找到与点击位置最匹配的样板供应器方向
                        Direction actualDirection = findMatchingDirection(partHost, masterPos, direction, player);
                        
                        // 使用实际方向检查部件
                        appeng.api.parts.IPart targetPart = partHost.getPart(actualDirection);
                        if (targetPart != null) {
                            String partType = getPatternProviderType(null, targetPart);
                            if (!partType.equals("Unknown")) {
                                hasValidProvider = true;
                                providerType = partType;
                                direction = actualDirection;
                            } else {
                                // 检查中心部件（线缆）
                                appeng.api.parts.IPart centerPart = partHost.getPart(null);
                                if (centerPart != null) {
                                    String centerType = getPatternProviderType(null, centerPart);
                                    if (!centerType.equals("Unknown")) {
                                        hasValidProvider = true;
                                        providerType = centerType;
                                        direction = null;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
        }
        
        // 如果包含有效样板供应器，设置主方块
        if (hasValidProvider) {
            // 创建主方块的键
            PatternProviderKey masterKey = new PatternProviderKey(masterPos, direction != null ? direction : Direction.UP);
            
            // 如果该方块已经是从方块，先移除其从方块关系
            if (slaveToMaster.containsKey(masterKey)) {
                PatternProviderKey oldMaster = slaveToMaster.remove(masterKey);
                if (oldMaster != null) {
                    Set<PatternProviderKey> oldSlaves = masterToSlaves.get(oldMaster);
                    if (oldSlaves != null) {
                        oldSlaves.remove(masterKey);
                        if (oldSlaves.isEmpty()) {
                            masterToSlaves.remove(oldMaster);
                        }
                    }
                }
            }
            
            // 如果该方块已经是主方块，不需要移除，只需要更新当前选择
            // 这样可以保持之前的从方块关系不变
            if (!masterToSlaves.containsKey(masterKey)) {
                // 新的主方块，添加为主方块（初始没有从方块）
                masterToSlaves.put(masterKey, new HashSet<>());
            }
            
            // 更新当前选择的主方块
            currentSelectedMaster = masterKey;
            
            if (player != null) {
                String providerTypeName = "样板供应器";
                if (providerType.equals("ExPatternProvider")) {
                    providerTypeName = "扩展样板供应器";
                } else if (providerType.equals("AECraftingPatternProvider")) {
                    providerTypeName = "AE2普通样板供应器";
                } else if (providerType.equals("AAEAdvPatternProvider")) {
                    providerTypeName = "高级AE高级样板供应器";
                }
                player.sendSystemMessage(Component.literal("已将此" + providerTypeName + "设为主方块").withStyle(ChatFormatting.GREEN));
            }
            
            // 保存同步数据
            saveSyncData(world);
        } else {
            // 如果不包含有效样板供应器，提示玩家
            if (player != null) {
                player.sendSystemMessage(Component.literal("此位置不包含有效的样板供应器").withStyle(ChatFormatting.RED));
            }
        }
    }
    
    // 获取给定主方块的样板供应器类型
    private String getMasterProviderType(Level world, PatternProviderKey masterKey) {
        BlockEntity blockEntity = world.getBlockEntity(masterKey.getPos());
        if (blockEntity != null) {
            // 检查是否是直接放置的样板供应器
            if (blockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider ||
                blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                blockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity) {
                return getPatternProviderType(blockEntity, null);
            } 
            // 如果是IPartHost，检查部件
            else if (blockEntity instanceof appeng.api.parts.IPartHost partHost) {
                // 检查指定方向的部件
                appeng.api.parts.IPart targetPart = partHost.getPart(masterKey.getDirection());
                if (targetPart != null) {
                    String partType = getPatternProviderType(null, targetPart);
                    if (!partType.equals("Unknown")) {
                        return partType;
                    }
                }
                // 检查中心部件
                appeng.api.parts.IPart centerPart = partHost.getPart(null);
                if (centerPart != null) {
                    String centerType = getPatternProviderType(null, centerPart);
                    if (!centerType.equals("Unknown")) {
                        return centerType;
                    }
                }
            }
        }
        return "Unknown";
    }
    
    // 添加为从方块
    public void addAsSlave(Level world, BlockPos slavePos, Direction direction, Player player) {
        // 使用当前选择的主方块
        PatternProviderKey masterKey = currentSelectedMaster;
        if (masterKey == null) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("请先设置一个主样板供应器").withStyle(ChatFormatting.RED));
            }
            return;
        }
        
        // 检查方块位置是否包含有效的样板供应器
        boolean hasValidProvider = false;
        String slaveProviderType = "Unknown";
        
        // 获取方块实体
        BlockEntity blockEntity = world.getBlockEntity(slavePos);
        if (blockEntity != null) {
            // 检查是否是直接放置的样板供应器
            if (blockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider ||
                blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                blockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity) {
                hasValidProvider = true;
                slaveProviderType = getPatternProviderType(blockEntity, null);
            } 
            // 如果不是，检查是否包含样板供应器部件
            else {
                try {
                    // 检查是否是IPartHost，包含部件
                    if (blockEntity instanceof appeng.api.parts.IPartHost partHost) {
                        // 找到与点击位置最匹配的样板供应器方向
                        Direction actualDirection = findMatchingDirection(partHost, slavePos, direction, player);
                        
                        // 使用实际方向检查部件
                        appeng.api.parts.IPart targetPart = partHost.getPart(actualDirection);
                        if (targetPart != null) {
                            String partType = getPatternProviderType(null, targetPart);
                            if (!partType.equals("Unknown")) {
                                hasValidProvider = true;
                                slaveProviderType = partType;
                                direction = actualDirection;
                            } else {
                                // 检查中心部件（线缆）
                                appeng.api.parts.IPart centerPart = partHost.getPart(null);
                                if (centerPart != null) {
                                    String centerType = getPatternProviderType(null, centerPart);
                                    if (!centerType.equals("Unknown")) {
                                        hasValidProvider = true;
                                        slaveProviderType = centerType;
                                        direction = null;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
        }
        
        // 如果包含有效样板供应器，添加为从方块
        if (hasValidProvider) {
            // 获取主方块的类型
            String masterProviderType = getMasterProviderType(world, masterKey);
            
            // 确保只有同类样板供应器可以绑定
            if (!slaveProviderType.equals(masterProviderType)) {
                if (player != null) {
                    player.sendSystemMessage(Component.literal("只有同类样板供应器可以绑定").withStyle(ChatFormatting.RED));
                }
                return;
            }
            // 创建从方块的键
            PatternProviderKey slaveKey = new PatternProviderKey(slavePos, direction != null ? direction : Direction.UP);
            
            // 检查是否是方块形式
            boolean isBlockForm = blockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider ||
                                 blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                                 blockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity;
            
            // 如果该方块已经是从方块，先移除其从方块关系
            if (isBlockForm) {
                // 方块形式：检查该位置是否有任何从端关系（不考虑方向）
                for (Map.Entry<PatternProviderKey, PatternProviderKey> entry : slaveToMaster.entrySet()) {
                    PatternProviderKey existingSlaveKey = entry.getKey();
                    if (existingSlaveKey.getPos().equals(slavePos)) {
                        PatternProviderKey oldMaster = slaveToMaster.remove(existingSlaveKey);
                        if (oldMaster != null) {
                            Set<PatternProviderKey> oldSlaves = masterToSlaves.get(oldMaster);
                            if (oldSlaves != null) {
                                oldSlaves.remove(existingSlaveKey);
                                if (oldSlaves.isEmpty()) {
                                    masterToSlaves.remove(oldMaster);
                                }
                            }
                        }
                        break; // 只需要移除一个，因为一个位置只能有一个从端
                    }
                }
            } else {
                // 面板形式：需要考虑方向
                if (slaveToMaster.containsKey(slaveKey)) {
                    PatternProviderKey oldMaster = slaveToMaster.remove(slaveKey);
                    if (oldMaster != null) {
                        Set<PatternProviderKey> oldSlaves = masterToSlaves.get(oldMaster);
                        if (oldSlaves != null) {
                            oldSlaves.remove(slaveKey);
                            if (oldSlaves.isEmpty()) {
                                masterToSlaves.remove(oldMaster);
                            }
                        }
                    }
                }
            }
            
            // 如果该方块是主方块，先移除其主方块关系
            if (isBlockForm) {
                // 方块形式：检查该位置是否有任何主端关系（不考虑方向）
                for (PatternProviderKey existingMasterKey : new HashSet<>(masterToSlaves.keySet())) {
                    if (existingMasterKey.getPos().equals(slavePos)) {
                        Set<PatternProviderKey> oldSlaves = masterToSlaves.remove(existingMasterKey);
                        if (oldSlaves != null) {
                            for (PatternProviderKey oldSlave : oldSlaves) {
                                slaveToMaster.remove(oldSlave);
                            }
                        }
                        break; // 只需要移除一个，因为一个位置只能有一个主端
                    }
                }
            } else {
                // 面板形式：需要考虑方向
                if (masterToSlaves.containsKey(slaveKey)) {
                    Set<PatternProviderKey> oldSlaves = masterToSlaves.remove(slaveKey);
                    if (oldSlaves != null) {
                        for (PatternProviderKey oldSlave : oldSlaves) {
                            slaveToMaster.remove(oldSlave);
                        }
                    }
                }
            }
            
            // 添加为从方块
            masterToSlaves.computeIfAbsent(masterKey, k -> new HashSet<>()).add(slaveKey);
            slaveToMaster.put(slaveKey, masterKey);
            
            if (player != null) {
                String providerTypeName = "样板供应器";
                if (slaveProviderType.equals("ExPatternProvider")) {
                    providerTypeName = "扩展样板供应器";
                } else if (slaveProviderType.equals("AECraftingPatternProvider")) {
                    providerTypeName = "AE2普通样板供应器";
                } else if (slaveProviderType.equals("AAEAdvPatternProvider")) {
                    providerTypeName = "高级AE高级样板供应器";
                }
                player.sendSystemMessage(Component.literal("已将此" + providerTypeName + "设为从方块，跟随主方块").withStyle(ChatFormatting.BLUE));
            }
            
            // 立即同步一次
            syncPatternsFromMaster(world, slaveKey, masterKey);
            
            // 获取从端样板供应器并设置为不在终端显示
            if (world instanceof ServerLevel serverLevel) {
                BlockEntity slaveBlockEntity = serverLevel.getBlockEntity(slavePos);
                if (slaveBlockEntity != null) {
                    // 检查是否是IPartHost，包含部件
                    if (slaveBlockEntity instanceof appeng.api.parts.IPartHost partHost) {
                        // 检查指定方向的部件，设置从端样板供应器不在终端显示
                        appeng.api.parts.IPart targetPart = partHost.getPart(direction);
                        if (targetPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                            // 设置AE2普通从端样板供应器不在终端显示
                            appeng.api.util.IConfigManager configManager = slaveHostPart.getConfigManager();
                            configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                        } else if (targetPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                            // 设置AdvancedAE高级从端样板供应器不在终端显示
                            appeng.api.util.IConfigManager configManager = aaeSlaveHostPart.getConfigManager();
                            configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                        } else {
                            // 检查中心部件（线缆）
                            appeng.api.parts.IPart centerPart = partHost.getPart(null);
                            if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                // 设置AE2普通从端样板供应器不在终端显示
                                appeng.api.util.IConfigManager configManager = slaveHostPart.getConfigManager();
                                configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                            } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                                // 设置AdvancedAE高级从端样板供应器不在终端显示
                                appeng.api.util.IConfigManager configManager = aaeSlaveHostPart.getConfigManager();
                                configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                            }
                        }
                    } else if (slaveBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
                        // 直接放置的AE2普通样板供应器
                        appeng.api.util.IConfigManager configManager = slaveHost.getConfigManager();
                        configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                    } else if (slaveBlockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHost) {
                        // 直接放置的AdvancedAE高级样板供应器
                        appeng.api.util.IConfigManager configManager = aaeSlaveHost.getConfigManager();
                        configManager.putSetting(appeng.api.config.Settings.PATTERN_ACCESS_TERMINAL, appeng.api.config.YesNo.NO);
                    }
                }
            }
            
            // 保存同步数据
            saveSyncData(world);
        } else {
            // 如果不包含有效样板供应器，提示玩家
            if (player != null) {
                player.sendSystemMessage(Component.literal("此位置不包含有效的样板供应器").withStyle(ChatFormatting.RED));
            }
        }
    }
    
    // 找到与点击位置最匹配的样板供应器方向
    private Direction findMatchingDirection(appeng.api.parts.IPartHost partHost, BlockPos blockPos, Direction initialDirection, Player player) {
        // 如果是直接放置的方块，直接返回初始方向
        BlockEntity blockEntity = player.level().getBlockEntity(blockPos);
        if (blockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider ||
            blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
            blockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity) {
            return initialDirection;
        }
        
        // 计算玩家点击的精确位置
        net.minecraft.world.phys.HitResult hitResult = player.pick(4.5D, 0.0F, false);
        if (hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return initialDirection;
        }
        
        net.minecraft.world.phys.BlockHitResult blockHitResult = (net.minecraft.world.phys.BlockHitResult) hitResult;
        net.minecraft.world.phys.Vec3 hitLocation = blockHitResult.getLocation().subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        
        // 遍历所有方向，找到与点击位置最匹配的样板供应器
        Direction bestDirection = initialDirection;
        double bestMatchScore = Double.MAX_VALUE;
        
        for (Direction dir : Direction.values()) {
            appeng.api.parts.IPart part = partHost.getPart(dir);
            if (part != null) {
                String partType = getPatternProviderType(null, part);
                if (!partType.equals("Unknown")) {
                    // 计算该方向与点击位置的匹配程度
                    double matchScore = calculateMatchScore(dir, hitLocation);
                    if (matchScore < bestMatchScore) {
                        bestMatchScore = matchScore;
                        bestDirection = dir;
                    }
                }
            }
        }
        
        return bestDirection;
    }
    
    // 计算方向与点击位置的匹配程度，分数越低表示匹配度越高
    private double calculateMatchScore(Direction dir, net.minecraft.world.phys.Vec3 hitLocation) {
        // 计算点击位置在该方向上的投影
        double dx = hitLocation.x - 0.5;
        double dy = hitLocation.y - 0.5;
        double dz = hitLocation.z - 0.5;
        
        // 计算该方向的法向量
        int nx = dir.getStepX();
        int ny = dir.getStepY();
        int nz = dir.getStepZ();
        
        // 对于每个方向，计算点击位置到该方向面板的距离
        // 面板的位置在方块的表面，距离方块中心0.5个单位
        double distanceToPanel = Math.abs(dx * nx + dy * ny + dz * nz) - 0.5;
        
        // 计算点击位置在面板平面内的偏移
        double inPlaneOffsetX = dx - nx * (0.5 + distanceToPanel * nx);
        double inPlaneOffsetY = dy - ny * (0.5 + distanceToPanel * ny);
        double inPlaneOffsetZ = dz - nz * (0.5 + distanceToPanel * nz);
        
        // 计算平面内的偏移距离
        double inPlaneDistance = Math.sqrt(inPlaneOffsetX * inPlaneOffsetX + inPlaneOffsetY * inPlaneOffsetY + inPlaneOffsetZ * inPlaneOffsetZ);
        
        // 总匹配分数 = 到面板的距离 + 平面内的偏移距离
        return Math.abs(distanceToPanel) + inPlaneDistance;
    }
    
    // 同步从方块与指定主方块的pattern
    private void syncPatternsFromMaster(Level world, PatternProviderKey slaveKey, PatternProviderKey masterKey) {
        if (masterKey == null) return;
        
        // 检查同步间隔
        long currentTime = System.currentTimeMillis();
        Long lastSync = lastSyncTime.get(slaveKey);
        if (lastSync != null && currentTime - lastSync < SYNC_INTERVAL) {
            return;
        }
        
        // 获取主方块的BlockEntity
        BlockEntity masterBlockEntity = world.getBlockEntity(masterKey.getPos());
        if (masterBlockEntity == null) return;
        
        // 获取主端的样板
        List<ItemStack> masterPatterns = new ArrayList<>();
        boolean masterFound = false;
        
        // 检查是否是直接放置的样板供应器
        if (masterBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHost) {
            // AE2普通和ExtendedAE扩展样板供应器
            var masterLogic = masterHost.getLogic();
            var masterInv = masterLogic.getPatternInv();
            for (ItemStack stack : masterInv) {
                if (!stack.isEmpty()) {
                    masterPatterns.add(stack.copy());
                }
            }
            masterFound = true;
        } else if (masterBlockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHost) {
            // AdvancedAE高级样板供应器
            var aaeMasterLogic = aaeMasterHost.getLogic();
            var aaeMasterInv = aaeMasterLogic.getPatternInv();
            for (ItemStack stack : aaeMasterInv) {
                if (!stack.isEmpty()) {
                    masterPatterns.add(stack.copy());
                }
            }
            masterFound = true;
        } else if (masterBlockEntity instanceof appeng.api.parts.IPartHost masterPartHost) {
            // 检查指定方向的部件
            appeng.api.parts.IPart targetPart = masterPartHost.getPart(masterKey.getDirection());
            if (targetPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHostPart) {
                // 面板形式的AE2普通或ExtendedAE扩展样板供应器
                var masterLogic = masterHostPart.getLogic();
                var masterInv = masterLogic.getPatternInv();
                for (ItemStack stack : masterInv) {
                    if (!stack.isEmpty()) {
                        masterPatterns.add(stack.copy());
                    }
                }
                masterFound = true;
            } else if (targetPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHostPart) {
                // 面板形式的AdvancedAE高级样板供应器
                var aaeMasterLogic = aaeMasterHostPart.getLogic();
                var aaeMasterInv = aaeMasterLogic.getPatternInv();
                for (ItemStack stack : aaeMasterInv) {
                    if (!stack.isEmpty()) {
                        masterPatterns.add(stack.copy());
                    }
                }
                masterFound = true;
            } else {
                // 检查中心部件（线缆）
                appeng.api.parts.IPart centerPart = masterPartHost.getPart(null);
                if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHostPart) {
                    var masterLogic = masterHostPart.getLogic();
                    var masterInv = masterLogic.getPatternInv();
                    for (ItemStack stack : masterInv) {
                        if (!stack.isEmpty()) {
                            masterPatterns.add(stack.copy());
                        }
                    }
                    masterFound = true;
                } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHostPart) {
                    var aaeMasterLogic = aaeMasterHostPart.getLogic();
                    var aaeMasterInv = aaeMasterLogic.getPatternInv();
                    for (ItemStack stack : aaeMasterInv) {
                        if (!stack.isEmpty()) {
                            masterPatterns.add(stack.copy());
                        }
                    }
                    masterFound = true;
                } else {
                    // 检查所有方向的部件，找到第一个匹配的
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        appeng.api.parts.IPart sidePart = masterPartHost.getPart(dir);
                        if (sidePart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHostPart) {
                            var masterLogic = masterHostPart.getLogic();
                            var masterInv = masterLogic.getPatternInv();
                            for (ItemStack stack : masterInv) {
                                if (!stack.isEmpty()) {
                                    masterPatterns.add(stack.copy());
                                }
                            }
                            masterFound = true;
                            break;
                        } else if (sidePart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHostPart) {
                            var aaeMasterLogic = aaeMasterHostPart.getLogic();
                            var aaeMasterInv = aaeMasterLogic.getPatternInv();
                            for (ItemStack stack : aaeMasterInv) {
                                if (!stack.isEmpty()) {
                                    masterPatterns.add(stack.copy());
                                }
                            }
                            masterFound = true;
                            break;
                        }
                    }
                }
            }
        }
        
        if (!masterFound || masterPatterns.isEmpty()) {
            return;
        }
        

        
        // 获取从方块的BlockEntity
        BlockEntity slaveBlockEntity = world.getBlockEntity(slaveKey.getPos());
        if (slaveBlockEntity == null) return;
        
        // 处理从端的样板供应器，包括直接放置的和面板形式的
        List<appeng.helpers.patternprovider.PatternProviderLogic> slaveLogics = new ArrayList<>();
        List<net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic> aaeSlaveLogics = new ArrayList<>();
        
        // 检查是否是直接放置的样板供应器
        if (slaveBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
            // 适用于所有直接实现PatternProviderLogicHost的方块实体
            slaveLogics.add(slaveHost.getLogic());
        } else if (slaveBlockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHost) {
            // AdvancedAE高级样板供应器
            aaeSlaveLogics.add(aaeSlaveHost.getLogic());
        } else if (slaveBlockEntity instanceof appeng.api.parts.IPartHost slavePartHost) {
            // 检查指定方向的部件
            appeng.api.parts.IPart targetPart = slavePartHost.getPart(slaveKey.getDirection());
            if (targetPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                slaveLogics.add(slaveHostPart.getLogic());
            } else if (targetPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                // 面板形式的AdvancedAE高级样板供应器
                aaeSlaveLogics.add(aaeSlaveHostPart.getLogic());
            } else {
                // 检查中心部件（线缆）
                appeng.api.parts.IPart centerPart = slavePartHost.getPart(null);
                if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                    slaveLogics.add(slaveHostPart.getLogic());
                } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                    // 中心部件形式的AdvancedAE高级样板供应器
                    aaeSlaveLogics.add(aaeSlaveHostPart.getLogic());
                }
            }
        }
        
        // 同步所有从端的pattern
        for (appeng.helpers.patternprovider.PatternProviderLogic slaveLogic : slaveLogics) {
            var slaveInv = slaveLogic.getPatternInv();
            
            // 清空从方块的inventory
            slaveInv.clear();
            
            // 复制主方块的所有pattern到从方块
            // 使用新的masterPatterns列表直接复制
            for (int i = 0; i < masterPatterns.size() && i < slaveInv.size(); i++) {
                slaveInv.setItemDirect(i, masterPatterns.get(i));
            }
            
            // 更新从方块的patterns
            slaveLogic.updatePatterns();
            
            // 为从端设置过滤器，防止取出样板
            try {
                // 获取PatternProviderLogic的patternInventory字段
                java.lang.reflect.Field logicField = slaveLogic.getClass().getDeclaredField("patternInventory");
                logicField.setAccessible(true);
                Object inventoryObj = logicField.get(slaveLogic);
                
                // 检查是否为AppEngInternalInventory类型
                if (inventoryObj instanceof appeng.util.inv.AppEngInternalInventory appEngInv) {
                    // 设置过滤器，阻止提取样板
                    appEngInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
                        @Override
                        public boolean allowExtract(appeng.api.inventories.InternalInventory inv, int slot, int amount) {
                            return false;
                        }
                    });
                }
            } catch (Exception e) {
                // 忽略反射异常
            }
        }
        
        // 同步所有AdvancedAE高级样板供应器从端的pattern
        for (net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic aaeSlaveLogic : aaeSlaveLogics) {
            var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
            
            // 清空从方块的inventory
            aaeSlaveInv.clear();
            
            // 复制主方块的所有pattern到从方块
            for (int i = 0; i < masterPatterns.size() && i < aaeSlaveInv.size(); i++) {
                aaeSlaveInv.setItemDirect(i, masterPatterns.get(i));
            }
            
            // 更新从方块的patterns
            aaeSlaveLogic.updatePatterns();
            
            // 为从端设置过滤器，防止取出样板
            try {
                // 获取AdvPatternProviderLogic的patternInventory字段
                java.lang.reflect.Field logicField = aaeSlaveLogic.getClass().getDeclaredField("patternInventory");
                logicField.setAccessible(true);
                Object inventoryObj = logicField.get(aaeSlaveLogic);
                
                // 检查是否为AppEngInternalInventory类型
                if (inventoryObj instanceof appeng.util.inv.AppEngInternalInventory appEngInv) {
                    // 设置过滤器，阻止提取样板
                    appEngInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
                        @Override
                        public boolean allowExtract(appeng.api.inventories.InternalInventory inv, int slot, int amount) {
                            return false;
                        }
                    });
                }
            } catch (Exception e) {
                // 忽略反射异常
            }
        }
        
        // 更新同步时间
        lastSyncTime.put(slaveKey, currentTime);
    }
    
    // 重置主方块选择（Shift+右键空气）
    public static void resetMasterPatternProvider(Level world) {
        // 取消当前的主方块选择状态
        currentSelectedMaster = null;
        
        // 清空临时的同步时间记录
        lastSyncTime.clear();
        
        // 保存同步数据（这里实际上不需要保存，因为我们没有修改主从关系）
        // 但为了保持代码一致性，仍然调用保存方法
        if (world instanceof ServerLevel serverLevel) {
            PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                    PatternProviderSyncData::load,
                    PatternProviderSyncData::new,
                    SYNC_DATA_TAG
            );
            // 不需要清空syncData，因为我们保留原有的主从关系
            syncData.setDirty();
        }
    }
    
    // 检查给定位置的从端是否属于当前选定的主端（用于渲染）
    public static boolean isSlaveOfCurrentMaster(BlockPos slavePos, Level level) {
        // 获取当前选定的主端
        PatternProviderKey selectedMasterKey = currentSelectedMaster;
        if (selectedMasterKey == null) {
            return false;
        }
        
        // 遍历所有从端，检查是否有匹配的位置
        for (Map.Entry<PatternProviderKey, PatternProviderKey> entry : slaveToMaster.entrySet()) {
            PatternProviderKey slaveKey = entry.getKey();
            PatternProviderKey masterKey = entry.getValue();
            
            // 检查从端位置是否匹配
            if (slaveKey.getPos().equals(slavePos)) {
                // 获取主端的方块实体
                BlockEntity masterBlockEntity = level.getBlockEntity(selectedMasterKey.getPos());
                
                // 检查主端是否是方块形式（直接放置的方块，不是面板）
                boolean isBlockForm = masterBlockEntity instanceof com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider ||
                                     masterBlockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                                     masterBlockEntity instanceof net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity;
                
                if (isBlockForm) {
                    // 方块形式：只需要匹配位置
                    if (masterKey.getPos().equals(selectedMasterKey.getPos())) {
                        return true;
                    }
                } else {
                    // 面板形式：需要匹配位置和方向
                    if (masterKey.equals(selectedMasterKey)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    // 检查给定位置是否是主端样板供应器（用于渲染）
    public static boolean isMasterPatternProvider(BlockPos pos) {
        // 遍历所有主端，检查是否有匹配的位置
        for (PatternProviderKey masterKey : masterToSlaves.keySet()) {
            if (masterKey.getPos().equals(pos)) {
                return true;
            }
        }
        
        return false;
    }
    
    // 检查给定位置是否是从端样板供应器（用于mixin和渲染）
    public static boolean isSlavePatternProvider(BlockPos pos) {
        // 遍历所有从端，检查是否有匹配的位置
        for (PatternProviderKey slaveKey : slaveToMaster.keySet()) {
            if (slaveKey.getPos().equals(pos)) {
                return true;
            }
        }
        
        return false;
    }
    
    // 定期同步所有从方块
    private static void syncAllSlaves(Level world) {
        long currentTime = System.currentTimeMillis();
        
        // 遍历所有主从关系
        for (Map.Entry<PatternProviderKey, Set<PatternProviderKey>> entry : masterToSlaves.entrySet()) {
            PatternProviderKey masterKey = entry.getKey();
            Set<PatternProviderKey> slaves = entry.getValue();
            
            if (slaves.isEmpty()) continue;
            
            // 获取主方块的BlockEntity
            BlockEntity blockEntity = world.getBlockEntity(masterKey.getPos());
            if (blockEntity == null) continue;
            
            // 获取主端的样板
            List<ItemStack> masterPatterns = new ArrayList<>();
            boolean masterFound = false;
            
            // 处理主端的不同类型
            if (blockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHost) {
                // AE2普通和ExtendedAE扩展样板供应器
                var masterLogic = masterHost.getLogic();
                var masterInv = masterLogic.getPatternInv();
                for (int i = 0; i < masterInv.size(); i++) {
                    ItemStack stack = masterInv.getStackInSlot(i);
                    masterPatterns.add(stack.copy());
                }
                masterFound = true;
            } else if (blockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHost) {
                // AdvancedAE高级样板供应器
                var aaeMasterLogic = aaeMasterHost.getLogic();
                var aaeMasterInv = aaeMasterLogic.getPatternInv();
                for (int i = 0; i < aaeMasterInv.size(); i++) {
                    ItemStack stack = aaeMasterInv.getStackInSlot(i);
                    masterPatterns.add(stack.copy());
                }
                masterFound = true;
            } else if (blockEntity instanceof appeng.api.parts.IPartHost masterPartHost) {
                // 面板形式的样板供应器
                appeng.api.parts.IPart targetPart = masterPartHost.getPart(masterKey.getDirection());
                if (targetPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHostPart) {
                    // 面板形式的AE2普通或ExtendedAE扩展样板供应器
                    var masterLogic = masterHostPart.getLogic();
                    var masterInv = masterLogic.getPatternInv();
                    for (int i = 0; i < masterInv.size(); i++) {
                        ItemStack stack = masterInv.getStackInSlot(i);
                        masterPatterns.add(stack.copy());
                    }
                    masterFound = true;
                } else if (targetPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHostPart) {
                    // 面板形式的AdvancedAE高级样板供应器
                    var aaeMasterLogic = aaeMasterHostPart.getLogic();
                    var aaeMasterInv = aaeMasterLogic.getPatternInv();
                    for (int i = 0; i < aaeMasterInv.size(); i++) {
                        ItemStack stack = aaeMasterInv.getStackInSlot(i);
                        masterPatterns.add(stack.copy());
                    }
                    masterFound = true;
                } else {
                    // 检查中心部件（线缆）
                    appeng.api.parts.IPart centerPart = masterPartHost.getPart(null);
                    if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost masterHostPart) {
                        var masterLogic = masterHostPart.getLogic();
                        var masterInv = masterLogic.getPatternInv();
                        for (int i = 0; i < masterInv.size(); i++) {
                            ItemStack stack = masterInv.getStackInSlot(i);
                            masterPatterns.add(stack.copy());
                        }
                        masterFound = true;
                    } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeMasterHostPart) {
                        var aaeMasterLogic = aaeMasterHostPart.getLogic();
                        var aaeMasterInv = aaeMasterLogic.getPatternInv();
                        for (int i = 0; i < aaeMasterInv.size(); i++) {
                            ItemStack stack = aaeMasterInv.getStackInSlot(i);
                            masterPatterns.add(stack.copy());
                        }
                        masterFound = true;
                    }
                }
            }
            
            if (!masterFound) continue;
            
            // 同步到所有从方块
            for (PatternProviderKey slaveKey : slaves) {
                // 检查同步间隔
                Long lastSync = lastSyncTime.get(slaveKey);
                if (lastSync == null || currentTime - lastSync > SYNC_INTERVAL) {
                    // 获取从方块的BlockEntity
                    BlockEntity slaveBlockEntity = world.getBlockEntity(slaveKey.getPos());
                    if (slaveBlockEntity == null) continue;
                    
                    // 获取从端的逻辑，支持不同类型的样板供应器
                    appeng.helpers.patternprovider.PatternProviderLogic slaveLogic = null;
                    net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic aaeSlaveLogic = null;
                    
                    // 检查是否是直接放置的样板供应器
                    if (slaveBlockEntity instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHost) {
                        // AE2普通和ExtendedAE扩展样板供应器
                        slaveLogic = slaveHost.getLogic();
                    } else if (slaveBlockEntity instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHost) {
                        // AdvancedAE高级样板供应器
                        aaeSlaveLogic = aaeSlaveHost.getLogic();
                    } else if (slaveBlockEntity instanceof appeng.api.parts.IPartHost slavePartHost) {
                        // 检查指定方向的部件
                        appeng.api.parts.IPart sidePart = slavePartHost.getPart(slaveKey.getDirection());
                        if (sidePart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                            // 面板形式的AE2普通或ExtendedAE扩展样板供应器
                            slaveLogic = slaveHostPart.getLogic();
                        } else if (sidePart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                            // 面板形式的AdvancedAE高级样板供应器
                            aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                        } else {
                            // 检查中心部件
                            appeng.api.parts.IPart centerPart = slavePartHost.getPart(null);
                            if (centerPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                // 中心部件形式的AE2普通或ExtendedAE扩展样板供应器
                                slaveLogic = slaveHostPart.getLogic();
                            } else if (centerPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                                // 中心部件形式的AdvancedAE高级样板供应器
                                aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                            } else {
                                // 检查所有方向的部件，找到第一个匹配的
                                for (Direction dir : Direction.values()) {
                                    appeng.api.parts.IPart dirPart = slavePartHost.getPart(dir);
                                    if (slaveLogic == null && dirPart instanceof appeng.helpers.patternprovider.PatternProviderLogicHost slaveHostPart) {
                                        slaveLogic = slaveHostPart.getLogic();
                                        break;
                                    } else if (aaeSlaveLogic == null && dirPart instanceof net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost aaeSlaveHostPart) {
                                        aaeSlaveLogic = aaeSlaveHostPart.getLogic();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    if (slaveLogic != null) {
                        var slaveInv = slaveLogic.getPatternInv();
                        
                        // 复制主方块的所有pattern到从方块
                        boolean needsUpdate = false;
                        for (int i = 0; i < masterPatterns.size() && i < slaveInv.size(); i++) {
                            ItemStack masterStack = masterPatterns.get(i);
                            ItemStack slaveStack = slaveInv.getStackInSlot(i);
                            
                            if (!ItemStack.matches(masterStack, slaveStack)) {
                                slaveInv.setItemDirect(i, masterStack.copy());
                                needsUpdate = true;
                            }
                        }
                        
                        if (needsUpdate) {
                            // 更新从方块的patterns
                            slaveLogic.updatePatterns();
                            // 更新同步时间
                            lastSyncTime.put(slaveKey, currentTime);
                        }
                        
                        // 为从端设置过滤器，防止取出样板
                        try {
                            // 获取PatternProviderLogic的patternInventory字段
                            java.lang.reflect.Field logicField = slaveLogic.getClass().getDeclaredField("patternInventory");
                            logicField.setAccessible(true);
                            Object inventoryObj = logicField.get(slaveLogic);
                            
                            // 检查是否为AppEngInternalInventory类型
                            if (inventoryObj instanceof appeng.util.inv.AppEngInternalInventory appEngInv) {
                                // 设置过滤器，阻止提取样板
                                appEngInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
                                    @Override
                                    public boolean allowExtract(appeng.api.inventories.InternalInventory inv, int slot, int amount) {
                                        return false;
                                    }
                                });
                            }
                        } catch (Exception e) {
                            // 忽略反射异常
                        }
                    } else if (aaeSlaveLogic != null) {
                        // 处理AdvancedAE高级样板供应器
                        var aaeSlaveInv = aaeSlaveLogic.getPatternInv();
                        
                        // 复制主方块的所有pattern到从方块
                        boolean needsUpdate = false;
                        for (int i = 0; i < masterPatterns.size() && i < aaeSlaveInv.size(); i++) {
                            ItemStack masterStack = masterPatterns.get(i);
                            ItemStack slaveStack = aaeSlaveInv.getStackInSlot(i);
                            
                            if (!ItemStack.matches(masterStack, slaveStack)) {
                                aaeSlaveInv.setItemDirect(i, masterStack.copy());
                                needsUpdate = true;
                            }
                        }
                        
                        if (needsUpdate) {
                            // 更新从方块的patterns
                            aaeSlaveLogic.updatePatterns();
                            // 更新同步时间
                            lastSyncTime.put(slaveKey, currentTime);
                        }
                        
                        // 为从端设置过滤器，防止取出样板
                        try {
                            // 获取AdvPatternProviderLogic的patternInventory字段
                            java.lang.reflect.Field logicField = aaeSlaveLogic.getClass().getDeclaredField("patternInventory");
                            logicField.setAccessible(true);
                            Object inventoryObj = logicField.get(aaeSlaveLogic);
                            
                            // 检查是否为AppEngInternalInventory类型
                            if (inventoryObj instanceof appeng.util.inv.AppEngInternalInventory appEngInv) {
                                // 设置过滤器，阻止提取样板
                                appEngInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
                                    @Override
                                    public boolean allowExtract(appeng.api.inventories.InternalInventory inv, int slot, int amount) {
                                        return false;
                                    }
                                });
                            }
                        } catch (Exception e) {
                            // 忽略反射异常
                        }
                    }
                }
            }
        }
    }
    
    // 保存同步数据到游戏数据中
    private void saveSyncData(Level world) {
        if (!(world instanceof ServerLevel serverLevel)) return;
        
        // 获取或创建同步数据
        PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                PatternProviderSyncData::load,
                PatternProviderSyncData::new,
                SYNC_DATA_TAG
        );
        
        // 清空现有数据
        syncData.clear();
        
        // 保存主从关系
        syncData.getMasterToSlaves().putAll(masterToSlaves);
        syncData.getSlaveToMaster().putAll(slaveToMaster);
        
        // 标记为已更改并保存
        syncData.setDirty();
    }
    
    // 从游戏数据中加载同步数据
    private static void loadSyncData(Level world) {
        if (!(world instanceof ServerLevel serverLevel)) return;
        
        PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                PatternProviderSyncData::load,
                PatternProviderSyncData::new,
                SYNC_DATA_TAG
        );
        
        // 清空现有数据
        masterToSlaves.clear();
        slaveToMaster.clear();
        
        // 加载主从关系
        masterToSlaves.putAll(syncData.getMasterToSlaves());
        slaveToMaster.putAll(syncData.getSlaveToMaster());
    }
    
    // 执行连锁强制挖掘
    private void performChainForceMining(Level level, BlockPos originPos, BlockState originState, Player player, ItemStack stack) {
        // 根据增强连锁设置使用不同的连锁逻辑
        boolean enhancedMode = isEnhancedChainMiningMode(stack);
        List<BlockPos> blocksToMine;
        
        // 调用相应的工具类查找待挖掘的方块，强制挖掘模式下forceMining为true
        if (enhancedMode) {
            blocksToMine = EnhancedChainMiningUtils.findBlocksToMine(originPos, originState, level, stack, true);
        } else {
            blocksToMine = NormalChainMiningUtils.findBlocksToMine(originPos, originState, level, stack, true);
        }
        
        // 对所有符合条件的方块执行强制挖掘
        for (BlockPos pos : blocksToMine) {
            BlockState state = level.getBlockState(pos);
            if (!level.isEmptyBlock(pos)) {
                BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                        level, pos, state, player
                );
                ForceBreakUtils.forceBreakBlock(breakEvent, level, pos, state, player, stack);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        // 显示当前模式
        if (isSilkTouchMode(stack)) {
            tooltip.add(Component.translatable("tooltip.useless_mod.silk_touch_mode").withStyle(ChatFormatting.AQUA));
            int silkTouchLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SILK_TOUCH, stack);
            if (silkTouchLevel > 0) {
                tooltip.add(Component.translatable("tooltip.useless_mod.silk_touch_level", silkTouchLevel).withStyle(ChatFormatting.GOLD));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.useless_mod.fortune_mode").withStyle(ChatFormatting.GOLD));
            int fortuneLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BLOCK_FORTUNE, stack);
            if (fortuneLevel > 0) {
                tooltip.add(Component.translatable("tooltip.useless_mod.fortune_level", fortuneLevel).withStyle(ChatFormatting.GREEN));
                if (fortuneLevel > ConfigManager.getFortuneLevel()) {
                    tooltip.add(Component.translatable("tooltip.useless_mod.external_enchantment").withStyle(ChatFormatting.RED));
                }
            }
        }
        
        
        
        // 增强连锁模式提示
        tooltip.add(Component.literal(isEnhancedChainMiningMode(stack) ? "增强连锁模式: 已开启" : "增强连锁模式: 已关闭").withStyle(isEnhancedChainMiningMode(stack) ? ChatFormatting.BLUE : ChatFormatting.GRAY));
        
        // 强制挖掘模式提示
        tooltip.add(Component.literal(isForceMiningMode(stack) ? "强制挖掘模式: 已开启" : "强制挖掘模式: 已关闭").withStyle(isForceMiningMode(stack) ? ChatFormatting.RED : ChatFormatting.GRAY));

        // 功能提示 - 动态显示按键绑定
        // 尝试获取实际按键绑定（仅在客户端）
        String silkTouchKey = "Page Down";
        String fortuneKey = "Page Up";
        String chainMiningKey = "Tab";
        String enhancedChainMiningKey = "Numpad 8";
        String forceMiningKey = "Numpad 9";
        String triggerForceMiningKey = "R";
        String modeWheelKey = "G";
        
        // 新增：主从选择按键
        String setMasterKey = "M";
        String setSlaveKey = "S";
        
        try {
            // 获取精准采集/时运切换按键
            KeyMapping silkTouchMapping = KeyBindings.SWITCH_SILK_TOUCH_KEY;
            silkTouchKey = silkTouchMapping.getTranslatedKeyMessage().getString();

            KeyMapping fortuneMapping = KeyBindings.SWITCH_FORTUNE_KEY;
            fortuneKey = fortuneMapping.getTranslatedKeyMessage().getString();

            // 获取连锁挖掘切换按键
            chainMiningKey = KeyBindings.SWITCH_CHAIN_MINING_KEY.getTranslatedKeyMessage().getString();

            // 获取增强连锁模式切换按键
            enhancedChainMiningKey = KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY.getTranslatedKeyMessage().getString();
            
            // 获取强制挖掘模式切换按键
            forceMiningKey = KeyBindings.SWITCH_FORCE_MINING_KEY.getTranslatedKeyMessage().getString();
            
            // 获取强制挖掘触发按键
            triggerForceMiningKey = KeyBindings.TRIGGER_FORCE_MINING_KEY.getTranslatedKeyMessage().getString();
            
            // 获取模式选择轮盘按键
            KeyMapping modeWheelMapping = KeyBindings.SWITCH_MODE_WHEEL_KEY;
            modeWheelKey = modeWheelMapping.getTranslatedKeyMessage().getString();
            
            // 获取主从选择按键
            KeyMapping setMasterMapping = KeyBindings.SET_MASTER_PATTERN_KEY;
            setMasterKey = setMasterMapping.getTranslatedKeyMessage().getString();
            
            KeyMapping setSlaveMapping = KeyBindings.SET_SLAVE_PATTERN_KEY;
            setSlaveKey = setSlaveMapping.getTranslatedKeyMessage().getString();
        } catch (Exception e) {
            // 如果获取失败，使用默认按键名称
        }
        
        // 添加动态按键提示
        tooltip.add(Component.translatable("tooltip.useless_mod.switch_enchantment").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按住 " + chainMiningKey + "开启连锁挖掘").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按下 " + enhancedChainMiningKey + "切换增强连锁挖掘").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按下 " + forceMiningKey + "切换强制挖掘模式").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按下 " + triggerForceMiningKey + "触发强制破坏").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("按下 " + modeWheelKey + " 打开模式选择界面").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("按下 " + setMasterKey + "设置主扩展样板供应器").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.literal("按下 " + setSlaveKey + "设置从扩展样板供应器").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.useless_mod.fast_break_plastic").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN)); // 新增提示
        tooltip.add(Component.translatable("tooltip.useless_mod.enhanced_chain_description").withStyle(ChatFormatting.BLUE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 始终显示附魔光效
    }
    


    // 禁止效率附魔
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.BLOCK_EFFICIENCY) {
            return false; // 禁止效率附魔
        }
        return true; // 允许其他所有附魔
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 30; // 允许被附魔
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true; // 允许被附魔
    }

    // 重写获取附魔等级的方法 - 现在直接使用NBT中的附魔数据
    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        return EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
    }
    
    // 移除了getTags方法，因为Forge中物品标签是在注册时静态定义的
    // 改为在物品注册时创建多个具有不同标签的物品实例
    // 这里我们使用更灵活的方式处理工具模式：通过NBT标签来跟踪激活的工具模式

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        // 首次创建时设置为时运模式
        if (!stack.hasTag() || !stack.getTag().contains("SilkTouchMode")) {
            switchEnchantmentMode(stack, false); // 默认时运模式
        } else {
            // 确保已有抢夺附魔
            updateEnchantments(stack);
        }
        // 移除了连锁挖掘模式的默认设置，现在只根据按键状态控制
    }

    // 基础物品注册
    public static final RegistryObject<Item> ENDLESS_BEAF_ITEM = ITEMS.register("endless_beaf_item",
            () -> new EndlessBeafItem(
                    Tiers.NETHERITE,  // Tier - 可根据需要调整
                    50,               // Attack damage modifier
                    2.0f,            // Attack speed modifier
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .durability(0)
            ) {
                @Override
                public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
                    return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                            state.is(BlockTags.MINEABLE_WITH_AXE) ||
                            state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                            state.is(BlockTags.MINEABLE_WITH_HOE);
                }
            });
    
    // 扳手子类物品注册
    public static final RegistryObject<Item> ENDLESS_BEAF_WRENCH = ITEMS.register("endless_beaf_wrench",
            () -> new EndlessBeafItem(
                    Tiers.NETHERITE,  // Tier - 可根据需要调整
                    50,               // Attack damage modifier
                    2.0f,            // Attack speed modifier
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .durability(0)
            ) {
                @Override
                public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
                    return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                            state.is(BlockTags.MINEABLE_WITH_AXE) ||
                            state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                            state.is(BlockTags.MINEABLE_WITH_HOE);
                }
            });
    
    // 螺丝刀子类物品注册
    public static final RegistryObject<Item> ENDLESS_BEAF_SCREWDRIVER = ITEMS.register("endless_beaf_screwdriver",
            () -> new EndlessBeafItem(
                    Tiers.NETHERITE,  // Tier - 可根据需要调整
                    50,               // Attack damage modifier
                    2.0f,            // Attack speed modifier
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .durability(0)
            ) {
                @Override
                public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
                    return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                            state.is(BlockTags.MINEABLE_WITH_AXE) ||
                            state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                            state.is(BlockTags.MINEABLE_WITH_HOE);
                }
            });
    
    // 锤子子类物品注册
    public static final RegistryObject<Item> ENDLESS_BEAF_MALLET = ITEMS.register("endless_beaf_mallet",
            () -> new EndlessBeafItem(
                    Tiers.NETHERITE,  // Tier - 可根据需要调整
                    50,               // Attack damage modifier
                    2.0f,            // Attack speed modifier
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .durability(0)
            ) {
                @Override
                public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
                    return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                            state.is(BlockTags.MINEABLE_WITH_AXE) ||
                            state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                            state.is(BlockTags.MINEABLE_WITH_HOE);
                }
            });
    
    // 撬棍子类物品注册
    public static final RegistryObject<Item> ENDLESS_BEAF_CROWBAR = ITEMS.register("endless_beaf_crowbar",
            () -> new EndlessBeafItem(
                    Tiers.NETHERITE,  // Tier - 可根据需要调整
                    50,               // Attack damage modifier
                    2.0f,            // Attack speed modifier
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .durability(0)
            ) {
                @Override
                public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
                    return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                            state.is(BlockTags.MINEABLE_WITH_AXE) ||
                            state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                            state.is(BlockTags.MINEABLE_WITH_HOE);
                }
            });
    
    // 铁锤子类物品注册
    public static final RegistryObject<Item> ENDLESS_BEAF_HAMMER = ITEMS.register("endless_beaf_hammer",
            () -> new EndlessBeafItem(
                    Tiers.NETHERITE,  // Tier - 可根据需要调整
                    50,               // Attack damage modifier
                    2.0f,            // Attack speed modifier
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .durability(0)
            ) {
                @Override
                public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
                    return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ||
                            state.is(BlockTags.MINEABLE_WITH_AXE) ||
                            state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
                            state.is(BlockTags.MINEABLE_WITH_HOE);
                }
            });

    // 检查是否触发战利品大爆发
    private boolean shouldTriggerFestive(ItemStack stack) {
        // 5% 概率
        return Math.random() < 0.05;
    }

    // 显示触发提示
    private void sendFestiveMessage(Player player) {
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.useless_mod.festive_triggered"),
                    true
            );
        }
    }


    @Mod.EventBusSubscriber(modid = UselessMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class EventHandler {
        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent event) {
            // 检查伤害来源是否是玩家
            if (event.getSource().getEntity() instanceof Player player) {
                ItemStack mainHandItem = player.getMainHandItem();

                // 检查主手物品是否是EndlessBeafItem
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    endlessBeaf.onLivingDrops(event, mainHandItem, player);
                }
            }
        }

        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent event) {
            Player player = event.getPlayer();
            if (player == null) return;

            ItemStack mainHandItem = player.getMainHandItem();

            // 检查主手物品是否是EndlessBeafItem
            if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                endlessBeaf.onBlockBreak(event, mainHandItem, player);
            }
        }
        
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            // 获取触发事件的玩家
            Player player = event.getEntity();
            if (player == null) return;
            
            ItemStack mainHandItem = player.getMainHandItem();
            
            // 检查主手物品是否是EndlessBeafItem
            if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                // 处理左键点击事件，用于跟踪强制挖掘
                endlessBeaf.onLeftClickBlock(event, mainHandItem, player);
            }
        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) return;

            // 只检查增强连锁模式切换按键（数字键8）
            while (KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY.consumeClick()) {
                ItemStack mainHandItem = player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 处理增强模式切换按键逻辑
                    endlessBeaf.handleEnhancedChainMiningKey(mainHandItem, player);
                }
            }
            
            // 检查强制挖掘模式切换按键（数字键9）
            while (KeyBindings.SWITCH_FORCE_MINING_KEY.consumeClick()) {
                ItemStack mainHandItem = player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 处理强制挖掘模式切换按键逻辑
                    endlessBeaf.handleForceMiningKey(mainHandItem, player);
                }
            }
            
            // 检查设置主扩展样板供应器按键（M键）
            while (KeyBindings.SET_MASTER_PATTERN_KEY.consumeClick()) {
                ItemStack mainHandItem = player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 处理设置主方块按键逻辑
                    endlessBeaf.handleSetMasterPatternKey(mainHandItem, player);
                }
            }
            
            // 检查设置从扩展样板供应器按键（S键）
            while (KeyBindings.SET_SLAVE_PATTERN_KEY.consumeClick()) {
                ItemStack mainHandItem = player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 处理设置从方块按键逻辑
                    endlessBeaf.handleSetSlavePatternKey(mainHandItem, player);
                }
            }
        }
        
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton event) {
            // 现在强制破坏通过按键触发，不再需要处理鼠标左键事件
            // 此方法已废弃，保留用于向后兼容
        }
        
        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            Player player = event.player;
            Level world = player.level();
            
            // 只在服务器端执行
            if (world.isClientSide()) {
                return;
            }
            
            // 首次加载同步数据
            if (masterToSlaves.isEmpty() && slaveToMaster.isEmpty()) {
                loadSyncData(world);
            }
            
            // 定期同步扩展样板供应器主从方块
            if (event.phase == TickEvent.Phase.END) {
                syncAllSlaves(world);
            }
            
            // 处理飞行权限管理 - 每次tick都检查，不受phase限制
            // 检查玩家物品栏中是否有任何EndlessBeafItem变体
            boolean hasItemInInventory = player.getInventory().items.stream()
                    .anyMatch(item -> item.getItem() instanceof EndlessBeafItem || 
                    (item.hasTag() && item.getTag().contains("ToolModes")));
            
            UUID playerId = player.getUUID();
            
            if (hasItemInInventory) {
                // 物品在物品栏中，赋予飞行权限
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, true);
                
                // 给予饱和效果（不显示粒子，但显示图标）
                MobEffectInstance baohe = player.getEffect(MobEffects.SATURATION);
                if (baohe == null || baohe.getDuration() < 20) {
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0, true, false, true));
                }

                // 给予生命恢复效果（不显示粒子，但显示图标）
                MobEffectInstance zaisheng = player.getEffect(MobEffects.REGENERATION);
                if (zaisheng == null || (zaisheng.getDuration() < 20)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 5, true, false, true));
                }

                // 给予夜视效果（不显示粒子，但显示图标）
                MobEffectInstance yeshi = player.getEffect(MobEffects.NIGHT_VISION);
                if (yeshi == null || (yeshi.getDuration() < 2000)) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20000, 0, true, false, true));
                }
                
                // 新增：给予抗火效果（不显示粒子，但显示图标）
                MobEffectInstance kanghuo = player.getEffect(MobEffects.FIRE_RESISTANCE);
                if (kanghuo == null || kanghuo.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, true, false, true));
                }
                
                // 新增：给予水下呼吸效果（不显示粒子，但显示图标）
                MobEffectInstance shuixiabreath = player.getEffect(MobEffects.WATER_BREATHING);
                if (shuixiabreath == null || shuixiabreath.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 2000, 0, true, false, true));
                }
                
                // 新增：给予抗性提升效果（不显示粒子，但显示图标）
                MobEffectInstance kangxing = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (kangxing == null || kangxing.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2000, 5, true, false, true));
                }
            } else {
                // 物品不在物品栏中，对于非创造模式玩家，关闭飞行权限
                // 只有当玩家是因为我们的工具才获得飞行权限的情况下，才会关闭飞行权限
                if (!player.isCreative() && player.getAbilities().mayfly && playerFlightStatus.getOrDefault(playerId, false)) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, false);
            }
        }
    }

    // 处理掉落物事件的方法
    public void onLivingDrops(LivingDropsEvent event, ItemStack stack, Player player) {
        if (!shouldTriggerFestive(stack)) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Level level = killedEntity.level();

        if (!level.isClientSide) {
            // 显示提示消息
            sendFestiveMessage(player);
            // 直接修改掉落物堆叠数量 - 更简单有效的方法
            Collection<ItemEntity> drops = event.getDrops();
            List<ItemEntity> newDrops = new ArrayList<>();

            for (ItemEntity itemEntity : drops) {
                if (!isEquipment(itemEntity.getItem())) {
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

    // 处理玩家左键点击方块事件（用于强制挖掘）
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event, ItemStack stack, Player player) {
        // 现在强制破坏通过按键触发，不再需要处理左键点击事件
    }
    
    // 处理方块破坏事件的方法 - 新增功能
    public void onBlockBreak(BlockEvent.BreakEvent event, ItemStack stack, Player player) {
        // 修复：创造模式下不处理自动收集
        if (player.isCreative()) {
            return;
        }

        LevelAccessor levelAccessor = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        // 确保我们在服务器端并且 LevelAccessor 可以转换为 Level
        if (levelAccessor.isClientSide() || !(levelAccessor instanceof Level level)) {
            return;
        }
        
        // 现在强制挖掘通过按键触发，不再需要旧的按住左键触发逻辑
        // 旧机制相关代码已移除

        // 检查是否应该执行连锁挖掘
        boolean shouldChainMine = shouldUseChainMining(stack);
        
        if (shouldChainMine) {
            // 执行连锁挖掘
            performChainMining(event, level, pos, state, player, stack);
        } else {
            // 普通挖掘 - 调用工具类
            BlockBreakUtils.normalBreakBlock(event, level, pos, state, player, stack);
        }
    }
    
    // 执行强制挖掘
    private void performForceMining(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        // 调用强制破坏工具类
        ForceBreakUtils.forceBreakBlock(event, level, pos, state, player, stack);
    }
    
    // 执行连锁挖掘
    private void performChainMining(BlockEvent.BreakEvent event, Level level, BlockPos originPos, BlockState originState, Player player, ItemStack stack) {
        // 获取连锁挖掘最大方块数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        
        // 根据增强连锁设置使用不同的连锁逻辑
        boolean enhancedMode = isEnhancedChainMiningMode(stack);
        List<BlockPos> blocksToMine;
        
        // 调用相应的工具类查找待挖掘的方块，普通连锁模式下forceMining为false
        if (enhancedMode) {
            blocksToMine = EnhancedChainMiningUtils.findBlocksToMine(originPos, originState, level, stack, false);
        } else {
            blocksToMine = NormalChainMiningUtils.findBlocksToMine(originPos, originState, level, stack, false);
        }
        
        // 处理原点方块
        if (!blocksToMine.isEmpty()) {
            BlockPos firstPos = blocksToMine.get(0);
            if (firstPos.equals(originPos)) {
                // 对原点方块使用普通破坏，会自动处理事件取消
                BlockBreakUtils.normalBreakBlock(event, level, firstPos, level.getBlockState(firstPos), player, stack);
                
                // 处理剩余的方块
                for (int i = 1; i < blocksToMine.size() && i < maxBlocks; i++) {
                    BlockPos pos = blocksToMine.get(i);
                    BlockState state = level.getBlockState(pos);
                    
                    // 检查方块是否还存在
                    if (!level.isEmptyBlock(pos)) {
                        // 创建新的BreakEvent用于处理其他方块
                        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                                level, pos, state, player
                        );
                        BlockBreakUtils.normalBreakBlock(breakEvent, level, pos, state, player, stack);
                    }
                }
            }
        }
    }

    // 检查物品是否是装备（基于Festive Affix的逻辑）
    private boolean isEquipment(ItemStack stack) {
        // 检查是否有装备标记（基于Festive Affix的逻辑）
        if (stack.hasTag() && stack.getTag().getBoolean("apoth.equipment")) {
            return true;
        }

        // 可损坏的物品通常是装备（工具、武器、盔甲）
        return stack.isDamageableItem();
    }

    @Override
    public Component getName(ItemStack stack) {
        // 获取基础名称
        Component baseName = super.getName(stack);

        // 根据模式添加后缀
        if (isSilkTouchMode(stack)) {
            return Component.translatable("item.useless_mod.endless_beaf_item.silk_touch");
        } else {
            return Component.translatable("item.useless_mod.endless_beaf_item.fortune");
        }
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        super.inventoryTick(pStack, pLevel, pEntity, pSlotId, pIsSelected);
        if(pEntity instanceof Player player){
            UUID playerId = player.getUUID();
            boolean hasItemInInventory = player.getInventory().items.stream()
                    .anyMatch(item -> item.getItem() instanceof EndlessBeafItem || 
                    (item.hasTag() && item.getTag().contains("ToolModes")));

            if (hasItemInInventory) {
                // 给予饱和效果（不显示粒子，但显示图标）
                MobEffectInstance baohe = player.getEffect(MobEffects.SATURATION);
                if (baohe == null || baohe.getDuration() < 20) {
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0, true, false, true));
                }

                // 给予生命恢复效果（不显示粒子，但显示图标）
                MobEffectInstance zaisheng = player.getEffect(MobEffects.REGENERATION);
                if (zaisheng == null || (zaisheng.getDuration() < 20)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 5, true, false, true));
                }

                // 给予夜视效果（不显示粒子，但显示图标）
                MobEffectInstance yeshi = player.getEffect(MobEffects.NIGHT_VISION);
                if (yeshi == null || (yeshi.getDuration() < 2000)) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20000, 0, true, false, true));
                }
                
                // 新增：给予抗火效果（不显示粒子，但显示图标）
                MobEffectInstance kanghuo = player.getEffect(MobEffects.FIRE_RESISTANCE);
                if (kanghuo == null || kanghuo.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, true, false, true));
                }
                
                // 新增：给予水下呼吸效果（不显示粒子，但显示图标）
                MobEffectInstance shuixiabreath = player.getEffect(MobEffects.WATER_BREATHING);
                if (shuixiabreath == null || shuixiabreath.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 2000, 0, true, false, true));
                }
                
                // 新增：给予抗性提升效果（不显示粒子，但显示图标）
                MobEffectInstance kangxing = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (kangxing == null || kangxing.getDuration() < 200) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2000, 5, true, false, true));
                }
                
                // 新增：当物品在玩家物品栏内允许飞行（无论游戏模式）
                // 修复：直接检查玩家当前飞行状态，兼容外部飞行管理系统
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, true);
            } else {
                // 物品不在物品栏时，对于非创造模式的玩家，关闭飞行权限
                // 只有当玩家是因为我们的工具才获得飞行权限的情况下，才会关闭飞行权限
                if (!player.isCreative() && player.getAbilities().mayfly && playerFlightStatus.getOrDefault(playerId, false)) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, false);
            }
        }
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull BlockState state) {
        return true;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        // 声明这个物品可以执行斧头和锄头的所有动作
        return toolAction.equals(ToolActions.AXE_STRIP) ||
                toolAction.equals(ToolActions.AXE_SCRAPE) ||
                toolAction.equals(ToolActions.AXE_WAX_OFF) ||
                net.minecraftforge.common.ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction) ||
                super.canPerformAction(stack, toolAction)||
                toolAction.equals(ToolActions.HOE_TILL);
    }
    
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader world, BlockPos pos, Player player) {
        // 检查是否是塑料块，如果是则不跳过useOn方法，这样快速拆塑料块功能才能生效
        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof GlowPlasticBlock) {
            // 对于塑料块，不绕过useOn方法，以便执行快速破坏逻辑
            return false;
        }
        // 对于其他方块，允许Shift+右键事件传递到方块/机器，这对于格雷机器的边缘选择框功能至关重要
        return true;
    }

    private boolean isPlasticBlock(Block block) {
        // 直接检查是否是 GlowPlasticBlock 的实例
        if (block instanceof GlowPlasticBlock) {
            return true;
        }
        return false;
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState blockstate = world.getBlockState(blockpos);
        
        // 扩展样板供应器主从同步处理已改为按键绑定（M键和S键），这里不再处理
        // 允许默认的右键行为，以便打开GUI

        // 修复：创造模式下不处理快速破坏塑料块
        if (player != null && player.isCreative()) {
            return InteractionResult.PASS;
        }

        // 按住 Shift 的右键仍然保留你原本的"快速破坏塑料块（不掉落粒子）"逻辑
        if (player != null && player.isShiftKeyDown()) {
            if (isPlasticBlock(blockstate.getBlock())) {
                if (!world.isClientSide) {
                    // 在服务器端：把方块的掉落物放进背包（或在背包满时丢出）
                    List<ItemStack> drops = BlockBreakUtils.getBlockDrops(blockstate, (Level) world, blockpos, player, context.getItemInHand());
                    for (ItemStack drop : drops) {
                        // 复制一个堆叠放入（以免修改原 list）
                        ItemStack toAdd = drop.copy();
                        if (!BlockBreakUtils.addItemToPlayerInventory(player, toAdd)) {
                            // 背包满了：丢在玩家脚下
                            player.drop(toAdd, false);
                        }
                    }

                    // 移除方块并播放声音
                    world.setBlock(blockpos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    // 使用带冷却的音效播放
                    BlockBreakUtils.playBreakSoundWithCooldown(world, blockpos, blockstate, player);   
                } else {
                    // 客户端只播放声音（不做掉落/方块移除）
                    world.playSound(player, blockpos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 1.0F);
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }

        // 以下保持你原本的"万能工具作为斧头/锄头"等的行为
        BlockState resultToSet = null;

        // 1. 作为斧头（去皮）
        BlockState axeResult = blockstate.getToolModifiedState(context, ToolActions.AXE_STRIP, false);
        if (axeResult != null) {
            world.playSound(player, blockpos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            resultToSet = axeResult;
        }

        // 2. 刮蜡
        if (resultToSet == null) {
            BlockState scrapeResult = blockstate.getToolModifiedState(context, ToolActions.AXE_SCRAPE, false);
            if (scrapeResult != null) {
                world.playSound(player, blockpos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
                resultToSet = scrapeResult;
            }
        }

        // 3. 去蜡/解除氧化
        if (resultToSet == null) {
            BlockState oxidizeResult = blockstate.getToolModifiedState(context, ToolActions.AXE_WAX_OFF, false);
            if (oxidizeResult != null) {
                world.playSound(player, blockpos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                resultToSet = oxidizeResult;
            }
        }

        // 4. 锄头耕地
        if (resultToSet == null) {
            BlockState hoeResult = blockstate.getToolModifiedState(context, ToolActions.HOE_TILL, false);
            if (hoeResult != null) {
                world.playSound(player, blockpos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                resultToSet = hoeResult;
            }
        }

        if (resultToSet == null) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide) {
            ItemStack stack = context.getItemInHand();
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, blockpos, stack);
            }
            world.setBlock(blockpos, resultToSet, Block.UPDATE_ALL_IMMEDIATE);
            if (player != null) {
                stack.hurtAndBreak(1, player, onBroken -> onBroken.broadcastBreakEvent(context.getHand()));
            }
        }

        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        // 获取基础破坏速度
        float baseSpeed = 10.0f;

        // 只对有效方块应用速度加成
        if (state.getDestroySpeed(null, null) > 0) {
            // 应用类似MinersFervorEnchant的机制
            // 基础速度7.5F + 每级4.5F加成，最大29.9999F
            float maxSpeed = Math.min(29.9999F, baseSpeed);
            float hardness = state.getDestroySpeed(null, null);
            if (hardness > 0) {
                return maxSpeed * hardness;
            }
        }

        return baseSpeed;
    }
}