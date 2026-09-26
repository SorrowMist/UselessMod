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
            new Definition(BEEF_INVULNERABILITY, ModeTypeEnum.BEEF_INVULNERABILITY_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ALWAYS, false),
            new Definition(BEEF_ADVANCED_STEALTH, ModeTypeEnum.BEEF_ADVANCED_STEALTH_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ALWAYS, false),
            new Definition(BEEF_TELEPORT, ModeTypeEnum.BEEF_TELEPORT_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ENDLESS, false),
            new Definition(BEEF_MAGNET, ModeTypeEnum.BEEF_MAGNET_ENABLED.getTooltip(), GroupKind.AUXILIARY,
                    Availability.ENDLESS, false),
            new Definition(BEEF_FARMLAND_MODE, ModeTypeEnum.BEEF_FARMLAND_MODE_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_CROP_HARVEST, ModeTypeEnum.BEEF_CROP_HARVEST_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_SHEARS, ModeTypeEnum.BEEF_SHEARS_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_FLINT_AND_STEEL, ModeTypeEnum.BEEF_FLINT_AND_STEEL_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_RITUAL_SATCHEL, ModeTypeEnum.BEEF_RITUAL_SATCHEL_ENABLED.getTooltip(),
                    GroupKind.MINING, Availability.OCCULTISM, false),
            new Definition(BEEF_RIPEN, ModeTypeEnum.BEEF_RIPEN_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_FORCE_GROW, ModeTypeEnum.BEEF_FORCE_GROW_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_AUTO_CLICK, ModeTypeEnum.BEEF_AUTO_CLICK_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ALWAYS, false),
            new Definition(BEEF_WIRELESS_LOGISTICS, ModeTypeEnum.BEEF_WIRELESS_LOGISTICS_ENABLED.getTooltip(),
                    GroupKind.AUXILIARY, Availability.ENDLESS, false)
    );
    private static final List<String> AUTO_COMBAT_MODULES = List.of(
            BEEF_MALUM_SPIRIT,
            BEEF_MYSTICAL_AGRICULTURE,
            BEEF_BEHEADING);
    /** 新增的辅助类模块：老存档的布局里没有它们，进游戏时自动补进「辅助」分组。 */
    private static final List<String> AUTO_AUXILIARY_MODULES = List.of(
            BEEF_FARMLAND_MODE,
            BEEF_CROP_HARVEST,
            BEEF_SHEARS,
            BEEF_FLINT_AND_STEEL,
            BEEF_RIPEN,
            BEEF_FORCE_GROW,
            BEEF_AUTO_CLICK,
            BEEF_WIRELESS_LOGISTICS);
    /** 新增的挖掘类模块：老存档的布局里没有它们，进游戏时自动补进「挖掘」分组。 */
    private static final List<String> AUTO_MINING_MODULES = List.of(
            AE_NETWORK_CONNECT,
            AUTO_SMELT,
            BEEF_RITUAL_SATCHEL);

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
        addMissingModules(layout, target, AUTO_MINING_MODULES, GroupKind.MINING);
    }

    /**
     * 把新增模块补进对应分组；找不到合适分组时先放进未分配区，
     * 玩家可以在模式配置界面里自行拖拽。
     */
    private static void addMissingModules(BeefToolLayout layout, ItemStack target,
                                          List<String> moduleIds, GroupKind kind) {
        for (String moduleId : moduleIds) {
            if (!isAvailable(moduleId, target) || layout.containsModule(moduleId)) {
                continue;
            }

            BeefToolLayout.Group group = findGroupContainingKind(layout, kind);
            if (group != null) {
                group.modules().add(moduleId);
            } else if (layout.unassignedModules().size() < BeefToolLayout.MAX_TOTAL_MODULES) {
                layout.unassignedModules().add(moduleId);
            }
        }
    }

    /** 找到已经放着某一类模块、并且还有余量的分组（分组被改名也不影响）。 */
    private static BeefToolLayout.Group findGroupContainingKind(BeefToolLayout layout, GroupKind kind) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (group.modules().size() >= BeefToolLayout.MAX_MODULES_PER_GROUP) continue;
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

    private static BeefToolLayout.Group findOrCreateCombatGroup(BeefToolLayout layout) {
        BeefToolLayout.Group combat = findNamedCombatGroup(layout);
        if (combat != null) return combat;

        combat = findRenamedCombatGroup(layout);
        if (combat != null) return combat;

        return createCombatGroup(layout);
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

    private static BeefToolLayout.Group createCombatGroup(BeefToolLayout layout) {
        BeefToolLayout.Page page = layout.pages().stream()
                .filter(candidate -> candidate.groups().size() < BeefToolLayout.MAX_GROUPS_PER_PAGE)
                .findFirst()
                .orElse(null);

        if (page == null) {
            if (layout.pages().size() >= BeefToolLayout.MAX_PAGES) return null;
            page = new BeefToolLayout.Page("Page " + (layout.pages().size() + 1));
            layout.pages().add(page);
        }

        String name = GroupKind.COMBAT.defaultName();
        int suffix = 2;
        while (hasGroupName(layout, name)) {
            name = GroupKind.COMBAT.defaultName() + " " + suffix++;
        }

        BeefToolLayout.Group combat = new BeefToolLayout.Group(name);
        page.groups().add(combat);
        return combat;
    }

    private static boolean hasGroupName(BeefToolLayout layout, String name) {
        for (BeefToolLayout.Page page : layout.pages()) {
            for (BeefToolLayout.Group group : page.groups()) {
                if (name.equals(group.name())) return true;
            }
        }
        return false;
    }

    public enum GroupKind {
        TOOLS("Tools"),
        MINING("Mining"),
        COMBAT("Combat"),
        AUXILIARY("Auxiliary");

        private final String defaultName;

        GroupKind(String defaultName) {
            this.defaultName = defaultName;
        }

        public String defaultName() {
            return defaultName;
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
