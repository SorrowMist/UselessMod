package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.blocks.UselessGlassBlock;
import com.sorrowmist.useless.compat.enderio.EnderIOTravelCompat;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModDamageTypes;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
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
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EndlessBeafItem extends TieredItem {
    private static final String AE2LT_NATURAL_LIGHTNING_TAG = "ae2lt.natural_weather_lightning";
    private static final Map<UUID, ForceKillContext> FORCE_KILL_CONTEXTS = new ConcurrentHashMap<>();
    // 范围伤害重入保护：避免范围内实体受伤时再次触发范围伤害
    private static final Set<UUID> AOE_DAMAGE_CONTEXT = ConcurrentHashMap.newKeySet();
    private static final int MAX_STANDARD_DAMAGE_ATTEMPTS = 100;
    private static final int TELEPORT_COOLDOWN_TICKS = 5;
    private final ToolTypeMode toolType;

    public EndlessBeafItem() {
        this(ToolTypeMode.NONE_MODE, true);
    }

    public EndlessBeafItem(@Nullable ToolTypeMode toolType) {
        this(toolType, true);
    }

    public EndlessBeafItem(@Nullable ToolTypeMode toolType, boolean wrenchTagEnabled) {
        super(Tiers.NETHERITE, createProperties(wrenchTagEnabled));
        this.toolType = toolType;
    }

    private static Item.Properties createProperties(boolean wrenchTagEnabled) {
        Item.Properties properties = new Item.Properties()
                .attributes(DiggerItem.createAttributes(Tiers.NETHERITE, 0, 2.0F))
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .durability(0)
                .component(DataComponents.TOOL, new Tool(
                        List.of(
                                Tool.Rule.deniesDrops(Tiers.NETHERITE.getIncorrectBlocksForDrops()),
                                Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, Tiers.NETHERITE.getSpeed()),
                                Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, Tiers.NETHERITE.getSpeed()),
                                Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, Tiers.NETHERITE.getSpeed()),
                                Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_HOE, Tiers.NETHERITE.getSpeed())
                        ),
                        1.0F, 0
                ))
                .component(UComponents.EnchantModeComponent, EnchantMode.SILK_TOUCH)
                .component(UComponents.EnhancedChainMiningComponent, false)
                .component(UComponents.ForceMiningComponent, false)
                .component(UComponents.ForceKillEnabledComponent, false)
                .component(UComponents.BeefTimeAccelerationEnabledComponent, false)
                .component(UComponents.BeefInvulnerabilityEnabledComponent, true)
                .component(UComponents.BeefAdvancedStealthEnabledComponent, false)
                .component(UComponents.BeefCaptureEnabledComponent, false)
                .component(UComponents.BeefTeleportEnabledComponent, false)
                .component(UComponents.BeefAoeDamageEnabledComponent, false)
                .component(UComponents.BeefMagnetEnabledComponent, false)
                .component(UComponents.AEStoragePriorityComponent, false)
                .component(UComponents.WrenchTagEnabledComponent, wrenchTagEnabled)
                .component(UComponents.ConstructionWandEnabledComponent, false)
                .component(UComponents.ConstructionWandCoreComponent,
                           com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode.DEFAULT)
                .component(UComponents.CurrentToolTypeComponent, ToolTypeMode.NONE_MODE)
                .component(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));

        if (ModList.get().isLoaded(EnderIOTravelCompat.MOD_ID)) {
            properties = EnderIOTravelCompat.markAsTravelItem(properties);
        }
        return properties;
    }

    public static boolean isTeleportEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.BeefTeleportEnabledComponent.get(), false);
    }

    public static void setTeleportEnabled(ItemStack stack, boolean enabled) {
        stack.set(UComponents.BeefTeleportEnabledComponent.get(), enabled);
        if (ModList.get().isLoaded(EnderIOTravelCompat.MOD_ID)) {
            EnderIOTravelCompat.setTravelItemEnabled(stack, enabled);
        }
    }

    /** Keeps the tool's fixed enchantments aligned with its selected mode and server config. */
    public static void refreshEnchantments(ItemStack stack, Level level) {
        if (stack.isEmpty() || level == null || level.isClientSide()) {
            return;
        }

        EnchantMode mode = stack.getOrDefault(
                UComponents.EnchantModeComponent.get(), EnchantMode.SILK_TOUCH);
        HolderLookup.Provider lookup = level.registryAccess();
        EnchantmentUtil.applyEnchantment(
                stack, lookup, Enchantments.SILK_TOUCH, mode == EnchantMode.SILK_TOUCH ? 1 : 0);
        EnchantmentUtil.applyEnchantment(
                stack, lookup, Enchantments.FORTUNE,
                mode == EnchantMode.FORTUNE ? ConfigManager.getFortuneLevel() : 0);
        EnchantmentUtil.applyEnchantment(
                stack, lookup, Enchantments.LOOTING, ConfigManager.getLootingLevel());
    }

    public static InteractionResult tryTeleport(Level level, Player player, ItemStack stack) {
        if (!isTeleportEnabled(stack) || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.PASS;
        }
        InteractionResult result = BeefTeleportHandler.tryTeleport(
                level,
                player,
                ModList.get().isLoaded(EnderIOTravelCompat.MOD_ID));
        if (result != InteractionResult.PASS) {
            player.getCooldowns().addCooldown(stack.getItem(), TELEPORT_COOLDOWN_TICKS);
        }
        return result;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
            @NotNull Level level,
            @NotNull Player player,
            @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionResult teleportResult = tryTeleport(level, player, stack);
        if (teleportResult != InteractionResult.PASS) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    public static AttributeModifier createAttackDamageModifier() {
        return createAttackDamageModifier(AlloyFurnaceRecipeCatalog.currentRecipeCount());
    }

    static AttributeModifier createAttackDamageModifier(int recipeCount) {
        return new AttributeModifier(
                Item.BASE_ATTACK_DAMAGE_ID,
                recipeCount + Tiers.NETHERITE.getAttackDamageBonus(),
                AttributeModifier.Operation.ADD_VALUE);
    }

    public static void refreshAttackDamage(LivingEntity entity) {
        if (!(entity.getMainHandItem().getItem() instanceof EndlessBeafItem)) return;

        AttributeInstance attackDamage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.addOrUpdateTransientModifier(createAttackDamageModifier());
        }
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
        if (block instanceof GlowPlasticBlock || block instanceof UselessGlassBlock) {
            return true;
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
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        if (attacker instanceof Player player) {
            boolean forceKilled = forceKillLivingEntity(stack, target, player);
            applyAoeDamage(stack, player, target);
            if (forceKilled) {
                return true;
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public boolean onLeftClickEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull Entity entity) {
        Entity target = getForceKillTarget(entity);
        if (target instanceof LivingEntity livingEntity && forceKillLivingEntity(stack, livingEntity, player)) {
            applyAoeDamage(stack, player, target);
            return true;
        }
        if (!(target instanceof LivingEntity) && forceKillNonLivingEntity(stack, target, player)) {
            applyAoeDamage(stack, player, target);
            return true;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    /**
     * 对主目标周围范围内的实体结算全额攻击伤害。
     * 若工具开启了强制击杀模式，范围内的实体同样走强制击杀逻辑。
     *
     * @param stack   工具
     * @param player  攻击者
     * @param primary 直接命中的目标（作为范围中心，本身不重复结算）
     */
    private static void applyAoeDamage(ItemStack stack, Player player, Entity primary) {
        if (!(player.level() instanceof ServerLevel level)
                || !stack.getOrDefault(UComponents.BeefAoeDamageEnabledComponent, false)) {
            return;
        }
        // 重入保护：范围伤害过程中不再触发新的范围伤害
        if (!AOE_DAMAGE_CONTEXT.add(player.getUUID())) {
            return;
        }

        try {
            double rangeX = ConfigManager.getBeefAoeDamageRangeX();
            double rangeY = ConfigManager.getBeefAoeDamageRangeY();
            double rangeZ = ConfigManager.getBeefAoeDamageRangeZ();
            AABB area = AABB.ofSize(primary.position(), rangeX * 2 + 1, rangeY * 2 + 1, rangeZ * 2 + 1);

            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            DamageSource damageSource = ModDamageTypes.beefTool(level, player);
            int maxTargets = ConfigManager.getBeefAoeDamageMaxTargets();
            int hit = 0;

            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (hit >= maxTargets) {
                    break;
                }
                if (victim == primary || victim == player || victim instanceof Player
                        || !victim.isAlive() || player.isAlliedTo(victim)) {
                    continue;
                }

                hit++;
                if (forceKillLivingEntity(stack, victim, player)) {
                    continue;
                }
                victim.invulnerableTime = 0;
                victim.hurt(damageSource, damage);
            }

            if (hit > 0) {
                // 主动登记一次吸附：范围取「AoE 半径 + 磁力半径」，保证最外圈被打死的怪的掉落也能覆盖到。
                // 与单体死亡事件登记合并，覆盖强杀兜底和范围内普通伤害产生的掉落。
                BeefMagnetHandler.scheduleSweep(level, player, stack, primary.position(),
                        rangeX + ConfigManager.getBeefMagnetRangeX(),
                        rangeY + ConfigManager.getBeefMagnetRangeY(),
                        rangeZ + ConfigManager.getBeefMagnetRangeZ());
            }
        } finally {
            AOE_DAMAGE_CONTEXT.remove(player.getUUID());
        }
    }

    private static Entity getForceKillTarget(Entity entity) {
        if (entity instanceof PartEntity<?> partEntity) {
            return partEntity.getParent();
        }
        return entity;
    }

    public static void observeForceKillDeath(LivingDeathEvent event) {
        ForceKillContext context = FORCE_KILL_CONTEXTS.get(event.getEntity().getUUID());
        if (context != null) {
            context.deathEventAttempted = true;
        }
    }

    public static boolean handleForceKillDeath(LivingDeathEvent event) {
        ForceKillContext context = FORCE_KILL_CONTEXTS.get(event.getEntity().getUUID());
        if (context == null) {
            return false;
        }

        context.deathEventAttempted = true;
        context.deathEventCanceled = event.isCanceled();
        if (!event.isCanceled() && !context.captureHandled) {
            UselessItemUtils.tryCaptureSpawnEgg(context.target, context.stack, context.player);
            context.captureHandled = true;
        }
        return true;
    }

    public static boolean handleForceKillMagnetDeath(LivingDeathEvent event) {
        ForceKillContext context = FORCE_KILL_CONTEXTS.get(event.getEntity().getUUID());
        if (context == null) {
            return false;
        }

        if (event.isCanceled()) {
            context.deathEventCanceled = true;
        } else if (!context.magnetScheduled) {
            scheduleMagnetSweep(context.level, context.player, context.stack, context.deathPos);
            context.magnetScheduled = true;
        }
        return true;
    }

    public static void observeForceKillDrops(LivingDropsEvent event) {
        ForceKillContext context = FORCE_KILL_CONTEXTS.get(event.getEntity().getUUID());
        if (context != null) {
            context.dropsAttempted = true;
        }
    }

    private static boolean forceKillLivingEntity(ItemStack stack, LivingEntity target, Player player) {
        if (target.level().isClientSide || target instanceof Player || !target.isAlive()
                || isForceKillBlacklisted(target)
                || !stack.getOrDefault(UComponents.ForceKillEnabledComponent, false)) {
            return false;
        }

        ServerLevel level = (ServerLevel) target.level();
        DamageSource damageSource = ModDamageTypes.beefTool(level, player);
        ForceKillContext context = new ForceKillContext(level, target, stack, player, damageSource);
        if (FORCE_KILL_CONTEXTS.putIfAbsent(target.getUUID(), context) != null) {
            return false;
        }

        try {
            executeForceKill(context);

            if (!isDeathCommitted(target) && !context.deathEventAttempted) {
                rememberPositiveHealth(context);
                target.setHealth(0.0F);
                target.die(damageSource);
                restoreCanceledDeathHealth(context);
            }

            if (isDeathCommitted(target)) {
                settleForceKillEffects(context);
                return true;
            }

            if (context.deathEventAttempted) {
                context.deathEventCanceled = true;
            }
            restoreCanceledDeathHealth(context);
            executeMaxHealthFallback(context);
            executeFallbackDeath(context);
            settleForceKillEffects(context);

            if (!target.isRemoved()) {
                removeEntityFromServerStorage(context.level, target);
                context.removalCommitted = target.isRemoved();
            }
            return target.dead || context.removalCommitted;
        } finally {
            FORCE_KILL_CONTEXTS.remove(target.getUUID(), context);
        }
    }

    /** 用配置的磁力半径登记一次吸附。 */
    private static void scheduleMagnetSweep(ServerLevel level, Player player, ItemStack stack, Vec3 center) {
        BeefMagnetHandler.scheduleSweep(level, player, stack, center,
                ConfigManager.getBeefMagnetRangeX(),
                ConfigManager.getBeefMagnetRangeY(),
                ConfigManager.getBeefMagnetRangeZ());
    }

    private static void executeForceKill(ForceKillContext context) {
        LivingEntity target = context.target;
        if (target instanceof EnderDragon dragon) {
            clearVanillaInvulnerability(target);
            dragon.hurt(dragon.head, context.damageSource, getForceKillDamage(target));
            if (shouldStopStandardDamage(context)) {
                return;
            }
        }

        for (int i = 0; i < MAX_STANDARD_DAMAGE_ATTEMPTS; i++) {
            if (shouldStopStandardDamage(context)) {
                break;
            }
            rememberPositiveHealth(context);
            context.standardDamageAttempts++;
            clearVanillaInvulnerability(target);
            target.hurt(context.damageSource, getForceKillDamage(target));
        }
    }

    private static boolean shouldStopStandardDamage(ForceKillContext context) {
        LivingEntity target = context.target;
        return target.isRemoved()
                || target.dead
                || target.isDeadOrDying()
                || context.deathEventAttempted;
    }

    private static void clearVanillaInvulnerability(LivingEntity target) {
        target.invulnerableTime = 0;
        if (target instanceof WitherBoss wither) {
            wither.setInvulnerableTicks(0);
        }
    }

    private static boolean isDeathCommitted(LivingEntity target) {
        return target.dead || target.isRemoved();
    }

    private static void rememberPositiveHealth(ForceKillContext context) {
        float health = context.target.getHealth();
        if (Float.isFinite(health) && health > 0.0F) {
            context.lastPositiveHealth = health;
        }
    }

    private static void restoreCanceledDeathHealth(ForceKillContext context) {
        LivingEntity target = context.target;
        if (target.isRemoved() || target.dead || !target.isDeadOrDying()) {
            return;
        }

        float health = Math.min(context.lastPositiveHealth, target.getMaxHealth());
        if (Float.isFinite(health) && health > 0.0F) {
            target.setHealth(health);
        }
    }

    private static void executeMaxHealthFallback(ForceKillContext context) {
        LivingEntity target = context.target;
        if (target.isRemoved() || target.dead) {
            return;
        }

        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(0.0D);
        }
        target.setLastHurtByPlayer(context.player);
        target.setHealth(0.0F);
        target.die(context.damageSource);
    }

    private static void executeFallbackDeath(ForceKillContext context) {
        LivingEntity target = context.target;
        if (target.isRemoved() || target.dead) {
            return;
        }

        boolean dropLoot = !context.dropsAttempted;
        context.dropsAttempted = true;
        if (!target.isDeadOrDying()) {
            target.setHealth(0.0F);
        }
        forceDie(context.level, target, context.damageSource, dropLoot);
    }

    private static void settleForceKillEffects(ForceKillContext context) {
        if (!context.captureHandled) {
            UselessItemUtils.tryCaptureSpawnEgg(context.target, context.stack, context.player);
            context.captureHandled = true;
        }

        if (!context.magnetScheduled) {
            scheduleMagnetSweep(context.level, context.player, context.stack, context.deathPos);
            context.magnetScheduled = true;
        }
    }

    private static void forceDie(ServerLevel level, LivingEntity victim, DamageSource source, boolean dropLoot) {
        if (victim.isRemoved() || victim.dead) {
            return;
        }

        LivingEntity killer = victim.getKillCredit();
        if (victim.deathScore >= 0 && killer != null) {
            killer.awardKillScore(victim, victim.deathScore, source);
        }
        if (victim.isSleeping()) {
            victim.stopSleeping();
        }

        victim.dead = true;
        victim.getCombatTracker().recheckStatus();

        Entity sourceEntity = source.getEntity();
        if (sourceEntity == null || sourceEntity.killedEntity(level, victim)) {
            victim.gameEvent(GameEvent.ENTITY_DIE);
            if (dropLoot) {
                victim.dropAllDeathLoot(level, source);
            }
        }
        level.broadcastEntityEvent(victim, (byte) 3);
        victim.setPose(Pose.DYING);
    }

    private static void removeEntityFromServerStorage(ServerLevel level, Entity target) {
        if (target instanceof Player || target.isRemoved()) {
            return;
        }

        PersistentEntitySectionManager<Entity> entityManager = level.entityManager;
        EntityLookup<Entity> visibleEntities = entityManager.visibleEntityStorage;
        visibleEntities.byId.remove(target.getId());
        visibleEntities.byId.int2ObjectEntrySet().removeIf(entry -> entry.getValue() == target);
        visibleEntities.byUuid.remove(target.getUUID());
        visibleEntities.byUuid.entrySet().removeIf(entry -> entry.getValue() == target);
        entityManager.knownUuids.remove(target.getUUID());

        LevelEntityGetter<Entity> entityGetter = entityManager.entityGetter;
        if (entityGetter instanceof LevelEntityGetterAdapter<Entity> adapter) {
            adapter.visibleEntities.byId.remove(target.getId());
            adapter.visibleEntities.byUuid.remove(target.getUUID());
        }

        level.entityTickList.remove(target);
        target.setRemoved(Entity.RemovalReason.DISCARDED);
        level.getChunkSource().removeEntity(target);
    }

    private static float getForceKillDamage(LivingEntity target) {
        float damage = target.getHealth() + target.getAbsorptionAmount() + target.getMaxHealth() + 1.0F;
        if (!Float.isFinite(damage)) {
            return 1024.0F;
        }
        return Math.max(damage, 1024.0F);
    }

    private static boolean forceKillNonLivingEntity(ItemStack stack, Entity target, Player player) {
        if (target.level().isClientSide || target.isRemoved() || isForceKillBlacklisted(target)
                || !isForceKillNonLivingWhitelisted(target)
                || !stack.getOrDefault(UComponents.ForceKillEnabledComponent, false)) {
            return false;
        }

        if (target.level() instanceof ServerLevel level) {
            Vec3 deathPos = target.position();
            target.setRemoved(Entity.RemovalReason.DISCARDED);
            if (!target.isRemoved()) {
                return false;
            }
            scheduleMagnetSweep(level, player, stack, deathPos);
            return true;
        }
        return false;
    }

    private static final class ForceKillContext {
        private final ServerLevel level;
        private final LivingEntity target;
        private final ItemStack stack;
        private final Player player;
        private final DamageSource damageSource;
        private final Vec3 deathPos;
        private float lastPositiveHealth;
        private int standardDamageAttempts;
        private boolean deathEventAttempted;
        private boolean deathEventCanceled;
        private boolean dropsAttempted;
        private boolean captureHandled;
        private boolean magnetScheduled;
        private boolean removalCommitted;

        private ForceKillContext(ServerLevel level, LivingEntity target, ItemStack stack,
                                 Player player, DamageSource damageSource) {
            this.level = level;
            this.target = target;
            this.stack = stack;
            this.player = player;
            this.damageSource = damageSource;
            this.deathPos = target.position();
            this.lastPositiveHealth = target.getHealth();
        }
    }

    private static boolean isForceKillBlacklisted(Entity entity) {
        return ConfigManager.getBeefToolForceKillBlacklist().contains(getEntityId(entity));
    }

    private static boolean isForceKillNonLivingWhitelisted(Entity entity) {
        return ConfigManager.getBeefToolForceKillNonLivingWhitelist().contains(getEntityId(entity));
    }

    private static String getEntityId(Entity entity) {
        return entity.getType().builtInRegistryHolder().key().location().toString();
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
        Player player = ctx.getPlayer();

        if (player == null) return InteractionResult.PASS;

        InteractionResult teleportResult = tryTeleport(world, player, ctx.getItemInHand());
        if (teleportResult != InteractionResult.PASS) return teleportResult;

        InteractionResult lightningCollectorResult = trySummonLightningForCollector(ctx.getLevel(), ctx.getClickedPos(), ctx.getPlayer());
        if (lightningCollectorResult != InteractionResult.PASS) return lightningCollectorResult;

        InteractionResult timeAccelerationResult = BeefTimeAcceleration.tryUse(ctx);
        if (timeAccelerationResult != InteractionResult.PASS) return timeAccelerationResult;

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
        // 2. 统一工具行为链 (铲子 -> 锄头 -> 斧头)
        // ============================================================

        // 2.1 铲子 (铺路)
        InteractionResult res = this.tryToolAction(ctx, ItemAbilities.SHOVEL_FLATTEN, SoundEvents.SHOVEL_FLATTEN);
        if (res != InteractionResult.PASS) return res;

        // 2.2 锄头 (耕地)
        res = this.tryToolAction(ctx, ItemAbilities.HOE_TILL, SoundEvents.HOE_TILL);
        if (res != InteractionResult.PASS) return res;

        // 2.3 斧头 (剥皮)
        res = this.tryToolAction(ctx, ItemAbilities.AXE_STRIP, SoundEvents.AXE_STRIP);
        if (res != InteractionResult.PASS) return res;

        // 2.4 斧头 (刮铜)
        res = this.tryScrapeOrWaxOff(ctx, ItemAbilities.AXE_SCRAPE, SoundEvents.AXE_SCRAPE, 3005);
        if (res != InteractionResult.PASS) return res;

        // 2.5 斧头 (去蜡)
        return this.tryScrapeOrWaxOff(ctx, ItemAbilities.AXE_WAX_OFF, SoundEvents.AXE_WAX_OFF, 3004);
    }

    public static InteractionResult trySummonLightningForCollector(Level level, BlockPos clickedPos, @Nullable Player player) {
        if (!level.getBlockState(clickedPos).is(net.minecraft.world.level.block.Blocks.LIGHTNING_ROD)) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel serverLevel) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt == null) return InteractionResult.FAIL;

            Vec3 target = Vec3.atBottomCenterOf(clickedPos.above());
            bolt.moveTo(target.x, target.y, target.z);
            if (player instanceof ServerPlayer serverPlayer) {
                bolt.setCause(serverPlayer);
            }
            // ae2lt 存在时会读取此 NBT 键以识别自然天气闪电；不存在时该键被忽略，不影响功能
            bolt.getPersistentData().putBoolean(AE2LT_NATURAL_LIGHTNING_TAG, true);
            serverLevel.addFreshEntity(bolt);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack stack, @NotNull UseOnContext ctx) {
        InteractionResult teleportResult = tryTeleport(ctx.getLevel(), ctx.getPlayer(), stack);
        if (teleportResult != InteractionResult.PASS) return teleportResult;
        return BeefTimeAcceleration.tryUse(ctx);
    }

    @Override
    @SuppressWarnings("all")
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        // 基础工具速度
        Tool tool = stack.get(DataComponents.TOOL);
        float configSpeed = (float) ConfigManager.getBeefToolMiningSpeed();
        float baseSpeed = configSpeed > 0 ? configSpeed : (tool != null ? tool.getMiningSpeed(state) : 1.0F);

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
        InteractionResult teleportResult = tryTeleport(entity.level(), player, stack);
        if (teleportResult != InteractionResult.PASS) return teleportResult;

        if (BeefTimeAcceleration.shouldBlockOtherRightClick(stack, player)) {
            return InteractionResult.FAIL;
        }

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
        refreshEnchantments(stack, level);
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

        boolean forceKillEnabled = stack.getOrDefault(UComponents.ForceKillEnabledComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.force_kill_enabled_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               forceKillEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(forceKillEnabled ? ChatFormatting.GOLD : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.DARK_RED));

        boolean beefTimeAccelerationEnabled = stack.getOrDefault(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.time_acceleration_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefTimeAccelerationEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefTimeAccelerationEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.AQUA));

        Boolean beefInvulnerabilityValue = stack.get(UComponents.BeefInvulnerabilityEnabledComponent.get());
        boolean beefInvulnerabilityEnabled = beefInvulnerabilityValue != null
                ? beefInvulnerabilityValue
                : stack.getItem() instanceof EndlessBeafItem;
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.beef_invulnerability_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefInvulnerabilityEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefInvulnerabilityEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.DARK_PURPLE));

        boolean beefAdvancedStealthEnabled = stack.getOrDefault(
                UComponents.BeefAdvancedStealthEnabledComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.beef_advanced_stealth_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefAdvancedStealthEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefAdvancedStealthEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.LIGHT_PURPLE));

        boolean beefCaptureEnabled = stack.getOrDefault(UComponents.BeefCaptureEnabledComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.beef_capture_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefCaptureEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefCaptureEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.DARK_GREEN));

        boolean beefTeleportEnabled = isTeleportEnabled(stack);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.beef_teleport_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefTeleportEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefTeleportEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.LIGHT_PURPLE));

        boolean beefAoeDamageEnabled = stack.getOrDefault(UComponents.BeefAoeDamageEnabledComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.beef_aoe_damage_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefAoeDamageEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefAoeDamageEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.RED));

        boolean beefMagnetEnabled = stack.getOrDefault(UComponents.BeefMagnetEnabledComponent.get(), false);
        tooltipComponents.add(Component.translatable("tooltip.useless_mod.beef_magnet_mode")
                                       .append(": ")
                                       .append(Component.translatable(
                                               beefMagnetEnabled ? "tooltip.useless_mod.enable" :
                                                       "tooltip.useless_mod.disable"
                                       ).withStyle(beefMagnetEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                                       .withStyle(ChatFormatting.YELLOW));

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
            // 附魔切换
            this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_SILK_TOUCH_KEY,
                               "tooltip.useless_mod.key.switch_silk_touch"
            );
            this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_FORTUNE_KEY,
                               "tooltip.useless_mod.key.switch_fortune"
            );

            // 模式开关
            this.addKeyTooltip(tooltipComponents, KeyBindings.TOGGLE_CHAIN_MODE_KEY,
                               "tooltip.useless_mod.key.toggle_chain_mode"
            );
            this.addKeyTooltip(tooltipComponents, KeyBindings.SWITCH_FORCE_MINING_KEY,
                               "tooltip.useless_mod.key.switch_force_mining"
            );

            // 触发按键
            this.addKeyTooltip(tooltipComponents, KeyBindings.TRIGGER_CHAIN_MINING_KEY,
                               "tooltip.useless_mod.key.trigger_chain_mining"
            );
            this.addKeyTooltip(tooltipComponents, KeyBindings.TRIGGER_FORCE_MINING_KEY,
                               "tooltip.useless_mod.key.trigger_force_mining"
            );

            // UI
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
        if (ModList.get().isLoaded("ae2")) {
            tooltipComponents.add(
                    Component.translatable("tooltip.useless_mod.ae_storage_priority_bind_hint").withStyle(ChatFormatting.GREEN));
        }
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.festive_affix").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.auto_collect").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.time_acceleration_hint").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(
                Component.translatable("tooltip.useless_mod.beef_teleport_hint").withStyle(ChatFormatting.LIGHT_PURPLE));

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
