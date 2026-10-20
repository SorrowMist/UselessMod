package com.sorrowmist.useless.content.blocks.multiblock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class UselessCoilBlock extends MultiblockPartBlock {
    public static final int MIN_TIER = 1;
    public static final int USEFUL_TIER = 10;
    public static final int MAX_TIER = USEFUL_TIER;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final int tier;

    public UselessCoilBlock(int tier, Properties properties) {
        super(properties);
        validateTier(tier);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public int tier() {
        return tier;
    }

    public static String registryName(int tier) {
        validateTier(tier);
        return tier == USEFUL_TIER ? "useful_coil" : "useless_coil_tier_" + tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    private static void validateTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException(
                    "Coil tier must be between " + MIN_TIER + " and " + MAX_TIER);
        }
    }
}
