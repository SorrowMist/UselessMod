package com.sorrowmist.useless.items;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

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
        // TODO 合成后的附魔
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE),
                      ConfigManager.getFortuneLevel()
        );
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.LOOTING),
                      ConfigManager.getLootingLevel()
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltipComponents,
                                @NotNull TooltipFlag tooltipFlag) {

        // 1. 显示功能模式状态（强制挖掘 + 连锁挖掘）
        EnumSet<FunctionMode> activeModes = stack.getOrDefault(
                UComponents.FunctionModesComponent.get(),
                EnumSet.noneOf(FunctionMode.class)
        );

        FunctionMode.getTooltipDisplayGroups().forEach(group -> {
            MutableComponent title = group.getTitleComponent();
            Component status = FunctionMode.getStatusForGroup(group, activeModes);

            if (!title.getString().isEmpty()) { // 避免空行
                tooltipComponents.add(title.copy().append(": ").append(status));
            }
        });

        // 2. 分隔线（可选，美观）
        tooltipComponents.add(Component.empty());

        // 3. 动态按键提示（仅客户端有效）
        // 确保在客户端运行
        this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_FORTUNE_KEY,
                           "tooltip.useless_mod.key.switch_fortune"
        );
        this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_SILK_TOUCH_KEY,
                           "tooltip.useless_mod.key.switch_silk_touch"
        );
        this.addKeyTooltip(tooltipComponents, KeyBindings.TOGGLE_CHAIN_MODE_KEY,
                           "tooltip.useless_mod.key.toggle_chain_mode"
        );
        this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_FORCE_MINING_KEY,
                           "tooltip.useless_mod.key.switch_force_mining"
        );
        this.addKeyTooltip(tooltipComponents, KeyBindings.TRIGGER_FORCE_MINING_KEY,
                           "tooltip.useless_mod.key.trigger_force_mining"
        );
        this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_MODE_WHEEL_KEY,
                           "tooltip.useless_mod.key.open_mode_wheel"
        );

        // 4. 其他静态功能提示
        tooltipComponents.add(Component.empty());
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.wrench_function").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.fast_break_plastic").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN));

        // 可选：增强连锁说明
        // tooltipComponents.add(Component.translatable("tooltip.useless_mod.enhanced_chain_description").withStyle(ChatFormatting.BLUE));

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

    /**
     * 安全添加带按键名的提示行
     */
    private void addKeyTooltip(List<Component> tooltip, Lazy<KeyMapping> keyLazy, String translationKey) {
        KeyMapping key = keyLazy.get();
        String keyName = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);

        // 处理冲突或未绑定情况
        if (key.isUnbound()) {
            keyName = "Unbound";
        }

        tooltip.add(Component.translatable(translationKey, keyName)
                             .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}