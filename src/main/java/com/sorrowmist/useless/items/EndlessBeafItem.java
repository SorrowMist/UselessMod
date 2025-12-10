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
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
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

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class EndlessBeafItem extends PickaxeItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 音效冷却系统
    private static final Map<UUID, Long> lastSoundTime = new HashMap<>();
    private static final long SOUND_COOLDOWN = 50; // 50毫秒冷却时间
    
    // 移除了连锁挖掘模式枚举，现在只根据按键状态判断是否启用连锁挖掘
    
    // 按键状态跟踪
    private static final Map<UUID, Long> lastKeyPressTime = new HashMap<>();
// 不再需要防抖动处理，已移除 防抖动超时时间
    
    // 强制挖掘模式相关
    private static final Map<UUID, Long> miningStartTime = new HashMap<>(); // 跟踪玩家开始挖掘的时间
    private static final Map<UUID, BlockPos> currentMiningPos = new HashMap<>(); // 跟踪玩家当前挖掘的方块位置
    private static final long FORCE_MINING_THRESHOLD = 1000; // 强制挖掘阈值（毫秒）
    


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
        } else if (modeManager.isModeActive(ToolMode.SCREWDRIVER_MODE)) {
            tag.putString("ActiveToolTag", "forge:tools/screwdrivers");
        } else if (modeManager.isModeActive(ToolMode.MALLET_MODE)) {
            tag.putString("ActiveToolTag", "forge:tools/mallets");
        } else if (modeManager.isModeActive(ToolMode.MEK_CONFIGURATOR)) {
            // 添加兼容性检查，确保mek模组已安装
            net.minecraft.resources.ResourceLocation configuratorId = new net.minecraft.resources.ResourceLocation("mekanism:configurator");
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(configuratorId)) {
                tag.putString("ActiveToolTag", "forge:tools/configurators");
            } else {
                // 如果mek模组未安装，移除MEK_CONFIGURATOR模式
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
        
        // 检查激活的工具模式
        boolean hasWrenchMode = modeManager.isModeActive(ToolMode.WRENCH_MODE);
        boolean hasScrewdriverMode = modeManager.isModeActive(ToolMode.SCREWDRIVER_MODE);
        boolean hasMalletMode = modeManager.isModeActive(ToolMode.MALLET_MODE);
        boolean hasCrowbarMode = modeManager.isModeActive(ToolMode.CROWBAR_MODE);
        boolean hasHammerMode = modeManager.isModeActive(ToolMode.HAMMER_MODE);
        boolean hasMekConfiguratorMode = modeManager.isModeActive(ToolMode.MEK_CONFIGURATOR);
        
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
        } else if (hasMekConfiguratorMode) {
            // 创建Mekanism配置器实例 - 这里需要获取Mekanism配置器物品的实例
            // 由于我们不能直接访问Mekanism的物品注册，需要通过物品ID获取
            // 添加兼容性检查，确保mek模组已安装
            net.minecraft.resources.ResourceLocation configuratorId = new net.minecraft.resources.ResourceLocation("mekanism:configurator");
            if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(configuratorId)) {
                newStack = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(configuratorId));
            } else {
                // 如果mek模组未安装，使用基础实例
                newStack = new ItemStack(ENDLESS_BEAF_ITEM.get());
                // 禁用MEK_CONFIGURATOR模式
                modeManager.setModeActive(ToolMode.MEK_CONFIGURATOR, false);
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

    // 切换模式的方法（供数据包调用）
    public void switchEnchantmentMode(ItemStack stack, boolean silkTouchMode) {
        // 保存当前的连锁挖掘按键状态
        boolean chainMiningPressed = isChainMiningPressed(stack);
        
        // 使用模式管理器切换模式
        modeManager.loadFromStack(stack);
        if (silkTouchMode) {
            modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.SILK_TOUCH, true);
        } else {
            modeManager.setModeActive(com.sorrowmist.useless.modes.ToolMode.FORTUNE, true);
        }
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
        String modeWheelKey = "G";
        
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
            
            // 获取模式选择轮盘按键
            KeyMapping modeWheelMapping = KeyBindings.SWITCH_MODE_WHEEL_KEY;
            modeWheelKey = modeWheelMapping.getTranslatedKeyMessage().getString();
        } catch (Exception e) {
            // 如果获取失败，使用默认按键名称
        }
        
        // 添加动态按键提示
        tooltip.add(Component.translatable("tooltip.useless_mod.switch_enchantment").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按住 " + chainMiningKey + "开启连锁挖掘").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按下 " + enhancedChainMiningKey + "切换增强连锁挖掘").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("按下 " + forceMiningKey + "切换强制挖掘模式").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("可作为扳手使用（兼容其他模组）").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("按下 " + modeWheelKey + " 打开模式选择界面").withStyle(ChatFormatting.YELLOW));
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

    // 带冷却的音效播放方法
    private void playBreakSoundWithCooldown(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) return;

        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastSoundTime.get(playerId);

        // 检查冷却时间
        if (lastTime == null || currentTime - lastTime >= SOUND_COOLDOWN) {
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            lastSoundTime.put(playerId, currentTime);
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
        }
        
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton event) {
            // 只处理左键事件
            if (event.getButton() != 0) {
                return;
            }
            
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) {
                return;
            }
            
            ItemStack mainHandItem = player.getMainHandItem();
            if (!(mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf)) {
                return;
            }
            
            // 检查是否处于强制挖掘模式
            if (!endlessBeaf.isForceMiningMode(mainHandItem)) {
                return;
            }
            
            // 获取玩家视线内的方块
            double reachDistance = 4.5D; // 1.20.1中的默认交互范围
            net.minecraft.world.phys.HitResult hitResult = player.pick(reachDistance, 0.0F, false);
            
            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();
            
            if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                // 左键按下，开始计时
                if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    net.minecraft.world.phys.BlockHitResult blockHitResult = (net.minecraft.world.phys.BlockHitResult) hitResult;
                    BlockPos hitPos = blockHitResult.getBlockPos();
                    miningStartTime.put(playerId, currentTime);
                    currentMiningPos.put(playerId, hitPos);
                }
            } else if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
                // 左键松开，重置计时
                miningStartTime.remove(playerId);
                currentMiningPos.remove(playerId);
            }
        }
        
        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            // 只在服务器端执行
            if (event.player.level().isClientSide()) {
                return;
            }
            
            // 只在tick结束时执行
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            
            Player player = event.player;
            ItemStack mainHandItem = player.getMainHandItem();
            
            // 检查主手物品是否是EndlessBeafItem
            if (!(mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf)) {
                return;
            }
            
            // 检查是否处于强制挖掘模式
            if (!endlessBeaf.isForceMiningMode(mainHandItem)) {
                // 如果强制挖掘模式已关闭，清除挖掘状态
                UUID playerId = player.getUUID();
                miningStartTime.remove(playerId);
                currentMiningPos.remove(playerId);
                return;
            }
            
            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();
            
            // 检查是否正在挖掘方块
            // 由于Forge没有直接获取玩家正在挖掘的方块的API，我们需要使用之前记录的位置
            BlockPos miningPos = currentMiningPos.get(playerId);
            if (miningPos == null) {
                return;
            }
            
            // 检查是否持续挖掘同一个方块超过1秒
            Long startTime = miningStartTime.get(playerId);
            
            if (startTime != null && currentTime - startTime >= FORCE_MINING_THRESHOLD) {
                Level level = player.level();
                BlockState state = level.getBlockState(miningPos);
                
                // 执行强制挖掘
                BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                        level, miningPos, state, player
                );
                endlessBeaf.performForceMining(breakEvent, level, miningPos, state, player, mainHandItem);
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
        // 确保我们在服务器端
        if (event.getLevel().isClientSide()) {
            return;
        }
        
        // 检查是否处于强制挖掘模式
        if (!isForceMiningMode(stack)) {
            return;
        }
        
        LevelAccessor levelAccessor = event.getLevel();
        BlockPos pos = event.getPos();
        
        // 检查点击的方块是否存在（不是空气）
        if (levelAccessor.isEmptyBlock(pos)) {
            // 点击的是空气，说明原方块已经被破坏，重置挖掘状态
            UUID playerId = player.getUUID();
            miningStartTime.remove(playerId);
            currentMiningPos.remove(playerId);
            return;
        }
        
        // 计时逻辑已经移到鼠标按钮事件中，这里只需要处理服务器端的逻辑
        // 服务器端的计时和强制挖掘由PlayerTickEvent处理
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
        
        // 处理强制挖掘模式
        if (isForceMiningMode(stack)) {
            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();
            
            // 检查是否是同一个方块
            BlockPos lastPos = currentMiningPos.getOrDefault(playerId, null);
            if (lastPos == null || !lastPos.equals(pos)) {
                // 新的挖掘目标，重置时间
                miningStartTime.put(playerId, currentTime);
                currentMiningPos.put(playerId, pos);
            } else {
                // 同一个方块，检查挖掘时间
                long startTime = miningStartTime.getOrDefault(playerId, currentTime);
                if (currentTime - startTime >= FORCE_MINING_THRESHOLD) {
                    // 超过阈值，执行强制挖掘
                    performForceMining(event, level, pos, state, player, stack);
                    return;
                }
            }
        }

        // 检查是否应该执行连锁挖掘
        boolean shouldChainMine = shouldUseChainMining(stack);
        
        if (shouldChainMine) {
            // 执行连锁挖掘
            performChainMining(event, level, pos, state, player, stack);
        } else {
            // 普通挖掘
            // 尝试获取方块的掉落物
            List<ItemStack> drops = getBlockDrops(state, level, pos, player, stack);

            // 检查是否成功获取到掉落物
            if (drops == null || drops.isEmpty() || hasInvalidDrops(drops)) {
                // 如果没有获取到有效的掉落物，回退到原版破坏逻辑
                // 不取消事件，让方块正常破坏
                handleFallbackBlockBreak(level, pos, state, player, stack);
                return;
            }

            // 对于能正常获取掉落物的方块，使用自动收集逻辑
            handleNormalBlockBreak(event, level, pos, state, player, stack, drops);
        }
    }
    
    // 执行强制挖掘
    private void performForceMining(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        UUID playerId = player.getUUID();
        
        try {
            // 特殊处理混沌水晶：绕过击败混沌龙检查
            boolean isChaosCrystal = false;
            try {
                // 使用反射检查并处理混沌水晶
                Class<?> chaosCrystalClass = Class.forName("com.brandon3055.draconicevolution.blocks.ChaosCrystal");
                Class<?> tileChaosCrystalClass = Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileChaosCrystal");
                
                // 检查当前方块是否是混沌水晶
                if (chaosCrystalClass.isInstance(state.getBlock())) {
                    isChaosCrystal = true;
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
                }
            } catch (Exception e) {
                // 如果Draconic Evolution模组没有安装，忽略此处理
            }
            
            // 如果是混沌水晶，直接生成掉落物并破坏方块，绕过detonate方法的重生逻辑
            if (isChaosCrystal) {
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
                        playBreakSoundWithCooldown(level, pos, state, player);
                        
                        // 清除挖掘状态
                        miningStartTime.remove(playerId);
                        currentMiningPos.remove(playerId);
                        
                        return;
                    }
                } catch (Exception e) {
                    // 如果任何步骤失败，回退到正常的强制挖掘逻辑
                }
            }
            
            // 1. 尝试获取方块的掉落物（使用正常逻辑，包括战利品表和时运）
            List<ItemStack> drops = getBlockDrops(state, level, pos, player, stack);
            
            // 2. 如果没有有效的掉落物，强制掉落一个方块
            if (drops == null || drops.isEmpty() || hasInvalidDrops(drops)) {
                // 强制掉落一个目标方块
                ItemStack forcedDrop = new ItemStack(state.getBlock().asItem(), 1);
                drops = Collections.singletonList(forcedDrop);
            }
            
            // 3. 收集掉落物
            boolean allCollected = true;
            for (ItemStack drop : drops) {
                if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                    if (!addItemToPlayerInventory(player, drop.copy())) {
                        // 如果背包满了，标记为未完全收集
                        allCollected = false;
                        // 掉落在玩家脚下
                        ItemEntity itemEntity = new ItemEntity(level,
                                player.getX(), player.getY(), player.getZ(),
                                drop.copy());
                        level.addFreshEntity(itemEntity);
                    }
                }
            }
            
            // 4. 强制破坏方块
            event.setCanceled(true);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            
            // 5. 播放破坏音效
            playBreakSoundWithCooldown(level, pos, state, player);
            
        } finally {
            // 清除挖掘时间跟踪
            miningStartTime.remove(playerId);
            currentMiningPos.remove(playerId);
        }
    }
    
    // 执行连锁挖掘
    private void performChainMining(BlockEvent.BreakEvent event, Level level, BlockPos originPos, BlockState originState, Player player, ItemStack stack) {
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();
        
        // 获取原点方块类型
        Block originBlock = originState.getBlock();
        

        
        // 获取连锁挖掘最大方块数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        int blocksMined = 0;
        
        // 根据增强连锁设置使用不同的连锁逻辑
        boolean enhancedMode = isEnhancedChainMiningMode(stack);
        if (enhancedMode) {
            // 增强连锁模式：范围挖掘，无需相邻
            for (int x = -rangeX; x <= rangeX && blocksMined < maxBlocks; x++) {
                for (int y = -rangeY; y <= rangeY && blocksMined < maxBlocks; y++) {
                    for (int z = -rangeZ; z <= rangeZ && blocksMined < maxBlocks; z++) {
                        // 计算当前方块位置
                        BlockPos currentPos = originPos.offset(x, y, z);
                        
                        // 检查是否已经挖掘过
                        if (level.isEmptyBlock(currentPos)) {
                            continue;
                        }
                        
                        // 获取当前方块状态
                        BlockState currentState = level.getBlockState(currentPos);
                        Block currentBlock = currentState.getBlock();
                        
                        // 检查是否是相同类型的方块
                        if (currentBlock != originBlock) {
                            continue;
                        }
                        
                        // 检查是否可以被该工具挖掘
                        if (!isCorrectToolForDrops(stack, currentState)) {
                            continue;
                        }
                        
                        // 处理原点方块
                        if (currentPos.equals(originPos)) {
                            // 收集掉落物
                            List<ItemStack> drops = getBlockDrops(currentState, level, currentPos, player, stack);
                            if (drops != null && !drops.isEmpty() && !hasInvalidDrops(drops)) {
                                // 尝试将掉落物放入玩家背包
                                boolean allCollected = true;
                                for (ItemStack drop : drops) {
                                    if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                                        if (!addItemToPlayerInventory(player, drop.copy())) {
                                            // 如果背包满了，标记为未完全收集
                                            allCollected = false;
                                            // 掉落在玩家脚下
                                            ItemEntity itemEntity = new ItemEntity(level,
                                                    player.getX(), player.getY(), player.getZ(),
                                                    drop.copy());
                                            level.addFreshEntity(itemEntity);
                                        }
                                    }
                                }

                                // 取消原版事件并手动设置方块为空气
                                event.setCanceled(true);
                                level.setBlock(currentPos, Blocks.AIR.defaultBlockState(), 3);
                            } else {
                                // 回退到原版逻辑
                                handleFallbackBlockBreak(level, currentPos, currentState, player, stack);
                            }
                            
                            // 播放破坏音效
                            playBreakSoundWithCooldown(level, currentPos, currentState, player);
                        } else {
                            // 处理非原点方块
                            // 收集掉落物
                            List<ItemStack> drops = getBlockDrops(currentState, level, currentPos, player, stack);
                            if (drops != null && !drops.isEmpty() && !hasInvalidDrops(drops)) {
                                // 将方块添加到待挖掘队列
                                UselessMod.MiningTask task = new UselessMod.MiningTask(
                                        (ServerLevel) level, 
                                        player, 
                                        stack.copy(), 
                                        currentPos, 
                                        currentState, 
                                        drops
                                );
                                UselessMod.pendingMiningTasks.offer(task);
                            } else {
                                // 回退到原版逻辑
                                handleFallbackBlockBreak(level, currentPos, currentState, player, stack);
                            }
                            
                            // 播放破坏音效
                            playBreakSoundWithCooldown(level, currentPos, currentState, player);
                            
                            // 增加挖掘计数
                            blocksMined++;
                        }
                    }
                }
            }
        } else {
            // 传统连锁模式：相邻挖掘
            // 创建待挖掘方块集合
            Set<BlockPos> toMine = new HashSet<>();
            Set<BlockPos> visited = new HashSet<>();
            
            // 添加原点方块
            toMine.add(originPos);
            visited.add(originPos);
            
            // 遍历周围方块
            while (!toMine.isEmpty() && blocksMined < maxBlocks) {
                BlockPos currentPos = toMine.iterator().next();
                toMine.remove(currentPos);
                
                // 检查是否已经挖掘过
                if (level.isEmptyBlock(currentPos)) {
                    continue;
                }
                
                // 获取当前方块状态
                BlockState currentState = level.getBlockState(currentPos);
                Block currentBlock = currentState.getBlock();
                
                // 检查是否是相同类型的方块
                if (currentBlock != originBlock) {
                    continue;
                }
                
                // 检查是否可以被该工具挖掘
                if (!isCorrectToolForDrops(stack, currentState)) {
                    continue;
                }
                
                // 处理原点方块
                if (currentPos.equals(originPos)) {
                    // 收集掉落物
                    List<ItemStack> drops = getBlockDrops(currentState, level, currentPos, player, stack);
                    if (drops != null && !drops.isEmpty() && !hasInvalidDrops(drops)) {
                        // 尝试将掉落物放入玩家背包
                        boolean allCollected = true;
                        for (ItemStack drop : drops) {
                            if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                                if (!addItemToPlayerInventory(player, drop.copy())) {
                                    // 如果背包满了，标记为未完全收集
                                    allCollected = false;
                                    // 掉落在玩家脚下
                                    ItemEntity itemEntity = new ItemEntity(level,
                                            player.getX(), player.getY(), player.getZ(),
                                            drop.copy());
                                    level.addFreshEntity(itemEntity);
                                }
                            }
                        }

                        // 取消原版事件并手动设置方块为空气
                        event.setCanceled(true);
                        level.setBlock(currentPos, Blocks.AIR.defaultBlockState(), 3);
                    } else {
                        // 回退到原版逻辑
                        handleFallbackBlockBreak(level, currentPos, currentState, player, stack);
                    }
                    
                    // 播放破坏音效
                    playBreakSoundWithCooldown(level, currentPos, currentState, player);
                } else {
                    // 处理非原点方块
                    // 收集掉落物
                    List<ItemStack> drops = getBlockDrops(currentState, level, currentPos, player, stack);
                    if (drops != null && !drops.isEmpty() && !hasInvalidDrops(drops)) {
                        // 将方块添加到待挖掘队列
                        UselessMod.MiningTask task = new UselessMod.MiningTask(
                                (ServerLevel) level, 
                                player, 
                                stack.copy(), 
                                currentPos, 
                                currentState, 
                                drops
                        );
                        UselessMod.pendingMiningTasks.offer(task);
                        
                        // 增加挖掘计数
                        blocksMined++;
                    } else {
                        // 回退到原版逻辑
                        handleFallbackBlockBreak(level, currentPos, currentState, player, stack);
                    }
                    
                    // 播放破坏音效
                    playBreakSoundWithCooldown(level, currentPos, currentState, player);
                }
                
                // 遍历相邻方块
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            
                            BlockPos neighborPos = currentPos.offset(x, y, z);
                            
                            // 检查是否在范围内
                            int dx = Math.abs(neighborPos.getX() - originPos.getX());
                            int dy = Math.abs(neighborPos.getY() - originPos.getY());
                            int dz = Math.abs(neighborPos.getZ() - originPos.getZ());
                            
                            if (dx <= rangeX && dy <= rangeY && dz <= rangeZ) {
                                if (!visited.contains(neighborPos)) {
                                    toMine.add(neighborPos);
                                    visited.add(neighborPos);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 移除了连锁挖掘后的模式切换逻辑，现在完全由按键控制
        
        // 取消原版事件
        event.setCanceled(true);
    }

    // 检查掉落物列表是否有效
    private boolean hasInvalidDrops(List<ItemStack> drops) {
        // 检查所有掉落物是否都是空气或无效物品
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                return false; // 至少有一个有效掉落物
            }
        }
        return true; // 所有掉落物都无效
    }

    // 回退到原版破坏逻辑的处理
    private void handleFallbackBlockBreak(Level level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        // 记录破坏前已有的物品实体
        List<ItemEntity> existingItems = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos).inflate(3.0));
        Set<UUID> existingItemIds = new HashSet<>();
        for (ItemEntity item : existingItems) {
            existingItemIds.add(item.getUUID());
        }

        // 让方块正常破坏（不取消事件）
        // 使用延迟任务来收集新生成的掉落物
        level.getServer().execute(() -> {
            // 等待一小段时间让掉落物生成
            try {
                Thread.sleep(10); // 10ms通常足够
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 获取新生成的物品实体
            List<ItemEntity> newItems = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(pos).inflate(3.0));

            for (ItemEntity itemEntity : newItems) {
                // 只处理新生成的物品
                if (!existingItemIds.contains(itemEntity.getUUID())) {
                    ItemStack itemStack = itemEntity.getItem().copy();

                    if (!itemStack.isEmpty()) {
                        // 尝试将物品添加到玩家背包
                        if (addItemToPlayerInventory(player, itemStack)) {
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

    // 处理普通方块的自动收集
    private void handleNormalBlockBreak(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state, Player player, ItemStack stack, List<ItemStack> drops) {
        // 尝试将掉落物放入玩家背包
        boolean allCollected = true;
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && drop.getItem() != Items.AIR) {
                if (!addItemToPlayerInventory(player, drop.copy())) {
                    // 如果背包满了，标记为未完全收集
                    allCollected = false;
                    // 掉落在玩家脚下
                    ItemEntity itemEntity = new ItemEntity(level,
                            player.getX(), player.getY(), player.getZ(),
                            drop.copy());
                    level.addFreshEntity(itemEntity);
                }
            }
        }

        // 只有成功收集所有物品时才取消原版事件
        if (allCollected) {
            event.setCanceled(true);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } else {
            // 如果没有完全收集，回退到原版逻辑
            handleFallbackBlockBreak(level, pos, state, player, stack);
            return;
        }

        // 使用带冷却的音效播放
        playBreakSoundWithCooldown(level, pos, state, player);
    }



    // 改进的getBlockDrops方法，增加更完善的异常处理
    private List<ItemStack> getBlockDrops(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Collections.emptyList();
        }

        try {
            // 创建LootParams来获取正确的掉落物
            LootParams.Builder lootParamsBuilder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos));

            List<ItemStack> drops = state.getDrops(lootParamsBuilder);

            // 过滤掉空气和空堆叠
            return drops.stream()
                    .filter(drop -> !drop.isEmpty() && drop.getItem() != Items.AIR)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // 记录错误但不崩溃
            UselessMod.LOGGER.debug("Failed to get drops for block {} at {}: {}",
                    ForgeRegistries.BLOCKS.getKey(state.getBlock()), pos, e.getMessage());
            return Collections.emptyList();
        }
    }

    // 将物品添加到玩家背包
    private boolean addItemToPlayerInventory(Player player, ItemStack stack) {
        if (player.getInventory().add(stack)) {
            // 成功添加到背包
            return true;
        } else {
            // 背包已满
            return false;
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
            boolean hasItemInInventory = player.getInventory().items.stream()
                    .anyMatch(item -> item.getItem() == this);

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
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            } else {
                // 物品不在物品栏时，对于非创造模式的玩家，关闭飞行权限
                if (!player.isCreative()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
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

        // 修复：创造模式下不处理快速破坏塑料块
        if (player != null && player.isCreative()) {
            return InteractionResult.PASS;
        }

        // 按住 Shift 的右键仍然保留你原本的"快速破坏塑料块（不掉落粒子）"逻辑
        if (player != null && player.isShiftKeyDown()) {
            if (isPlasticBlock(blockstate.getBlock())) {
                if (!world.isClientSide) {
                    // 在服务器端：把方块的掉落物放进背包（或在背包满时丢出）
                    List<ItemStack> drops = getBlockDrops(blockstate, (Level) world, blockpos, player, context.getItemInHand());
                    for (ItemStack drop : drops) {
                        // 复制一个堆叠放入（以免修改原 list）
                        ItemStack toAdd = drop.copy();
                        if (!addItemToPlayerInventory(player, toAdd)) {
                            // 背包满了：丢在玩家脚下
                            player.drop(toAdd, false);
                        }
                    }

                    // 移除方块并播放声音
                    world.setBlock(blockpos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    // 使用带冷却的音效播放
                    playBreakSoundWithCooldown(world, blockpos, blockstate, player);
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