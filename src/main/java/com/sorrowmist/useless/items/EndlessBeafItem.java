package com.sorrowmist.useless.items;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class EndlessBeafItem extends TieredItem {
    private final ToolTypeMode toolType;

    public EndlessBeafItem() {
        this(ToolTypeMode.NONE_MODE);
    }

    public EndlessBeafItem(@Nullable ToolTypeMode toolType) {

        super(Tiers.NETHERITE,
              new Item.Properties()
                      .attributes(DiggerItem.createAttributes(Tiers.NETHERITE, 50, 2.0F))
                      .stacksTo(1)
                      .rarity(Rarity.EPIC)
                      .durability(0)
                      .component(DataComponents.TOOL, new Tool(
                                         List.of(
                                                 // TODO 测试强制挖掘使用
                                                 Tool.Rule.deniesDrops(Tiers.STONE.getIncorrectBlocksForDrops()),
                                                 Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE,
                                                                         Tiers.NETHERITE.getSpeed()
                                                 ),
                                                 Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, Tiers.NETHERITE.getSpeed()),
                                                 Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL,
                                                                         Tiers.NETHERITE.getSpeed()
                                                 ),
                                                 Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_HOE, Tiers.NETHERITE.getSpeed())
                                         ),
                                         1.0F, 0
                                 )
                      )
                      .component(UComponents.EnchantModeComponent, EnchantMode.SILK_TOUCH)
                      .component(UComponents.EnhancedChainMiningComponent, false)
                      .component(UComponents.ForceMiningComponent, false)
                      .component(UComponents.AEStoragePriorityComponent, false)
                      .component(UComponents.CurrentToolTypeComponent, ToolTypeMode.NONE_MODE)
                      .component(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1))
        );
        this.toolType = toolType;
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
    public boolean doesSneakBypassUse(@NotNull ItemStack stack,
                                      @NotNull LevelReader level,
                                      @NotNull BlockPos pos,
                                      @NotNull Player player) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof GlowPlasticBlock) {
            return false;
        }
        return super.doesSneakBypassUse(stack, level, pos, player);
    }

    @Override
    public void setDamage(@NotNull ItemStack stack, int damage) {
        // 阻止任何耐久度设置
        super.setDamage(stack, 0);
    }

    @Override
    public boolean canPerformAction(@NotNull ItemStack stack, @NotNull ItemAbility ability) {
        // 基础工具能力（所有工具都有）
        if (ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_BRUSH_ACTIONS.contains(ability) ||
                ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(ability) ||
                ability == ItemAbilities.SWORD_SWEEP) {
            return true;
        }

        // 根据工具类型返回特定能力
        if (this.toolType == null) {
            return false;
        }

        // GT部分适配
        return switch (this.toolType) {
            case NONE_MODE, OMNITOOL_MODE -> false;
            case WRENCH_MODE -> ability == ItemAbility.get("wrench_rotate") ||
                    ability == ItemAbility.get("wrench_configure") ||
                    ability == ItemAbility.get("wrench_configure_all") ||
                    ability == ItemAbility.get("wrench_configure_items") ||
                    ability == ItemAbility.get("wrench_configure_fluids") ||
                    ability == ItemAbility.get("wrench_dig") ||
                    ability == ItemAbility.get("wrench_dismantle") ||
                    ability == ItemAbility.get("wrench_connect");

            case SCREWDRIVER_MODE -> ability == ItemAbility.get("screwdriver_configure") ||
                    ability == ItemAbility.get("interact_with_cover");

            case MALLET_MODE -> ability == ItemAbility.get("mallet_pause") ||
                    ability == ItemAbility.get("mallet_configure") ||
                    ability == ItemAbility.get("interact_with_cover");

            case CROWBAR_MODE -> ability == ItemAbility.get("crowbar_rotate") ||
                    ability == ItemAbility.get("crowbar_remove_cover") ||
                    ability == ItemAbility.get("crowbar_dig");

            case HAMMER_MODE -> ability == ItemAbility.get("hammer_dig") ||
                    ability == ItemAbility.get("hammer_mute");
        };
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
    public void onUseTick(@NotNull Level level,
                          @NotNull LivingEntity livingEntity,
                          @NotNull ItemStack stack,
                          int remainingUseDuration) {

        if (!(livingEntity instanceof Player player) || remainingUseDuration < 0) {
            livingEntity.releaseUsingItem();
            return;
        }

        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                player,
                e -> !e.isSpectator() && e.isPickable(),
                player.blockInteractionRange()
        );

        if (!(hitResult instanceof BlockHitResult blockHit)
                || hitResult.getType() != HitResult.Type.BLOCK) {
            livingEntity.releaseUsingItem();
            return;
        }

        int i = this.getUseDuration(stack, livingEntity) - remainingUseDuration + 1;
        boolean doBrushTick = i % 10 == 5;

        if (!doBrushTick) {
            return;
        }

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);

        /* ---------- 客户端：粒子 & 音效 ---------- */
        HumanoidArm arm = livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        if (blockState.shouldSpawnTerrainParticles()
                && blockState.getRenderShape() != RenderShape.INVISIBLE) {
            this.spawnBrushParticles(
                    level,
                    blockHit,
                    blockState,
                    livingEntity.getViewVector(0.0F),
                    arm
            );
        }

        SoundEvent sound = blockState.getBlock() instanceof BrushableBlock brushable
                ? brushable.getBrushSound()
                : SoundEvents.BRUSH_GENERIC;

        level.playSound(player, blockPos, sound, SoundSource.BLOCKS);

        /* ---------- 服务端：正常刷取 + 战利品直收 ---------- */
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity instanceof BrushableBlockEntity brushable) {

                // 刷取前记录已有掉落
                AABB area = new AABB(blockPos).inflate(3.0);
                Set<UUID> before = level.getEntitiesOfClass(ItemEntity.class, area)
                                        .stream()
                                        .map(Entity::getUUID)
                                        .collect(Collectors.toSet());

                boolean finished = brushable.brush(
                        level.getGameTime(),
                        player,
                        blockHit.getDirection()
                );

                // 只有刷完那一刻才回收掉落
                if (finished) {
                    level.getEntitiesOfClass(ItemEntity.class, area).stream()
                         .filter(e -> !before.contains(e.getUUID()))
                         .forEach(entity -> {
                             ItemStack drop = entity.getItem().copy();
                             if (!drop.isEmpty()) {
                                 if (!player.getInventory().add(drop)) {
                                     player.drop(drop, false);
                                 }
                             }
                             entity.discard();
                         });
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (player == null) return InteractionResult.PASS;

        // ============================================================
        // 1. 刷子功能 (对 BrushableBlock 生效)
        // ============================================================
        HitResult hitresult = ProjectileUtil.getHitResultOnViewVector(
                player, (p) -> !p.isSpectator() && p.isPickable(), player.blockInteractionRange());

        if (hitresult instanceof BlockHitResult blockHit && hitresult.getType() == HitResult.Type.BLOCK) {
            if (world.getBlockState(blockHit.getBlockPos()).getBlock() instanceof BrushableBlock) {
                player.startUsingItem(ctx.getHand());
                return InteractionResult.CONSUME;
            }
        }

        // ============================================================
        // 2. 快速破坏塑料块 (Shift + 右键)
        // ============================================================
        BlockState state = world.getBlockState(pos);
        if (!player.isCreative() && player.isShiftKeyDown() && state.getBlock() instanceof GlowPlasticBlock) {
            if (!world.isClientSide) {
                MiningUtils.quickBreakBlock(world, pos, state, player, stack);
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        // ============================================================
        // 3. 统一工具行为链 (铲子 -> 锄头 -> 斧头)
        // ============================================================

        // 3.1 铲子 (铺路)
        InteractionResult res = this.tryToolAction(ctx, ItemAbilities.SHOVEL_FLATTEN, SoundEvents.SHOVEL_FLATTEN);
        if (res != InteractionResult.PASS) return res;

        // 3.2 锄头 (耕地)
        res = this.tryToolAction(ctx, ItemAbilities.HOE_TILL, SoundEvents.HOE_TILL);
        if (res != InteractionResult.PASS) return res;

        // 3.3 斧头 (剥皮)
        res = this.tryToolAction(ctx, ItemAbilities.AXE_STRIP, SoundEvents.AXE_STRIP);
        if (res != InteractionResult.PASS) return res;

        // 3.4 斧头 (刮铜)
        res = this.tryScrapeOrWaxOff(ctx, ItemAbilities.AXE_SCRAPE, SoundEvents.AXE_SCRAPE, 3005);
        if (res != InteractionResult.PASS) return res;

        // 3.5 斧头 (去蜡)
        return this.tryScrapeOrWaxOff(ctx, ItemAbilities.AXE_WAX_OFF, SoundEvents.AXE_WAX_OFF, 3004);
    }

    @Override
    @SuppressWarnings("all")
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        // 基础工具速度
        Tool tool = stack.get(DataComponents.TOOL);
        float baseSpeed = Math.max(tool != null ? tool.getMiningSpeed(state) : 10.0F, 10.0F);

        float hardness = state.getDestroySpeed(null, null);
        if (hardness < 0) {
            return 0.0F;
        }

        float speed = baseSpeed * hardness;

        // 防止 NaN / 极端情况
        if (speed <= 0 || Float.isNaN(speed) || Float.isInfinite(speed)) {
            return baseSpeed;
        }

        return speed;
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack,
                                                           @NotNull Player player,
                                                           @NotNull LivingEntity entity,
                                                           @NotNull InteractionHand hand) {
        if (entity instanceof IShearable target) {
            BlockPos pos = entity.blockPosition();
            boolean isClient = entity.level().isClientSide();
            if (target.isShearable(player, stack, entity.level(), pos)) {
                List<ItemStack> drops = target.onSheared(player, stack, entity.level(), pos);
                if (!isClient) {
                    for (ItemStack drop : drops) {
                        if (!drop.isEmpty()) {
                            if (!player.getInventory().add(drop)) {
                                player.drop(drop, false);
                            }
                        }
                    }
                }
                entity.gameEvent(GameEvent.SHEAR, player);
                return InteractionResult.sidedSuccess(isClient);
            }
        }
        return InteractionResult.PASS;
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
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.SILK_TOUCH), 1);
        stack.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.LOOTING),
                      ConfigManager.getLootingLevel()
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        Level level = Minecraft.getInstance().level;

        if (player != null && level != null && player.isUsingItem()) {
            HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                    player,
                    e -> !e.isSpectator() && e.isPickable(),
                    player.blockInteractionRange()
            );

            if (hitResult instanceof BlockHitResult blockHit
                    && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos blockPos = blockHit.getBlockPos();
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.getBlock() instanceof BrushableBlock) {
                    return UseAnim.BRUSH;
                }
            }
        }
        return super.getUseAnimation(stack);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 20;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltipComponents,
                                @NotNull TooltipFlag tooltipFlag) {
        if (ModList.get().isLoaded("gtceu")) {
            ToolTypeMode currentToolType = stack.getOrDefault(
                    UComponents.CurrentToolTypeComponent.get(),
                    ToolTypeMode.NONE_MODE
            );

            tooltipComponents.add(Component.translatable("tooltip.useless_mod.current_tool_mode")
                                           .append(": ")
                                           .append(currentToolType.getTooltip())
                                           .withStyle(ChatFormatting.GOLD));
            tooltipComponents.add(Component.empty());
        }

        // 增强连锁挖矿模式
        boolean chainMiningEnabled = stack.getOrDefault(UComponents.EnhancedChainMiningComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.enhanced_chain_mining_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               chainMiningEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(chainMiningEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.GREEN));

        // 强制挖掘状态
        boolean forceMiningEnabled = stack.getOrDefault(UComponents.ForceMiningComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.force_mining_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               forceMiningEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(forceMiningEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.RED));

        // AE存储优先状态（仅当AE2模组存在时）
        if (ModList.get().isLoaded("ae2")) {
            boolean aeStorageEnabled = stack.getOrDefault(UComponents.AEStoragePriorityComponent.get(), false);
            tooltipComponents.add(Component.translatable("tooltip.useless_mod.ae_storage_priority_mode")
                                           .append(": ")
                                           .append(Component.translatable(
                                                   aeStorageEnabled ? "tooltip.useless_mod.enable" :
                                                           "tooltip.useless_mod.disable"
                                           ).withStyle(aeStorageEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                           .withStyle(ChatFormatting.BLUE));
        }

        tooltipComponents.add(Component.empty());

        // 3. 动态按键提示（Shift 展开）
        if (Screen.hasShiftDown()) {
            // 显示详细按键绑定
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
            tooltipComponents.add(Component.empty());
        } else {
            // 未按 Shift 时显示提示
            tooltipComponents.add(Component.translatable("tooltip.useless_mod.press_shift_for_keys")
                                           .withStyle(ChatFormatting.GRAY));
        }

        // 4. 其他静态功能提示
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
    @OnlyIn(Dist.CLIENT)
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

    /**
     * 通用工具动作逻辑
     */
    private InteractionResult tryToolAction(UseOnContext ctx, ItemAbility ability, SoundEvent sound) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState modified = world.getBlockState(pos).getToolModifiedState(ctx, ability, false);
        if (modified != null) {
            world.playSound(ctx.getPlayer(), pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!world.isClientSide) {
                world.setBlock(pos, modified, 11);
                if (ctx.getPlayer() instanceof ServerPlayer sp) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(sp, pos, ctx.getItemInHand());
                }
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /**
     * 针对铜块刮擦和去蜡的特殊逻辑 (带 LevelEvent 粒子效果)
     */
    private InteractionResult tryScrapeOrWaxOff(UseOnContext ctx, ItemAbility ability, SoundEvent sound,
                                                int levelEvent) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState modified = world.getBlockState(pos).getToolModifiedState(ctx, ability, false);
        if (modified != null) {
            world.playSound(ctx.getPlayer(), pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            world.levelEvent(ctx.getPlayer(), levelEvent, pos, 0);
            if (!world.isClientSide) {
                world.setBlock(pos, modified, 11);
                if (ctx.getPlayer() instanceof ServerPlayer sp) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(sp, pos, ctx.getItemInHand());
                }
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void spawnBrushParticles(Level level,
                                     BlockHitResult hitResult,
                                     BlockState state,
                                     Vec3 pos,
                                     HumanoidArm arm) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        int j = level.getRandom().nextInt(7, 12);
        BlockParticleOption blockparticleoption = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Direction direction = hitResult.getDirection();
        Vec3 vec3 = hitResult.getLocation();

        for (int k = 0; k < j; ++k) {
            level.addParticle(blockparticleoption,
                              vec3.x - (double) (direction == Direction.WEST ? 1.0E-6F : 0.0F),
                              vec3.y,
                              vec3.z - (double) (direction == Direction.NORTH ? 1.0E-6F : 0.0F),
                              (direction.getAxis() == Direction.Axis.X ? 0.0 :
                                      direction.getStepX()) * i * 3.0 * level.getRandom().nextDouble(),
                              0.0,
                              (direction.getAxis() == Direction.Axis.Z ? 0.0 :
                                      direction.getStepZ()) * i * 3.0 * level.getRandom().nextDouble()
            );
        }
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

        tooltip.add(Component.translatable(translationKey, keyName).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}