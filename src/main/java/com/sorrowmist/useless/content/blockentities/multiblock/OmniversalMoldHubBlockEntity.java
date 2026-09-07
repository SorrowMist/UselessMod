package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.blockentities.PagedMenuPageMemory;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import com.sorrowmist.useless.content.menus.OmniversalMoldHubMenu;
import com.sorrowmist.useless.core.component.MultiblockPartData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmniversalMoldHubBlockEntity extends BlockEntity implements MenuProvider {
    private final RecoverableItemStackHandler molds = new RecoverableItemStackHandler(
            ConfigManager::getOmniversalMoldSlots,
            this::isValidMold,
            this::moldInventoryChanged);
    private final PagedMenuPageMemory pageMemory = new PagedMenuPageMemory(this::setChanged);
    private final Map<List<Ingredient>, Boolean> moldMatchCache = new HashMap<>();
    @Nullable
    private Map<Integer, ItemStack> cachedAvailableMolds;
    @Nullable
    private MoldMatcher.PreparedMolds cachedPreparedMolds;
    private int cachedActiveSlots = -1;
    private long cachedRecipeCatalogGeneration = -1L;
    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;

    public OmniversalMoldHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMNIVERSAL_MOLD_HUB.get(), pos, state);
    }

    public RecoverableItemStackHandler getMolds() {
        return molds;
    }

    public PagedMenuPageMemory getPageMemory() {
        return pageMemory;
    }

    public MultiblockPartData createItemData(HolderLookup.Provider registries) {
        return MultiblockPartData.inventory(molds, registries);
    }

    public void restoreItemData(MultiblockPartData data, HolderLookup.Provider registries) {
        if (data == null) return;
        data.restoreInventory(molds, registries);
        moldInventoryChanged();
    }

    private boolean isValidMold(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModTags.MOLDS)
                || AlloyFurnaceRecipeCatalog.isKnownMold(level, stack));
    }

    public boolean containsMold(Ingredient ingredient) {
        return containsMolds(ingredient == null ? List.of() : List.of(ingredient));
    }

    /** Returns whether the active mold slots can satisfy every independent requirement. */
    public boolean containsMolds(List<Ingredient> requirements) {
        List<Ingredient> normalized = MoldMatcher.normalizeRequirements(requirements);
        if (normalized.isEmpty()) return true;
        refreshMoldMatchCache();
        Boolean cached = moldMatchCache.get(normalized);
        if (cached != null) return cached;

        boolean matched = getPreparedMolds().matches(normalized);
        moldMatchCache.put(normalized, matched);
        return matched;
    }

    private Map<Integer, ItemStack> getAvailableMolds() {
        if (cachedAvailableMolds == null) {
            int activeSlots = molds.getActiveSlots();
            Map<Integer, ItemStack> available = new LinkedHashMap<>();
            for (int slot = 0; slot < activeSlots; slot++) {
                ItemStack mold = molds.getStackInSlot(slot);
                if (!mold.isEmpty()) available.put(slot, mold.copy());
            }
            cachedAvailableMolds = Collections.unmodifiableMap(available);
        }
        return cachedAvailableMolds;
    }

    private MoldMatcher.PreparedMolds getPreparedMolds() {
        if (cachedPreparedMolds == null) {
            cachedPreparedMolds = MoldMatcher.prepare(getAvailableMolds());
        }
        return cachedPreparedMolds;
    }

    /**
     * Tests the one-to-one assignment independently of inventory storage. Each available stack is
     * one device slot, regardless of its item count; augmenting paths handle overlapping ingredients.
     */
    public static boolean matchesMolds(List<Ingredient> requirements, List<ItemStack> available) {
        return MoldMatcher.matches(requirements, available);
    }

    public static boolean matchesMolds(
            List<Ingredient> requirements, Map<Integer, ItemStack> available) {
        return MoldMatcher.matches(requirements, available);
    }

    private void refreshMoldMatchCache() {
        int activeSlots = molds.getActiveSlots();
        long catalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (cachedActiveSlots != activeSlots || cachedRecipeCatalogGeneration != catalogGeneration) {
            moldMatchCache.clear();
            cachedAvailableMolds = null;
            cachedPreparedMolds = null;
            cachedActiveSlots = activeSlots;
            cachedRecipeCatalogGeneration = catalogGeneration;
        }
    }

    private void moldInventoryChanged() {
        moldMatchCache.clear();
        cachedAvailableMolds = null;
        cachedPreparedMolds = null;
        setChanged();
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!java.util.Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            setChanged();
        }
    }

    @Nullable
    public MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (unloading || isRemoved() || level == null || controllerPos == null
                || !level.isLoaded(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.isMoldHubLinked(worldPosition, structureGeneration)) {
            return core;
        }
        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.omniversal_mold_hub");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OmniversalMoldHubMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        molds.deserializeNBT(registries, tag.getCompound("Molds"));
        moldMatchCache.clear();
        cachedAvailableMolds = null;
        cachedPreparedMolds = null;
        cachedActiveSlots = -1;
        cachedRecipeCatalogGeneration = -1L;
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
        pageMemory.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Molds", molds.serializeNBT(registries));
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
        pageMemory.save(tag);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unloading = true;
        super.setRemoved();
    }
}
