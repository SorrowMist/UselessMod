package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 造化杖的「顺手收菜」行为。
 *
 * <p>右键成熟作物时直接把产物收下，并把作物重置回 0 龄（种子留在地里），
 * 而不是把整株拔掉。这样即使在整合包装了各种右键收菜功能的情况下，
 * 造化杖也不会把菜连根拔起。
 *
 * <p>掉落计算完全走原版战利品表（{@link Block#getDrops}），因此时运等附魔
 * 依然生效；同时扣除一颗种子以模拟「种子留在地里」。
 * 支持的作物与主流右键收菜模组保持一致：{@link CropBlock}（含绝大多数模组作物）、
 * {@link NetherWartBlock} 与 {@link CocoaBlock}。
 */
public final class BeefCropHarvest {

    private BeefCropHarvest() {
    }

    /** 判断该方块状态是否为「可收获的成熟作物」。 */
    public static boolean isHarvestable(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        return false;
    }

    /**
     * 服务端执行一次「收获但保留植株」，把产物收集进 {@code out}。
     *
     * <p>只负责「算出掉落 + 重置作物」，产物去向交由调用方决定，这样收菜才能和挖掘/杀怪
     * 共用同一套「AE 存储优先 / 范围磁力」处理。</p>
     *
     * @return 是否真的收获成功（非成熟作物或不受支持的方块返回 false）
     */
    public static boolean harvestAndCollect(ServerLevel level, BlockPos pos, Player player, ItemStack tool,
                                            List<ItemStack> out) {
        BlockState state = level.getBlockState(pos);
        if (!isHarvestable(state)) {
            return false;
        }

        BlockState replanted = replantState(state);
        if (replanted == null || replanted == state) {
            return false;
        }

        Item seedItem = cloneItem(state, level, pos, player);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);

        level.setBlockAndUpdate(pos, replanted);
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        player.awardStat(Stats.ITEM_USED.get(tool.getItem()));
        player.causeFoodExhaustion(0.005F);

        boolean seedKept = false;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            // 留一颗种子在地里（与右键收菜模组一致）
            if (!seedKept && seedItem != null && drop.getItem() == seedItem) {
                seedKept = true;
                drop.shrink(1);
            }
            if (drop.isEmpty()) {
                continue;
            }
            out.add(drop);
        }

        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * 服务端执行一次「收获但保留植株」，产物按工具的 AE 存储优先 / 范围磁力设置处理。
     *
     * @return 是否真的收获成功（非成熟作物或不受支持的方块返回 false）
     */
    public static boolean harvest(ServerLevel level, BlockPos pos, Player player, ItemStack tool) {
        List<ItemStack> drops = new java.util.ArrayList<>();
        if (!harvestAndCollect(level, pos, player, tool, drops)) {
            return false;
        }
        if (!drops.isEmpty()) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(drops), tool, Vec3.atCenterOf(pos));
        }
        return true;
    }

    /**
     * 计算重置后的方块状态：优先保留其它属性（例如农夫乐事番茄的 ropelogged），
     * 只在找不到 age 属性时才退回默认状态。
     */
    private static BlockState replantState(BlockState state) {
        IntegerProperty age = findAgeProperty(state);
        if (age != null) {
            return state.setValue(age, 0);
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        return null;
    }

    /** 该方块「留在地里」的那颗种子/种苗物品，找不到时返回 null。 */
    private static Item cloneItem(BlockState state, ServerLevel level, BlockPos pos, Player player) {
        // 作物的克隆物品就是它的种子（CropBlock#getBaseSeedId），命中信息对作物没有意义，
        // 这里用一个朝上的正面命中占位。
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack clone = state.getCloneItemStack(hit, level, pos, player);
        return clone.isEmpty() ? null : clone.getItem();
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && "age".equals(property.getName())) {
                return integerProperty;
            }
        }
        return null;
    }
}
