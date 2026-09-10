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
    public static final String AE_STORAGE_PRIORITY = "mode.ae_storage_priority";
    public static final String WRENCH_TAG = "mode.wrench_tag";
    public static final String FORCE_KILL = "mode.force_kill";
    public static final String BEEF_TIME_ACCELERATION = "mode.beef_time_acceleration";
    public static final String BEEF_INVULNERABILITY = "mode.beef_invulnerability";
    public static final String BEEF_ADVANCED_STEALTH = "mode.beef_advanced_stealth";
    public static final String BEEF_CAPTURE = "mode.beef_capture";
    public static final String BEEF_TELEPORT = "mode.beef_teleport";
    public static final String BEEF_AOE_DAMAGE = "mode.beef_aoe_damage";
    public static final String BEEF_MAGNET = "mode.beef_magnet";

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
            new Definition(AE_STORAGE_PRIORITY, ModeTypeEnum.AE_STORAGE_PRIORITY_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.AE2, false),
            new Definition(WRENCH_TAG, ModeTypeEnum.WRENCH_TAG_ENABLED.getTooltip(), GroupKind.MINING,
                    Availability.BASE, false),
            new Definition(FORCE_KILL, ModeTypeEnum.FORCE_KILL.getTooltip(), GroupKind.COMBAT,
                    Availability.ALWAYS, false),
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
                    Availability.ENDLESS, false)
    );

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

    /** Adds newly available modules without disturbing any existing order. */
    public static void addMissingAvailableModules(BeefToolLayout layout, ItemStack target) {
        for (Definition definition : DEFINITIONS) {
            if (definition.availability().isAvailable(target) && !layout.containsModule(definition.id())) {
                layout.unassignedModules().add(definition.id());
            }
        }
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
