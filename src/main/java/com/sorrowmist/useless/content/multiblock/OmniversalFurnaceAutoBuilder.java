package com.sorrowmist.useless.content.multiblock;

import appeng.api.config.Actionable;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmniversalFurnaceAutoBuilder {
    private OmniversalFurnaceAutoBuilder() {
    }

    public record Result(boolean success, Component message) {
    }

    public static Result build(ServerPlayer player, ItemStack tool, BlockPos corePos) {
        ServerLevel level = player.serverLevel();
        BlockState coreState = level.getBlockState(corePos);
        if (!coreState.is(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get())) {
            return failure("gui.useless_mod.multiblock_builder.invalid_core");
        }
        Direction facing = coreState.getValue(MultiblockAlloyFurnaceCoreBlock.FACING);

        int existingTier = 0;
        for (OmniversalAlloyFurnaceStructure.Entry entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() != OmniversalAlloyFurnaceStructure.Part.COIL) continue;
            Block block = level.getBlockState(entry.worldPos(corePos, facing)).getBlock();
            if (block instanceof UselessCoilBlock coil) {
                if (existingTier == 0) existingTier = coil.tier();
                else if (existingTier != coil.tier()) {
                    return failure("gui.useless_mod.multiblock_builder.mixed_coils");
                }
            }
        }

        int coilTier = existingTier;
        if (coilTier == 0) coilTier = coilTier(player.getOffhandItem());
        if (coilTier == 0 && player.getAbilities().instabuild) coilTier = UselessCoilBlock.MAX_TIER;
        if (coilTier == 0) {
            for (int candidate = UselessCoilBlock.MAX_TIER;
                 candidate >= UselessCoilBlock.MIN_TIER; candidate--) {
                int missing = missingCoils(level, corePos, facing, candidate);
                ItemStack coil = new ItemStack(ModBlocks.USELESS_COILS.get(candidate).get(), missing);
                long available = AE2Compat.tryExtractFromLinkedGrid(tool, player, coil, Actionable.SIMULATE)
                        + countInventory(player, coil.getItem());
                if (available >= missing) {
                    coilTier = candidate;
                    break;
                }
            }
        }
        if (coilTier == 0) return failure("gui.useless_mod.multiblock_builder.missing_coils");

        List<Placement> placements = new ArrayList<>();
        Map<Item, Integer> requirements = new LinkedHashMap<>();
        for (OmniversalAlloyFurnaceStructure.Entry entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() == OmniversalAlloyFurnaceStructure.Part.CORE) continue;
            BlockPos pos = entry.worldPos(corePos, facing);
            if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)
                    || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, tool)) {
                return failure("gui.useless_mod.multiblock_builder.no_permission");
            }
            BlockState current = level.getBlockState(pos);
            BlockState expected = expectedState(entry.part(), coilTier);
            if (matches(entry.part(), current, coilTier)) continue;
            if (level.getBlockEntity(pos) != null || (!current.canBeReplaced() && !current.isAir())) {
                return failure("gui.useless_mod.multiblock_builder.blocked", pos.toShortString());
            }
            placements.add(new Placement(pos.immutable(), current, expected));
            if (!expected.isAir()) requirements.merge(expected.getBlock().asItem(), 1, Integer::sum);
        }

        if (!player.getAbilities().instabuild && !hasMaterials(player, tool, requirements)) {
            return failure("gui.useless_mod.multiblock_builder.missing_materials");
        }

        MaterialTransaction materials = new MaterialTransaction(player, tool);
        if (!player.getAbilities().instabuild && !materials.commit(requirements)) {
            materials.rollback();
            return failure("gui.useless_mod.multiblock_builder.commit_failed");
        }

        List<Placement> completed = new ArrayList<>();
        for (Placement placement : placements) {
            if (!level.setBlock(placement.pos, placement.expected, 3)) {
                for (int index = completed.size() - 1; index >= 0; index--) {
                    Placement previous = completed.get(index);
                    level.setBlock(previous.pos, previous.original, 3);
                }
                materials.rollback();
                return failure("gui.useless_mod.multiblock_builder.commit_failed");
            }
            completed.add(placement);
        }

        if (level.getBlockEntity(corePos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core) {
            core.requestStructureValidation();
            core.validateStructure();
        }
        return new Result(true, Component.translatable(
                "gui.useless_mod.multiblock_builder.success", coilTier));
    }

    private static boolean hasMaterials(ServerPlayer player, ItemStack tool, Map<Item, Integer> requirements) {
        for (Map.Entry<Item, Integer> entry : requirements.entrySet()) {
            ItemStack requested = new ItemStack(entry.getKey(), entry.getValue());
            long network = AE2Compat.tryExtractFromLinkedGrid(tool, player, requested, Actionable.SIMULATE);
            if (network + countInventory(player, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    private static int missingCoils(ServerLevel level, BlockPos corePos, Direction facing, int tier) {
        int missing = 0;
        for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() == OmniversalAlloyFurnaceStructure.Part.COIL
                    && !matches(entry.part(), level.getBlockState(entry.worldPos(corePos, facing)), tier)) {
                missing++;
            }
        }
        return missing;
    }

    private static int coilTier(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof UselessCoilBlock coil ? coil.tier() : 0;
    }

    private static boolean matches(OmniversalAlloyFurnaceStructure.Part part, BlockState state, int coilTier) {
        return switch (part) {
            case CORE -> state.is(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get());
            case PATTERN_ASSEMBLY -> state.is(ModBlocks.ME_PATTERN_ASSEMBLY.get());
            case MOLD_HUB -> state.is(ModBlocks.OMNIVERSAL_MOLD_HUB.get());
            case CASING -> state.is(ModTags.OMNIVERSAL_FURNACE_CASINGS);
            case COIL -> state.getBlock() instanceof UselessCoilBlock coil && coil.tier() == coilTier;
            case AIR -> state.isAir();
        };
    }

    private static BlockState expectedState(OmniversalAlloyFurnaceStructure.Part part, int coilTier) {
        return switch (part) {
            case PATTERN_ASSEMBLY -> ModBlocks.ME_PATTERN_ASSEMBLY.get().defaultBlockState();
            case MOLD_HUB -> ModBlocks.OMNIVERSAL_MOLD_HUB.get().defaultBlockState();
            case CASING -> ModBlocks.OMNIVERSAL_FURNACE_CASING.get().defaultBlockState();
            case COIL -> ModBlocks.USELESS_COILS.get(coilTier).get().defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
            case CORE -> ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get().defaultBlockState();
        };
    }

    private static int countInventory(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Result failure(String key, Object... arguments) {
        return new Result(false, Component.translatable(key, arguments));
    }

    private record Placement(BlockPos pos, BlockState original, BlockState expected) {
    }

    private static final class MaterialTransaction {
        private final ServerPlayer player;
        private final ItemStack tool;
        private final Map<Item, Integer> fromNetwork = new LinkedHashMap<>();
        private final List<InventoryRemoval> fromInventory = new ArrayList<>();

        private MaterialTransaction(ServerPlayer player, ItemStack tool) {
            this.player = player;
            this.tool = tool;
        }

        private boolean commit(Map<Item, Integer> requirements) {
            for (Map.Entry<Item, Integer> requirement : requirements.entrySet()) {
                int needed = requirement.getValue();
                ItemStack requested = new ItemStack(requirement.getKey(), needed);
                int network = (int) Math.min(needed,
                        AE2Compat.tryExtractFromLinkedGrid(tool, player, requested, Actionable.MODULATE));
                if (network > 0) fromNetwork.merge(requirement.getKey(), network, Integer::sum);
                needed -= network;
                for (int slot = 0; slot < player.getInventory().getContainerSize() && needed > 0; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.is(requirement.getKey())) continue;
                    int removed = Math.min(needed, stack.getCount());
                    stack.shrink(removed);
                    fromInventory.add(new InventoryRemoval(slot, requirement.getKey(), removed));
                    needed -= removed;
                }
                if (needed > 0) return false;
            }
            player.getInventory().setChanged();
            return true;
        }

        private void rollback() {
            for (Map.Entry<Item, Integer> entry : fromNetwork.entrySet()) {
                ItemStack stack = new ItemStack(entry.getKey(), entry.getValue());
                long inserted = AE2Compat.tryInsertIntoLinkedGrid(tool, player, stack, Actionable.MODULATE);
                int remaining = entry.getValue() - (int) inserted;
                if (remaining > 0) player.getInventory().placeItemBackInInventory(
                        new ItemStack(entry.getKey(), remaining));
            }
            for (InventoryRemoval removal : fromInventory) {
                ItemStack current = player.getInventory().getItem(removal.slot);
                if (current.isEmpty()) {
                    player.getInventory().setItem(removal.slot, new ItemStack(removal.item, removal.count));
                } else if (current.is(removal.item)) {
                    current.grow(removal.count);
                } else {
                    player.getInventory().placeItemBackInInventory(new ItemStack(removal.item, removal.count));
                }
            }
            fromNetwork.clear();
            fromInventory.clear();
            player.getInventory().setChanged();
        }
    }

    private record InventoryRemoval(int slot, Item item, int count) {
    }
}
