package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.compat.productivebees.ProductiveBeesCaptureCompat;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class UselessItemUtils {
    private static final String PRODUCTIVE_BEES_MOD_ID = "productivebees";
    private static final ResourceLocation COGNIZANT_DUST_ID = ResourceLocation.fromNamespaceAndPath(
            "mysticalagriculture", "cognizant_dust");

    // 写进刷怪蛋前必须清除的字段，分四类：
    // ① 位置/运动/朝向 —— 会覆盖放置点；
    // ② 死亡瞬间状态（Health=0 会让生物一召唤出来就死）；
    // ③ 每只生物各不相同的运行时状态 —— 不清掉的话，同种生物的蛋永远无法堆叠；
    // ④ NeoForge 自己的「生成来源」标记。
    // 注意：模组/脚本的数据容器（NeoForgeData / KubeJSPersistentData / neoforge:attachments）**不在这里整块丢** ——
    //      里面可能装着整合包作者定义的「身份」。它们改由配置项按「具体键 / 命名空间」精确清理，
    //      未知条目一律保留，避免误伤其它整合包（见 ConfigManager#getBeefCaptureStripKeys 等）。
    private static final List<String> VOLATILE_ENTITY_KEYS = List.of(
            "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown",
            "UUID",
            "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
            "Passengers", "leash",
            "Brain", "LeftHanded", "TicksFrozen", "HasVisualFire", "FallFlying",
            "InLove", "LoveCause", "SleepingX", "SleepingY", "SleepingZ", "RaidId",
            "neoforge:spawn_type");

    // Mob.finalizeSpawn 每次生成都会给 FOLLOW_RANGE 随机加这个永久修饰符（每只都不一样），必须剔除
    private static final String RANDOM_SPAWN_BONUS_ID = "minecraft:random_spawn_bonus";

    // NeoForge 数据附件的 NBT 键（Entity#saveWithoutId 写入的运行时数据）
    private static final String ATTACHMENTS_KEY = "neoforge:attachments";

    public static void applyEndlessBeafEffects(Player player) {
        if (player == null) return;

        // 检查是否启用药水效果
        if (ConfigManager.shouldEnablePotionEffects()) {
            List<String> customEffects = ConfigManager.getCustomPotionEffects();
            
            for (String effectConfig : customEffects) {
                applyPotionEffectFromConfig(player, effectConfig);
            }
        }
    }

    private static final int POTION_DURATION = 20000;

    /**
     * 从配置条目解析并应用药水效果
     * 格式: "modid:effect_name,amplifier"
     */
    private static void applyPotionEffectFromConfig(Player player, String effectConfig) {
        try {
            if (effectConfig == null) {
                return;
            }

            String[] parts = effectConfig.split(",", -1);
            if (parts.length != 2) {
                return;
            }

            String effectId = parts[0].trim();
            int amplifier = Integer.parseInt(parts[1].trim()) - 1;
            if (amplifier < 0) {
                return;
            }

            ResourceLocation location = ResourceLocation.tryParse(effectId);
            if (location == null) {
                return;
            }
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(location);

            if (effect == null) {
                return;
            }

            Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            MobEffectInstance currentEffect = player.getEffect(effectHolder);
            if (currentEffect == null || currentEffect.getDuration() < 200) {
                player.addEffect(new MobEffectInstance(effectHolder, POTION_DURATION, Math.max(0, amplifier), true, false, true));
            }
        } catch (Exception e) {
            // 静默处理配置解析错误，避免每tick输出日志
        }
    }

    public static void onLivingDrops(LivingDropsEvent event, ItemStack stack, Player player) {
        if (player == null) return;

        // 根据配置的概率判断是否触发
        int chance = ConfigManager.getFestiveDropChance();
        if (!(Math.random() * 100 < chance)) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Level level = killedEntity.level();

        if (level.isClientSide()) return;

        sendFestiveMessage(player);

        Collection<ItemEntity> drops = event.getDrops();
        List<ItemEntity> remainingDrops = new ArrayList<>(); // 保留原样掉落的（可损坏物品）
        List<ItemStack> amplifiedDrops = new ArrayList<>();  // ×20 后的战利品

        for (ItemEntity itemEntity : drops) {
            ItemStack dropStack = itemEntity.getItem();

            if (dropStack.isDamageableItem()) {
                // 可损坏物品（如剑、弓、护甲）保持原版掉落行为
                remainingDrops.add(itemEntity);
            } else {
                // 非可损坏物品：数量 ×20，交给统一的掉落通道处理
                ItemStack amplifiedStack = dropStack.copy();
                amplifiedStack.setCount(dropStack.getCount() * 20);
                amplifiedDrops.add(amplifiedStack);
            }
        }

        // 清空原掉落物，重新添加只需掉在地上的部分（主要是可损坏物品）
        drops.clear();
        drops.addAll(remainingDrops);

        if (!amplifiedDrops.isEmpty()) {
            // 走 handleDrops 而不是直接 placeItemBackInInventory：
            // 这样战利品大爆发才会像普通掉落一样受「AE 存储优先」与「范围磁力」影响
            // （AE 优先且已绑定 → 进 AE；否则磁力开 → 进背包；都没开 → 落在尸体处走原版拾取）。
            MiningUtils.handleDrops(player, amplifiedDrops, stack, killedEntity.position());
        }
    }

    public static void tryAddCognizantDustDrop(LivingDropsEvent event, ItemStack stack) {
        if (!(stack.getItem() instanceof EndlessBeafItem)
                || !stack.getOrDefault(UComponents.BeefMysticalAgricultureEnabledComponent.get(), false)) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Level level = killedEntity.level();
        if (level.isClientSide() || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }

        int count = killedEntity.getType() == EntityType.WITHER ? 4
                : killedEntity.getType() == EntityType.ENDER_DRAGON ? 6 : 0;
        if (count == 0) {
            return;
        }

        Item cognizantDust = BuiltInRegistries.ITEM.get(COGNIZANT_DUST_ID);
        if (cognizantDust != Items.AIR) {
            event.getDrops().add(new ItemEntity(
                    level,
                    killedEntity.getX(),
                    killedEntity.getY(),
                    killedEntity.getZ(),
                    new ItemStack(cognizantDust, count)
            ));
        }
    }

    public static void tryAddBeheadingDrop(LivingDropsEvent event, ItemStack stack) {
        if (!(stack.getItem() instanceof EndlessBeafItem)
                || !stack.getOrDefault(UComponents.BeefBeheadingEnabledComponent.get(), false)) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Level level = killedEntity.level();
        if (event.isCanceled() || level.isClientSide()
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }

        ItemStack head = beheadingDrop(killedEntity);
        if (!head.isEmpty()) {
            event.getDrops().add(new ItemEntity(
                    level,
                    killedEntity.getX(),
                    killedEntity.getY(),
                    killedEntity.getZ(),
                    head
            ));
        }
    }

    private static ItemStack beheadingDrop(LivingEntity entity) {
        if (entity instanceof Player player) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            return head;
        }

        EntityType<?> type = entity.getType();
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            return new ItemStack(Items.SKELETON_SKULL);
        }
        if (type == EntityType.WITHER_SKELETON) {
            return new ItemStack(Items.WITHER_SKELETON_SKULL);
        }
        if (type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER
                || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return new ItemStack(Items.ZOMBIE_HEAD);
        }
        if (type == EntityType.CREEPER) {
            return new ItemStack(Items.CREEPER_HEAD);
        }
        if (type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) {
            return new ItemStack(Items.PIGLIN_HEAD);
        }
        if (type == EntityType.ENDER_DRAGON) {
            return new ItemStack(Items.DRAGON_HEAD);
        }
        return ItemStack.EMPTY;
    }

    public static void tryCaptureSpawnEgg(LivingEntity killedEntity, ItemStack stack, Player player) {
        if (killedEntity.level().isClientSide()
                || !(stack.getItem() instanceof EndlessBeafItem)
                || !stack.getOrDefault(UComponents.BeefCaptureEnabledComponent.get(), false)) {
            return;
        }

        ItemStack spawnEggStack = null;
        if (ModList.get().isLoaded(PRODUCTIVE_BEES_MOD_ID)) {
            spawnEggStack = ProductiveBeesCaptureCompat.tryCreateSpawnEgg(killedEntity);
        }

        if (spawnEggStack == null) {
            boolean carrier = false;
            SpawnEggItem spawnEgg = SpawnEggItem.byId(killedEntity.getType());
            if (spawnEgg == null) {
                // 无对应原版刷怪蛋的模组生物：猪刷怪蛋当外壳，
                // 靠 ENTITY_DATA 的 id 让 SpawnEggItem#getType 还原成真正的实体
                spawnEgg = SpawnEggItem.byId(EntityType.PIG);
                carrier = true;
            }
            if (spawnEgg == null) {
                return;
            }
            spawnEggStack = new ItemStack(spawnEgg);
            // 精准模式才写入完整 NBT；时运模式只写入生物类型（猪外壳依然靠它还原实体）
            boolean precise = stack.getOrDefault(
                    UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE) == EnchantMode.SILK_TOUCH;
            applyCapturedEntityData(spawnEggStack, killedEntity, carrier, precise);
        }

        if (spawnEggStack.isEmpty()) {
            return;
        }

        // 只按「范围磁力」决定去留，刻意绕过「AE 存储优先」：
        // 刷怪蛋是玩家想立刻拿在手上的东西，不该被吸进 AE 网络。
        MiningUtils.handleDrops(player, java.util.List.of(spawnEggStack), stack,
                killedEntity.position(), false);
    }

    /**
     * 把生物的 NBT 写进刷怪蛋。分两种模式（由造化杖当前的附魔模式决定）：
     *
     * <ul>
     *   <li><b>精准模式（{@code SILK_TOUCH}）</b>：写入生物完整身份 NBT —— 保留自定义名、
     *       装备、Apotheosis 战利品表等，捕捉到的生物与野生个体完全一致。
     *       先清掉「死亡瞬间状态」与「每只生物各不相同的运行时状态」，这样同种生物产出的蛋仍可堆叠。</li>
     *   <li><b>时运模式（{@code FORTUNE}）</b>：只写「生物类型」，不带任何身份 NBT ——
     *       有原版刷怪蛋的生物直接产出纯净原版蛋；没有原版蛋的模组生物仍用猪刷怪蛋当外壳，
     *       只补一个 {@code id} 让 {@code SpawnEggItem#getType} 还原成真正的实体。
     *       此模式下同类生物的蛋 NBT 完全相同，必定可堆叠。</li>
     * </ul>
     */
    private static void applyCapturedEntityData(ItemStack eggStack, LivingEntity entity, boolean carrier, boolean precise) {
        CompoundTag tag = new CompoundTag();
        if (precise) {
            // 精准模式：完整身份 NBT
            tag = entity.saveWithoutId(new CompoundTag());
            for (String key : VOLATILE_ENTITY_KEYS) {
                tag.remove(key);
            }
            canonicalizeAttributes(tag, entity.getType());
            stripConfiguredKeys(tag);
            stripAttachments(tag);
        }
        // 决定 SpawnEggItem 生成哪种实体：猪外壳必须靠它还原，精准模式也用它对齐
        if (precise || carrier) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id != null) {
                tag.putString("id", id.toString());
            }
        }
        if (!tag.isEmpty()) {
            eggStack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        }

        // 显示名：猪外壳绝不允许露出「猪刷怪蛋」。
        Component name = null;
        if (precise) {
            // 精准模式：
            // ① 有自定义名 → 用自定义名（普通蛋 / 猪外壳都适用）
            // ② 否则若是猪外壳（无原版蛋的模组生物）→ 用该生物类型的本地化名
            // ③ 否则（有原版蛋且无自定义名）→ 保留原版蛋名（如「僵尸刷怪蛋」）
            name = entity.getCustomName();
            if (name == null && carrier) {
                name = Component.translatable(entity.getType().getDescriptionId());
            }
        } else if (carrier) {
            // 时运模式：只有猪外壳需要显名（否则会露出「猪刷怪蛋」）；
            // 有原版蛋的生物保持纯净原版蛋名，且同类蛋 NBT 完全一致、必定可堆叠。
            name = Component.translatable(entity.getType().getDescriptionId());
        }
        if (name != null) {
            eggStack.set(DataComponents.ITEM_NAME, name);
        }
    }

    /**
     * 把 {@code attributes} 规范化成「只保留真正偏离默认值的属性」，并按 id 排序。
     * <p>关键：{@code AttributeMap} 的属性是**惰性**创建的（{@code computeIfAbsent}），
     * {@code save()} 只写出「被访问过」的属性 —— 一只活过的生物访问过的属性集合与顺序，
     * 跟另一只同种生物完全不同。不规范化的话，两头原版牛的蛋 NBT 也永远不一样、无法堆叠。
     * <p>顺带剔除 {@code Mob.finalizeSpawn} 每次随机加在 FOLLOW_RANGE 上的 spawn bonus 修饰符。
     */
    @SuppressWarnings("unchecked")
    private static void canonicalizeAttributes(CompoundTag tag, EntityType<?> type) {
        ListTag attributes = tag.getList("attributes", Tag.TAG_COMPOUND);
        if (attributes.isEmpty()) {
            return;
        }
        AttributeSupplier defaults = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        ListTag kept = new ListTag();
        for (int i = 0; i < attributes.size(); i++) {
            CompoundTag attribute = attributes.getCompound(i);
            ResourceLocation attributeId = ResourceLocation.tryParse(attribute.getString("id"));
            Holder<Attribute> holder = attributeId == null
                    ? null
                    : BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
            if (holder == null) {
                continue;
            }

            ListTag modifiers = attribute.getList("modifiers", Tag.TAG_COMPOUND);
            ListTag keptModifiers = new ListTag();
            for (int j = 0; j < modifiers.size(); j++) {
                CompoundTag modifier = modifiers.getCompound(j);
                if (!RANDOM_SPAWN_BONUS_ID.equals(modifier.getString("id"))) {
                    keptModifiers.add(modifier);
                }
            }

            // 默认数值 + 无有效修饰符 ⇒ 不是这只生物的身份，直接丢弃
            // （用 Double.compare 而非 ==：NaN 也是「默认值」的一种，== 对 NaN 恒为 false）
            boolean isDefault = defaults != null
                    && keptModifiers.isEmpty()
                    && Double.compare(attribute.getDouble("base"), defaults.getBaseValue(holder)) == 0;
            if (isDefault) {
                continue;
            }

            CompoundTag copy = attribute.copy();
            if (keptModifiers.isEmpty()) {
                copy.remove("modifiers");
            } else {
                copy.put("modifiers", keptModifiers);
            }
            kept.add(copy);
        }

        if (kept.isEmpty()) {
            tag.remove("attributes");
            return;
        }
        kept.sort(Comparator.comparing((Tag entry) -> ((CompoundTag) entry).getString("id")));
        tag.put("attributes", kept);
    }

    /**
     * 按配置精确丢弃指定的 NBT 键：{@code 容器.子键}（不含点则视为顶层键）。
     * <p>模组/脚本的数据容器（{@code NeoForgeData} / {@code KubeJSPersistentData}）**不整块丢** ——
     * 里面可能装着整合包作者定义的「身份」，整块丢会误伤其它整合包。
     * 这里只清配置里点名的、已知是「每只各不相同」的运行时数值（存活计时 / 落地坐标 / 衰老计时…）。
     */
    private static void stripConfiguredKeys(CompoundTag tag) {
        for (String path : ConfigManager.getBeefCaptureStripKeys()) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            int dot = path.indexOf('.');
            if (dot < 0) {
                tag.remove(path);
                continue;
            }
            String container = path.substring(0, dot);
            String child = path.substring(dot + 1);
            if (child.isEmpty() || !tag.contains(container, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag nested = tag.getCompound(container);
            nested.remove(child);
            if (nested.isEmpty()) {
                tag.remove(container);
            }
        }
    }

    /**
     * 按配置丢弃 {@code neoforge:attachments} 里指定命名空间的附件（体温 {@code cold_sweat}、
     * 灵魂 {@code malum}、火焰 {@code blueflame} …）—— 这些每只生物都不一样，留着蛋就永远不堆叠。
     * <p>只清配置里点名的命名空间；其余（含 Apotheosis 的战利品表、以及所有未知模组）一律保留，
     * 避免误伤其它整合包把「身份」存在附件里。
     */
    private static void stripAttachments(CompoundTag tag) {
        CompoundTag attachments = tag.getCompound(ATTACHMENTS_KEY);
        if (attachments.isEmpty()) {
            return;
        }
        Set<String> volatileNamespaces =
                Set.copyOf(ConfigManager.getBeefCaptureStripAttachmentNamespaces());
        for (String key : new ArrayList<>(attachments.getAllKeys())) {
            int colon = key.indexOf(':');
            String namespace = colon < 0 ? key : key.substring(0, colon);
            if (volatileNamespaces.contains(namespace)) {
                attachments.remove(key);
            }
        }
        if (attachments.isEmpty()) {
            tag.remove(ATTACHMENTS_KEY);
        }
    }

    // 显示触发提示
    private static void sendFestiveMessage(Player player) {
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("gui.useless_mod.festive_triggered"),
                    true
            );
        }
    }

    /**
     * 检查物品是否是目标工具（牛排或特定模式的omnitools扳手）
     */
    private static boolean isTargetTool(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        // 检查是否是永恒牛排工具
        if (itemStack.getItem() instanceof EndlessBeafItem) {
            return true;
        }

        // 检查是否是omnitools扳手且处于正确模式
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return itemId.getNamespace().equals("omnitools")
                && itemStack.get(UComponents.CurrentToolTypeComponent) == ToolTypeMode.OMNITOOL_MODE;
    }

    /**
     * 从玩家的主手和副手中查找目标工具
     * 返回包含目标物品和对应手的Optional
     */
    public static Optional<SimpleImmutableEntry<ItemStack, InteractionHand>> findTargetToolInHands(Player player) {
        if (player == null) {
            return Optional.empty();
        }

        ItemStack mainHandItem = player.getMainHandItem();
        ItemStack offHandItem = player.getOffhandItem();

        // 检查主手
        if (isTargetTool(mainHandItem)) {
            return Optional.of(new SimpleImmutableEntry<>(mainHandItem, InteractionHand.MAIN_HAND));
        }

        // 检查副手
        if (isTargetTool(offHandItem)) {
            return Optional.of(new SimpleImmutableEntry<>(offHandItem, InteractionHand.OFF_HAND));
        }

        return Optional.empty();
    }

    public static boolean hasTargetToolInInventory(Player player) {
        if (player == null || player.getInventory() == null) {
            return false;
        }

        return isTargetTool(player.getMainHandItem())
                || isTargetTool(player.getOffhandItem())
                || player.getInventory().items.stream().anyMatch(UselessItemUtils::isTargetTool);
    }

    /**
     * 玩家身上是否带着指定物品（主手 / 副手 / 主背包）。
     * <p>
     * 注意 {@code player.getInventory().items} 只有主背包那 36 格，<b>不含副手</b>
     * （副手在 {@code getInventory().offhand} 里）。凡是「玩家是否携带」的判定都必须走这里，
     * 否则造化杖放进副手就会失效。
     */
    public static boolean hasItemInInventory(Player player, Item item) {
        if (player == null || player.getInventory() == null || item == null) {
            return false;
        }

        return player.getMainHandItem().is(item)
                || player.getOffhandItem().is(item)
                || player.getInventory().items.stream().anyMatch(stack -> stack.is(item));
    }

    public static boolean hasInvulnerabilityEnabledTargetToolInInventory(Player player) {
        if (player == null || player.getInventory() == null) {
            return false;
        }

        return isInvulnerabilityEnabledTargetTool(player.getMainHandItem())
                || isInvulnerabilityEnabledTargetTool(player.getOffhandItem())
                || player.getInventory().items.stream().anyMatch(UselessItemUtils::isInvulnerabilityEnabledTargetTool);
    }

    public static boolean hasAdvancedStealthEnabledTargetToolInInventory(Player player) {
        if (player == null || player.getInventory() == null) {
            return false;
        }

        return isAdvancedStealthEnabledTargetTool(player.getMainHandItem())
                || isAdvancedStealthEnabledTargetTool(player.getOffhandItem())
                || player.getInventory().items.stream().anyMatch(UselessItemUtils::isAdvancedStealthEnabledTargetTool);
    }

    /**
     * 一趟「主手 → 副手 → 快捷栏 / 主背包」扫描的结果快照。
     *
     * <p>造化杖相关的每-tick 判定（飞行 / 无敌保护 / 高级潜行 / 杀戮光环）原本各走一趟背包，
     * 而 {@link #isTargetTool} 对每个非空物品都要做一次 {@code BuiltInRegistries.ITEM.getKey(...)}
     * 注册表反查。合并成一趟后，四个结果一次算出，布尔语义与原先各自的
     * {@code hasXxxTargetToolInInventory} <b>逐个等价</b>（都是存在性判定，与遍历顺序无关）。</p>
     *
     * @param killAuraStaff 身上第一把「开着杀戮光环」的造化杖，没有则为 {@code null}
     */
    public record StaffScan(boolean anyStaff,
                            boolean invulnerabilityEnabled,
                            boolean advancedStealthEnabled,
                            @Nullable ItemStack killAuraStaff) {
        public boolean hasKillAura() {
            return killAuraStaff != null;
        }
    }

    /**
     * 一次性扫描玩家身上的造化杖相关状态，供 {@code EventHandler#onPlayerTick} 的三个消费者共用，
     * 避免每 tick 把背包走三遍。
     *
     * <p>遍历顺序与各个 {@code hasXxxTargetToolInInventory} 保持一致：主手 → 副手 →
     * 快捷栏 / 主背包（{@code items[0..8]} 是快捷栏、{@code [9..35]} 是主背包，当前手持格也在其中）。</p>
     */
    public static StaffScan scanStaff(Player player) {
        if (player == null || player.getInventory() == null) {
            return new StaffScan(false, false, false, null);
        }

        boolean anyStaff = false;
        boolean invulnerabilityEnabled = false;
        boolean advancedStealthEnabled = false;
        ItemStack killAuraStaff = null;

        var items = player.getInventory().items;
        // i == -2 主手、i == -1 副手、其余为主背包/快捷栏下标
        for (int i = -2, size = items.size(); i < size; i++) {
            ItemStack stack = i == -2 ? player.getMainHandItem()
                    : i == -1 ? player.getOffhandItem()
                    : items.get(i);
            if (stack.isEmpty() || !isTargetTool(stack)) {
                continue;
            }
            anyStaff = true;
            invulnerabilityEnabled |= isInvulnerabilityEnabledTargetTool(stack);
            advancedStealthEnabled |= isAdvancedStealthEnabledTargetTool(stack);
            if (killAuraStaff == null
                    && stack.getItem() instanceof EndlessBeafItem
                    && EndlessBeafItem.isKillAuraEnabled(stack)) {
                killAuraStaff = stack;
            }
        }

        return new StaffScan(anyStaff, invulnerabilityEnabled, advancedStealthEnabled, killAuraStaff);
    }

    /**
     * 找出「杀戮光环」开关要作用的那把造化杖：优先返回已经开着光环的那把（用于关闭），
     * 否则返回身上第一把造化杖（用于开启）。
     *
     * <p>只认造化杖本体（omnitools 扳手模式不给光环）。开关只在按键 / 点击那一刻调用，
     * 不在每-tick 热路径上，因此这里直接扫一趟即可，不做缓存。</p>
     *
     * @return 找到的杖；身上没有造化杖时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack findKillAuraToggleTarget(Player player) {
        if (player == null || player.getInventory() == null) {
            return ItemStack.EMPTY;
        }

        ItemStack firstStaff = ItemStack.EMPTY;
        var items = player.getInventory().items;
        for (int i = -2, size = items.size(); i < size; i++) {
            ItemStack stack = i == -2 ? player.getMainHandItem()
                    : i == -1 ? player.getOffhandItem()
                    : items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof EndlessBeafItem)) {
                continue;
            }
            if (EndlessBeafItem.isKillAuraEnabled(stack)) {
                // 已经开着光环：优先作用在它身上，按一次就是关闭
                return stack;
            }
            if (firstStaff.isEmpty()) {
                firstStaff = stack;
            }
        }
        return firstStaff;
    }

    public static boolean enableInvulnerabilityForAdvancedStealth(Player player) {
        if (player == null || player.getInventory() == null) {
            return false;
        }

        boolean changed = enableInvulnerabilityForAdvancedStealth(player.getMainHandItem());
        changed |= enableInvulnerabilityForAdvancedStealth(player.getOffhandItem());
        for (ItemStack itemStack : player.getInventory().items) {
            changed |= enableInvulnerabilityForAdvancedStealth(itemStack);
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return changed;
    }

    private static boolean isInvulnerabilityEnabledTargetTool(ItemStack itemStack) {
        if (!isTargetTool(itemStack)) {
            return false;
        }

        Boolean enabled = itemStack.get(UComponents.BeefInvulnerabilityEnabledComponent.get());
        return enabled != null ? enabled : itemStack.getItem() instanceof EndlessBeafItem;
    }

    private static boolean isAdvancedStealthEnabledTargetTool(ItemStack itemStack) {
        return isTargetTool(itemStack)
                && itemStack.getOrDefault(UComponents.BeefAdvancedStealthEnabledComponent.get(), false);
    }

    private static boolean enableInvulnerabilityForAdvancedStealth(ItemStack itemStack) {
        if (!isAdvancedStealthEnabledTargetTool(itemStack)
                || itemStack.getOrDefault(UComponents.BeefInvulnerabilityEnabledComponent.get(), false)) {
            return false;
        }
        itemStack.set(UComponents.BeefInvulnerabilityEnabledComponent.get(), true);
        return true;
    }
}
