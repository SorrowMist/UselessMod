package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.energy.IEnergyManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
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
    
    // 任务进度管理
    ConcurrentHashMap<Integer, AdvancedAlloyFurnaceBlockEntity.AETaskProgress> getAETaskProgressMap();
    AtomicInteger getTotalAEMaxProgressAtomic();
    AtomicInteger getTotalAEProgressAtomic();
    
    // 锁和流体罐
    ReentrantLock getCraftingLock();
    FluidTank[] getOutputFluidTanks();
}
