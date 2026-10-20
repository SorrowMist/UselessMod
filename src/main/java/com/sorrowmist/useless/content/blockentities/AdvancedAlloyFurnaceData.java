package com.sorrowmist.useless.content.blockentities;

import net.minecraft.world.inventory.ContainerData;

/**
 * 高级合金炉数据同步类
 * 用于在服务端和客户端之间同步方块实体的状态数据
 */
public class AdvancedAlloyFurnaceData implements ContainerData {

    // ==================== 数据索引常量 ====================
    public static final int DATA_ENERGY_CAPACITY_LOW = 0;
    public static final int DATA_ENERGY_CAPACITY_HIGH = 1;
    public static final int DATA_ENERGY_STORED_LOW = 2;
    public static final int DATA_ENERGY_STORED_HIGH = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_MAX_PROGRESS = 5;
    public static final int DATA_CURRENT_PARALLEL = 6;
    public static final int DATA_HAS_MOLD = 7;
    // AE网络合成任务相关
    public static final int DATA_AE_ACTIVE_TASKS = 8;
    public static final int DATA_AE_TOTAL_PROGRESS = 9;
    public static final int DATA_AE_TOTAL_MAX_PROGRESS = 10;
    public static final int DATA_FURNACE_TIER = 11;
    public static final int DATA_RETURN_OUTPUT_TO_AE = 12;
    public static final int DATA_COUNT = 13;

    private final AdvancedAlloyFurnaceBlockEntity entity;

    AdvancedAlloyFurnaceData(AdvancedAlloyFurnaceBlockEntity entity) {
        this.entity = entity;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case DATA_ENERGY_CAPACITY_LOW -> lowBits(this.entity.getEnergyManager().getMaxEnergyStoredLong());
            case DATA_ENERGY_CAPACITY_HIGH -> highBits(this.entity.getEnergyManager().getMaxEnergyStoredLong());
            case DATA_ENERGY_STORED_LOW -> lowBits(this.entity.getEnergyManager().getEnergyStoredLong());
            case DATA_ENERGY_STORED_HIGH -> highBits(this.entity.getEnergyManager().getEnergyStoredLong());
            case DATA_PROGRESS -> this.entity.getProgress();
            case DATA_MAX_PROGRESS -> this.entity.getMaxProgress();
            case DATA_CURRENT_PARALLEL -> this.entity.getCurrentParallel();
            case DATA_HAS_MOLD -> this.entity.hasMold() ? 1 : 0;
            case DATA_AE_ACTIVE_TASKS -> this.entity.getActiveAETaskCount();
            case DATA_AE_TOTAL_PROGRESS -> this.entity.getTotalAEProgress();
            case DATA_AE_TOTAL_MAX_PROGRESS -> this.entity.getTotalAEMaxProgress();
            case DATA_FURNACE_TIER -> this.entity.getFurnaceTier();
            case DATA_RETURN_OUTPUT_TO_AE -> this.entity.isReturnOutputToAe() ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        switch (index) {
            case DATA_ENERGY_CAPACITY_LOW -> this.entity.setMaxEnergy(joinBits(
                    value, highBits(this.entity.getMaxEnergy())));
            case DATA_ENERGY_CAPACITY_HIGH -> this.entity.setMaxEnergy(joinBits(
                    lowBits(this.entity.getMaxEnergy()), value));
            case DATA_ENERGY_STORED_LOW -> this.entity.setEnergy(joinBits(
                    value, highBits(this.entity.getEnergy())));
            case DATA_ENERGY_STORED_HIGH -> this.entity.setEnergy(joinBits(
                    lowBits(this.entity.getEnergy()), value));
            case DATA_PROGRESS -> this.entity.setProgress(value);
            case DATA_MAX_PROGRESS -> this.entity.setMaxProgress(value);
            case DATA_CURRENT_PARALLEL -> this.entity.setCurrentParallel(value);
            case DATA_HAS_MOLD -> this.entity.setHasMold(value > 0);
            case DATA_AE_ACTIVE_TASKS -> this.entity.setActiveAETaskCount(value);
            case DATA_AE_TOTAL_PROGRESS -> this.entity.setTotalAEProgress(value);
            case DATA_AE_TOTAL_MAX_PROGRESS -> this.entity.setTotalAEMaxProgress(value);
            case DATA_FURNACE_TIER -> this.entity.setClientFurnaceTier(value);
            case DATA_RETURN_OUTPUT_TO_AE -> this.entity.setReturnOutputToAe(value > 0);
        }
    }

    @Override
    public int getCount() {
        return DATA_COUNT;
    }

    public static long joinBits(int low, int high) {
        return ((long) high << 32) | Integer.toUnsignedLong(low);
    }

    private static int lowBits(long value) {
        return (int) value;
    }

    private static int highBits(long value) {
        return (int) (value >>> 32);
    }
}
