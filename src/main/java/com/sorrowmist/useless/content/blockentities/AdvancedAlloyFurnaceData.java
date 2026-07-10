package com.sorrowmist.useless.content.blockentities;

import net.minecraft.world.inventory.ContainerData;

/**
 * 高级合金炉数据同步类
 * 用于在服务端和客户端之间同步方块实体的状态数据
 */
public class AdvancedAlloyFurnaceData implements ContainerData {

    // ==================== 数据索引常量 ====================
    public static final int DATA_ENERGY_STORED = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_MAX_PROGRESS = 3;
    public static final int DATA_CURRENT_PARALLEL = 4;
    public static final int DATA_HAS_MOLD = 5;
    // AE网络合成任务相关
    public static final int DATA_AE_ACTIVE_TASKS = 6;
    public static final int DATA_AE_TOTAL_PROGRESS = 7;
    public static final int DATA_AE_TOTAL_MAX_PROGRESS = 8;
    public static final int DATA_FURNACE_TIER = 9;
    public static final int DATA_RETURN_OUTPUT_TO_AE = 10;
    public static final int DATA_COUNT = 11;

    private final AdvancedAlloyFurnaceBlockEntity entity;

    AdvancedAlloyFurnaceData(AdvancedAlloyFurnaceBlockEntity entity) {
        this.entity = entity;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case DATA_ENERGY_STORED -> this.entity.getEnergyManager().getEnergyStored();
            case DATA_ENERGY_CAPACITY -> this.entity.getEnergyManager().getMaxEnergyStored();
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
            case DATA_ENERGY_STORED -> this.entity.setEnergy(value);
            case DATA_ENERGY_CAPACITY -> this.entity.setMaxEnergy(value);
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
}
