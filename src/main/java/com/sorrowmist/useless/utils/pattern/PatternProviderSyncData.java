package com.sorrowmist.useless.utils.pattern;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 用于存储同步数据的SavedData类
 */
public class PatternProviderSyncData extends SavedData {
    private final Map<PatternProviderKey, Set<PatternProviderKey>> masterToSlaves = new HashMap<>();
    private final Map<PatternProviderKey, PatternProviderKey> slaveToMaster = new HashMap<>();
    
    @Override
    public CompoundTag save(CompoundTag tag) {
        // 保存主从关系
        CompoundTag masterToSlavesTag = new CompoundTag();
        for (Map.Entry<PatternProviderKey, Set<PatternProviderKey>> entry : this.masterToSlaves.entrySet()) {
            PatternProviderKey masterKey = entry.getKey();
            Set<PatternProviderKey> slaves = entry.getValue();
            
            ListTag slaveList = new ListTag();
            for (PatternProviderKey slave : slaves) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", slave.getPos().getX());
                posTag.putInt("y", slave.getPos().getY());
                posTag.putInt("z", slave.getPos().getZ());
                posTag.putString("direction", slave.getDirection().getName());
                slaveList.add(posTag);
            }
            
            String masterKeyStr = masterKey.getPos().getX() + "," + masterKey.getPos().getY() + "," + masterKey.getPos().getZ() + "," + masterKey.getDirection().getName();
            masterToSlavesTag.put(masterKeyStr, slaveList);
        }
        tag.put("MasterToSlaves", masterToSlavesTag);
        
        // 保存从主关系
        CompoundTag slaveToMasterTag = new CompoundTag();
        for (Map.Entry<PatternProviderKey, PatternProviderKey> entry : this.slaveToMaster.entrySet()) {
            PatternProviderKey slave = entry.getKey();
            PatternProviderKey master = entry.getValue();
            
            String slaveKeyStr = slave.getPos().getX() + "," + slave.getPos().getY() + "," + slave.getPos().getZ() + "," + slave.getDirection().getName();
            CompoundTag masterTag = new CompoundTag();
            masterTag.putInt("x", master.getPos().getX());
            masterTag.putInt("y", master.getPos().getY());
            masterTag.putInt("z", master.getPos().getZ());
            masterTag.putString("direction", master.getDirection().getName());
            slaveToMasterTag.put(slaveKeyStr, masterTag);
        }
        tag.put("SlaveToMaster", slaveToMasterTag);
        
        return tag;
    }
    
    public static PatternProviderSyncData load(CompoundTag tag) {
        PatternProviderSyncData data = new PatternProviderSyncData();
        
        // 加载主从关系
        if (tag.contains("MasterToSlaves")) {
            CompoundTag masterToSlavesTag = tag.getCompound("MasterToSlaves");
            for (String masterKeyStr : masterToSlavesTag.getAllKeys()) {
                ListTag slaveList = masterToSlavesTag.getList(masterKeyStr, CompoundTag.TAG_COMPOUND);
                
                // 解析主方块位置和方向
                String[] masterCoords = masterKeyStr.split(",");
                if (masterCoords.length == 4) {
                    try {
                        net.minecraft.core.BlockPos masterPos = new net.minecraft.core.BlockPos(
                                Integer.parseInt(masterCoords[0]),
                                Integer.parseInt(masterCoords[1]),
                                Integer.parseInt(masterCoords[2])
                        );
                        net.minecraft.core.Direction masterDirection = net.minecraft.core.Direction.byName(masterCoords[3]);
                        PatternProviderKey masterKey = new PatternProviderKey(masterPos, masterDirection);
                        
                        // 解析从方块位置和方向
                        Set<PatternProviderKey> slaves = new HashSet<>();
                        for (int i = 0; i < slaveList.size(); i++) {
                            CompoundTag posTag = slaveList.getCompound(i);
                            net.minecraft.core.BlockPos slavePos = new net.minecraft.core.BlockPos(
                                    posTag.getInt("x"),
                                    posTag.getInt("y"),
                                    posTag.getInt("z")
                            );
                            net.minecraft.core.Direction slaveDirection = net.minecraft.core.Direction.byName(posTag.getString("direction"));
                            slaves.add(new PatternProviderKey(slavePos, slaveDirection));
                        }
                        
                        data.masterToSlaves.put(masterKey, slaves);
                    } catch (NumberFormatException e) {
                        // 忽略格式错误的坐标
                    }
                }
            }
        }
        
        // 加载从主关系
        if (tag.contains("SlaveToMaster")) {
            CompoundTag slaveToMasterTag = tag.getCompound("SlaveToMaster");
            for (String slaveKeyStr : slaveToMasterTag.getAllKeys()) {
                CompoundTag masterTag = slaveToMasterTag.getCompound(slaveKeyStr);
                
                // 解析从方块位置和方向
                String[] slaveCoords = slaveKeyStr.split(",");
                if (slaveCoords.length == 4) {
                    try {
                        net.minecraft.core.BlockPos slavePos = new net.minecraft.core.BlockPos(
                                Integer.parseInt(slaveCoords[0]),
                                Integer.parseInt(slaveCoords[1]),
                                Integer.parseInt(slaveCoords[2])
                        );
                        net.minecraft.core.Direction slaveDirection = net.minecraft.core.Direction.byName(slaveCoords[3]);
                        PatternProviderKey slaveKey = new PatternProviderKey(slavePos, slaveDirection);
                        
                        // 解析主方块位置和方向
                        net.minecraft.core.BlockPos masterPos = new net.minecraft.core.BlockPos(
                                masterTag.getInt("x"),
                                masterTag.getInt("y"),
                                masterTag.getInt("z")
                        );
                        net.minecraft.core.Direction masterDirection = net.minecraft.core.Direction.byName(masterTag.getString("direction"));
                        PatternProviderKey masterKey = new PatternProviderKey(masterPos, masterDirection);
                        
                        data.slaveToMaster.put(slaveKey, masterKey);
                    } catch (NumberFormatException e) {
                        // 忽略格式错误的坐标
                    }
                }
            }
        }
        
        return data;
    }
    
    public Map<PatternProviderKey, Set<PatternProviderKey>> getMasterToSlaves() {
        return masterToSlaves;
    }
    
    public Map<PatternProviderKey, PatternProviderKey> getSlaveToMaster() {
        return slaveToMaster;
    }
    
    public void clear() {
        masterToSlaves.clear();
        slaveToMaster.clear();
    }
}