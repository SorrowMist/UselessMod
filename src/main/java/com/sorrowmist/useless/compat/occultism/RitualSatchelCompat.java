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
 * <p>occultism 的仪式挎包（RitualSatchelItem）可在玩家用魔典预览某个五芒星后，
 * 一次性摆放出整座仪式所需的方块。本实现以「玩家背包 + 工具绑定的 AE2 无线网络」
 * 替代挎包内物品栏：摆放时优先取用玩家背包内的方块，不足部分从 ME 网络提取。</p>
 *
 * <p>识别与扣料规则对齐挎包 {@code RitualSatchelItem#tryPlaceBlockForMatcher}：
 * 先将候选物品解析为其将放置出的方块状态，再由该格的 stateMatcher 判定是否吻合，
 * 吻合时才执行放置。对粉笔而言，颜色与符号由 {@link ChalkItem#getGlyphBlock()} 决定，
 * 因此该步骤即可筛选出颜色正确的粉笔。</p>
 *
 * <p>本类仅在 occultism 已加载时才会被 JVM 解析：调用方一律先经
 * {@code ModList.get().isLoaded("occultism")} 守卫，避免形成硬依赖。</p>
 */
public final class RitualSatchelCompat {

    public static final String MOD_ID = "occultism";

    private RitualSatchelCompat() {
    }

    /**
     * 依据已预览的五芒星，从玩家背包与 AE 网络提取方块并摆放整座仪式。
     *
     * @param multiblockId 魔典预览中的多方块结构 id
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
            // 「任意方块」与「仅显示」两类占位不参与实际放置
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
     * 为单个多方块占位查找匹配方块并放置：优先查询玩家背包，其次查询 AE 网络。
     *
     * <p>放置上下文的坐标语义与挎包保持一致：命中位置取目标格中心，
     * 被点击的方块为目标格<b>上方</b>那一格，inside 为 false；
     * 而 stateMatcher 判定所用的位置仍是目标格本身。该坐标组合与挎包行为一致，
     * 是粉笔颜色与符号判定正确的前提。</p>
     *
     * @return 是否成功放置一个方块
     */
    private static boolean placeForMatcher(ServerLevel level, ServerPlayer player, ItemStack tool,
                                           Multiblock.SimulateResult targetMatcher) {
        BlockPos worldPos = targetMatcher.getWorldPosition();

        // 目标格已是正确方块时直接跳过。
        // 缺少该判定时，重复右键会在目标格上方继续放置：目标格已有正确方块即不可替换，
        // BlockPlaceContext 会把放置点上移到相邻格。
        if (targetMatcher.getStateMatcher().getStatePredicate()
                .test(level, worldPos, level.getBlockState(worldPos))) {
            return false;
        }

        // 命中格必须取目标格本身且 inside=true，放置点才会落在 worldPos 上。
        // 若命中格改为 worldPos.above()（该位置可替换），BlockPlaceContext 会把放置点
        // 上移到命中格，导致整座仪式整体偏移一格。
        BlockHitResult placeHit = new BlockHitResult(
                worldPos.getCenter(), Direction.UP, worldPos, true);

        // 优先从玩家背包取用
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (!matches(stack, player, level, worldPos, placeHit, targetMatcher)) continue;
            if (consumeFromInventory(player, inventory, stack, level, placeHit)) return true;
        }

        // 背包内无匹配项时，改从绑定的 AE 网络提取
        return placeFromAe(level, player, tool, worldPos, placeHit, targetMatcher);
    }

    /**
     * 判定一件物品是否满足该格所需方块。
     *
     * <p>与挎包一致：先将候选物品解析为其将放置出的方块状态，
     * 再由该格的状态匹配器判定。粉笔的颜色信息即在此步骤体现，
     * 白色粉笔不会匹配需要其它颜色粉笔的格子。</p>
     *
     * <p>粉笔额外要求能在目标格上方存活（下方有支撑），否则放置出的符号方块会立即消失。</p>
     */
    private static boolean matches(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos worldPos,
                                   BlockHitResult placeHit, Multiblock.SimulateResult targetMatcher) {
        // 必须使用真实玩家构造判定上下文：粉笔朝向取自玩家朝向；
        // 若此处传 null，判定朝向与实际放置时不一致，会选中不匹配的物品。
        BlockPlaceContext placeContext = new BlockPlaceContext(
                new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, placeHit));
        BlockState stateToPlace = resolveState(stack, placeContext);
        if (stateToPlace == null) return false;

        if (!targetMatcher.getStateMatcher().getStatePredicate()
                .test(level, worldPos, stateToPlace)) {
            return false;
        }

        // 放置点即目标格本身，存活判定同样针对该格
        return stateToPlace.canSurvive(level, worldPos);
    }

    /**
     * 使用玩家背包内的该件物品完成一次放置。
     *
     * <p>直接在该玩家<b>真实的物品栈</b>上执行 {@code useOn}，由物品自身处理
     * 耐久消耗与音效，随后回写背包。带耐久的物品（粉笔）因此只消耗少量耐久，
     * 而非整件消失；创造模式或配置为不消耗时不扣除。</p>
     *
     * @return 是否成功放置
     */
    private static boolean consumeFromInventory(ServerPlayer player, Inventory inventory,
                                                ItemStack stack, ServerLevel level,
                                                BlockHitResult placeHit) {
        // 剩余耐久不足 1 点的物品跳过，避免放置中途损坏
        if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1) {
            return false;
        }

        stack.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, placeHit));
        // 强制回写：放置可能消耗耐久，也可能耗尽整件物品
        inventory.setChanged();
        return true;
    }

    /**
     * 从绑定的 AE 网络提取一件匹配方块并放置。
     *
     * <p>提取时按网络中该件物品自身的 key 精确匹配，因此取得的物品栈保留其原有耐久；
     * 放置直接作用于该真实物品栈，由物品自身决定耐久消耗，随后原样放回网络。
     * 由此耐久扣除正确，而非整件消失。</p>
     *
     * @return 是否成功放置
     */
    private static boolean placeFromAe(ServerLevel level, ServerPlayer player, ItemStack tool,
                                       BlockPos worldPos, BlockHitResult placeHit,
                                       Multiblock.SimulateResult targetMatcher) {
        ItemStack extracted = AE2Compat.extractMatchingFromLinkedGrid(tool, player,
                candidate -> matches(candidate, player, level, worldPos, placeHit, targetMatcher));
        if (extracted == null || extracted.isEmpty()) return false;

        // 剩余耐久不足 1 点的粉笔不参与，原样放回网络
        if (extracted.isDamageableItem()
                && extracted.getMaxDamage() - extracted.getDamageValue() <= 1) {
            AE2Compat.tryInsertIntoLinkedGrid(tool, player, extracted, Actionable.MODULATE);
            return false;
        }

        extracted.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, extracted, placeHit));

        // 未耗尽则携带新耐久放回网络；已耗尽则不放回，等价于消耗该件物品
        if (!extracted.isEmpty()) {
            AE2Compat.tryInsertIntoLinkedGrid(tool, player, extracted, Actionable.MODULATE);
        }
        return true;
    }

    /**
     * 将候选物品解析为其将放置出的方块状态。
     *
     * <p>普通方块经 {@link BlockItem} 的标准流程；occultism 的粉笔不属于方块物品，
     * 其绘制的符号方块需单独经 {@link ChalkItem#getGlyphBlock()} 获取。</p>
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
