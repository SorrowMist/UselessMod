package com.sorrowmist.useless.data;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ModeTypeEnum;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stable IDs and availability rules for every button on the beef tool screen. */
public final class BeefToolModuleRegistry {
    public static final String ENCHANT_SILK_TOUCH = "enchant.silk_touch";
    public static final String ENCHANT_FORTUNE = "enchant.fortune";
    public static final String TOOL_NONE = "tool.none";
    public static final String TOOL_WRENCH = "tool.wrench";
    public static final String TOOL_SCREWDRIVER = "tool.screwdriver";
    public static final String TOOL_MALLET = "tool.mallet";
    public static final String TOOL_CROWBAR = "tool.crowbar";
    public static final String TOOL_HAMMER = "tool.hammer";
    public static final String TOOL_OMNITOOL = "tool.omnitool";
    public static final String CONSTRUCTION_WAND = "mode.construction_wand";
    public static final String CONSTRUCTION_WAND_ANGEL = "mode.construction_wand_angel";
    public static final String CONSTRUCTION_WAND_DESTRUCTION = "mode.construction_wand_destruction";
    public static final String ENHANCED_CHAIN_MINING = "mode.enhanced_chain_mining";
    public static final String FORCE_MINING = "mode.force_mining";
    public static final String AUTO_SMELT = "mode.auto_smelt";
    public static final String AE_STORAGE_PRIORITY = "mode.ae_storage_priority";
    public static final String AE_NETWORK_CONNECT = "mode.ae_network_connect";
    public static final String WRENCH_TAG = "mode.wrench_tag";
    public static final String FORCE_KILL = "mode.force_kill";
    public static final String BEEF_MALUM_SPIRIT = "mode.beef_malum_spirit";
    public static final String BEEF_MYSTICAL_AGRICULTURE = "mode.beef_mystical_agriculture";
    public static final String BEEF_BEHEADING = "mode.beef_beheading";
    public static final String BEEF_TIME_ACCELERATION = "mode.beef_time_acceleration";
    public static final String BEEF_INVULNERABILITY = "mode.beef_invulnerability";
    public static final String BEEF_ADVANCED_STEALTH = "mode.beef_advanced_stealth";
    public static final String BEEF_CAPTURE = "mode.beef_capture";
    public static final String BEEF_TELEPORT = "mode.beef_teleport";
    public static final String BEEF_AOE_DAMAGE = "mode.beef_aoe_damage";
    public static final String BEEF_MAGNET = "mode.beef_magnet";
    public static final String BEEF_FARMLAND_MODE = "mode.beef_farmland";
    public static final String BEEF_CROP_HARVEST = "mode.beef_crop_harvest";
    public static final String BEEF_SHEARS = "mode.beef_shears";
    public static final String BEEF_FLINT_AND_STEEL = "mode.beef_flint_and_steel";
    public static final String BEEF_RITUAL_SATCHEL = "mode.beef_ritual_satchel";
    public static final String BEEF_RIPEN = "mode.beef_ripen";
    public static final String BEEF_FORCE_GROW = "mode.beef_force_grow";
    public static final String BEEF_AUTO_CLICK = "mode.beef_auto_click";
    public static final String BEEF_WIRELESS_LOGISTICS = "mode.beef_wireless_logistics";
    public static final String BEEF_KILL_AURA = "mode.beef_kill_aura";
    public static final String BEEF_PROTECT_MODE = "mode.beef_protect_mode";
    public static final String BEEF_ENTITY_TIME_ACCELERATION = "mode.beef_entity_time_acceleration";
    public static final String EXDEORUM_CROOK = "mode.exdeorum_crook";
    public static final String EXDEORUM_HAMMER = "mode.exdeorum_hammer";
    public static final String EXDEORUM_COMPRESSED_HAMMER = "mode.exdeorum_compressed_hammer";

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(ENCHANT_SILK_TOUCH, EnchantMode.SILK_TOUCH.getTooltip(), GroupKind.TOOLS,
                    Availability.ALWAYS, true),
            new Definition(ENCHANT_FORTUNE, EnchantMode.FORTUNE.getTooltip(), GroupKind.TOOLS,
                    Availability.ALWAYS, true),
            new Definition(TOOL_NONE, ToolTypeMode.NONE_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.REMOVED, true),
            new Definition(TOOL_WRENCH, ToolTypeMode.WRENCH_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.REMOVED, true),
            new Definition(TOOL_SCREWDRIVER, ToolTypeMode.SCREWDRIVER_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.REMOVED, true),
            new Definition(TOOL_MALLET, ToolTypeMode.MALLET_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.REMOVED, true),
            new Definition(TOOL_CROWBAR, ToolTypeMode.CROWBAR_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.REMOVED, true),
            new Definition(TOOL_HAMMER, ToolTypeMode.HAMMER_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.REMOVED, true),
            new Definition(TOOL_OMNITOOL, ToolTypeMode.OMNITOOL_MODE.getTooltip(), GroupKind.TOOLS,
                    Availability.OMNITOOLS, false),
            new Definition(CONSTRUCTION_WAND, ModeTypeEnum.CONSTRUCTION_WAND_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.ENDLESS, false),
            new Definition(CONSTRUCTION_WAND_ANGEL, ModeTypeEnum.CONSTRUCTION_WAND_ANGEL_CORE.getTooltip(), GroupKind.MINING,
                    Availability.ENDLESS, true),
            new Definition(CONSTRUCTION_WAND_DESTRUCTION, ModeTypeEnum.CONSTRUCTION_WAND_DESTRUCTION_CORE.getTooltip(), GroupKind.MINING,
                    Availability.ENDLESS, true),
            new Definition(ENHANCED_CHAIN_MINING, ModeTypeEnum.ENHANCED_CHAIN_MINING_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.ALWAYS, false),
            new Definition(FORCE_MINING, ModeTypeEnum.FORCE_MINING_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.ALWAYS, false),
            new Definition(AUTO_SMELT, ModeTypeEnum.AUTO_SMELT_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.ALWAYS, false),
            new Definition(EXDEORUM_CROOK, ModeTypeEnum.EXDEORUM_CROOK_ENABLED.getTooltip(), GroupKind.EXDEORUM,
                    Availability.EXDEORUM, false),
            new Definition(EXDEORUM_HAMMER, ModeTypeEnum.EXDEORUM_HAMMER_ENABLED.getTooltip(), GroupKind.EXDEORUM,
                    Availability.EXDEORUM, false),
            new Definition(EXDEORUM_COMPRESSED_HAMMER, ModeTypeEnum.EXDEORUM_COMPRESSED_HAMMER_ENABLED.getTooltip(),
                    GroupKind.EXDEORUM, Availability.EXDEORUM, false),
            new Definition(AE_STORAGE_PRIORITY, ModeTypeEnum.AE_STORAGE_PRIORITY_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.AE2, false),
            new Definition(AE_NETWORK_CONNECT, ModeTypeEnum.AE_NETWORK_CONNECT_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.AE2, false),
            new Definition(WRENCH_TAG, ModeTypeEnum.WRENCH_TAG_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.BASE, false),
            new Definition(FORCE_KILL, ModeTypeEnum.FORCE_KILL.getTooltip(), GroupKind.COMBAT,
                    Availability.ALWAYS, false),
            new Definition(BEEF_MALUM_SPIRIT, ModeTypeEnum.BEEF_MALUM_SPIRIT_ENABLED.getTooltip(), GroupKind.COMBAT,
                    Availability.MALUM, false),
            new Definition(BEEF_MYSTICAL_AGRICULTURE,
                    ModeTypeEnum.BEEF_MYSTICAL_AGRICULTURE_ENABLED.getTooltip(), GroupKind.COMBAT,
                    Availability.MYSTICAL_AGRICULTURE, false),
            new Definition(BEEF_BEHEADING, ModeTypeEnum.BEEF_BEHEADING_ENABLED.getTooltip(), GroupKind.COMBAT,
                    Availability.ENDLESS, false),
            new Definition(BEEF_CAPTURE, ModeTypeEnum.BEEF_CAPTURE_ENABLED.getTooltip(), GroupKind.COMBAT,
                    Availability.ENDLESS, false),
            new Definition(BEEF_AOE_DAMAGE, ModeTypeEnum.BEEF_AOE_DAMAGE_ENABLED.getTooltip(), GroupKind.COMBAT,
                    Availability.ENDLESS, false),
            new Definition(BEEF_TIME_ACCELERATION, ModeTypeEnum.BEEF_TIME_ACCELERATION_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ENDLESS, false),
            new Definition(BEEF_ENTITY_TIME_ACCELERATION,
                    ModeTypeEnum.BEEF_ENTITY_TIME_ACCELERATION_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ENDLESS, false),
            new Definition(BEEF_INVULNERABILITY, ModeTypeEnum.BEEF_INVULNERABILITY_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ALWAYS, false),
            new Definition(BEEF_ADVANCED_STEALTH, ModeTypeEnum.BEEF_ADVANCED_STEALTH_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ALWAYS, false),
            new Definition(BEEF_TELEPORT, ModeTypeEnum.BEEF_TELEPORT_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ENDLESS, false),
            new Definition(BEEF_MAGNET, ModeTypeEnum.BEEF_MAGNET_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ENDLESS, false),
            new Definition(BEEF_FARMLAND_MODE, ModeTypeEnum.BEEF_FARMLAND_MODE_ENABLED.getTooltip(),
                    GroupKind.VANILLA, Availability.ALWAYS, false),
            new Definition(BEEF_CROP_HARVEST, ModeTypeEnum.BEEF_CROP_HARVEST_ENABLED.getTooltip(),
                    GroupKind.VANILLA, Availability.ALWAYS, false),
            new Definition(BEEF_SHEARS, ModeTypeEnum.BEEF_SHEARS_ENABLED.getTooltip(),
                    GroupKind.VANILLA, Availability.ALWAYS, false),
            new Definition(BEEF_FLINT_AND_STEEL, ModeTypeEnum.BEEF_FLINT_AND_STEEL_ENABLED.getTooltip(),
                    GroupKind.VANILLA, Availability.ALWAYS, false),
            new Definition(BEEF_RITUAL_SATCHEL, ModeTypeEnum.BEEF_RITUAL_SATCHEL_ENABLED.getTooltip(),
                    GroupKind.MINING, Availability.OCCULTISM, false),
            new Definition(BEEF_RIPEN, ModeTypeEnum.BEEF_RIPEN_ENABLED.getTooltip(),
                    GroupKind.VANILLA, Availability.ALWAYS, false),
            new Definition(BEEF_FORCE_GROW, ModeTypeEnum.BEEF_FORCE_GROW_ENABLED.getTooltip(),
                    GroupKind.VANILLA, Availability.ALWAYS, false),
            new Definition(BEEF_AUTO_CLICK, ModeTypeEnum.BEEF_AUTO_CLICK_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_WIRELESS_LOGISTICS, ModeTypeEnum.BEEF_WIRELESS_LOGISTICS_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ENDLESS, false),
            new Definition(BEEF_KILL_AURA, ModeTypeEnum.BEEF_KILL_AURA_ENABLED.getTooltip(),
                    GroupKind.COMBAT, Availability.ENDLESS, false),
            new Definition(BEEF_PROTECT_MODE, ModeTypeEnum.BEEF_PROTECT_MODE_ENABLED.getTooltip(),
                    GroupKind.COMBAT, Availability.ENDLESS, false)
    );
    private static final List<String> AUTO_COMBAT_MODULES = List.of(
            BEEF_MALUM_SPIRIT,
            BEEF_MYSTICAL_AGRICULTURE,
            BEEF_BEHEADING,
            BEEF_KILL_AURA,
            BEEF_PROTECT_MODE);
    /** 新增的辅助类模块：老存档的布局里没有它们，进游戏时自动补进「辅助」分组。 */
    private static final List<String> AUTO_AUXILIARY_MODULES = List.of(
            BEEF_AUTO_CLICK,
            BEEF_WIRELESS_LOGISTICS,
            BEEF_ENTITY_TIME_ACCELERATION);
    /** 「原版」分组的模块：承载原版工具动作（右键、剪羊毛、点火、催熟等），老存档自动补组。 */
    private static final List<String> AUTO_VANILLA_MODULES = List.of(
            BEEF_FARMLAND_MODE,
            BEEF_CROP_HARVEST,
            BEEF_SHEARS,
            BEEF_FLINT_AND_STEEL,
            BEEF_RIPEN,
            BEEF_FORCE_GROW);
    /** 新增的挖掘类模块：老存档的布局里没有它们，进游戏时自动补进「挖掘」分组。 */
    private static final List<String> AUTO_MINING_MODULES = List.of(
            AE_NETWORK_CONNECT,
            AUTO_SMELT,
            BEEF_RITUAL_SATCHEL);
    /**
     * Ex Deorum 专属分组的模块：三者共用同一套配方体系，单独成组承载，
     * 既不必在按钮上标注所属模组，也避免其较长的功能名撑宽通用分组的按钮。
     */
    private static final List<String> AUTO_EXDEORUM_MODULES = List.of(
            EXDEORUM_CROOK,
            EXDEORUM_HAMMER,
            EXDEORUM_COMPRESSED_HAMMER);

    private static final Map<String, Definition> BY_ID;

    static {
        Map<String, Definition> definitions = new HashMap<>();
        for (Definition definition : DEFINITIONS) {
            definitions.put(definition.id(), definition);
        }
        BY_ID = Collections.unmodifiableMap(definitions);
    }

    private BeefToolModuleRegistry() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition get(String id) {
        return BY_ID.get(id);
    }

    public static boolean isKnown(String id) {
        return BY_ID.containsKey(id);
    }

    public static boolean isAvailable(String id, ItemStack target) {
        Definition definition = get(id);
        return definition != null && definition.availability().isAvailable(target);
    }

    public static Component name(String id) {
        Definition definition = get(id);
        return definition == null ? Component.literal(id) : definition.name();
    }

    public static boolean isExclusive(String id) {
        Definition definition = get(id);
        return definition != null && definition.exclusive();
    }

    public static BeefToolLayout defaultLayout() {
        List<BeefToolLayout.Page> pages = new ArrayList<>();
        BeefToolLayout.Page page = new BeefToolLayout.Page("Page 1");
        for (GroupKind kind : GroupKind.values()) {
            if (!kind.isLoaded()) {
                continue;
            }
            BeefToolLayout.Group group = new BeefToolLayout.Group(kind.defaultName());
            for (Definition definition : DEFINITIONS) {
                if (definition.group() == kind && definition.availability() != Availability.REMOVED) {
                    group.modules().add(definition.id());
                }
            }
            page.groups().add(group);
        }
        pages.add(page);
        return new BeefToolLayout(0, pages, List.of());
    }

    /** Adds newly available modules to their default group without disturbing any existing order. */
    public static void addMissingAvailableModules(BeefToolLayout layout, ItemStack target) {
        for (String moduleId : AUTO_COMBAT_MODULES) {
            if (!isAvailable(moduleId, target)) {
                continue;
            }

            boolean inUnassigned = layout.unassignedModules().contains(moduleId);
            if (!inUnassigned && layout.containsModule(moduleId)) {
                continue;
            }

            BeefToolLayout.Group combat = findOrCreateCombatGroup(layout);
            if (combat == null || combat.modules().size() >= BeefToolLayout.MAX_MODULES_PER_GROUP) {
                continue;
            }

            if (inUnassigned) {
                layout.unassignedModules().remove(moduleId);
            }
            combat.modules().add(moduleId);
        }

        addMissingModules(layout, target, AUTO_AUXILIARY_MODULES, GroupKind.AUXILIARY);
        addMissingModules(layout, target, AUTO_VANILLA_MODULES, GroupKind.VANILLA);
        addMissingModules(layout, target, AUTO_MINING_MODULES, GroupKind.MINING);
        addMissingModules(layout, target, AUTO_EXDEORUM_MODULES, GroupKind.EXDEORUM);
    }

    /**
     * 把布局中尚不存在的模块补进对应类别的分组；分组无法创建或已满时放入未分配区，
     * 玩家可以在模式配置界面里自行拖拽。
     */
    private static void addMissingModules(BeefToolLayout layout, ItemStack target,
                                          List<String> moduleIds, GroupKind kind) {
        for (String moduleId : moduleIds) {
            if (!isAvailable(moduleId, target) || layout.containsModule(moduleId)) {
                continue;
            }

            BeefToolLayout.Group group = findOrCreateGroup(layout, kind);
            if (group != null && group.modules().size() < BeefToolLayout.MAX_MODULES_PER_GROUP) {
                group.modules().add(moduleId);
            } else if (layout.unassignedModules().size() < BeefToolLayout.MAX_TOTAL_MODULES) {
                layout.unassignedModules().add(moduleId);
            }
        }
    }

    /**
     * 找到可以接收某一类模块、并且还有余量的分组。
     *
     * <p>判定分两轮：先按默认分组名匹配，使分组归属不因其中模块构成变化而漂移；
     * 名称都不匹配时，再按分组内已有模块的类别推断，以兼容被玩家改名的分组。
     * 第二轮会跳过使用其它类别默认名的分组，否则模块被重新归类后，
     * 承载它的旧分组会被误判为仍属于新类别。
     */
    private static BeefToolLayout.Group findGroupContainingKind(BeefToolLayout layout, GroupKind kind) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (group.modules().size() >= BeefToolLayout.MAX_MODULES_PER_GROUP) continue;
                if (matchesDefaultName(group.name(), kind)) {
                    return group;
                }
            }
        }

        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (group.modules().size() >= BeefToolLayout.MAX_MODULES_PER_GROUP) continue;
                if (hasKnownDefaultName(group.name())) continue;
                for (String moduleId : group.modules()) {
                    Definition definition = get(moduleId);
                    if (definition != null && definition.group() == kind) {
                        return group;
                    }
                }
            }
        }
        return null;
    }

    /** 分组名是否等于该类别的默认名，或为其追加序号后的形式。 */
    private static boolean matchesDefaultName(String name, GroupKind kind) {
        String defaultName = kind.defaultName();
        return defaultName.equals(name) || name.startsWith(defaultName + " ");
    }

    /** 分组名是否已被某个类别占用为默认名。 */
    private static boolean hasKnownDefaultName(String name) {
        for (GroupKind kind : GroupKind.values()) {
            if (matchesDefaultName(name, kind)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把老存档中归在其它分组的原版工具动作迁入「原版」分组。
     *
     * <p>补缺逻辑只处理布局中尚不存在的模块：旧版本把这些模块归在「辅助」分组，
     * 它们因此不会自行出现在新增的原版分组中，需要在首次加载时显式迁移。
     */
    public static void migrateVanillaModules(BeefToolLayout layout) {
        migrateModulesToGroup(layout, AUTO_VANILLA_MODULES, GroupKind.VANILLA);
    }

    /**
     * 把 Ex Deorum 工具模式迁入其专属分组。
     *
     * <p>三项在旧版本中归入「挖掘」分组，且已随存档落盘，因此不能只靠补缺逻辑
     * 让它们进入新建的专属分组，需要在首次加载时显式迁移。</p>
     */
    public static void migrateExDeorumModules(BeefToolLayout layout) {
        if (!GroupKind.EXDEORUM.isLoaded()) {
            return;
        }
        migrateModulesToGroup(layout, AUTO_EXDEORUM_MODULES, GroupKind.EXDEORUM);
    }

    /**
     * 把指定类别的模块统一搬入该类别的分组。
     *
     * <p>迁移只搬动模块本身，不改动分组顺序、分组名称与分组数量；
     * 目标分组已满或无法创建时保留原有归属，不做部分迁移。
     */
    private static void migrateModulesToGroup(BeefToolLayout layout, List<String> moduleIds, GroupKind kind) {
        List<String> pending = new ArrayList<>();
        for (String moduleId : moduleIds) {
            Definition definition = get(moduleId);
            if (definition == null || definition.group() != kind) continue;
            if (isInGroupOfKind(layout, moduleId, kind)) continue;
            pending.add(moduleId);
        }
        if (pending.isEmpty()) return;

        BeefToolLayout.Group target = findOrCreateGroup(layout, kind);
        if (target == null) return;
        if (target.modules().size() + pending.size() > BeefToolLayout.MAX_MODULES_PER_GROUP) return;

        for (String moduleId : pending) {
            removeModuleFromLayout(layout, moduleId);
            target.modules().add(moduleId);
        }
    }

    /** 模块是否已经位于名称与该类别默认名一致的分组中。 */
    private static boolean isInGroupOfKind(BeefToolLayout layout, String moduleId, GroupKind kind) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (matchesDefaultName(group.name(), kind) && group.modules().contains(moduleId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 把模块从布局中的任意分组与未分配区移除。 */
    private static void removeModuleFromLayout(BeefToolLayout layout, String moduleId) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                group.modules().remove(moduleId);
            }
        }
        layout.unassignedModules().remove(moduleId);
    }

    /**
     * 取得可以接收指定类别模块的分组：优先复用已承载该类别的分组，
     * 不存在时新建一个以默认名命名的分组。
     *
     * <p>既有存档由旧版本写入，其中不存在「原版」这类新增分组，
     * 若仅查找复用会把这些模块推进未分配区，因此需要在此补建。
     */
    private static BeefToolLayout.Group findOrCreateGroup(BeefToolLayout layout, GroupKind kind) {
        BeefToolLayout.Group existing = findGroupContainingKind(layout, kind);
        if (existing != null) {
            return existing;
        }
        return createGroup(layout, kind);
    }

    private static BeefToolLayout.Group findOrCreateCombatGroup(BeefToolLayout layout) {
        BeefToolLayout.Group combat = findNamedCombatGroup(layout);
        if (combat != null) return combat;

        combat = findRenamedCombatGroup(layout);
        if (combat != null) return combat;

        return createGroup(layout, GroupKind.COMBAT);
    }

    private static BeefToolLayout.Group findNamedCombatGroup(BeefToolLayout layout) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (GroupKind.COMBAT.defaultName().equals(group.name())
                        && group.modules().size() < BeefToolLayout.MAX_MODULES_PER_GROUP) {
                    return group;
                }
            }
        }
        return null;
    }

    private static BeefToolLayout.Group findRenamedCombatGroup(BeefToolLayout layout) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (group.modules().size() >= BeefToolLayout.MAX_MODULES_PER_GROUP) continue;
                for (String moduleId : group.modules()) {
                    Definition definition = get(moduleId);
                    if (definition != null && definition.group() == GroupKind.COMBAT
                            && !AUTO_COMBAT_MODULES.contains(moduleId)) {
                        return group;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 新建一个指定类别的分组：优先放入尚未占满的既有页面，全部占满时另开新页；
     * 默认名已被占用时追加序号，避免同页出现重名分组。
     *
     * <p>「原版」分组插入到同页战斗分组之后，使它在界面上的位置紧随战斗分组，
     * 而非按创建顺序落到页面末尾。
     */
    private static BeefToolLayout.Group createGroup(BeefToolLayout layout, GroupKind kind) {
        BeefToolLayout.Page page = layout.pages().stream()
                .filter(candidate -> candidate.groups().size() < BeefToolLayout.MAX_GROUPS_PER_PAGE)
                .findFirst()
                .orElse(null);

        if (page == null) {
            if (layout.pages().size() >= BeefToolLayout.MAX_PAGES) return null;
            page = new BeefToolLayout.Page("Page " + (layout.pages().size() + 1));
            layout.pages().add(page);
        }

        String name = kind.defaultName();
        int suffix = 2;
        while (hasGroupName(layout, name)) {
            name = kind.defaultName() + " " + suffix++;
        }

        BeefToolLayout.Group group = new BeefToolLayout.Group(name);
        page.groups().add(insertionIndex(page, kind), group);
        return group;
    }

    /** 新建分组在同页中的插入位置：原版分组紧随战斗分组，其余类别追加到末尾。 */
    private static int insertionIndex(BeefToolLayout.Page page, GroupKind kind) {
        if (kind != GroupKind.VANILLA) {
            return page.groups().size();
        }
        for (int i = 0; i < page.groups().size(); i++) {
            if (matchesDefaultName(page.groups().get(i).name(), GroupKind.COMBAT)) {
                return i + 1;
            }
        }
        return page.groups().size();
    }

    private static boolean hasGroupName(BeefToolLayout layout, String name) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (name.equals(group.name())) return true;
            }
        }
        return false;
    }

    /**
     * 分组类别。枚举声明顺序即 {@link #defaultLayout()} 生成的分组顺序，
     * 因此同时决定模式配置界面中分组的默认排布。
     */
    public enum GroupKind {
        TOOLS("Tools", null),
        MINING("Mining", null),
        COMBAT("Combat", null),
        VANILLA("Vanilla", null),
        AUXILIARY("Auxiliary", null),
        /**
         * Ex Deorum 专属分组，承载其三项工具模式。
         *
         * <p>该分组的模块全部来自 exdeorum，因此绑定 {@code exdeorum} 作为前置模组：
         * 未加载时默认布局不生成该分组，避免出现一个没有任何按钮的空分组。</p>
         */
        EXDEORUM("Ex Deorum", "exdeorum");

        private final String defaultName;
        private final String requiredMod;

        GroupKind(String defaultName, String requiredMod) {
            this.defaultName = defaultName;
            this.requiredMod = requiredMod;
        }

        public String defaultName() {
            return defaultName;
        }

        /** 分组的前置模组是否已加载；不绑定前置模组的类别恒为 {@code true}。 */
        public boolean isLoaded() {
            return requiredMod == null || ModList.get().isLoaded(requiredMod);
        }
    }

    public record Definition(String id, Component name, GroupKind group,
                             Availability availability, boolean exclusive) {
    }

    public enum Availability {
        ALWAYS {
            @Override
            boolean isAvailable(ItemStack target) {
                return target != null && !target.isEmpty();
            }
        },
        ENDLESS {
            @Override
            boolean isAvailable(ItemStack target) {
                return target != null && target.getItem() instanceof EndlessBeafItem;
            }
        },
        BASE {
            @Override
            boolean isAvailable(ItemStack target) {
                return target != null && BeefToolVariants.isBaseVariant(target);
            }
        },
        AE2 {
            @Override
            boolean isAvailable(ItemStack target) {
                return ModList.get().isLoaded("ae2") && target != null && !target.isEmpty();
            }
        },
        EXDEORUM {
            @Override
            boolean isAvailable(ItemStack target) {
                // 三项 Ex Deorum 工具模式均依赖其配方体系，缺该模组时不在轮盘中出现
                return ModList.get().isLoaded("exdeorum") && target != null
                        && target.getItem() instanceof EndlessBeafItem;
            }
        },
        MALUM {
            @Override
            boolean isAvailable(ItemStack target) {
                return ModList.get().isLoaded("malum") && target != null
                        && target.getItem() instanceof EndlessBeafItem;
            }
        },
        OCCULTISM {
            @Override
            boolean isAvailable(ItemStack target) {
                // 匠心仪式挎包依赖 occultism 与 modonomicon 的预览 API，两者由 occultism 传递引入
                return ModList.get().isLoaded("occultism") && target != null
                        && target.getItem() instanceof EndlessBeafItem;
            }
        },
        MYSTICAL_AGRICULTURE {
            @Override
            boolean isAvailable(ItemStack target) {
                return ModList.get().isLoaded("mysticalagriculture") && target != null
                        && target.getItem() instanceof EndlessBeafItem;
            }
        },
        REMOVED {
            @Override
            boolean isAvailable(ItemStack target) {
                return false;
            }
        },
        OMNITOOLS {
            @Override
            boolean isAvailable(ItemStack target) {
                return ModList.get().isLoaded("omnitools") && target != null && !target.isEmpty();
            }
        };

        abstract boolean isAvailable(ItemStack target);
    }
}
