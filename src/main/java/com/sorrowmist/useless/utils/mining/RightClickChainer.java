package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.content.items.BeefCropHarvest;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.ArrayList;
import java.util.List;

/**
 * 右键连锁：按住连锁键（Tab，与连锁挖掘同一个触发键）时，把造化杖自己的方块右键操作
 * ——土壤（锄头耕地 / 铲子铺路）、斧头（剥皮 / 刮铜 / 去蜡），以及顺手收菜——
 * 按连锁挖掘那套「等价组 + X/Y/Z 范围 + 数量上限」整片执行。
 *
 * <p>只覆盖工具自身的方块操作：扳手等其它方块交互、放置方块都不在此列。
 * 潜行时不连锁（只作用一块），方便把这次右键精确地用在单个方块上。
 *
 * <p>本类放在 {@code utils.mining} 是因为要复用包内可见的 {@link MiningUtils#scanBlocksForUse}。
 */
public final class RightClickChainer {

    private RightClickChainer() {
    }

    /**
     * 本次右键是否应该走连锁。
     *
     * <p>判定条件：手持造化杖 + 按住连锁键 + 未潜行。Tab 状态由服务端维护并同步到客户端，
     * 因此两端判定一致（客户端同样按连锁整片预测，不会闪烁）。
     */
    public static boolean shouldChain(Player player, ItemStack stack) {
        if (player == null || player.isShiftKeyDown()) {
            return false;
        }
        if (!(stack.getItem() instanceof EndlessBeafItem)) {
            return false;
        }
        PlayerMiningData data = MiningDispatcher.getPlayerData(player);
        return data != null && data.isTabPressed();
    }

    /**
     * 把同一个工具动作作用到连锁范围内的所有同类方块。
     *
     * <p>范围内不接受该动作的方块会被跳过（等价组可能把「同类但形态不同」的方块也算进来，
     * 例如原木与去皮原木），因此不会改动不应处理的方块。
     *
     * @param ctx        原始右键上下文（方向、手、工具沿用原上下文）
     * @param ability    要作用的工具动作
     * @param sound      生效时播放一次的音效（整片只响一次，避免叠成噪音）
     * @param levelEvent 额外 LevelEvent（刮铜 / 去蜡的粒子），小于 0 表示没有
     * @return 至少作用到一块时返回成功，否则返回 PASS 交回单块逻辑
     */
    public static InteractionResult applyToolAction(UseOnContext ctx, ItemAbility ability,
                                                     SoundEvent sound, int levelEvent) {
        Level level = ctx.getLevel();
        BlockPos origin = ctx.getClickedPos();
        BlockState originState = level.getBlockState(origin);

        List<BlockPos> targets = MiningUtils.scanBlocksForUse(
                origin, originState, level, UComponentUtils.isEnhancedChainMiningEnabled(ctx.getItemInHand()));

        boolean soundPlayed = false;
        int applied = 0;
        for (BlockPos targetPos : targets) {
            BlockState state = level.getBlockState(targetPos);
            UseOnContext targetCtx = contextAt(ctx, targetPos);
            BlockState modified = state.getToolModifiedState(targetCtx, ability, false);
            if (modified == null) {
                continue;
            }

            if (!soundPlayed) {
                // 服务端带 player 播放会自动跳过本人（本地已预测），与单块逻辑保持一致
                level.playSound(ctx.getPlayer(), origin, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                soundPlayed = true;
            }
            if (levelEvent >= 0) {
                level.levelEvent(ctx.getPlayer(), levelEvent, targetPos, 0);
            }

            if (!level.isClientSide) {
                level.setBlock(targetPos, modified, 11);
                if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, targetPos, ctx.getItemInHand());
                }
            }
            applied++;
        }

        if (applied == 0) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 连锁收菜：收获连锁范围内所有成熟作物（种子照旧留在地里）。
     *
     * @return 实际收获的作物数量
     */
    public static int harvestCrops(ServerLevel level, BlockPos origin, Player player, ItemStack tool) {
        BlockState originState = level.getBlockState(origin);
        List<BlockPos> targets = MiningUtils.scanBlocksForUse(
                origin, originState, level, UComponentUtils.isEnhancedChainMiningEnabled(tool));

        // 需要接管掉落（范围磁力或 AE 存储优先任一开启）时先整片收集、合并同类项，
        // 最后只调一次 handleDrops，避免逐株向 AE 发高频请求。
        boolean collect = UComponentUtils.shouldCollectDrops(tool);
        List<ItemStack> collected = collect ? new ArrayList<>() : null;

        int harvested = 0;
        for (BlockPos targetPos : targets) {
            if (collect) {
                if (BeefCropHarvest.harvestAndCollect(level, targetPos, player, tool, collected)) {
                    harvested++;
                }
            } else if (BeefCropHarvest.harvest(level, targetPos, player, tool)) {
                harvested++;
            }
        }

        if (collect && collected != null && !collected.isEmpty()) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(collected), tool, Vec3.atCenterOf(origin));
        }
        return harvested;
    }

    /** 以原上下文为准，为连锁中的另一个方块构造右键上下文（沿用同一朝向与手）。 */
    private static UseOnContext contextAt(UseOnContext origin, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), origin.getClickedFace(), pos, false);
        return new UseOnContext(origin.getPlayer(), origin.getHand(), hit);
    }
}
