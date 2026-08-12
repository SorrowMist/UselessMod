package com.sorrowmist.useless.content.recipe;

/** Stable identifiers used to partition the shared alloy-furnace recipe directory. */
public final class RecipeSourceIds {
    public static final String CORE = "core";
    public static final String UNKNOWN = "compatibility";
    public static final String MINECRAFT = "minecraft";
    public static final String EXTENDED_AE = "extendedae";
    public static final String ADVANCED_AE = "advanced_ae";
    public static final String MEKANISM = "mekanism";
    public static final String MEKANISM_GENERATORS = "mekanismgenerators";
    public static final String APP_MEK = "appmek";
    public static final String AE2 = "ae2";
    public static final String AE2CS = "ae2cs";
    public static final String INDUSTRIAL_FOREGOING = "industrialforegoing";
    public static final String ACTUALLY_ADDITIONS = "actuallyadditions";
    public static final String ARS_NOUVEAU = "ars_nouveau";
    public static final String MYSTICAL_AGRICULTURE = "mysticalagriculture";
    public static final String AE2LT = "ae2lt";
    public static final String DATA_ENERGISTICS = "data_energistics";
    public static final String PRODUCTIVE_BEES = "productivebees";
    public static final String DRACONIC_EVOLUTION = "draconicevolution";
    public static final String POWAH = "powah";
    public static final String EXTENDED_CRAFTING = "extendedcrafting";
    public static final String NEO_ECO_AE = "neoecoae";
    public static final String NATURES_AURA = "naturesaura";
    public static final String FORBIDDEN_ARCANUS = "forbidden_arcanus";
    public static final String OCCULTISM = "occultism";
    public static final String MALUM = "malum";
    public static final String ENDER_IO = "enderio";

    private RecipeSourceIds() {
    }

    public static String normalize(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) return UNKNOWN;
        return sourceId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Best-effort stable source for adapters that do not explicitly provide one. */
    public static String fromAdapterClass(Class<?> adapterClass) {
        if (adapterClass == null) return UNKNOWN;
        String name = adapterClass.getPackageName();
        if (name.endsWith(".minecraft")) return MINECRAFT;
        if (name.endsWith(".extendedae")) return EXTENDED_AE;
        if (name.endsWith(".advancedae")) return ADVANCED_AE;
        if (name.endsWith(".ae2cs")) return AE2CS;
        if (name.endsWith(".ae2lt")) return AE2LT;
        if (name.endsWith(".dataenergistics")) return DATA_ENERGISTICS;
        if (name.endsWith(".ae2")) return AE2;
        if (name.endsWith(".industrialforegoing")) return INDUSTRIAL_FOREGOING;
        if (name.endsWith(".actuallyadditions")) return ACTUALLY_ADDITIONS;
        if (name.endsWith(".arsnouveau")) return ARS_NOUVEAU;
        if (name.endsWith(".mysticalagriculture")) return MYSTICAL_AGRICULTURE;
        if (name.endsWith(".productivebees")) return PRODUCTIVE_BEES;
        if (name.endsWith(".draconicevolution")) return DRACONIC_EVOLUTION;
        if (name.endsWith(".powah")) return POWAH;
        if (name.endsWith(".extendedcrafting")) return EXTENDED_CRAFTING;
        if (name.endsWith(".eco")) return NEO_ECO_AE;
        if (name.endsWith(".naturesaura")) return NATURES_AURA;
        if (name.endsWith(".forbiddenarcanus")) return FORBIDDEN_ARCANUS;
        if (name.endsWith(".occultism")) return OCCULTISM;
        if (name.endsWith(".malum")) return MALUM;
        if (name.endsWith(".enderio")) return ENDER_IO;
        if (name.endsWith(".mekanism") || name.endsWith(".generators")) return MEKANISM;
        return UNKNOWN;
    }
}
