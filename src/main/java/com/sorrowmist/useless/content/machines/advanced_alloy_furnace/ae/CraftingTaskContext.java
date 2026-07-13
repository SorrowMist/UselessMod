package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.energy.IEnergyManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CraftingTask 的上下文接口，提供对 AdvancedAlloyFurnaceBlockEntity 必要成员的访问
 */
public interface CraftingTaskContext {
    
    // 槽位常量
    int getInputSlotsStart();
    int getInputSlotsCount();
    int getOutputSlotsStart();
    int getOutputSlotsCount();
    int getCatalystSlot();
    int getMoldSlot();
    int getFluidTankCount();
    
    // 基础访问
    Level getLevel();
    net.minecraft.core.BlockPos getBlockPos();
    ItemStackHandler getItemHandler();
    IEnergyManager getEnergyManager();
    
    // 状态更新
    void markChanged();
    void sendAETaskProgressToClients();
    
    // 催化剂相关
    int getCatalystMaxParallel();
    
    // AE网络输出
    long tryOutputToAE(net.minecraft.world.item.ItemStack stack);
    long tryOutputFluidToAE(FluidStack stack);
    long tryOutputKeyToAE(AEKey key, long amount);

    // 产物输出模式
    boolean isReturnOutputToAe();

    /** 暂存未能返还的输入（AE 写入失败时防丢失，由管理器逐 tick 重试写回） */
    void stashUnreturnedInput(AEKey key, long amount);

    /**
     * 统一 AE 输出端口：受“产物返回AE”开关约束，开关关闭时不写入 AE 网络，
     * 剩余部分由调用方回退到本地槽位。
     */
    default FurnaceOutputPort.AeOutput createAeOutputPort() {
        return new FurnaceOutputPort.AeOutput() {
            @Override
            public long insertItem(ItemStack stack) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputToAE(stack);
            }

            @Override
            public long insertFluid(FluidStack stack) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputFluidToAE(stack);
            }

            @Override
            public long insertKey(AEKey key, long amount) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputKeyToAE(key, amount);
            }
        };
    }
    
    // 任务进度管理
    ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressMap();
    AtomicInteger getTotalAEMaxProgressAtomic();
    AtomicInteger getTotalAEProgressAtomic();
    
    // 锁和流体罐
    ReentrantLock getCraftingLock();
    FluidTank[] getInputFluidTanks();
    FluidTank[] getOutputFluidTanks();
}
