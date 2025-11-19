package com.sorrowmist.useless.items;

import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class EndlessBeafItem extends AxeItem {
    public EndlessBeafItem(Tiers type, Properties pProperties) {
        super(type, pProperties);
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        // 阻止任何耐久度设置
        super.setDamage(stack, 0);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return -1; // 允许被附魔
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false; // 物品不可损坏
    }


    private boolean isPlasticBlock(Block block) {
        // 直接检查是否是 GlowPlasticBlock 的实例
        return block instanceof GlowPlasticBlock;
    }


    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        BlockState modified;

        Level world = ctx.getLevel();
        BlockPos blockpos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        BlockState blockstate = world.getBlockState(blockpos);

        if (player != null && player.isCreative()) {
            return InteractionResult.PASS;
        }

        // 按住 Shift 的右键仍然保留你原本的"快速破坏塑料块（不掉落粒子）"逻辑
        if (player != null && player.isShiftKeyDown()) {
            if (isPlasticBlock(blockstate.getBlock())) {
                if (!world.isClientSide) {
                    // 在服务器端：把方块的掉落物放进背包（或在背包满时丢出）
                    List<ItemStack> drops = getBlockDrops(blockstate, world, blockpos, player, ctx.getItemInHand());
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

        // 耕地
        modified = ctx.getLevel().getBlockState(ctx.getClickedPos()).getToolModifiedState(ctx, ItemAbilities.HOE_TILL, false);
        if (modified != null) {
            ctx.getLevel().playSound(null, ctx.getClickedPos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1F, 1F);
            if (!ctx.getLevel().isClientSide) {
                ctx.getLevel().setBlock(ctx.getClickedPos(), modified, 11);
            }
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }

        // 铺路
        modified = ctx.getLevel().getBlockState(ctx.getClickedPos()).getToolModifiedState(ctx, ItemAbilities.SHOVEL_FLATTEN, false);
        if (modified != null) {
            ctx.getLevel().playSound(null, ctx.getClickedPos(), SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1F, 1F);
            if (!ctx.getLevel().isClientSide) {
                ctx.getLevel().setBlock(ctx.getClickedPos(), modified, 11);
            }
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }

        // 其他全部走 AxeItem（剥皮、刮铜、刮蜡）
        return super.useOn(ctx);
    }

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
            return Collections.emptyList();
        }
    }

    // 将物品添加到玩家背包
    private boolean addItemToPlayerInventory(Player player, ItemStack stack) {
        return player.getInventory().add(stack);
    }

    // 音效冷却系统
    private static final Map<UUID, Long> lastSoundTime = new HashMap<>();
    private static final long SOUND_COOLDOWN = 50; // 50毫秒冷却时间

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

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability) ||
                ability == ItemAbilities.SWORD_SWEEP;
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

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        super.inventoryTick(pStack, pLevel, pEntity, pSlotId, pIsSelected);
        if (pEntity instanceof Player player) {
            boolean hasItemInInventory = player.getInventory().items.stream().anyMatch(item -> item.getItem() == this);

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
            }
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE), 5);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        // 功能提示
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.switch_enchantment").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.wrench_function").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.fast_break_plastic").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN)); // 新增提示
    }

    @Override
    public Component getName(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        // 根据模式添加后缀
        if (level != null && level.isClientSide && stack.getTagEnchantments().getLevel(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE)) != 0) {
            return Component.translatable("item.useless_mod.endless_beaf_item.fortune");
        } else {
            return Component.translatable("item.useless_mod.endless_beaf_item.silk_touch");
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 始终显示附魔光效
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true; // 允许被附魔
    }
}