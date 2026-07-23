package com.sorrowmist.useless.content.blocks.multiblock;

public final class UselessCoilBlock extends MultiblockPartBlock {
    public static final int MIN_TIER = 1;
    public static final int USEFUL_TIER = 10;
    public static final int MAX_TIER = USEFUL_TIER;

    private final int tier;

    public UselessCoilBlock(int tier, Properties properties) {
        super(properties);
        validateTier(tier);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public static String registryName(int tier) {
        validateTier(tier);
        return tier == USEFUL_TIER ? "useful_coil" : "useless_coil_tier_" + tier;
    }

    private static void validateTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException(
                    "Coil tier must be between " + MIN_TIER + " and " + MAX_TIER);
        }
    }
}
