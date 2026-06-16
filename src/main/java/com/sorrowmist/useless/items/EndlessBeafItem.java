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

import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.PlayerSource;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.client.KeyBindings;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.modes.ModeManager;
import com.sorrowmist.useless.modes.ToolMode;
import com.sorrowmist.useless.networking.EnhancedChainMiningTogglePacket;
import com.sorrowmist.useless.networking.ForceMiningTogglePacket;
import com.sorrowmist.useless.networking.ModMessages;
import com.sorrowmist.useless.networking.ResetMasterPatternPacket;
import com.sorrowmist.useless.networking.SetMasterPatternPacket;
import com.sorrowmist.useless.networking.SetSlavePatternPacket;
import com.sorrowmist.useless.utils.BlockBreakUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import com.sorrowmist.useless.utils.pattern.PatternProviderEvent;
import com.sorrowmist.useless.utils.pattern.PatternProviderKey;
import com.sorrowmist.useless.utils.pattern.PatternProviderManager;
import com.sorrowmist.useless.utils.pattern.PatternProviderOperation;
import com.sorrowmist.useless.utils.pattern.PatternProviderSyncData;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EndlessBeafItem extends PickaxeItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // AE2无线访问点链接相关
    private static final String TAG_ACCESS_POINT_POS = "accessPoint";
    public static final IGridLinkableHandler LINKABLE_HANDLER = new LinkableHandler();

    // 飞行状态跟踪，避免重复设置导致卡顿
    private static final Map<UUID, Boolean> playerFlightStatus = new HashMap<>();

    // 同步数据标签
    private static final String SYNC_DATA_TAG = "PatternProviderSyncData";

    // 为了兼容其他类（如mixin），提供静态访问方法
    public static Map<PatternProviderKey, Set<PatternProviderKey>> masterToSlaves = new HashMap<>() {
        @Override
        public Set<PatternProviderKey> get(Object key) {
            return PatternProviderManager.getMasterToSlaves().get(key);
        }

        @Override
        public Set<PatternProviderKey> put(PatternProviderKey key, Set<PatternProviderKey> value) {
            return PatternProviderManager.getMasterToSlaves().put(key, value);
        }

        @Override
        public Set<PatternProviderKey> remove(Object key) {
            return PatternProviderManager.getMasterToSlaves().remove(key);
        }

        @Override
        public void clear() {
            PatternProviderManager.getMasterToSlaves().clear();
        }

        @Override
        public boolean containsKey(Object key) {
            return PatternProviderManager.getMasterToSlaves().containsKey(key);
        }

        @Override
        public boolean isEmpty() {
            return PatternProviderManager.getMasterToSlaves().isEmpty();
        }

        @Override
        public Set<Map.Entry<PatternProviderKey, Set<PatternProviderKey>>> entrySet() {
            return PatternProviderManager.getMasterToSlaves().entrySet();
        }

        @Override
        public Set<PatternProviderKey> keySet() {
            return PatternProviderManager.getMasterToSlaves().keySet();
        }

        @Override
        public int size() {
            return PatternProviderManager.getMasterToSlaves().size();
        }
    };

    public static Map<PatternProviderKey, PatternProviderKey> slaveToMaster = new HashMap<>() {
        @Override
        public PatternProviderKey get(Object key) {
            return PatternProviderManager.getSlaveToMaster().get(key);
        }

        @Override
        public PatternProviderKey put(PatternProviderKey key, PatternProviderKey value) {
            return PatternProviderManager.getSlaveToMaster().put(key, value);
        }

        @Override
        public PatternProviderKey remove(Object key) {
            return PatternProviderManager.getSlaveToMaster().remove(key);
        }

        @Override
        public void clear() {
            PatternProviderManager.getSlaveToMaster().clear();
        }

        @Override
        public boolean containsKey(Object key) {
            return PatternProviderManager.getSlaveToMaster().containsKey(key);
        }

        @Override
        public boolean isEmpty() {
            return PatternProviderManager.getSlaveToMaster().isEmpty();
        }

        @Override
        public Set<Map.Entry<PatternProviderKey, PatternProviderKey>> entrySet() {
            return PatternProviderManager.getSlaveToMaster().entrySet();
        }

        @Override
        public Set<PatternProviderKey> keySet() {
            return PatternProviderManager.getSlaveToMaster().keySet();
        }

        @Override
        public int size() {
            return PatternProviderManager.getSlaveToMaster().size();
        }
    };

    // 使用静态代理来处理currentSelectedMaster的访问
    public static PatternProviderKey getCurrentSelectedMaster() {
        return PatternProviderManager.getCurrentSelectedMaster();
    }

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
        return modeManager.isModeActive(ToolMode.SILK_TOUCH);
    }

    // 获取强化连锁模式
    public boolean isEnhancedChainMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(ToolMode.ENHANCED_CHAIN_MINING);
    }

    // 切换强化连锁模式
    public boolean toggleEnhancedChainMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        modeManager.toggleMode(ToolMode.ENHANCED_CHAIN_MINING);
        modeManager.saveToStack(stack);
        return modeManager.isModeActive(ToolMode.ENHANCED_CHAIN_MINING);
    }

    // 检查是否处于强制挖掘模式
    public boolean isForceMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(ToolMode.FORCE_MINING);
    }

    // 切换强制挖掘模式
    public boolean toggleForceMiningMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        modeManager.toggleMode(ToolMode.FORCE_MINING);
        modeManager.saveToStack(stack);
        return modeManager.isModeActive(ToolMode.FORCE_MINING);
    }

    // 检查是否处于AE存储优先模式
    public boolean isAEStoragePriorityMode(ItemStack stack) {
        modeManager.loadFromStack(stack);
        return modeManager.isModeActive(ToolMode.AE_STORAGE_PRIORITY);
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
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        GlobalPos linkedPos = getLinkedPosition(stack);
        if (linkedPos == null) {
            return null;
        }

        ServerLevel linkedLevel = serverLevel.getServer().getLevel(linkedPos.dimension());
        if (linkedLevel == null) {
            return null;
        }

        BlockEntity be = linkedLevel.getBlockEntity(linkedPos.pos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            return null;
        }

        return accessPoint.getGrid();
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
            return false;
        }

        // 尝试从链接的无线访问点获取AE网络
        try {
            // 获取链接的网格
            IGrid grid = getLinkedGrid(toolItem, player.level(), player);
            if (grid == null) {
                return false;
            }

            // 获取物品存储处理程序
            MEStorage storage = grid.getStorageService().getInventory();
            if (storage == null) {
                return false;
            }

            // 转换为AE物品栈
            AEItemKey aeKey = AEItemKey.of(stack);
            if (aeKey == null) {
                return false;
            }

            // 存入AE网络
            long inserted = storage.insert(aeKey, stack.getCount(), appeng.api.config.Actionable.MODULATE, new PlayerSource(player, null));
            // 如果插入的数量等于物品栈数量，说明全部存入
            if (inserted == stack.getCount()) {
                return true;
            } else if (inserted > 0) {
                // 更新物品栈为剩余数量
                stack.setCount((int) (stack.getCount() - inserted));
                return stack.isEmpty();
            } else {
                return false;
            }
        } catch (Exception e) {
            // 忽略任何异常
        }

        return false;
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

    // 处理设置主扩展样板供应器按键（M键）
    private void handleSetMasterPatternKey(ItemStack stack, Player player) {
        // 获取玩家看向的方块
        double reachDistance = 4.5D;
        HitResult hitResult = player.pick(reachDistance, 0.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos pos = blockHitResult.getBlockPos();
            Direction direction = blockHitResult.getDirection();
            // 发送数据包到服务器，由服务器处理设置主方块
            ModMessages.sendToServer(new SetMasterPatternPacket(pos, direction));
        } else if (hitResult.getType() == HitResult.Type.MISS) {
            // 如果点击到空气，发送数据包到服务器，由服务器处理重置主方块选择
            ModMessages.sendToServer(new ResetMasterPatternPacket());
        }
    }

    // 处理设置从扩展样板供应器按键（S键）
    private void handleSetSlavePatternKey(ItemStack stack, Player player) {
        // 获取玩家看向的方块
        double reachDistance = 4.5D;
        HitResult hitResult = player.pick(reachDistance, 0.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos pos = blockHitResult.getBlockPos();
            Direction direction = blockHitResult.getDirection();
            // 发送数据包到服务器，由服务器处理设置从方块
            ModMessages.sendToServer(new SetSlavePatternPacket(pos, direction));
        }
    }

    // 更新实际的附魔NBT
    public void updateEnchantments(ItemStack stack) {
        // 保存关键状态（使用局部变量存储，确保不会丢失）
        ModeManager modeManager = new ModeManager();
        modeManager.loadFromStack(stack);

        boolean enhancedChainMining = isEnhancedChainMiningMode(stack);
        boolean silkTouchMode = isSilkTouchMode(stack);

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
        return ModList.get().isLoaded("gtceu");
    }

    // 更新工具模式，通过NBT标签跟踪激活的工具模式
    private void updateToolModeTags(ItemStack stack, ModeManager modeManager) {
        // 在NBT中存储激活的工具模式，以便在游戏逻辑中使用
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag toolModesTag = tag.getCompound("ToolModes");

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
            ResourceLocation omnitoolId = new ResourceLocation("omnitools:omni_wrench");
            if (ForgeRegistries.ITEMS.containsKey(omnitoolId)) {
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
            ResourceLocation omnitoolId = new ResourceLocation("omnitools:omni_wrench");
            if (ForgeRegistries.ITEMS.containsKey(omnitoolId)) {
                newStack = new ItemStack(ForgeRegistries.ITEMS.getValue(omnitoolId));
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
            // 物品在物品栏中，根据配置决定是否赋予飞行权限
            if (ConfigManager.enableFlightEffect() && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            playerFlightStatus.put(playerId, true);

            // 根据配置决定是否给予药水效果
            if (ConfigManager.enablePotionEffects()) {
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
        // 使用模式管理器切换模式，保持其他模式状态
        modeManager.loadFromStack(stack);

        // 保存当前所有模式状态
        boolean aeStoragePriority = modeManager.isModeActive(ToolMode.AE_STORAGE_PRIORITY);
        boolean forceMining = modeManager.isModeActive(ToolMode.FORCE_MINING);
        boolean enhancedChainMining = modeManager.isModeActive(ToolMode.ENHANCED_CHAIN_MINING);

        if (silkTouchMode) {
            modeManager.setModeActive(ToolMode.SILK_TOUCH, true);
            modeManager.setModeActive(ToolMode.FORTUNE, false);
        } else {
            modeManager.setModeActive(ToolMode.SILK_TOUCH, false);
            modeManager.setModeActive(ToolMode.FORTUNE, true);
        }

        // 恢复其他重要模式状态
        modeManager.setModeActive(ToolMode.AE_STORAGE_PRIORITY, aeStoragePriority);
        modeManager.setModeActive(ToolMode.FORCE_MINING, forceMining);
        modeManager.setModeActive(ToolMode.ENHANCED_CHAIN_MINING, enhancedChainMining);

        modeManager.saveToStack(stack);

        // 更新实际的附魔NBT
        updateEnchantments(stack);

        // 强制客户端更新物品渲染
        if (!stack.isEmpty()) {
            // 通过修改NBT强制更新
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong("LastModeSwitch", System.currentTimeMillis());
            stack.setTag(tag);
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

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof EndlessBeafItem)) return;

        float newSpeed = event.getOriginalSpeed();

        if (player.getAbilities().flying || player.isInWater()) {
            newSpeed *= 5.0F;
        }
        event.setNewSpeed(newSpeed);
    }

    // 监听方块掉落事件，防止从端样板供应器掉落样板
    @SubscribeEvent
    public static void onBlockDrops(BlockEvent.BreakEvent event) {
        PatternProviderEvent.onBlockDrops(event);
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
                PatternProviderManager.getSlaveToMaster().remove(slaveKey);
                // 移除同步时间记录
                PatternProviderManager.getLastSyncTime().remove(slaveKey);
            }
        }

        // 如果当前选择的主方块是被破坏的主方块，重置选择
        if (PatternProviderManager.getCurrentSelectedMaster() != null && PatternProviderManager.getCurrentSelectedMaster().equals(masterKey)) {
            PatternProviderManager.setCurrentSelectedMaster(null);
        }

        // 保存同步数据
        saveSyncDataStatic(levelAccessor);
    }

    // 处理从端样板供应器被破坏的逻辑
    public static void handleSlaveBreak(LevelAccessor levelAccessor, PatternProviderKey slaveKey) {
        // 获取对应的主端
        PatternProviderKey masterKey = PatternProviderManager.getSlaveToMaster().remove(slaveKey);
        if (masterKey != null) {
            // 从主端的从端列表中移除该从端
            Set<PatternProviderKey> slaves = PatternProviderManager.getMasterToSlaves().get(masterKey);
            if (slaves != null) {
                slaves.remove(slaveKey);
                // 如果主端没有从端了，移除主端映射
                if (slaves.isEmpty()) {
                    PatternProviderManager.getMasterToSlaves().remove(masterKey);
                }
            }
            // 移除同步时间记录
            PatternProviderManager.getLastSyncTime().remove(slaveKey);
        }

        // 保存同步数据
        saveSyncDataStatic(levelAccessor);
    }

    // 清空从端样板供应器的样板
    public static void clearSlavePatterns(LevelAccessor levelAccessor, PatternProviderKey slaveKey) {
        PatternProviderEvent.clearSlavePatterns(levelAccessor, slaveKey);
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
        syncData.getMasterToSlaves().putAll(PatternProviderManager.getMasterToSlaves());
        syncData.getSlaveToMaster().putAll(PatternProviderManager.getSlaveToMaster());

        // 标记为已更改并保存
        syncData.setDirty();
    }

    // 设置为主方块
    public void setAsMaster(Level world, BlockPos masterPos, Direction direction, Player player) {
        PatternProviderOperation.setAsMaster(world, masterPos, direction, player);
    }

    // 添加为从方块
    public void addAsSlave(Level world, BlockPos slavePos, Direction direction, Player player) {
        PatternProviderOperation.addAsSlave(world, slavePos, direction, player);
    }

    // 重置主方块选择（Shift+右键空气）
    public static void resetMasterPatternProvider(Level world) {
        PatternProviderOperation.resetMasterPatternProvider(world);
    }

    // 检查给定位置的从端是否属于当前选定的主端（用于渲染）
    public static boolean isSlaveOfCurrentMaster(BlockPos slavePos, Level level) {
        // 获取当前选定的主端
        PatternProviderKey selectedMasterKey = getCurrentSelectedMaster();
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
                String masterClassName = masterBlockEntity.getClass().getName();
                boolean isBlockForm = masterClassName.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                                     masterBlockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                                     masterClassName.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") ||
                                     masterClassName.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity");

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
        // 调用工具类中的同步方法
        PatternProviderManager.syncAllSlaves(world);
    }

    // 从游戏数据中加载同步数据
    private static void loadSyncData(Level world) {
        if (!(world instanceof ServerLevel serverLevel)) return;

        PatternProviderSyncData syncData = serverLevel.getDataStorage().computeIfAbsent(
                PatternProviderSyncData::load,
                PatternProviderSyncData::new,
                SYNC_DATA_TAG
        );

        // 加载主从关系 - 已移至PatternProviderManager
        PatternProviderManager.loadSyncData(serverLevel);
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
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                MiningDispatcher.dispatchBreak(event, mainHandItem, player);
            }
        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) return;

            // 只检查增强连锁模式切换按键（数字键8）
            while (KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY.consumeClick()) {
                ModMessages.sendToServer(new EnhancedChainMiningTogglePacket());
            }

            // 检查强制挖掘模式切换按键（数字键9）
            while (KeyBindings.SWITCH_FORCE_MINING_KEY.consumeClick()) {
                ModMessages.sendToServer(new ForceMiningTogglePacket());
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
                // 物品在物品栏中，根据配置决定是否赋予飞行权限
                if (ConfigManager.enableFlightEffect() && !player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
                playerFlightStatus.put(playerId, true);

                // 根据配置决定是否给予药水效果
                if (ConfigManager.enablePotionEffects()) {
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
                // 根据配置决定是否给予药水效果
                if (ConfigManager.enablePotionEffects()) {
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
                }

                // 新增：当物品在玩家物品栏内允许飞行（无论游戏模式）
                // 修复：直接检查玩家当前飞行状态，兼容外部飞行管理系统
                if (ConfigManager.enableFlightEffect() && !player.getAbilities().mayfly) {
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
                ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction) ||
                super.canPerformAction(stack, toolAction)||
                toolAction.equals(ToolActions.HOE_TILL);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader world, BlockPos pos, Player player) {
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