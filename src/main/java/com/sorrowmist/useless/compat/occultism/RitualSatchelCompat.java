package com.sorrowmist.useless.compat.occultism;

import appeng.api.config.Actionable;
import com.klikli_dev.modonomicon.api.ModonomiconAPI;
import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.modonomicon.api.multiblock.StateMatcher;
import com.klikli_dev.modonomicon.multiblock.AbstractMultiblock;
import com.klikli_dev.modonomicon.multiblock.matcher.AnyMatcher;
import com.klikli_dev.modonomicon.multiblock.matcher.DisplayOnlyMatcher;
import com.klikli_dev.occultism.common.block.ChalkGlyphBlock;
import com.klikli_dev.occultism.common.item.tool.ChalkItem;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 用玩家背包和工具绑定的 AE 网络实现仪式挎包能力。
 *
 * <p>Occultism 1.158.0 本身没有 RitualSatchelItem，也不公开粉笔的 glyphBlock。
 * 这里通过真实注册的 {@link ChalkGlyphBlock#getChalk()} 反查颜色，实际放置始终走
 * Forge {@link ItemStack#useOn(UseOnContext)}，保留放置事件、扣料和粉笔耐久语义。</p>
 *
 * <p>预览只是一份请求，不是授权。服务端重新查询仪式/多方块、射线与权限；不调用
 * Multiblock.place，也不直接 setBlock，以免绕过材料或领地保护。</p>
 */
public final class RitualSatchelCompat {
    public static final String MOD_ID = "occultism";

    // 原生最大五芒星是 15 x 2 x 15。允许一次铺完整座，但不允许借预览远程建造/加载区块。
    static final int MAX_PLACEMENT_DISTANCE = 32;
    static final int MAX_SIMULATED_BLOCKS = 4096;

    private RitualSatchelCompat() {
    }

    /** anchor 是 Modonomicon 1.79.3 getStartPos() 的预览坐标，不是 validate 的仪式中心。 */
    public static void placeFromAe(ServerLevel level, ServerPlayer player, ItemStack tool,
                                   ResourceLocation multiblockId, BlockPos anchor, Rotation facing) {
        if (!canUseTool(level, player, tool) || multiblockId == null || anchor == null || facing == null
                || !accessibleTarget(level, player, anchor)) return;

        var multiblock = ModonomiconAPI.get().getMultiblock(multiblockId);
        if (multiblock == null) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.ritual_satchel.unknown_multiblock")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        // 不能把任意 Modonomicon 演示结构当成仪式；也支持服务端数据包定义的五芒星 id。
        if (!isRitualMultiblock(level, multiblockId) || !boundedSize(multiblock)) return;

        // 与目标版 MultiblockPreviewRenderer.renderMultiblock 完全一致（view offset + Y=1）。
        var targets = multiblock.simulate(level, anchor, facing, true, false).getSecond();
        if (targets.size() > MAX_SIMULATED_BLOCKS) return;
        for (var target : targets) {
            if (isPlaceable(target.getStateMatcher())
                    && !accessibleTarget(level, player, target.getWorldPosition())) return;
        }

        double reach = Math.min(MAX_PLACEMENT_DISTANCE, player.getBlockReach() + 1.5);
        if (!Double.isFinite(reach) || reach <= 0) return;
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !player.canReach(hit.getBlockPos(), 1.5)
                || !accessibleTarget(level, player, hit.getBlockPos())) return;
        // 空气中的预览通常点不到；向上点击它的支撑方块也算点击该格。
        boolean clickedRitual = targets.stream().anyMatch(target -> isPlaceable(target.getStateMatcher())
                && (target.getWorldPosition().equals(hit.getBlockPos())
                || hit.getDirection() == Direction.UP && target.getWorldPosition().equals(hit.getBlockPos().above())));
        if (!clickedRitual) return;
        InteractionHand hand = toolHand(player, tool);
        if (!interactionAllowed(player, hand, hit) || !canUseTool(level, player, tool)) return;

        boolean placedAnything = false;
        for (var target : targets) {
            if (!canUseTool(level, player, tool) || toolHand(player, tool) != hand) break;
            if (isPlaceable(target.getStateMatcher())
                    && placeForMatcher(level, player, tool, hand, target, facing)) placedAnything = true;
        }
        if (!placedAnything) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.ritual_satchel.no_matching_item")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    static boolean canUseTool(Level level, Player player, ItemStack tool) {
        return player != null && player.level() == level && player.isAlive() && !player.isSpectator()
                && player.mayBuild() && !player.isShiftKeyDown() && tool != null && !tool.isEmpty()
                && tool.getItem() instanceof EndlessBeafItem && toolHand(player, tool) != null
                && ComponentAccess.getOrDefault(tool, UComponents.BeefRitualSatchelComponent.get(), false)
                && tool.isItemEnabled(level.enabledFeatures()) && !player.getCooldowns().isOnCooldown(tool.getItem());
    }

    private static InteractionHand toolHand(Player player, ItemStack tool) {
        if (player.getMainHandItem() == tool) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem() == tool) return InteractionHand.OFF_HAND;
        return null;
    }

    static boolean isRitualMultiblock(Level level, ResourceLocation id) {
        return id != null && level.getRecipeManager().getRecipes().stream()
                .anyMatch(recipe -> recipe instanceof RitualRecipe ritual && id.equals(ritual.getPentacleId()));
    }

    static boolean boundedSize(Multiblock multiblock) {
        var size = multiblock.getSize();
        return size.getX() > 0 && size.getX() <= MAX_PLACEMENT_DISTANCE
                && size.getY() > 0 && size.getY() <= MAX_PLACEMENT_DISTANCE
                && size.getZ() > 0 && size.getZ() <= MAX_PLACEMENT_DISTANCE
                && (long) size.getX() * size.getY() * size.getZ() <= MAX_SIMULATED_BLOCKS;
    }

    static boolean isPlaceable(StateMatcher matcher) {
        return !AnyMatcher.TYPE.equals(matcher.getType()) && !DisplayOnlyMatcher.TYPE.equals(matcher.getType())
                && matcher.countsTowardsTotalBlocks(); // 包括 AIR 在内的非材料格，不破坏方块来满足它们
    }

    static boolean accessibleTarget(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && !level.isOutsideBuildHeight(pos.below())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_PLACEMENT_DISTANCE * MAX_PLACEMENT_DISTANCE
                && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos) && level.hasChunkAt(pos.below())
                && player.mayInteract(level, pos) && player.mayInteract(level, pos.below());
    }

    private static boolean interactionAllowed(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        var event = ForgeHooks.onRightClickBlock(player, hand, hit.getBlockPos(), hit);
        // 不能把取消事件的 SUCCESS 当成放置成功，也不能靠换材料/AE 重试规避 DENY。
        return !event.isCanceled() && event.getUseItem() != Event.Result.DENY && event.getUseBlock() != Event.Result.DENY;
    }

    static boolean placeForMatcher(ServerLevel level, ServerPlayer player, ItemStack tool, InteractionHand hand,
                                   Multiblock.SimulateResult target, Rotation facing) {
        BlockPos pos = target.getWorldPosition();
        if (!canUseTool(level, player, tool) || toolHand(player, tool) != hand
                || !accessibleTarget(level, player, pos) || target.test(level, facing)) return false;
        // inside 不控制替换位置。必须检查 BlockPlaceContext 仍落在 pos，不能悄悄放到 pos.above()。
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matches(stack, player, level, hand, hit, target, facing)) continue;
            try {
                // 一旦已选中材料，失败/取消也结束该格，不再换物品或从 AE 重试。
                return useForMatcher(level, player, tool, stack, hand, hit, target, facing);
            } finally {
                inventory.setChanged();
            }
        }
        if (!canUseTool(level, player, tool) || toolHand(player, tool) != hand
                || !accessibleTarget(level, player, pos)) return false;
        ItemStack extracted = AE2Compat.extractMatchingFromLinkedGrid(tool, player,
                candidate -> matches(candidate, player, level, hand, hit, target, facing));
        if (extracted == null || extracted.isEmpty()) return false;
        try {
            return useForMatcher(level, player, tool, extracted, hand, hit, target, facing);
        } finally {
            returnRemainder(player, tool, extracted);
        }
    }

    static boolean matches(ItemStack stack, ServerPlayer player, ServerLevel level, InteractionHand hand,
                           BlockHitResult hit, Multiblock.SimulateResult target, Rotation facing) {
        if (stack.isEmpty() || !stack.isItemEnabled(level.enabledFeatures())
                || player.getCooldowns().isOnCooldown(stack.getItem())
                || stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage()) return false;
        BlockPos pos = target.getWorldPosition();
        if (!player.mayUseItemAt(pos, Direction.UP, stack)) return false;
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(level, player, hand, stack, hit));
        if (!context.getClickedPos().equals(pos) || !context.canPlace()) return false;
        BlockState state = resolveState(stack, context);
        if (state == null) return false;
        if (state.getBlock() instanceof ChalkGlyphBlock glyph) {
            if (!glyph.canSurvive(level.getBlockState(pos), level, pos)) return false;
            // 目标版粉笔会随机选择 SIGN，不能在筛料时掷一次骰子，实际 useOn 再掷一次。
            // 原生仪式只匹配颜色；有符号限制的数据包必须对所有随机结果都成立才可自动绘制。
            for (int sign : ChalkGlyphBlock.SIGN.getPossibleValues()) {
                if (!matchesState(target, level, pos, state.setValue(ChalkGlyphBlock.SIGN, sign), facing)) return false;
            }
            return true;
        }
        return state.canSurvive(level, pos) && matchesState(target, level, pos, state, facing);
    }

    private static boolean matchesState(Multiblock.SimulateResult target, ServerLevel level, BlockPos pos,
                                        BlockState state, Rotation facing) {
        // 与目标版 SimulateResultImpl.test 的旋转规则一致。
        return target.getStateMatcher().getStatePredicate()
                .test(level, pos, state.rotate(AbstractMultiblock.fixHorizontal(facing)));
    }

    static boolean useForMatcher(ServerLevel level, ServerPlayer player, ItemStack tool, ItemStack material,
                                 InteractionHand hand, BlockHitResult hit, Multiblock.SimulateResult target, Rotation facing) {
        if (!canUseTool(level, player, tool) || toolHand(player, tool) != hand
                || !accessibleTarget(level, player, target.getWorldPosition()) || !interactionAllowed(player, hand, hit)) return false;
        // 事件处理器可能修改手持工具、权限或世界，放置前再次核查。
        if (!canUseTool(level, player, tool) || toolHand(player, tool) != hand
                || !accessibleTarget(level, player, target.getWorldPosition())
                || target.test(level, facing) || !matches(material, player, level, hand, hit, target, facing)) return false;
        BlockState before = level.getBlockState(target.getWorldPosition());
        ItemStack previous = material.copy();
        // 显式传入真实材料栈，不能调用 Item.useOn（会跳过 Forge 快照/EntityPlaceEvent）。
        InteractionResult result = material.useOn(new UseOnContext(level, player, hand, material, hit));
        BlockState after = level.getBlockState(target.getWorldPosition());
        if (after.equals(before)) {
            // ChalkItem 忽略 setBlockAndUpdate 的返回值并总返回 SUCCESS；粉笔空操作不收费。
            // 普通 BlockItem 不得据此退款：原生头颅可能已召出凋灵并消失，退款会复制材料。
            // Forge 取消放置时会自行恢复材料和世界，不能直接 setBlock 补放。
            if (previous.getItem() instanceof ChalkItem) {
                material.setCount(previous.getCount());
                material.setTag(previous.getTag() == null ? null : previous.getTag().copy());
            }
            return false;
        }
        return result.consumesAction() && target.test(level, facing);
    }

    /** AE 的 insert 返回数量，不会缩小传入栈；容量/连接变化时必须保留未收下的部分。 */
    static void returnRemainder(ServerPlayer player, ItemStack tool, ItemStack remainder) {
        if (remainder.isEmpty()) return;
        try {
            long inserted = AE2Compat.tryInsertIntoLinkedGrid(tool, player, remainder, Actionable.MODULATE);
            remainder.shrink((int) Math.max(0L, Math.min(remainder.getCount(), inserted)));
        } finally {
            if (!remainder.isEmpty()) {
                // 不用 Inventory.add：创造模式背包满时它会直接清空栈。此方法会退回背包或掉落。
                player.getInventory().placeItemBackInInventory(remainder);
                player.getInventory().setChanged();
            }
        }
    }

    static BlockState resolveState(ItemStack stack, BlockPlaceContext context) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            var updated = blockItem.updatePlacementContext(context);
            if (updated == null || !updated.getClickedPos().equals(context.getClickedPos()) || !updated.canPlace()) return null;
            BlockState state = blockItem.getBlock().getStateForPlacement(updated);
            if (state != null && stack.hasTag()) {
                var tag = stack.getTag().getCompound("BlockStateTag");
                for (String key : tag.getAllKeys()) {
                    Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                    if (property != null) state = withProperty(state, property, tag.get(key).getAsString());
                }
            }
            return state;
        }
        if (stack.getItem() instanceof ChalkItem) {
            ChalkGlyphBlock glyph = chalkGlyph(stack);
            return glyph == null ? null : glyph.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
        }
        return null;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static ChalkGlyphBlock chalkGlyph(ItemStack stack) {
        for (var block : ForgeRegistries.BLOCKS.getValues()) {
            if (block instanceof ChalkGlyphBlock glyph && glyph.getChalk() == stack.getItem()) return glyph;
        }
        return null;
    }
}
