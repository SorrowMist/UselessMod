package com.sorrowmist.useless.compat.occultism;

import appeng.api.config.Actionable;
import com.klikli_dev.modonomicon.api.ModonomiconAPI;
import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.modonomicon.multiblock.matcher.AnyMatcher;
import com.klikli_dev.modonomicon.multiblock.matcher.DisplayOnlyMatcher;
import com.klikli_dev.occultism.common.item.tool.ChalkItem;
import com.sorrowmist.useless.compat.AE2Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 「匠心仪式挎包」能力的造化杖兼容实现。
 *
 * <p>occultism 的仪式挎包（RitualSatchelItem）能在玩家用魔典预览了某个五芒星后，
 * 一次性把整座仪式所需的方块从挎包内物品栏摆出来。这里把「挎包内物品栏」换成
 * 「玩家背包 + 工具绑定的 AE2 无线网络」：摆阵时优先用玩家背包里的方块，
 * 不够的再从 ME 网络取。</p>
 *
 * <p>识别与扣料规则严格对齐挎包 {@code RitualSatchelItem#tryPlaceBlockForMatcher}：
 * 先用候选物品解析出「它将要放置出来的方块状态」，再拿该格的 stateMatcher 判定是否吻合，
 * 吻合才真正放置。对粉笔而言颜色与符号由 {@link ChalkItem#getGlyphBlock()} 决定，
 * 所以这一步天然就能挑出正确颜色的那一支。</p>
 *
 * <p>整个类只在 occultism 已加载时才会被 JVM 解析：调用方一律先经
 * {@code ModList.get().isLoaded("occultism")} 守卫，避免硬依赖。</p>
 */
public final class RitualSatchelCompat {

    public static final String MOD_ID = "occultism";

    private RitualSatchelCompat() {
    }

    /**
     * 按已预览的五芒星，从玩家背包与 AE 网络取方块并把整座仪式摆出来。
     *
     * @param multiblockId 魔典预览里的多方块结构 id
     * @param anchor       预览锚点（玩家放置预览时的起始位置）
     * @param facing       预览朝向
     */
    public static void placeFromAe(ServerLevel level, ServerPlayer player, ItemStack tool,
                                   ResourceLocation multiblockId, BlockPos anchor, Rotation facing) {
        var multiblock = ModonomiconAPI.get().getMultiblock(multiblockId);
        if (multiblock == null) {
            player.displayClientMessage(
                    Component.translatable("gui.useless_mod.ritual_satchel.unknown_multiblock")
                            .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        var simulation = multiblock.simulate(level, anchor, facing, false, false);
        boolean placedAnything = false;
        for (var targetMatcher : simulation.getSecond()) {
            // 「任意方块」与「仅显示」两类占位不需要真的放东西
            var type = targetMatcher.getStateMatcher().getType();
            if (type.equals(AnyMatcher.TYPE) || type.equals(DisplayOnlyMatcher.TYPE)) continue;

            if (placeForMatcher(level, player, tool, targetMatcher)) {
                placedAnything = true;
            }
        }

        if (!placedAnything) {
            player.displayClientMessage(
                    Component.translatable("gui.useless_mod.ritual_satchel.no_matching_item")
                            .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /**
     * 为单个多方块占位找一个匹配方块并放下：先翻玩家背包，再问 AE 网络。
     *
     * <p>放置上下文的坐标语义完全对齐挎包：命中位置取目标格的中心，
     * 被点击的方块是目标格<b>上方</b>那一格，inside 为 false。
     * 而 stateMatcher 判定用的位置仍是目标格本身——这一对坐标是挎包
     * 经实验确定的行为，照搬才能让粉笔的颜色与符号判定正确。</p>
     *
     * @return 是否真的放下了一个方块
     */
    private static boolean placeForMatcher(ServerLevel level, ServerPlayer player, ItemStack tool,
                                           Multiblock.SimulateResult targetMatcher) {
        BlockPos worldPos = targetMatcher.getWorldPosition();

        // 这一格已经摆对了就直接跳过。
        // 没有这一步的话，重复右键会把方块越堆越高：目标格已有正确方块时它不再可替换，
        // 放置点就会被算到上一格去。
        if (targetMatcher.getStateMatcher().getStatePredicate()
                .test(level, worldPos, level.getBlockState(worldPos))) {
            return false;
        }

        // 命中格必须是目标格本身，且 inside=true，放置点才会落在 worldPos 上。
        // 若把命中格写成 worldPos.above()（可替换），BlockPlaceContext 会把放置点
        // 定在命中格上，结果整座仪式统统高出一格。
        BlockHitResult placeHit = new BlockHitResult(
                worldPos.getCenter(), Direction.UP, worldPos, true);

        // 一、优先用玩家背包里的方块
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (!matches(stack, player, level, worldPos, placeHit, targetMatcher)) continue;
            if (consumeFromInventory(player, inventory, stack, level, placeHit)) return true;
        }

        // 二、背包里没有，再从绑定的 AE 网络取
        return placeFromAe(level, player, tool, worldPos, placeHit, targetMatcher);
    }

    /**
     * 判定一件物品是不是这一格要的方块。
     *
     * <p>与挎包一致：先把候选解析成「它将要放置出来的方块状态」，
     * 再用该格的状态匹配器判定。粉笔的颜色信息就在这一步体现出来，
     * 白色粉笔不会匹配需要其它颜色粉笔的格子。</p>
     *
     * <p>粉笔额外要求它能在目标格上方存活（下方有支撑），否则会放出一个立刻消失的符号。</p>
     */
    private static boolean matches(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos worldPos,
                                   BlockHitResult placeHit, Multiblock.SimulateResult targetMatcher) {
        // 必须用真实玩家构造判定上下文：粉笔的朝向取自玩家朝向，
        // 若这里传 null，判定用的朝向会与实际放置时不一致，进而挑错方块。
        BlockPlaceContext placeContext = new BlockPlaceContext(
                new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, placeHit));
        BlockState stateToPlace = resolveState(stack, placeContext);
        if (stateToPlace == null) return false;

        if (!targetMatcher.getStateMatcher().getStatePredicate()
                .test(level, worldPos, stateToPlace)) {
            return false;
        }

        // 放置点就是目标格本身，所以存活判定也看这一格
        return stateToPlace.canSurvive(level, worldPos);
    }

    /**
     * 用玩家背包里的这一件完成一次放置。
     *
     * <p>关键点：直接在该玩家<b>真实的物品栈</b>上执行 {@code useOn}，
     * 让物品自己处理耐久与音效，然后回写背包。带耐久的物品（粉笔）因此
     * 只会掉一点耐久，而不是整件消失；创造模式或配置成不破坏时则不扣。</p>
     *
     * @return 是否成功放置
     */
    private static boolean consumeFromInventory(ServerPlayer player, Inventory inventory,
                                                ItemStack stack, ServerLevel level,
                                                BlockHitResult placeHit) {
        // 只差一点就损坏的物品不用，避免摆到一半碎掉
        if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1) {
            return false;
        }

        stack.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, placeHit));
        // 强制回写：放置可能消耗了耐久，也可能把整件用掉
        inventory.setChanged();
        return true;
    }

    /**
     * 从绑定的 AE 网络取一件匹配方块并放置。
     *
     * <p>取料时按网络上那一件自己的 key 精确提取，因此拿到的栈带着它原有的耐久；
     * 放置直接作用在这个真实栈上，物品自己决定掉多少耐久，随后再原样放回网络。
     * 这样耐久就是被正确扣除的，而不是整件消失。</p>
     *
     * @return 是否成功放置
     */
    private static boolean placeFromAe(ServerLevel level, ServerPlayer player, ItemStack tool,
                                       BlockPos worldPos, BlockHitResult placeHit,
                                       Multiblock.SimulateResult targetMatcher) {
        ItemStack extracted = AE2Compat.extractMatchingFromLinkedGrid(tool, player,
                candidate -> matches(candidate, player, level, worldPos, placeHit, targetMatcher));
        if (extracted == null || extracted.isEmpty()) return false;

        // 只差一点就损坏的粉笔不参与，原样放回网络
        if (extracted.isDamageableItem()
                && extracted.getMaxDamage() - extracted.getDamageValue() <= 1) {
            AE2Compat.tryInsertIntoLinkedGrid(tool, player, extracted, Actionable.MODULATE);
            return false;
        }

        extracted.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, extracted, placeHit));

        // 还没用完就带着新耐久放回网络；用尽了则不放回，等价于消耗掉这一件
        if (!extracted.isEmpty()) {
            AE2Compat.tryInsertIntoLinkedGrid(tool, player, extracted, Actionable.MODULATE);
        }
        return true;
    }

    /**
     * 把一个候选物品解析成它将要放置出来的方块状态。
     *
     * <p>普通方块走 {@link BlockItem} 的标准流程；occultism 的粉笔不是方块物品，
     * 它画出来的符号方块要单独问 {@link ChalkItem#getGlyphBlock()}。</p>
     */
    private static BlockState resolveState(ItemStack stack, BlockPlaceContext placeContext) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            var updated = blockItem.updatePlacementContext(placeContext);
            if (updated == null) return null;
            return blockItem.getBlock().getStateForPlacement(updated);
        }
        if (stack.getItem() instanceof ChalkItem chalkItem) {
            return chalkItem.getGlyphBlock().getStateForPlacement(placeContext);
        }
        return null;
    }
}
