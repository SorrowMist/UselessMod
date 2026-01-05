package com.sorrowmist.useless.items;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

public class EndlessBeafItem extends AxeItem {
    public EndlessBeafItem(Tiers type, Properties pProperties) {
        super(type, pProperties);
    }

    @Override
    public @NotNull ItemStack getCraftingRemainingItem(ItemStack stack) {
        // 返回物品本身，使其在合成后保留在工作台中
        return stack.copy();
    }

    @Override
    public boolean hasCraftingRemainingItem(@NotNull ItemStack stack) {
        // 确保该物品有剩余物品（即本身）
        return true;
    }

    @Override
    public void setDamage(@NotNull ItemStack stack, int damage) {
        // 阻止任何耐久度设置
        super.setDamage(stack, 0);
    }

    @Override
    public int getEnchantmentValue(@NotNull ItemStack stack) {
        return -1; // 允许被附魔
    }

    @Override
    public boolean isDamageable(@NotNull ItemStack stack) {
        return false; // 物品不可损坏
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockpos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        BlockState blockstate = world.getBlockState(blockpos);

        if (player != null && player.isCreative()) {
            return InteractionResult.PASS;
        }

        // === 快速破坏塑料块（Shift + 右键）===
        if (player != null && player.isShiftKeyDown() && blockstate.getBlock() instanceof GlowPlasticBlock) {
            MiningUtils.quickBreakBlock(world, blockpos, blockstate, player, ctx.getItemInHand());
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        // 耕地
        BlockState modified = blockstate.getToolModifiedState(ctx, ItemAbilities.HOE_TILL, false);
        if (modified != null) {
            world.playSound(null, blockpos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1F, 1F);
            if (!world.isClientSide) {
                world.setBlock(blockpos, modified, 11);
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        // 铺路
        modified = blockstate.getToolModifiedState(ctx, ItemAbilities.SHOVEL_FLATTEN, false);
        if (modified != null) {
            world.playSound(null, blockpos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1F, 1F);
            if (!world.isClientSide) {
                world.setBlock(blockpos, modified, 11);
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        // 其他全部走 AxeItem（剥皮、刮铜、刮蜡）
        return super.useOn(ctx);
    }

    @Override
    public boolean canPerformAction(@NotNull ItemStack stack, @NotNull ItemAbility ability) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability) ||
                ability == ItemAbilities.SWORD_SWEEP;
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, BlockState state) {
        // 获取基础破坏速度
        float baseSpeed = 10.0f;
        // 只对有效方块应用速度加成
        if (state.getDestroySpeed(null, null) > 0) {
            // 应用类似MinersFervorEnchant的机制
            // 基础速度7.5F + 每级4.5F加成，最大29.9999F
            float hardness = state.getDestroySpeed(null, null);
            if (hardness > 0) {
                return baseSpeed * hardness;
            }
        }

        return baseSpeed;
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        return true;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack pStack,
                              @NotNull Level pLevel,
                              @NotNull Entity pEntity,
                              int pSlotId,
                              boolean pIsSelected) {
        super.inventoryTick(pStack, pLevel, pEntity, pSlotId, pIsSelected);
        if (pEntity instanceof Player player) {
            boolean hasItemInInventory = player.getInventory().items.stream().anyMatch(item -> item.getItem() == this);
            if (hasItemInInventory) {
                UselessItemUtils.applyEndlessBeafEffects(player);
            }
        }
    }

    @Override
    public void onCraftedBy(@NotNull ItemStack stack, @NotNull Level level, @NotNull Player player) {
        super.onCraftedBy(stack, level, player);
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE), 10);
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.LOOTING), 10);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {

        FunctionMode.getTooltipDisplayGroups().forEach(group -> {
            MutableComponent title = group.getTitleComponent();
            Component status = FunctionMode.getStatusForGroup(
                    group,
                    stack.getOrDefault(UComponents.FunctionModesComponent.get(), EnumSet.noneOf(FunctionMode.class))
            );

            MutableComponent line = title.copy().append(status);
            tooltipComponents.add(line);
        });

        // 功能提示
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.switch_enchantment")
                                       .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.wrench_function").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.fast_break_plastic").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN)); // 新增提示

//
//        // 显示当前模式
//        if (isSilkTouchMode(stack)) {
//            tooltip.add(Component.translatable("tooltip.useless_mod.silk_touch_mode").withStyle(ChatFormatting.AQUA));
//            int silkTouchLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SILK_TOUCH, stack);
//            if (silkTouchLevel > 0) {
//                tooltip.add(Component.translatable("tooltip.useless_mod.silk_touch_level", silkTouchLevel).withStyle(ChatFormatting.GOLD));
//            }
//        } else {
//            tooltip.add(Component.translatable("tooltip.useless_mod.fortune_mode").withStyle(ChatFormatting.GOLD));
//            int fortuneLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BLOCK_FORTUNE, stack);
//            if (fortuneLevel > 0) {
//                tooltip.add(Component.translatable("tooltip.useless_mod.fortune_level", fortuneLevel).withStyle(ChatFormatting.GREEN));
//                if (fortuneLevel > ConfigManager.getFortuneLevel()) {
//                    tooltip.add(Component.translatable("tooltip.useless_mod.external_enchantment").withStyle(ChatFormatting.RED));
//                }
//            }
//        }
//
//
//
//        // 增强连锁模式提示
//        tooltip.add(Component.literal(isEnhancedChainMiningMode(stack) ? "增强连锁模式: 已开启" : "增强连锁模式: 已关闭").withStyle(isEnhancedChainMiningMode(stack) ? ChatFormatting.BLUE : ChatFormatting.GRAY));
//
//        // 强制挖掘模式提示
//        tooltip.add(Component.literal(isForceMiningMode(stack) ? "强制挖掘模式: 已开启" : "强制挖掘模式: 已关闭").withStyle(isForceMiningMode(stack) ? ChatFormatting.RED : ChatFormatting.GRAY));
//
//        // 功能提示 - 动态显示按键绑定
//        // 尝试获取实际按键绑定（仅在客户端）
//        String silkTouchKey = "Page Down";
//        String fortuneKey = "Page Up";
//        String chainMiningKey = "Tab";
//        String enhancedChainMiningKey = "Numpad 8";
//        String forceMiningKey = "Numpad 9";
//        String triggerForceMiningKey = "R";
//        String modeWheelKey = "G";
//
//        // 新增：主从选择按键
//        String setMasterKey = "M";
//        String setSlaveKey = "S";
//
//        try {
//            // 获取精准采集/时运切换按键
//            KeyMapping silkTouchMapping = KeyBindings.SWITCH_SILK_TOUCH_KEY;
//            silkTouchKey = silkTouchMapping.getTranslatedKeyMessage().getString();
//
//            KeyMapping fortuneMapping = KeyBindings.SWITCH_FORTUNE_KEY;
//            fortuneKey = fortuneMapping.getTranslatedKeyMessage().getString();
//
//            // 获取连锁挖掘切换按键
//            chainMiningKey = KeyBindings.SWITCH_CHAIN_MINING_KEY.getTranslatedKeyMessage().getString();
//
//            // 获取增强连锁模式切换按键
//            enhancedChainMiningKey = KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY.getTranslatedKeyMessage().getString();
//
//            // 获取强制挖掘模式切换按键
//            forceMiningKey = KeyBindings.SWITCH_FORCE_MINING_KEY.getTranslatedKeyMessage().getString();
//
//            // 获取强制挖掘触发按键
//            triggerForceMiningKey = KeyBindings.TRIGGER_FORCE_MINING_KEY.getTranslatedKeyMessage().getString();
//
//            // 获取模式选择轮盘按键
//            KeyMapping modeWheelMapping = KeyBindings.SWITCH_MODE_WHEEL_KEY;
//            modeWheelKey = modeWheelMapping.getTranslatedKeyMessage().getString();
//
//            // 获取主从选择按键
//            KeyMapping setMasterMapping = KeyBindings.SET_MASTER_PATTERN_KEY;
//            setMasterKey = setMasterMapping.getTranslatedKeyMessage().getString();
//
//            KeyMapping setSlaveMapping = KeyBindings.SET_SLAVE_PATTERN_KEY;
//            setSlaveKey = setSlaveMapping.getTranslatedKeyMessage().getString();
//        } catch (Exception e) {
//            // 如果获取失败，使用默认按键名称
//        }
//
//        // 添加动态按键提示
//        tooltip.add(Component.translatable("tooltip.useless_mod.switch_enchantment").withStyle(ChatFormatting.LIGHT_PURPLE));
//        tooltip.add(Component.literal("按住 " + chainMiningKey + "开启连锁挖掘").withStyle(ChatFormatting.LIGHT_PURPLE));
//        tooltip.add(Component.literal("按下 " + enhancedChainMiningKey + "切换增强连锁挖掘").withStyle(ChatFormatting.LIGHT_PURPLE));
//        tooltip.add(Component.literal("按下 " + forceMiningKey + "切换强制挖掘模式").withStyle(ChatFormatting.LIGHT_PURPLE));
//        tooltip.add(Component.literal("按下 " + triggerForceMiningKey + "触发强制破坏").withStyle(ChatFormatting.RED));
//        tooltip.add(Component.literal("按下 " + modeWheelKey + " 打开模式选择界面").withStyle(ChatFormatting.YELLOW));
//        tooltip.add(Component.literal("按下 " + setMasterKey + "设置主扩展样板供应器").withStyle(ChatFormatting.BLUE));
//        tooltip.add(Component.literal("按下 " + setSlaveKey + "设置从扩展样板供应器").withStyle(ChatFormatting.BLUE));
//        tooltip.add(Component.translatable("tooltip.useless_mod.fast_break_plastic").withStyle(ChatFormatting.GREEN));
//        tooltip.add(Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
//        tooltip.add(Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN)); // 新增提示
//        tooltip.add(Component.translatable("tooltip.useless_mod.enhanced_chain_description").withStyle(ChatFormatting.BLUE));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }


    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        // 根据模式添加后缀
        if (level != null
                && level.isClientSide
                && stack.getTagEnchantments()
                        .getLevel(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE)) != 0) {
            return Component.translatable("item.useless_mod.endless_beaf_item.fortune");
        } else {
            return Component.translatable("item.useless_mod.endless_beaf_item.silk_touch");
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true; // 始终显示附魔光效
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return true; // 允许被附魔
    }

    @Override
    public @NotNull ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        // 构造附魔
        ItemEnchantments.Mutable ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        ench.set(EnchantmentUtil.getEnchantmentHolder(Enchantments.SILK_TOUCH), 1);
        ench.set(EnchantmentUtil.getEnchantmentHolder(Enchantments.LOOTING), 10);
        stack.set(DataComponents.ENCHANTMENTS, ench.toImmutable());
        return stack;
    }
}