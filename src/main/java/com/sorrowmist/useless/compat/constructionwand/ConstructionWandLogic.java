package com.sorrowmist.useless.compat.constructionwand;

import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import appeng.api.config.Actionable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Independent construction-wand behavior for the beef tool. */
public final class ConstructionWandLogic {
    private static final String MOD_ID = "constructionwand";
    private static final int MAX_UNDO_OPERATIONS = 3;
    public static final int MAX_PREVIEW_BLOCKS = 4096;
    private static final Map<UUID, Deque<Operation>> HISTORY = new ConcurrentHashMap<>();

    private ConstructionWandLogic() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isEnabled(ItemStack stack) {
        return isAvailable()
                && stack.getItem() instanceof EndlessBeafItem
                && stack.getOrDefault(UComponents.ConstructionWandEnabledComponent.get(), false);
    }

    public static List<BlockPos> preview(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        ItemStack tool = player.getItemInHand(hand);
        if (!isEnabled(tool)) return List.of();

        ConstructionWandCoreMode core = tool.getOrDefault(
                UComponents.ConstructionWandCoreComponent.get(), ConstructionWandCoreMode.DEFAULT);
        return switch (core) {
            case DEFAULT -> previewBuild(player.serverLevel(), player, hit, tool);
            case ANGEL -> previewAngel(player.serverLevel(), player, hit, tool);
            case DESTRUCTION -> previewDestroy(player.serverLevel(), hit);
        };
    }

    public static boolean handleRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isEnabled(event.getItemStack())) return false;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        BlockHitResult hit = event.getHitVec();
        if (player.isShiftKeyDown()) {
            undo(player, level, hit.getBlockPos());
            return true;
        }

        ItemStack tool = event.getItemStack();
        ConstructionWandCoreMode core = tool.getOrDefault(
                UComponents.ConstructionWandCoreComponent.get(), ConstructionWandCoreMode.DEFAULT);
        Operation operation = switch (core) {
            case DEFAULT -> build(level, player, hit, tool);
            case ANGEL -> angel(level, player, hit, false, tool);
            case DESTRUCTION -> destroy(level, player, hit, tool);
        };
        remember(player, operation);
        return true;
    }

    public static boolean handleRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isEnabled(event.getItemStack())) return false;

        event.setCanceled(true);
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        ItemStack tool = event.getItemStack();
        ConstructionWandCoreMode core = tool.getOrDefault(
                UComponents.ConstructionWandCoreComponent.get(), ConstructionWandCoreMode.DEFAULT);
        if (core == ConstructionWandCoreMode.ANGEL) {
            remember(player, angel(level, player, null, true, tool));
        }
        return true;
    }

    public static void clearPlayer(UUID playerId) {
        HISTORY.remove(playerId);
    }

    private static Operation build(ServerLevel level, ServerPlayer player, BlockHitResult hit, ItemStack tool) {
        BlockState targetState = level.getBlockState(hit.getBlockPos());
        if (!(targetState.getBlock().asItem() instanceof BlockItem targetItem)) return null;

        int limit = ConfigManager.getBeefConstructionWandBuildLimit();
        Direction face = hit.getDirection();
        Direction[] planeDirections = planeDirections(face);
        Deque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<SavedBlock> changed = new ArrayList<>();
        pending.add(hit.getBlockPos().relative(face));
        int attempts = 0;
        int maxAttempts = Math.max(64, Math.min(100_000, limit * 8));

        while (!pending.isEmpty() && changed.size() < limit && attempts++ < maxAttempts) {
            if (!player.isCreative() && findSupply(player, tool, targetItem).stack.isEmpty()) break;
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;

            BlockPos supportPos = pos.relative(face.getOpposite());
            if (level.getBlockState(supportPos).getBlock() != targetState.getBlock()) continue;
            if (!place(level, player, tool, pos, face, targetItem, changed)) continue;
            if (changed.size() >= limit) break;

            for (Direction direction : planeDirections) {
                pending.addLast(pos.relative(direction));
            }
        }
        return changed.isEmpty() ? null : new Operation(changed, tool.copy());
    }

    private static List<BlockPos> previewBuild(ServerLevel level, ServerPlayer player,
                                               BlockHitResult hit, ItemStack tool) {
        BlockState targetState = level.getBlockState(hit.getBlockPos());
        if (!(targetState.getBlock().asItem() instanceof BlockItem targetItem)) return List.of();

        int limit = previewLimit(level, player, tool, targetItem, ConfigManager.getBeefConstructionWandBuildLimit());
        if (limit <= 0) return List.of();

        Direction face = hit.getDirection();
        Direction[] planeDirections = planeDirections(face);
        Deque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> preview = new ArrayList<>(Math.min(limit, MAX_PREVIEW_BLOCKS));
        pending.add(hit.getBlockPos().relative(face));

        int attempts = 0;
        int maxAttempts = Math.max(64, Math.min(100_000, limit * 8));
        while (!pending.isEmpty() && preview.size() < limit && attempts++ < maxAttempts) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;
            if (level.getBlockState(pos.relative(face.getOpposite())).getBlock() != targetState.getBlock()) {
                continue;
            }
            if (getPlacementState(level, player, pos, face, targetItem, new ItemStack(targetItem)) == null) {
                continue;
            }

            preview.add(pos.immutable());
            for (Direction direction : planeDirections) {
                pending.addLast(pos.relative(direction));
            }
        }
        return List.copyOf(preview);
    }

    private static Operation angel(ServerLevel level, ServerPlayer player,
                                   BlockHitResult hit, boolean fromAir, ItemStack tool) {
        BlockItem targetItem;
        Direction face;
        BlockPos start;

        if (fromAir) {
            ItemStack offhand = player.getOffhandItem();
            if (!(offhand.getItem() instanceof BlockItem blockItem)) return null;
            targetItem = blockItem;
            face = Direction.UP;
            start = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(2.0)));
        } else {
            BlockState supportingState = level.getBlockState(hit.getBlockPos());
            if (!(supportingState.getBlock().asItem() instanceof BlockItem blockItem)) return null;
            targetItem = blockItem;
            face = hit.getDirection();
            start = hit.getBlockPos().relative(face.getOpposite());
        }

        int limit = ConfigManager.getBeefConstructionWandAngelLimit();
        List<SavedBlock> changed = new ArrayList<>();
        Direction step = fromAir ? player.getDirection() : face.getOpposite();
        BlockPos pos = start;
        for (int i = 0; i < limit; i++) {
            if (place(level, player, tool, pos, face, targetItem, changed)) {
                if (changed.size() >= limit) break;
            }
            pos = pos.relative(step);
        }
        return changed.isEmpty() ? null : new Operation(changed, tool.copy());
    }

    private static List<BlockPos> previewAngel(ServerLevel level, ServerPlayer player,
                                               BlockHitResult hit, ItemStack tool) {
        BlockState supportingState = level.getBlockState(hit.getBlockPos());
        if (!(supportingState.getBlock().asItem() instanceof BlockItem targetItem)) return List.of();

        int limit = previewLimit(level, player, tool, targetItem, ConfigManager.getBeefConstructionWandAngelLimit());
        if (limit <= 0) return List.of();
        Direction face = hit.getDirection();
        Direction step = face.getOpposite();
        BlockPos pos = hit.getBlockPos().relative(step);
        List<BlockPos> preview = new ArrayList<>(Math.min(limit, MAX_PREVIEW_BLOCKS));
        for (int i = 0; i < limit && preview.size() < MAX_PREVIEW_BLOCKS; i++) {
            if (getPlacementState(level, player, pos, face, targetItem, new ItemStack(targetItem)) != null) {
                preview.add(pos.immutable());
            }
            pos = pos.relative(step);
        }
        return List.copyOf(preview);
    }

    private static Operation destroy(ServerLevel level, ServerPlayer player,
                                     BlockHitResult hit, ItemStack tool) {
        BlockState target = level.getBlockState(hit.getBlockPos());
        if (target.isAir()) return null;

        int limit = ConfigManager.getBeefConstructionWandDestructionLimit();
        Direction[] planeDirections = planeDirections(hit.getDirection());
        Deque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<SavedBlock> changed = new ArrayList<>();
        pending.add(hit.getBlockPos());

        while (!pending.isEmpty() && changed.size() < limit) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;

            BlockState state = level.getBlockState(pos);
            boolean destroyed = false;
            if (!state.isAir() && state.getBlock() == target.getBlock()) {
                SavedBlock saved = SavedBlock.capture(level, pos, state, ItemStack.EMPTY, false);
                if (level.destroyBlock(pos, false, player)) {
                    saved.afterState = level.getBlockState(pos);
                    changed.add(saved);
                    destroyed = true;
                }
            }
            if (!destroyed) continue;
            for (Direction direction : planeDirections) {
                pending.addLast(pos.relative(direction));
            }
        }
        return changed.isEmpty() ? null : new Operation(changed, tool.copy());
    }

    private static List<BlockPos> previewDestroy(ServerLevel level, BlockHitResult hit) {
        BlockState target = level.getBlockState(hit.getBlockPos());
        if (target.isAir()) return List.of();

        int limit = Math.min(ConfigManager.getBeefConstructionWandDestructionLimit(), MAX_PREVIEW_BLOCKS);
        Direction[] planeDirections = planeDirections(hit.getDirection());
        Deque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> preview = new ArrayList<>(limit);
        pending.add(hit.getBlockPos());

        while (!pending.isEmpty() && preview.size() < limit) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() != target.getBlock()) continue;

            preview.add(pos.immutable());
            for (Direction direction : planeDirections) {
                pending.addLast(pos.relative(direction));
            }
        }
        return List.copyOf(preview);
    }

    private static boolean place(ServerLevel level, ServerPlayer player, ItemStack tool,
                                 BlockPos pos,
                                 Direction face, BlockItem targetItem, List<SavedBlock> changed) {
        BlockState existing = level.getBlockState(pos);
        Supply supply = findSupply(player, tool, targetItem);
        if (supply.stack.isEmpty() && !player.isCreative()) return false;
        if (!existing.canBeReplaced()) return false;

        ItemStack placementStack = supply.stack.isEmpty() ? new ItemStack(targetItem) : supply.stack;
        BlockState placed = getPlacementState(level, player, pos, face, targetItem, placementStack);
        if (placed == null) return false;

        SavedBlock saved = SavedBlock.capture(level, pos, existing,
                player.isCreative() ? ItemStack.EMPTY : placementStack.copyWithCount(1), supply.fromAe);
        if (!level.setBlock(pos, placed, Block.UPDATE_ALL)) return false;
        if (!player.isCreative() && supply.fromAe) {
            long extracted = AE2Compat.tryExtractFromLinkedGrid(
                    tool, player, placementStack.copyWithCount(1), Actionable.MODULATE);
            if (extracted != 1) {
                saved.restore(level);
                return false;
            }
        } else if (!player.isCreative()) {
            placementStack.shrink(1);
        }
        saved.afterState = level.getBlockState(pos);
        changed.add(saved);
        return true;
    }

    private static Supply findSupply(ServerPlayer player, ItemStack tool, BlockItem targetItem) {
        boolean aePriority = isAeStoragePriorityEnabled(tool);
        Supply aeSupply = findAeSupply(player, tool, targetItem);
        if (aePriority && !aeSupply.stack.isEmpty()) {
            return aeSupply;
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() == targetItem && !offhand.isEmpty()) return new Supply(offhand, false);

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == targetItem && !stack.isEmpty()) return new Supply(stack, false);
        }
        return aePriority ? new Supply(ItemStack.EMPTY, false) : aeSupply;
    }

    private static BlockState getPlacementState(ServerLevel level, ServerPlayer player, BlockPos pos,
                                                Direction face, BlockItem targetItem, ItemStack stack) {
        if (!level.getBlockState(pos).canBeReplaced()) return null;
        BlockHitResult placeHit = new BlockHitResult(
                Vec3.atCenterOf(pos), face, pos.relative(face.getOpposite()), false);
        BlockPlaceContext context = new BlockPlaceContext(
                level, player, InteractionHand.MAIN_HAND, stack, placeHit);
        BlockState placed = targetItem.getBlock().getStateForPlacement(context);
        if (placed == null || !placed.canSurvive(level, pos)
                || !level.isUnobstructed(placed, pos,
                                         net.minecraft.world.phys.shapes.CollisionContext.of(player))) {
            return null;
        }
        return placed;
    }

    private static int previewLimit(ServerLevel level, ServerPlayer player, ItemStack tool,
                                     BlockItem targetItem, int configuredLimit) {
        int limit = Math.min(configuredLimit, MAX_PREVIEW_BLOCKS);
        if (player.isCreative()) return limit;

        long available = countLocalSupply(player, targetItem);
        if (tool.has(UComponents.WIRELESS_LINK_TARGET.get())) {
            try {
                available += AE2Compat.tryExtractFromLinkedGrid(
                        tool, player, new ItemStack(targetItem, limit), Actionable.SIMULATE);
            } catch (Throwable ignored) {
            }
        }
        return (int) Math.min(limit, available);
    }

    private static long countLocalSupply(ServerPlayer player, BlockItem targetItem) {
        long count = 0;
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() == targetItem) count += offhand.getCount();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == targetItem) count += stack.getCount();
        }
        return count;
    }

    private static Supply findAeSupply(ServerPlayer player, ItemStack tool, BlockItem targetItem) {
        if (!tool.has(UComponents.WIRELESS_LINK_TARGET.get())) {
            return new Supply(ItemStack.EMPTY, false);
        }

        ItemStack requested = new ItemStack(targetItem);
        try {
            if (AE2Compat.tryExtractFromLinkedGrid(tool, player, requested, Actionable.SIMULATE) > 0) {
                return new Supply(requested, true);
            }
        } catch (Throwable ignored) {
        }
        return new Supply(ItemStack.EMPTY, false);
    }

    private static boolean isAeStoragePriorityEnabled(ItemStack tool) {
        return tool.getOrDefault(UComponents.AEStoragePriorityComponent.get(), false)
                && tool.has(UComponents.WIRELESS_LINK_TARGET.get());
    }

    private static Direction[] planeDirections(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
            case Y -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            case Z -> new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
        };
    }

    private static void remember(ServerPlayer player, Operation operation) {
        if (operation == null || operation.blocks.isEmpty()) return;
        Deque<Operation> operations = HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        operations.addLast(operation);
        while (operations.size() > MAX_UNDO_OPERATIONS) operations.removeFirst();
    }

    private static void undo(ServerPlayer player, ServerLevel level, BlockPos clickedPos) {
        Deque<Operation> operations = HISTORY.get(player.getUUID());
        if (operations == null || operations.isEmpty()) return;

        Operation operation = operations.peekLast();
        if (operation.dimension != level.dimension() || !operation.contains(clickedPos)) return;

        boolean restored = false;
        for (SavedBlock block : operation.blocks) {
            if (!level.getBlockState(block.pos).equals(block.afterState)) continue;
            block.restore(level);
            if (!block.refund.isEmpty()) {
                ItemStack refund = block.refund.copy();
                if (block.refundToAe) {
                    try {
                        long inserted = AE2Compat.tryInsertIntoLinkedGrid(
                                operation.tool, player, refund, Actionable.MODULATE);
                        refund.shrink((int) Math.min(Integer.MAX_VALUE, inserted));
                    } catch (Throwable ignored) {
                    }
                }
                if (!refund.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(refund);
                }
            }
            restored = true;
        }
        if (restored) operations.removeLast();
    }

    private record Operation(List<SavedBlock> blocks,
                             ItemStack tool,
                             net.minecraft.resources.ResourceKey<Level> dimension) {
        private Operation(List<SavedBlock> blocks, ItemStack tool) {
            this(blocks, tool, blocks.getFirst().dimension);
        }

        private boolean contains(BlockPos pos) {
            return blocks.stream().anyMatch(block -> block.pos.equals(pos));
        }
    }

    private static final class SavedBlock {
        private final BlockPos pos;
        private final BlockState beforeState;
        private final CompoundTag blockEntityData;
        private final ItemStack refund;
        private final boolean refundToAe;
        private BlockState afterState;
        private final net.minecraft.resources.ResourceKey<Level> dimension;

        private SavedBlock(BlockPos pos, BlockState beforeState, CompoundTag blockEntityData,
                           ItemStack refund, boolean refundToAe,
                           net.minecraft.resources.ResourceKey<Level> dimension) {
            this.pos = pos.immutable();
            this.beforeState = beforeState;
            this.blockEntityData = blockEntityData;
            this.refund = refund;
            this.refundToAe = refundToAe;
            this.dimension = dimension;
        }

        private static SavedBlock capture(ServerLevel level, BlockPos pos, BlockState state,
                                          ItemStack refund, boolean refundToAe) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            CompoundTag data = blockEntity == null
                    ? null
                    : blockEntity.saveWithFullMetadata(level.registryAccess());
            return new SavedBlock(pos, state, data, refund, refundToAe, level.dimension());
        }

        private void restore(ServerLevel level) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, beforeState, Block.UPDATE_ALL);
            if (blockEntityData != null) {
                BlockEntity restored = BlockEntity.loadStatic(
                        pos, beforeState, blockEntityData, level.registryAccess());
                if (restored != null) level.setBlockEntity(restored);
            }
        }
    }

    private record Supply(ItemStack stack, boolean fromAe) {
    }
}
