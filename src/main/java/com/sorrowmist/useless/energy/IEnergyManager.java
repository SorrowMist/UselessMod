package com.sorrowmist.useless.energy;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 能源管理器接口 - 提供统一的能源管理功能
 */
public interface IEnergyManager extends IEnergyStorage {

    /**
     * 设置当前能量值
     */
    void setEnergyStored(int energy);

    /**
     * 设置最大能量容量
     */
    void setMaxEnergyStored(int capacity);

    /**
     * 设置最大接收速率
     */
    void setMaxReceive(int maxReceive);

    /**
     * 设置最大输出速率
     */
    void setMaxExtract(int maxExtract);

    /**
     * 修改能量值（正数增加，负数减少）
     */
    void modifyEnergy(int delta);

    /**
     * 是否可以工作（能量是否足够）
     */
    boolean canWork(int energyRequired);

    /**
     * 尝试消耗能量进行工作
     * @return 是否成功消耗
     */
    boolean tryConsumeEnergy(int amount);

    /**
     * 获取能量百分比 (0.0 - 1.0)
     */
    default float getEnergyPercentage() {
        return (float) this.getEnergyStored() / this.getMaxEnergyStored();
    }

    /**
     * 序列化到NBT
     */
    CompoundTag serializeNBT();

    /**
     * 从NBT反序列化
     */
    void deserializeNBT(CompoundTag tag);

    /**
     * 设置能量变化监听器
     */
    void setChangeListener(Runnable listener);

    /**
     * 移除能量变化监听器
     */
    void removeChangeListener();
}
