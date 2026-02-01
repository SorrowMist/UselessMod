package com.sorrowmist.useless.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedstorage.block.*;

public class SophisticatedCompat {

    /**
     * 处理精妙系列存储方块的精准采集掉落
     *
     * @param block 方块
     * @param be    方块实体
     * @param stack 物品堆
     * @return 如果处理了返回 true
     */
    public static boolean handleSilkTouchDrop(Block block, BlockEntity be, ItemStack stack) {
        if (be instanceof StorageBlockEntity storageBe && block instanceof IAdditionalDropDataBlock dropDataBlock) {
            // WoodStorageBlockEntity（箱子、木桶等），需要先设置 packed = true
            // addDropData 才会保存物品内容到 stack 中
            if (be instanceof WoodStorageBlockEntity) {
                prepareWoodStorageForDrop((WoodStorageBlockEntity) be, block, true);
            }
            // 调用 addDropData 来正确保存物品内容
            dropDataBlock.addDropData(stack, storageBe);
            return true;
        }
        return false;
    }

    /**
     * 在移除方块前处理 SophisticatedStorage 方块
     *
     * @param be 方块实体
     */
    public static void handlePreRemoval(BlockEntity be) {
        if (be instanceof WoodStorageBlockEntity woodBe) {
            prepareWoodStorageForDrop(woodBe, be.getBlockState().getBlock(), false);
        }
        // 潜影盒不需要特殊处理，因为它的内容保存机制不同
    }

    /**
     * 准备 WoodStorage 方块用于掉落或移除
     * 统一处理单箱子、双箱子、木桶等的 packed 状态设置
     *
     * @param woodBe    WoodStorageBlockEntity
     * @param block     方块
     * @param copyData  是否复制主箱子数据到副箱子（精准采集模式需要）
     */
    private static void prepareWoodStorageForDrop(WoodStorageBlockEntity woodBe, Block block, boolean copyData) {
        // 处理双箱子的情况
        if (woodBe instanceof ChestBlockEntity chestBe && block instanceof ChestBlock) {
            prepareDoubleChest(chestBe, copyData);
        } else {
            // 单个箱子/木桶的处理
            boolean hasContents = !InventoryHelper.isEmpty(woodBe.getStorageWrapper().getInventoryHandler())
                    || !InventoryHelper.isEmpty(woodBe.getStorageWrapper().getUpgradeHandler());
            if (hasContents && !woodBe.isPacked()) {
                woodBe.setPacked(true);
            }
        }
    }

    /**
     * 准备双箱子的打包逻辑
     * 关键：两个箱子都需要设置 packed = true，并且主箱子的内容需要被正确复制
     *
     * @param chestBe   当前箱子实体
     * @param copyData  是否复制主箱子数据到副箱子
     */
    private static void prepareDoubleChest(ChestBlockEntity chestBe, boolean copyData) {
        BlockState state = chestBe.getBlockState();

        // 获取箱子类型
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) {
            // 单个箱子，直接设置 packed
            setPackedIfHasContents(chestBe);
            return;
        }

        // 双箱子的情况
        Level level = chestBe.getLevel();
        if (level == null) return;

        // 获取另一个箱子的位置
        Direction connectedDir = ChestBlock.getConnectedDirection(state);
        BlockPos otherPos = chestBe.getBlockPos().relative(connectedDir);

        // 获取另一个箱子的实体
        BlockEntity otherBe = level.getBlockEntity(otherPos);
        if (!(otherBe instanceof ChestBlockEntity otherChestBe)) {
            // 如果找不到另一个箱子，按单个箱子处理
            setPackedIfHasContents(chestBe);
            return;
        }

        // 确定哪个是主箱子，哪个是副箱子
        ChestBlockEntity mainChestBe;
        ChestBlockEntity secondaryChestBe;
        if (chestBe.isMainChest()) {
            mainChestBe = chestBe;
            secondaryChestBe = otherChestBe;
        } else {
            mainChestBe = otherChestBe;
            secondaryChestBe = chestBe;
        }

        // 设置主箱子的 packed = true
        if (!mainChestBe.isPacked()) {
            mainChestBe.setPacked(true);
        }

        // 设置副箱子的 packed = true
        if (!secondaryChestBe.isPacked()) {
            secondaryChestBe.setPacked(true);
        }

        // 如果需要复制数据，将主箱子的内容复制到副箱子
        if (copyData) {
            secondaryChestBe.getStorageWrapper().load(mainChestBe.getStorageWrapper().save(new CompoundTag()));
        }
    }

    /**
     * 如果方块有内容且未打包，则设置 packed = true
     *
     * @param woodBe WoodStorageBlockEntity
     */
    private static void setPackedIfHasContents(WoodStorageBlockEntity woodBe) {
        boolean hasContents = !InventoryHelper.isEmpty(woodBe.getStorageWrapper().getInventoryHandler())
                || !InventoryHelper.isEmpty(woodBe.getStorageWrapper().getUpgradeHandler());
        if (hasContents && !woodBe.isPacked()) {
            woodBe.setPacked(true);
        }
    }
}
