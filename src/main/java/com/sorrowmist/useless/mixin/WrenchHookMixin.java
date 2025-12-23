package com.sorrowmist.useless.mixin;

import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(appeng.hooks.WrenchHook.class)
public class WrenchHookMixin {

    @Inject(method = "onPlayerUseBlock", at = @At(value = "INVOKE", target = "Lappeng/blockentity/AEBaseBlockEntity;disassembleWithWrench(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionResult;", shift = At.Shift.BEFORE), cancellable = false, remap = false)
    private static void onDisassembleWithWrench(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if (level.isClientSide()) {
            return;
        }
        
        BlockPos pos = hitResult.getBlockPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        
        // 处理直接放置的扩展样板供应器（方块形式）
        if (blockEntity instanceof TileExPatternProvider) {
            // 方块形式，不需要匹配方向，检查所有方向的主从关系
            handleBlockFormDisassembly(level, pos);
        } else if (blockEntity instanceof appeng.api.parts.IPartHost partHost) {
            // 处理面板形式的扩展样板供应器
            // 计算玩家点击的精确位置（相对于方块的局部坐标）
            net.minecraft.world.phys.Vec3 hitLocation = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            
            // 遍历所有方向，找到与点击位置最匹配的扩展样板供应器
            appeng.api.parts.IPart targetPart = null;
            net.minecraft.core.Direction targetDir = null;
            double bestMatchScore = Double.MAX_VALUE;
            
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                appeng.api.parts.IPart part = partHost.getPart(dir);
                if (part != null && part.getClass().getName().contains("ExPatternProvider")) {
                    // 计算该方向的扩展样板供应器与点击位置的匹配程度
                    double matchScore = calculateMatchScore(dir, hitLocation);
                    if (matchScore < bestMatchScore) {
                        targetPart = part;
                        targetDir = dir;
                        bestMatchScore = matchScore;
                    }
                }
            }
            
            // 如果找到匹配的扩展样板供应器，处理它
            if (targetPart != null && targetDir != null) {
                handlePanelFormDisassembly(level, pos, targetDir);
            }
        }
    }
    
    // 计算方向与点击位置的匹配程度，分数越低表示匹配度越高
    private static double calculateMatchScore(net.minecraft.core.Direction dir, net.minecraft.world.phys.Vec3 hitLocation) {
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
    
    // 处理方块形式的扩展样板供应器拆除逻辑（不需要匹配方向）
    private static void handleBlockFormDisassembly(Level level, BlockPos pos) {
        boolean handled = false;
        
        // 遍历所有可能的方向，检查是否是主端
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            EndlessBeafItem.PatternProviderKey keyWithDir = new EndlessBeafItem.PatternProviderKey(pos, dir);
            if (EndlessBeafItem.masterToSlaves.containsKey(keyWithDir)) {
                // 处理主端拆除，清空所有从端的样板并取消关系
                EndlessBeafItem.handleMasterBreak(level, keyWithDir);
                handled = true;
                break;
            }
        }
        
        // 如果不是主端，检查是否是从端（方块形式可能有多个方向的从端）
        if (!handled) {
            // 遍历所有从端，检查是否有匹配的位置
            for (EndlessBeafItem.PatternProviderKey slaveKey : EndlessBeafItem.slaveToMaster.keySet()) {
                if (slaveKey.getPos().equals(pos)) {
                    // 处理从端拆除，取消关系并清空样板
                    EndlessBeafItem.handleSlaveBreak(level, slaveKey);
                    EndlessBeafItem.clearSlavePatterns(level, slaveKey);
                    handled = true;
                    break;
                }
            }
        }
    }
    
    // 处理面板形式的扩展样板供应器拆除逻辑（严格匹配方向）
    private static void handlePanelFormDisassembly(Level level, BlockPos pos, net.minecraft.core.Direction direction) {
        boolean handled = false;
        
        // 检查是否是主端（严格匹配方向）
        EndlessBeafItem.PatternProviderKey masterKey = new EndlessBeafItem.PatternProviderKey(pos, direction);
        if (EndlessBeafItem.masterToSlaves.containsKey(masterKey)) {
            // 处理主端拆除，清空所有从端的样板并取消关系
            EndlessBeafItem.handleMasterBreak(level, masterKey);
            handled = true;
        }
        
        // 如果不是主端，检查是否是从端（严格匹配方向）
        if (!handled) {
            // 遍历所有从端，检查是否有匹配的位置和方向
            for (EndlessBeafItem.PatternProviderKey slaveKey : EndlessBeafItem.slaveToMaster.keySet()) {
                // 严格匹配位置和方向
                if (slaveKey.getPos().equals(pos) && slaveKey.getDirection().equals(direction)) {
                    // 处理从端拆除，取消关系并清空样板
                    EndlessBeafItem.handleSlaveBreak(level, slaveKey);
                    EndlessBeafItem.clearSlavePatterns(level, slaveKey);
                    handled = true;
                    break;
                }
            }
        }
    }
}
