package com.sorrowmist.useless.content.menus;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class OmniversalPatternEncoderMenu extends AbstractContainerMenu {
    public static final int PREVIOUS_CANDIDATE = 0;
    public static final int NEXT_CANDIDATE = 1;
    public static final int SELECT_CANDIDATE_BASE = 100;

    private final Inventory playerInventory;
    private final SimpleContainer source = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private List<AlloyFurnaceRecipeCatalog.Entry> candidates = List.of();
    private int selectedCandidate = -1;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? selectedCandidate : candidates.size();
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) selectedCandidate = value;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public OmniversalPatternEncoderMenu(int containerId, Inventory inventory) {
        super(ModMenuType.OMNIVERSAL_PATTERN_ENCODER_MENU.get(), containerId);
        this.playerInventory = inventory;
        addSlot(new Slot(source, 0, 26, 19) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                var details = PatternDetailsHelper.decodePattern(stack, playerInventory.player.level());
                return details instanceof AEProcessingPattern && !(details instanceof OmniversalPatternDetails);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(result, 0, 53, 19) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                source.removeItem(0, 1);
                recomputeCandidates();
                super.onTake(player, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
        addDataSlots(data);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == source) recomputeCandidates();
    }

    private void recomputeCandidates() {
        ItemStack stack = source.getItem(0);
        var details = PatternDetailsHelper.decodePattern(stack, playerInventory.player.level());
        candidates = details instanceof AEProcessingPattern
                ? AlloyFurnaceRecipeCatalog.findPatternCandidates(playerInventory.player.level(), details)
                : List.of();
        if (candidates.isEmpty()) {
            selectedCandidate = -1;
            result.setItem(0, ItemStack.EMPTY);
        } else if (candidates.size() == 1) {
            selectedCandidate = 0;
            result.setItem(0, OmniversalPatternEncoding.encode(
                    stack, candidates.getFirst(), playerInventory.player.level()));
        } else {
            selectedCandidate = -1;
            result.setItem(0, ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    public List<AlloyFurnaceRecipeCatalog.Entry> getCandidates() {
        return candidates;
    }

    public int getSelectedCandidate() {
        return selectedCandidate;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (candidates.isEmpty()) return false;
        if (id == PREVIOUS_CANDIDATE) {
            selectedCandidate = selectedCandidate < 0 ? candidates.size() - 1 : selectedCandidate - 1;
        } else if (id == NEXT_CANDIDATE) {
            selectedCandidate = selectedCandidate < 0 ? 0 : selectedCandidate + 1;
        }
        else if (id >= SELECT_CANDIDATE_BASE && id < SELECT_CANDIDATE_BASE + candidates.size()) {
            selectedCandidate = id - SELECT_CANDIDATE_BASE;
        } else return false;
        selectedCandidate = Math.floorMod(selectedCandidate, candidates.size());
        result.setItem(0, OmniversalPatternEncoding.encode(
                source.getItem(0), candidates.get(selectedCandidate), player.level()));
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index == 1) {
            if (!moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickCraft(original, copy);
        } else if (index == 0) {
            if (!moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (slots.get(0).mayPlace(original)) {
            if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;

        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (original.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem().getItem() instanceof com.sorrowmist.useless.content.items.OmniversalPatternEncoderItem
                || player.getOffhandItem().getItem() instanceof com.sorrowmist.useless.content.items.OmniversalPatternEncoderItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, source);
    }
}
