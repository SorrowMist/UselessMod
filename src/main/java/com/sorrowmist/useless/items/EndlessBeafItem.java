package com.sorrowmist.useless.items;

import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EndlessBeafItem extends AxeItem {
    public EndlessBeafItem(Tiers type, Properties pProperties) {
        super(type, pProperties);
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

        // 快速破坏塑料块
        if (player != null && player.isShiftKeyDown()) {
            if (blockstate.getBlock() instanceof GlowPlasticBlock) {
                if (!world.isClientSide) {
                    // 在服务器端：把方块的掉落物放进背包（或在背包满时丢出）
                    List<ItemStack> drops = UselessItemUtils.getBlockDrops(blockstate, world, blockpos, player, ctx.getItemInHand());
                    for (ItemStack drop : drops) {
                        // 复制一个堆叠放入（以免修改原 list）
                        ItemStack toAdd = drop.copy();
                        if (!player.getInventory().add(toAdd)) {
                            // 背包满了：丢在玩家脚下
                            player.drop(toAdd, false);
                        }
                    }

                    // 移除方块并播放声音
                    world.setBlock(blockpos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    // 使用带冷却的音效播放
                    UselessItemUtils.playBreakSoundWithCooldown(world, blockpos, blockstate, player);
                } else {
                    // 客户端只播放声音（不做掉落/方块移除）
                    world.playSound(player, blockpos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 1.0F);
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
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
    public void inventoryTick(@NotNull ItemStack pStack, @NotNull Level pLevel, @NotNull Entity pEntity, int pSlotId, boolean pIsSelected) {
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
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE), 5);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        // 功能提示
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.switch_enchantment").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.wrench_function").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.fast_break_plastic").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN)); // 新增提示
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        // 根据模式添加后缀
        if (level != null && level.isClientSide && stack.getTagEnchantments().getLevel(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE)) != 0) {
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
}