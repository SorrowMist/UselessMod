package com.sorrowmist.useless.utils.pattern;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/**
 * 样板供应器工具类，提供类型检测、方向匹配等通用功能
 */
public class PatternProviderUtils {
    /**
     * 获取样板供应器类型
     */
    public static String getPatternProviderType(BlockEntity blockEntity, IPart part) {
        // 使用字符串类名检测，避免直接引用可选模组的类
        if (blockEntity != null) {
            String className = blockEntity.getClass().getName();
            if (className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider")) {
                return "ExPatternProvider"; // ExtendedAE扩展样板供应器
            } else if (blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity) {
                return "AECraftingPatternProvider"; // AE2普通样板供应器
            } else if (className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity") ||
                       className.equals("net.pedroksl.advanced_ae.common.entities.SmallAdvPatternProviderEntity")) {
                return "AAEAdvPatternProvider"; // 高级AE的高级样板供应器（普通和小型）
            }
        } 
        
        if (part != null) {
            String partName = part.getClass().getName();
            if (partName.contains("ExPatternProvider")) {
                return "ExPatternProvider"; // 面板形式的ExtendedAE扩展样板供应器
            } else if (partName.contains("PatternProvider") && !partName.contains("AdvPatternProvider")) {
                return "AECraftingPatternProvider"; // 面板形式的AE2普通样板供应器
            } else if (partName.contains("AdvPatternProvider")) {
                return "AAEAdvPatternProvider"; // 面板形式的高级AE高级样板供应器
            }
        }
        return "Unknown";
    }

    /**
     * 找到与点击位置最匹配的样板供应器方向
     */
    public static Direction findMatchingDirection(IPartHost partHost, BlockPos blockPos, Direction initialDirection, Player player) {
        // 如果是直接放置的方块，直接返回初始方向
        BlockEntity blockEntity = player.level().getBlockEntity(blockPos);
        if (blockEntity != null) {
            String className = blockEntity.getClass().getName();
            if (className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity")) {
                return initialDirection;
            }
        }
        
        // 计算玩家点击的精确位置
        net.minecraft.world.phys.HitResult hitResult = player.pick(4.5D, 0.0F, false);
        if (hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return initialDirection;
        }
        
        net.minecraft.world.phys.BlockHitResult blockHitResult = (net.minecraft.world.phys.BlockHitResult) hitResult;
        net.minecraft.world.phys.Vec3 hitLocation = blockHitResult.getLocation().subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        
        // 遍历所有方向，找到与点击位置最匹配的样板供应器
        Direction bestDirection = initialDirection;
        double bestMatchScore = Double.MAX_VALUE;
        
        for (Direction dir : Direction.values()) {
            appeng.api.parts.IPart part = partHost.getPart(dir);
            if (part != null) {
                String partType = getPatternProviderType(null, part);
                if (!partType.equals("Unknown")) {
                    // 计算该方向与点击位置的匹配程度
                    double matchScore = calculateMatchScore(dir, hitLocation);
                    if (matchScore < bestMatchScore) {
                        bestMatchScore = matchScore;
                        bestDirection = dir;
                    }
                }
            }
        }
        
        return bestDirection;
    }

    /**
     * 计算部件与点击位置的匹配分数
     */
    public static double calculateMatchScore(Direction dir, net.minecraft.world.phys.Vec3 hitLocation) {
        // 计算点击位置在该方向上的投影
        double dx = hitLocation.x - 0.5;
        double dy = hitLocation.y - 0.5;
        double dz = hitLocation.z - 0.5;
        
        // 计算该方向的法向量
        int nx = dir.getStepX();
        int ny = dir.getStepY();
        int nz = dir.getStepZ();
        
        // 对于每个方向，计算点击位置到该方向面板的距离
        // 面板的位置在方块的表面，距离方块中心0.5个单位
        double distanceToPanel = Math.abs(dx * nx + dy * ny + dz * nz) - 0.5;
        
        // 计算点击位置在面板平面内的偏移
        double inPlaneOffsetX = dx - nx * (0.5 + distanceToPanel * nx);
        double inPlaneOffsetY = dy - ny * (0.5 + distanceToPanel * ny);
        double inPlaneOffsetZ = dz - nz * (0.5 + distanceToPanel * nz);
        
        // 计算平面内的偏移距离
        double inPlaneDistance = Math.sqrt(inPlaneOffsetX * inPlaneOffsetX + inPlaneOffsetY * inPlaneOffsetY + inPlaneOffsetZ * inPlaneOffsetZ);
        
        // 总匹配分数 = 到面板的距离 + 平面内的偏移距离
        return Math.abs(distanceToPanel) + inPlaneDistance;
    }

    /**
     * 获取主样板供应器的类型
     */
    public static String getMasterProviderType(Level world, PatternProviderKey masterKey) {
        BlockEntity blockEntity = world.getBlockEntity(masterKey.getPos());
        if (blockEntity != null) {
            // 检查是否是直接放置的样板供应器
            String className = blockEntity.getClass().getName();
            if (className.equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider") ||
                blockEntity instanceof appeng.blockentity.crafting.PatternProviderBlockEntity ||
                className.equals("net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity")) {
                return getPatternProviderType(blockEntity, null);
            }
            // 如果是IPartHost，检查部件
            else if (blockEntity instanceof appeng.api.parts.IPartHost partHost) {
                // 检查指定方向的部件
                appeng.api.parts.IPart targetPart = partHost.getPart(masterKey.getDirection());
                if (targetPart != null) {
                    String partType = getPatternProviderType(null, targetPart);
                    if (!partType.equals("Unknown")) {
                        return partType;
                    }
                }
                // 检查中心部件
                appeng.api.parts.IPart centerPart = partHost.getPart(null);
                if (centerPart != null) {
                    String centerType = getPatternProviderType(null, centerPart);
                    if (!centerType.equals("Unknown")) {
                        return centerType;
                    }
                }
            }
        }
        return "Unknown";
    }
}